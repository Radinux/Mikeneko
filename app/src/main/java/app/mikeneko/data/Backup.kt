package app.mikeneko.data

import android.content.Context
import android.util.Xml
import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader
import java.time.LocalDate
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlSerializer

/**
 * Le fichier de sauvegarde de Mikeneko, en XML : tout ce qu'on a fait soi-même dans l'appli
 * (abonnements, playlists musique et vidéo, albums et artistes sauvegardés, radios favorites,
 * « À regarder plus tard », vidéos aimées). L'import ajoute ce qui manque, sans rien effacer :
 * une playlist du même nom reçoit les morceaux qui lui manquent.
 *
 * ```xml
 * <mikeneko version="1" app="1.15.0" date="2026-11-04">
 *   <subscriptions><channel id="UC…" name="…" avatar="…"/></subscriptions>
 *   <playlists kind="music"><playlist name="…"><video url="…" title="…" channel="…"/></playlist></playlists>
 *   <playlists kind="videos">…</playlists>
 *   <library><album url="…" title="…" artist="…"/><artist url="…" name="…"/></library>
 *   <favorites>…</favorites> <later>…</later> <liked>…</liked>
 * </mikeneko>
 * ```
 */
object MikenekoBackup {
    private const val VERSION = "1"

    /** Ce qu'un fichier contient. */
    class Content(
        val subscriptions: List<Subscription> = emptyList(),
        val musicPlaylists: List<Pair<String, List<VideoItem>>> = emptyList(),
        val videoPlaylists: List<Pair<String, List<VideoItem>>> = emptyList(),
        val albums: List<MusicCollection> = emptyList(),
        val artists: List<ChannelItem> = emptyList(),
        val favorites: List<VideoItem> = emptyList(),
        val later: List<VideoItem> = emptyList(),
        val liked: List<VideoItem> = emptyList(),
    )

    /** Ce que l'import a ajouté. */
    class Added(val channels: Int, val playlists: Int, val library: Int, val videos: Int) {
        val total get() = channels + playlists + library + videos
    }

    /** Un fichier de Mikeneko ? (pour l'import des abonnements, qui accepte aussi le reste) */
    fun looksLike(text: String) = text.trimStart('﻿', ' ', '\n', '\r', '\t').let { it.startsWith("<") && it.contains("<mikeneko") }

    fun export(context: Context, out: OutputStream, appVersion: String) {
        val xml = Xml.newSerializer()
        xml.setOutput(out, "UTF-8")
        xml.startDocument("UTF-8", true)
        xml.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
        xml.startTag(null, "mikeneko")
        xml.attribute(null, "version", VERSION)
        xml.attribute(null, "app", appVersion)
        xml.attribute(null, "date", LocalDate.now().toString())

        xml.startTag(null, "subscriptions")
        SubscriptionStore.get(context).items.value.forEach { s ->
            xml.startTag(null, "channel")
            xml.attr("id", s.channelId).attr("name", s.name).attr("url", s.url).attr("avatar", s.avatar)
            xml.endTag(null, "channel")
        }
        xml.endTag(null, "subscriptions")

        for ((kind, store) in listOf("music" to Playlists.get(context), "videos" to Playlists.videos(context))) {
            xml.startTag(null, "playlists")
            xml.attribute(null, "kind", kind)
            store.items.value.forEach { p ->
                xml.startTag(null, "playlist")
                xml.attribute(null, "name", p.name)
                p.items.forEach { xml.video(it) }
                xml.endTag(null, "playlist")
            }
            xml.endTag(null, "playlists")
        }

        val library = MusicLibrary.get(context)
        xml.startTag(null, "library")
        library.albums.value.forEach { a ->
            xml.startTag(null, "album")
            xml.attr("url", a.url).attr("title", a.title).attr("artist", a.artist).attr("thumbnail", a.thumbnail).attr("count", a.count.toString())
            xml.endTag(null, "album")
        }
        library.artists.value.forEach { a ->
            xml.startTag(null, "artist")
            xml.attr("url", a.url).attr("name", a.name).attr("avatar", a.avatar).attr("channelId", a.channelId)
            xml.endTag(null, "artist")
        }
        xml.endTag(null, "library")

        val videos = VideoLibrary.get(context)
        xml.videos("favorites", Favorites.get(context).items.value)
        xml.videos("later", videos.later.value)
        xml.videos("liked", videos.liked.value)

        xml.endTag(null, "mikeneko")
        xml.endDocument()
        out.flush()
    }

