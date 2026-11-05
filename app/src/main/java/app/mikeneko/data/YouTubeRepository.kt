package app.mikeneko.data

import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import app.mikeneko.settings.AppLocale
import app.mikeneko.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Extractor
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/** Tout ce qui parle à YouTube passe par NewPipeExtractor : pas d'API Google, pas de pubs. */
object YouTubeRepository {
    private val yt get() = ServiceList.YouTube

    /**
     * NewPipeExtractor n'annonce que l'anglais (en-GB) pour YouTube et remplace toute autre langue
     * par celle-là : YouTube renvoie alors les titres traduits automatiquement en anglais. On impose
     * donc la langue (et le pays) de l'appli à chaque requête.
     */
    private val clientLock = Mutex()
    @Volatile private var clientKnown = false

    /**
     * NewPipe cherche la version du client YouTube à la première requête, sans verrou : au démarrage,
     * chaque requête lancée en parallèle retéléchargeait une page de ~900 Ko (7 fois, ~6 Mo), et ce
     * trafic fait passer l'appli pour un robot aux yeux de YouTube. On la cherche une seule fois :
     * les autres requêtes attendent le résultat.
     */
    suspend fun ensureClient() {
        if (clientKnown) return
        clientLock.withLock {
            if (clientKnown) return
            withContext(Dispatchers.IO) { runCatching { YoutubeParsingHelper.getClientVersion() } }
                .onSuccess { clientKnown = true } // en cas d'échec, la requête suivante réessaiera (une à la fois)
        }
    }

    /** Une requête à YouTube : version du client connue d'abord, puis le travail en arrière-plan. */
    internal suspend fun <T> youtube(block: suspend CoroutineScope.() -> T): T {
        ensureClient()
        return withContext(Dispatchers.IO, block)
    }

    fun <T : Extractor> T.localized(): T = apply {
        forceLocalization(Localization(AppLocale.language()))
        forceContentCountry(ContentCountry(AppLocale.region()))
    }

    /** Accueil, recherches et radios restent 10 min en mémoire. */
    private val lists = TimedCache<List<VideoItem>>(10 * 60_000L)
    private const val HOME = "accueil"


    /** Changement de langue / région : tout est à recharger. */
    fun clearCache() = lists.clear()
    fun cachedSearch(query: String): List<VideoItem>? = lists.peek("recherche:$query")
    fun cachedKiosk(id: String): List<VideoItem>? = lists.peek("kiosque:$id")

    /** Un des fils de YouTube (« live », « trending_gaming »…), voir kioskList.availableKiosks. */
    suspend fun kiosk(id: String, fresh: Boolean = false): List<VideoItem> = lists.get("kiosque:$id", fresh) {
        youtube {
            val extractor = yt.kioskList.getExtractorById(id, null).localized()
            extractor.fetchPage()
            extractor.initialPage.items.filterIsInstance<StreamInfoItem>().map { it.toVideo() }.distinctBy { it.url }
        }
    }

    /**
     * « Pour toi » sans compte : un mélange de plusieurs fils et recherches, entrelacés pour
     * qu'aucun thème ne prenne toute la place, sans directs. (Le fil « Tendances » de YouTube
     * a disparu, et le fil par défaut de NewPipe n'est plus fait que de directs.)
     * Ces sources servent aussi aux rubriques : les ouvrir ensuite est instantané.
     */
    suspend fun home(fresh: Boolean = false): List<VideoItem> = lists.get("$HOME:${AppLocale.language()}:${AppLocale.region()}", fresh) {
        coroutineScope {
            val sources = (HOME_KIOSKS.map { id -> async { runCatching { kiosk(id) }.getOrDefault(emptyList()) } } +
                HOME_SEARCHES.map { q -> async { runCatching { search(q) }.getOrDefault(emptyList()) } })
                .awaitAll()
                // Rafraîchi : d'autres vidéos de chaque source (pas forcément les 8 premières), sans recharger
                .map { list -> list.filterNot { it.isLive }.let { if (fresh) it.shuffled() else it }.take(PER_SOURCE) }
                .filter { it.isNotEmpty() }
                .shuffled() // un ordre différent à chaque rafraîchissement (toutes les 10 min)
            interleave(sources).distinctBy { it.url }
        }
    }

