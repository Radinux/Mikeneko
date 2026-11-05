package app.mikeneko.data

import androidx.core.text.HtmlCompat
import androidx.annotation.StringRes
import app.mikeneko.R
import app.mikeneko.data.YouTubeRepository.localized
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** Un album ou une playlist trouvé sur YouTube Music. */
data class MusicCollection(
    val url: String,
    val title: String,
    val artist: String,
    val thumbnail: String?,
    val count: Long = -1,
)

/** Une ambiance de la Radio : une capsule, et la recherche de lives qui va avec. */
data class Ambiance(val id: String, @StringRes val label: Int, val query: String)

/** Ce qu'on cherche dans la recherche musique (un onglet chacun). */
enum class MusicKind(val filter: String) {
    Songs(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
    Albums(YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS),
    Artists(YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS),
    Playlists(YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS),
}

/** Une page de morceaux (album, playlist, mix) et de quoi charger la suite. */
class TrackPage(val tracks: List<VideoItem>, val next: Page?)

/**
 * La musique : recherche YouTube Music, albums, mix et lives par ambiance. Tout passe par
 * NewPipeExtractor, comme le reste (voir [YouTubeRepository]).
 */
object Music {
    private val yt get() = ServiceList.YouTube

    val AMBIANCES = listOf(
        Ambiance("lofi", R.string.ambiance_lofi, "lofi hip hop radio live"),
        Ambiance("chill", R.string.ambiance_chill, "chill music radio live"),
        Ambiance("jazz", R.string.ambiance_jazz, "jazz radio live"),
        Ambiance("classique", R.string.ambiance_classical, "classical music radio live"),
        Ambiance("piano", R.string.ambiance_piano, "relaxing piano radio live"),
        Ambiance("ambient", R.string.ambiance_ambient, "ambient music radio live"),
        Ambiance("synthwave", R.string.ambiance_synthwave, "synthwave radio live"),
        Ambiance("electro", R.string.ambiance_electro, "electronic music radio live"),
        Ambiance("rap_fr", R.string.ambiance_rap_fr, "rap français radio live"),
        Ambiance("rock", R.string.ambiance_rock, "rock music radio live"),
        Ambiance("jeux", R.string.ambiance_games, "video game music radio live"),
        Ambiance("kpop", R.string.ambiance_kpop, "kpop radio live"),
    )

    fun ambiance(id: String): Ambiance = AMBIANCES.firstOrNull { it.id == id } ?: AMBIANCES.first()

    private val lives = TimedCache<List<VideoItem>>(10 * 60_000L)
    private val songs = TimedCache<List<VideoItem>>(10 * 60_000L)
    private val collections = TimedCache<List<MusicCollection>>(10 * 60_000L)
    private val artists = TimedCache<List<ChannelItem>>(10 * 60_000L)
    private val albums = TimedCache<TrackPage>(10 * 60_000L)

    fun cachedLives(ambiance: Ambiance): List<VideoItem>? = lives.peek(ambiance.id)

    /** Les lives d'une ambiance, ceux vraiment en direct d'abord (une seule recherche). */
    suspend fun lives(ambiance: Ambiance): List<VideoItem> = lives.get(ambiance.id) {
        val all = YouTubeRepository.search(ambiance.query)
        val live = all.filter { it.isLive }
        live.ifEmpty { all }
    }

    fun cachedSongs(query: String): List<VideoItem>? = songs.peek(query)
    fun cachedCollections(query: String, kind: MusicKind): List<MusicCollection>? = collections.peek("${kind.name}:$query")
    fun cachedArtists(query: String): List<ChannelItem>? = artists.peek(query)

    /** Les morceaux qui correspondent à [query] sur YouTube Music. */
    suspend fun songs(query: String): List<VideoItem> = songs.get(query) {
        searchItems(query, MusicKind.Songs).filterIsInstance<StreamInfoItem>().map { it.toTrack() }.distinctBy { it.url }
    }

    /** Albums ([MusicKind.Albums]) ou playlists ([MusicKind.Playlists]) qui correspondent à [query]. */
    suspend fun collections(query: String, kind: MusicKind): List<MusicCollection> = collections.get("${kind.name}:$query") {
        searchItems(query, kind).filterIsInstance<PlaylistInfoItem>().map { it.toCollection() }.distinctBy { it.url }
    }

    suspend fun artists(query: String): List<ChannelItem> = artists.get(query) {
        searchItems(query, MusicKind.Artists).filterIsInstance<ChannelInfoItem>()
            .map { it.toChannel().let { c -> c.copy(url = normalizeChannel(c.url)) } }
            .distinctBy { it.url }
    }

    private suspend fun searchItems(query: String, kind: MusicKind) = YouTubeRepository.youtube {
        val handler = yt.searchQHFactory.fromQuery(query, listOf(kind.filter), "")
        val extractor = yt.getSearchExtractor(handler).localized()
        extractor.fetchPage()
        extractor.initialPage.items
    }

    /**
     * L'album d'un morceau : son nom vient de la description que YouTube écrit pour les morceaux de
     * YouTube Music (« Provided to YouTube by… »), puis une recherche d'albums le retrouve.
     * null pour un clip ou une vidéo qui n'a pas cette description.
     */
    suspend fun albumOf(artist: String, description: String): MusicCollection? {
        val name = albumNameFrom(description) ?: return null
        val found = collections("$name $artist", MusicKind.Albums)
        return found.firstOrNull { it.title.equals(name, ignoreCase = true) }
            ?: found.firstOrNull { it.title.contains(name, ignoreCase = true) || name.contains(it.title, ignoreCase = true) }
    }

