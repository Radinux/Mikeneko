package app.mikeneko.data

import app.mikeneko.settings.AppLocale
import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonWriter
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.localization.TimeAgoParser
import org.schabi.newpipe.extractor.localization.TimeAgoPatternsManager
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamInfoItemExtractor
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamInfoItemLockupExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector

/** Une liste de vidéos d'une chaîne et le jeton de la suite (null : c'est fini). */
class ChannelVideosPage(val videos: List<VideoItem>, val next: String?)

/**
 * Ce que NewPipe ne sait pas faire sur une chaîne (2.14.0) : ses vidéos **les plus vues** (le tri
 * « Populaires » de l'onglet Vidéos) et la **recherche dans ses vidéos** (l'onglet loupe de YouTube).
 * On envoie nous-mêmes la requête « browse » de YouTube (le même client et les mêmes en-têtes que
 * NewPipe, via ses outils), et on relit les vidéos avec les lecteurs de NewPipe (`videoRenderer`,
 * et le nouveau format `lockupViewModel`).
 */
object ChannelBrowse {
    /** L'onglet Vidéos d'une chaîne (le même paramètre que NewPipe). */
    private const val VIDEOS_PARAMS = "EgZ2aWRlb3PyBgQKAjoA"
    /** L'onglet Recherche d'une chaîne. */
    private const val SEARCH_PARAMS = "EgZzZWFyY2jyBgQKAloA"
    /** Les capsules de tri de l'onglet Vidéos sont toujours dans cet ordre : Récentes, Populaires, Anciennes. */
    private const val POPULAR_CHIP = 1

    /** Les vidéos les plus vues de la chaîne [channelId]. */
    suspend fun popular(channelId: String, name: String): ChannelVideosPage = YouTubeRepository.youtube {
        val videos = browse { it.value("browseId", channelId).value("params", VIDEOS_PARAMS) }
        val token = chipTokens(videos).getOrNull(POPULAR_CHIP) ?: error("pas de tri Populaires")
        read(browse { it.value("continuation", token) }.getArray("onResponseReceivedActions"), name)
    }

    /** Les vidéos de la chaîne [channelId] qui répondent à [query]. */
    suspend fun search(channelId: String, name: String, query: String): ChannelVideosPage = YouTubeRepository.youtube {
        val response = browse { it.value("browseId", channelId).value("params", SEARCH_PARAMS).value("query", query) }
        // Seulement l'onglet affiché (l'en-tête de la chaîne contient d'autres listes)
        read(selectedTab(response), name)
    }

    /** La suite d'une liste ([ChannelVideosPage.next]). */
    suspend fun more(token: String, name: String): ChannelVideosPage = YouTubeRepository.youtube {
        read(browse { it.value("continuation", token) }.getArray("onResponseReceivedActions"), name)
    }

    private fun localization() = Localization(AppLocale.language())

    private fun browse(fill: (com.grack.nanojson.JsonBuilder<JsonObject>) -> Unit): JsonObject {
        val localization = localization()
        val builder = YoutubeParsingHelper.prepareDesktopJsonBuilder(localization, ContentCountry(AppLocale.region()))
        fill(builder)
        val body = JsonWriter.string(builder.done()).toByteArray(Charsets.UTF_8)
        return YoutubeParsingHelper.getJsonPostResponse("browse", body, localization)
    }

    private fun selectedTab(response: JsonObject): Any? =
        response.getObject("contents").getObject("twoColumnBrowseResultsRenderer").getArray("tabs")
            .filterIsInstance<JsonObject>()
            .mapNotNull { tab -> (tab["tabRenderer"] ?: tab["expandableTabRenderer"]) as? JsonObject }
            .firstOrNull { it.getBoolean("selected") }
            ?.get("content")

    /** Les jetons des capsules de tri (nouveau format `chipViewModel`, ou l'ancien `chipCloudChipRenderer`). */
    private fun chipTokens(response: JsonObject): List<String> {
        val tokens = mutableListOf<String>()
        fun walk(node: Any?) {
            when (node) {
                is JsonObject -> for ((key, value) in node) {
                    if ((key == "chipViewModel" || key == "chipCloudChipRenderer") && value is JsonObject) {
                        firstToken(value)?.let(tokens::add)
                    } else {
                        walk(value)
                    }
                }
                is JsonArray -> node.forEach(::walk)
            }
        }
        walk(selectedTab(response))
        return tokens
    }

    /** Le premier jeton de suite (`continuationCommand.token`) sous [node]. */
    private fun firstToken(node: Any?): String? = when (node) {
        is JsonObject -> (node["continuationCommand"] as? JsonObject)?.getString("token")
            ?: node.values.firstNotNullOfOrNull { firstToken(it) }
        is JsonArray -> node.firstNotNullOfOrNull { firstToken(it) }
        else -> null
    }

    /** Les vidéos sous [root], et le jeton de la suite. */
    private fun read(root: Any?, name: String): ChannelVideosPage {
        val parser: TimeAgoParser = TimeAgoPatternsManager.getTimeAgoParserFor(localization())
            ?: TimeAgoPatternsManager.getTimeAgoParserFor(Localization("en"))!!
        val collector = StreamInfoItemsCollector(ServiceList.YouTube.serviceId)
        var next: String? = null
        fun walk(node: Any?) {
            when (node) {
                is JsonObject -> for ((key, value) in node) {
                    when {
                        key == "videoRenderer" && value is JsonObject -> collector.commit(YoutubeStreamInfoItemExtractor(value, parser))
                        key == "lockupViewModel" && value is JsonObject -> {
                            if (value.getString("contentType") == "LOCKUP_CONTENT_TYPE_VIDEO") {
                                collector.commit(YoutubeStreamInfoItemLockupExtractor(value, parser))
                            }
                        }
                        key == "continuationItemRenderer" -> if (next == null) next = firstToken(value)
                        else -> walk(value)
                    }
                }
                is JsonArray -> node.forEach(::walk)
            }
        }
        walk(root)
        val videos = collector.items
            .map { it.toVideo() }
            // Toutes de cette chaîne. (Dans ses onglets, YouTube n'écrit pas son nom sous chaque vidéo,
            // et le lecteur de NewPipe prenait alors la ligne des vues pour le nom de la chaîne.)
            .map { it.copy(channel = name) }
            .distinctBy { it.url }
        return ChannelVideosPage(videos, next)
    }
}