    /** a1, b1, c1, a2, b2, c2… */
    private fun interleave(lists: List<List<VideoItem>>): List<VideoItem> {
        val longest = lists.maxOfOrNull { it.size } ?: 0
        return (0 until longest).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } }
    }

    private val HOME_KIOSKS = listOf("trending_gaming", "trending_podcasts_episodes", "trending_movies_and_shows")
    private val HOME_SEARCHES get() = listOf(R.string.query_science, R.string.query_comedy, R.string.query_cooking, R.string.query_sport).map { AppLocale.string(it) }
    private const val PER_SOURCE = 8

    /** [fresh] : recharge sans le cache, avec la page suivante en plus (plus de vidéos à montrer). */
    suspend fun search(query: String, fresh: Boolean = false): List<VideoItem> = lists.get("recherche:$query", fresh) {
        youtube {
            val extractor = yt.getSearchExtractor(yt.searchQHFactory.fromQuery(query)).localized()
            extractor.fetchPage()
            val info = SearchInfo.getInfo(extractor)
            val more = if (fresh && info.hasNextPage()) {
                runCatching { extractor.getPage(info.nextPage).items }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            (info.relatedItems + more).filterIsInstance<StreamInfoItem>().map { it.toVideo() }.distinctBy { it.url }
        }
    }

    private val searchPages = TimedCache<SearchPage>(10 * 60_000L)

    fun cachedSearchPage(query: String): SearchPage? = searchPages.peek(query)

    /**
     * Une recherche tapée : les deux premières pages d'un coup (une quarantaine de vidéos à trier),
     * avec les chaînes. [SearchPage.next] sert à charger la suite en défilant ([searchMore]).
     */
    suspend fun searchFirst(query: String, fresh: Boolean = false): SearchPage = searchPages.get(query, fresh) {
        youtube {
            val extractor = yt.getSearchExtractor(yt.searchQHFactory.fromQuery(query)).localized()
            extractor.fetchPage()
            val first = extractor.initialPage
            val page = searchPage(first.items, first.nextPage)
            val second = page.next?.let { next -> runCatching { extractor.getPage(next) }.getOrNull() }
            if (second == null) page else page + searchPage(second.items, second.nextPage)
        }
    }

    /** La page suivante d'une recherche. */
    suspend fun searchMore(query: String, next: Page): SearchPage = youtube {
        val more = yt.getSearchExtractor(yt.searchQHFactory.fromQuery(query)).localized().getPage(next)
        searchPage(more.items, more.nextPage)
    }

    private val channelSearches = TimedCache<List<ChannelItem>>(10 * 60_000L)

    /** Les chaînes qui correspondent à une recherche (la recherche normale en renvoie peu). */
    suspend fun searchChannels(query: String): List<ChannelItem> = channelSearches.get(query) {
        youtube {
            val handler = yt.searchQHFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.CHANNELS), "")
            val extractor = yt.getSearchExtractor(handler).localized()
            extractor.fetchPage()
            extractor.initialPage.items.filterIsInstance<ChannelInfoItem>().map { it.toChannel() }.distinctBy { it.url }
        }
    }

    /**
     * L'avatar d'une chaîne seul (une requête, la page de la chaîne sans ses vidéos), pour les
     * abonnements importés sans image. Une taille moyenne : il s'affiche en 60 dp.
     */
    suspend fun channelAvatar(channelId: String): String? = youtube {
        val extractor = yt.getChannelExtractor("https://www.youtube.com/channel/$channelId").localized()
        extractor.fetchPage()
        val avatars = extractor.avatars
        (avatars.filter { it.height >= 176 }.minByOrNull { it.height } ?: avatars.maxByOrNull { it.height })?.url
    }

    /** Une chaîne : ses infos et ses dernières vidéos ([ChannelPage.next] : la suite). */
    suspend fun channel(url: String): ChannelPage = youtube {
        val extractor = yt.getChannelExtractor(url).localized()
        extractor.fetchPage()
        val info = ChannelInfo.getInfo(extractor)
        val tab = info.tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters }
        val videos = tab?.let {
            val tabExtractor = yt.getChannelTabExtractor(it).localized()
            tabExtractor.fetchPage()
            ChannelTabInfo.getInfo(tabExtractor)
        }
        // Les onglets qu'on sait afficher, dans l'ordre de YouTube : vidéos, shorts, directs
        val tabs = info.tabs
            .filter { t -> CHANNEL_TABS.any { it in t.contentFilters } }
            .sortedBy { t -> CHANNEL_TABS.indexOfFirst { it in t.contentFilters } }
        ChannelPage(
            id = info.id,
            url = info.url,
            name = info.name,
            avatar = info.avatars.maxByOrNull { it.height }?.url,
            banner = info.banners.maxByOrNull { it.width }?.url,
            subscribers = info.subscriberCount,
            verified = info.isVerified,
            description = info.description.orEmpty(),
            videos = videos?.relatedItems.orEmpty().channelVideos(info.name),
            tab = tab,
            next = videos?.nextPage?.takeIf { Page.isValid(it) },
            tabs = tabs,
        )
    }

    /** L'onglet Playlists d'une chaîne, et de quoi charger la suite. */
    suspend fun channelPlaylists(tab: ListLinkHandler): Pair<List<PlaylistItem>, Page?> = youtube {
        val extractor = yt.getChannelTabExtractor(tab).localized()
        extractor.fetchPage()
        val info = ChannelTabInfo.getInfo(extractor)
        info.relatedItems.playlists() to info.nextPage?.takeIf { Page.isValid(it) }
    }

    suspend fun channelPlaylistsMore(tab: ListLinkHandler, next: Page): Pair<List<PlaylistItem>, Page?> = youtube {
        val more = yt.getChannelTabExtractor(tab).localized().getPage(next)
        more.items.playlists() to more.nextPage?.takeIf { Page.isValid(it) }
    }

    private fun List<InfoItem>.playlists() = filterIsInstance<PlaylistInfoItem>().map {
        PlaylistItem(it.url, it.name, it.thumbnails.maxByOrNull { t -> t.height }?.url, it.streamCount, it.uploaderName.orEmpty())
    }.distinctBy { it.url }

    /** Les vidéos d'une playlist, et de quoi charger la suite. */
    suspend fun playlist(url: String): Pair<List<VideoItem>, Page?> = youtube {
        val extractor = yt.getPlaylistExtractor(url).localized()
        extractor.fetchPage()
        val info = PlaylistInfo.getInfo(extractor)
        info.relatedItems.map { it.toVideo() }.distinctBy { it.url } to info.nextPage?.takeIf { Page.isValid(it) }
    }

    suspend fun playlistMore(url: String, next: Page): Pair<List<VideoItem>, Page?> = youtube {
        val more = PlaylistInfo.getMoreItems(yt, url, next)
        more.items.map { it.toVideo() } to more.nextPage?.takeIf { Page.isValid(it) }
    }

    /** Le début d'un onglet de chaîne (shorts, directs…) et de quoi charger la suite. */
    suspend fun channelTab(tab: ListLinkHandler, name: String): Pair<List<VideoItem>, Page?> = youtube {
        val extractor = yt.getChannelTabExtractor(tab).localized()
        extractor.fetchPage()
        val info = ChannelTabInfo.getInfo(extractor)
        info.relatedItems.channelVideos(name) to info.nextPage?.takeIf { Page.isValid(it) }
    }

    /**
     * Les dernières vidéos d'une chaîne (onglet « Vidéos »), sans charger toute sa page : le secours
     * des abonnements quand le flux RSS de YouTube ne répond pas.
     */
    suspend fun channelLatest(channelId: String, name: String): List<VideoItem> = youtube {
        val extractor = yt.getChannelTabExtractorFromId("channel/$channelId", ChannelTabs.VIDEOS).localized()
        extractor.fetchPage()
        extractor.initialPage.items.channelVideos(name)
    }

    /** La suite des vidéos d'une chaîne. */
    suspend fun channelMore(tab: ListLinkHandler, name: String, next: Page): Pair<List<VideoItem>, Page?> = youtube {
        val more = yt.getChannelTabExtractor(tab).localized().getPage(next)
        more.items.channelVideos(name) to more.nextPage?.takeIf { Page.isValid(it) }
    }

    /** Les vidéos d'une chaîne n'indiquent pas toujours leur chaîne : on la remet. */
    private fun List<InfoItem>.channelVideos(name: String) =
        filterIsInstance<StreamInfoItem>().map { it.toVideo() }.map { if (it.channel.isEmpty()) it.copy(channel = name) else it }.distinctBy { it.url }

    /** Suggestions de YouTube pendant la frappe (« recette cr » → « recette crêpes »…). */
    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        yt.suggestionExtractor.apply {
            forceLocalization(Localization(AppLocale.language()))
            forceContentCountry(ContentCountry(AppLocale.region()))
        }.suggestionList(query)
    }

    suspend fun stream(url: String): StreamInfo = youtube {
        val extractor = yt.getStreamExtractor(url).localized()
        extractor.fetchPage()
        StreamInfo.getInfo(extractor)
    }
}