    private fun XmlSerializer.attr(name: String, value: String?): XmlSerializer {
        if (!value.isNullOrEmpty()) attribute(null, name, value)
        return this
    }

    private fun XmlSerializer.video(v: VideoItem) {
        startTag(null, "video")
        attr("url", v.url).attr("title", v.title).attr("channel", v.channel).attr("thumbnail", v.thumbnail)
        if (v.durationSec > 0) attribute(null, "duration", v.durationSec.toString())
        if (v.isLive) attribute(null, "live", "true")
        endTag(null, "video")
    }

    private fun XmlSerializer.videos(tag: String, list: List<VideoItem>) {
        startTag(null, tag)
        list.forEach { video(it) }
        endTag(null, tag)
    }

    fun read(input: InputStream): Content = read(input.bufferedReader().readText())

    fun read(text: String): Content {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(text.trimStart('﻿')))
        val subscriptions = mutableListOf<Subscription>()
        val music = mutableListOf<Pair<String, List<VideoItem>>>()
        val videosLists = mutableListOf<Pair<String, List<VideoItem>>>()
        val albums = mutableListOf<MusicCollection>()
        val artists = mutableListOf<ChannelItem>()
        val favorites = mutableListOf<VideoItem>()
        val later = mutableListOf<VideoItem>()
        val liked = mutableListOf<VideoItem>()

        var section: String? = null // subscriptions, playlists, library, favorites, later, liked
        var playlistKind: String? = null
        var playlistName: String? = null
        var playlistItems = mutableListOf<VideoItem>()
        fun a(name: String) = parser.getAttributeValue(null, name)?.takeIf { it.isNotEmpty() }

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "subscriptions", "library", "favorites", "later", "liked" -> section = parser.name
                    "playlists" -> { section = "playlists"; playlistKind = a("kind") }
                    "playlist" -> { playlistName = a("name"); playlistItems = mutableListOf() }
                    "channel" -> {
                        val id = a("id") ?: a("url")?.let(::channelIdFrom)
                        if (id != null) subscriptions += Subscription(a("name") ?: id, a("url") ?: "https://www.youtube.com/channel/$id", id, a("avatar"))
                    }
                    "album" -> a("url")?.let { url ->
                        albums += MusicCollection(url, a("title").orEmpty(), a("artist").orEmpty(), a("thumbnail"), a("count")?.toLongOrNull() ?: -1)
                    }
                    "artist" -> {
                        val name = a("name")
                        if (name != null) artists += ChannelItem(a("url").orEmpty(), name, a("avatar"), a("channelId"))
                    }
                    "video" -> a("url")?.let { url ->
                        val v = VideoItem(
                            url = url,
                            title = a("title").orEmpty(),
                            channel = a("channel").orEmpty(),
                            thumbnail = a("thumbnail") ?: thumbnailFor(url),
                            durationSec = a("duration")?.toLongOrNull() ?: -1,
                            isLive = a("live") == "true",
                        )
                        when {
                            playlistName != null -> playlistItems += v
                            section == "favorites" -> favorites += v
                            section == "later" -> later += v
                            section == "liked" -> liked += v
                        }
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "playlist" -> {
                        val name = playlistName
                        if (name != null) (if (playlistKind == "videos") videosLists else music) += name to playlistItems.toList()
                        playlistName = null
                    }
                    "subscriptions", "playlists", "library", "favorites", "later", "liked" -> section = null
                }
            }
        }
        return Content(subscriptions, music, videosLists, albums, artists, favorites, later, liked)
    }

    /** Ajoute le contenu d'un fichier (sans rien effacer). */
    fun import(context: Context, content: Content): Added {
        val store = SubscriptionStore.get(context)
        val before = store.items.value.size
        if (content.subscriptions.isNotEmpty()) store.addAll(content.subscriptions)
        val channels = store.items.value.size - before
        var playlists = 0
        content.musicPlaylists.forEach { (name, items) -> if (Playlists.get(context).merge(name, items)) playlists++ }
        content.videoPlaylists.forEach { (name, items) -> if (Playlists.videos(context).merge(name, items)) playlists++ }
        val library = MusicLibrary.get(context).addAll(content.albums, content.artists)
        val videos = VideoLibrary.get(context)
        val added = Favorites.get(context).addAll(content.favorites) + videos.addLater(content.later) + videos.addLiked(content.liked)
        return Added(channels, playlists, library, added)
    }
}