    fun cachedAlbum(url: String): TrackPage? = albums.peek(url)

    /** Les morceaux d'un album ou d'une playlist (la pochette de l'album quand un morceau n'en a pas). */
    suspend fun album(collection: MusicCollection): TrackPage = albums.get(collection.url) {
        YouTubeRepository.youtube {
            val extractor = yt.getPlaylistExtractor(collection.url).localized()
            extractor.fetchPage()
            val info = PlaylistInfo.getInfo(extractor)
            TrackPage(
                info.relatedItems.map { it.toTrack(fallbackArtist = collection.artist, fallbackCover = collection.thumbnail) }.distinctBy { it.url },
                info.nextPage?.takeIf { Page.isValid(it) },
            )
        }
    }

    suspend fun albumMore(collection: MusicCollection, next: Page): TrackPage = YouTubeRepository.youtube {
        val more = PlaylistInfo.getMoreItems(yt, collection.url, next)
        TrackPage(
            more.items.map { it.toTrack(fallbackArtist = collection.artist, fallbackCover = collection.thumbnail) },
            more.nextPage?.takeIf { Page.isValid(it) },
        )
    }

    /**
     * Le mix de YouTube à partir d'un morceau (la playlist « RD » + identifiant) : une suite de titres
     * proches, sans fin. [next] : la page suivante, pour continuer.
     */
    suspend fun mix(videoId: String, next: Page? = null): TrackPage = YouTubeRepository.youtube {
        val url = mixUrl(videoId)
        if (next != null) {
            val more = PlaylistInfo.getMoreItems(yt, url, next)
            TrackPage(more.items.map { it.toTrack() }, more.nextPage?.takeIf { Page.isValid(it) })
        } else {
            val extractor = yt.getPlaylistExtractor(url).localized()
            extractor.fetchPage()
            val info = PlaylistInfo.getInfo(extractor)
            TrackPage(info.relatedItems.map { it.toTrack() }, info.nextPage?.takeIf { Page.isValid(it) })
        }
    }

    fun mixUrl(videoId: String) = "https://www.youtube.com/watch?v=$videoId&list=RD$videoId"
}

private val VIDEO_ID = Regex("[?&]v=([\\w-]{11})|youtu\\.be/([\\w-]{11})|/shorts/([\\w-]{11})")
private val LIST_ID = Regex("[?&]list=([\\w-]+)")

/** L'identifiant (11 caractères) d'une vidéo YouTube, ou null (fichier local…). */
fun videoIdOf(url: String): String? = VIDEO_ID.find(url)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }

/** Deux adresses désignent le même morceau (music.youtube.com, paramètres en plus…). */
fun sameTrack(a: String?, b: String?): Boolean {
    if (a == null || b == null) return false
    if (a == b) return true
    val id = videoIdOf(a) ?: return false
    return id == videoIdOf(b)
}

/** Les adresses de YouTube Music deviennent des adresses YouTube classiques (une seule forme partout). */
private fun normalizeWatch(url: String): String = videoIdOf(url)?.let { "https://www.youtube.com/watch?v=$it" } ?: url

private fun normalizePlaylist(url: String): String = LIST_ID.find(url)?.groupValues?.get(1)?.let { "https://www.youtube.com/playlist?list=$it" } ?: url

private fun normalizeChannel(url: String): String = url.replace("://music.youtube.com/", "://www.youtube.com/")

/**
 * Le nom de l'album dans une description générée par YouTube :
 * « Provided to YouTube by … », « Titre · Artiste », puis le nom de l'album, puis « ℗ … ».
 */
internal fun albumNameFrom(description: String): String? {
    val text = HtmlCompat.fromHtml(description.replace("\n", "<br>"), HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
    if (!text.contains("Provided to YouTube", ignoreCase = true) && !text.contains("Auto-generated by YouTube", ignoreCase = true)) return null
    val blocks = text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
    val credits = blocks.indexOfFirst { " · " in it } // « Titre · Artiste »
    if (credits < 0) return null
    val album = blocks.getOrNull(credits + 1)?.lineSequence()?.first()?.trim() ?: return null
    if (album.startsWith("℗") || album.startsWith("©") || album.startsWith("Released on", ignoreCase = true) || album.startsWith("Auto-generated", ignoreCase = true)) return null
    return album.ifEmpty { null }
}

/** « Artiste - Topic » (chaînes automatiques de YouTube) → « Artiste ». */
fun cleanArtist(name: String): String = name.removeSuffix(" - Topic").removeSuffix(" – Topic").trim()

private fun StreamInfoItem.toTrack(fallbackArtist: String? = null, fallbackCover: String? = null): VideoItem {
    val video = toVideo()
    return video.copy(
        url = normalizeWatch(video.url),
        channel = cleanArtist(video.channel).ifEmpty { fallbackArtist.orEmpty() },
        thumbnail = video.thumbnail ?: fallbackCover,
    )
}

private fun PlaylistInfoItem.toCollection() = MusicCollection(
    url = normalizePlaylist(url),
    title = name,
    artist = cleanArtist(uploaderName.orEmpty()),
    thumbnail = thumbnails.maxByOrNull { it.height }?.url,
    count = streamCount,
)