fun StreamInfoItem.toVideo() = VideoItem(
    url = url,
    title = name,
    channel = uploaderName.orEmpty(),
    thumbnail = thumbnails.maxByOrNull { it.height }?.url,
    durationSec = duration,
    views = viewCount,
    isLive = streamType == StreamType.LIVE_STREAM || streamType == StreamType.AUDIO_LIVE_STREAM,
    // La date calculée par NewPipe, formatée en français (le texte brut de YouTube est souvent en anglais)
    uploaded = uploadDate?.offsetDateTime()?.toInstant()?.let(::formatAgo) ?: textualUploadDate,
    channelId = uploaderUrl?.let(::channelIdFrom),
    uploadedAt = uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli(),
    isShort = isShortFormContent || "/shorts/" in url,
)

fun ChannelInfoItem.toChannel() = ChannelItem(
    url = url,
    name = name,
    avatar = thumbnails.maxByOrNull { it.height }?.url,
    channelId = channelIdFrom(url),
    subscribers = subscriberCount,
    videos = streamCount,
    verified = isVerified,
)

/** Une chaîne ouverte : ses infos et ses vidéos (la suite avec [tab] et [next]). */
class ChannelPage(
    val id: String,
    val url: String,
    val name: String,
    val avatar: String?,
    val banner: String?,
    val subscribers: Long,
    val verified: Boolean,
    val description: String,
    val videos: List<VideoItem>,
    val tab: ListLinkHandler?,
    val next: Page?,
    /** Les onglets affichables ([CHANNEL_TABS]) que la chaîne a vraiment. */
    val tabs: List<ListLinkHandler>,
)

/** Les onglets de chaîne que Mikeneko sait afficher, dans l'ordre. */
val CHANNEL_TABS = listOf(ChannelTabs.VIDEOS, ChannelTabs.SHORTS, ChannelTabs.LIVESTREAMS, ChannelTabs.PLAYLISTS)

/** L'onglet ([CHANNEL_TABS]) que montre ce lien. */
fun ListLinkHandler.channelTabKind(): String? = CHANNEL_TABS.firstOrNull { it in contentFilters }

/** Une page de résultats de recherche : vidéos, chaînes, et de quoi charger la suite. */
class SearchPage(val videos: List<VideoItem>, val channels: List<ChannelItem>, val next: Page?) {
    operator fun plus(other: SearchPage) = SearchPage(
        (videos + other.videos).distinctBy { it.url },
        (channels + other.channels).distinctBy { it.url },
        other.next,
    )
}

private fun searchPage(items: List<InfoItem>, next: Page?) = SearchPage(
    items.filterIsInstance<StreamInfoItem>().map { it.toVideo() }.distinctBy { it.url },
    items.filterIsInstance<ChannelInfoItem>().map { it.toChannel() }.distinctBy { it.url },
    next?.takeIf { Page.isValid(it) },
)
