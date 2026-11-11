package app.mikeneko.data

import android.util.Base64
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
 *   <favorites>…</favorites> <later>…</later> <liked>…</liked> <shorts>…</shorts>
 *   <tastes skipped="true"><taste id="cooking"/></tastes>                      (2.19.1)
 *   <history><watched url="…" title="…" channel="…" at="…" part="0.8" tags="a|b"/></history>
 *   <positions><position id="…" ms="…" at="…" duration="…"/></positions>
 *   <removed><gone k="(clé en base64)" at="…" back="true"/></removed>         (2.23.0, voir Deletions)
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
        val shorts: List<VideoItem> = emptyList(),
        /** Les sujets choisis du « Pour toi » (2.19.1), et si la page des sujets a été passée. */
        val tastes: Set<String> = emptySet(),
        val tastesSkipped: Boolean = false,
        /** L'historique des vidéos (2.19.1), sans les suggestions de chacune. */
        val history: List<WatchHistory.Entry> = emptyList(),
        /** Où on en était dans les dernières vidéos (2.19.1). */
        val positions: List<WatchPositions.Saved> = emptyList(),
        /** Ce qui a été retiré (ou remis) sur l'appareil qui a écrit le fichier (2.23.0) : pour la synchro. */
        val removals: Map<String, Deletions.Mark> = emptyMap(),
    )

    /** Ce que l'import a ajouté. */
    class Added(val channels: Int, val playlists: Int, val library: Int, val videos: Int, val history: Int = 0) {
        val total get() = channels + playlists + library + videos + history
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
        xml.videos("shorts", videos.shorts.value)

        // Le « Pour toi » (2.19.1) : les sujets choisis, l'historique et les positions de reprise
        val tastes = Tastes.get(context)
        xml.startTag(null, "tastes")
        if (tastes.skipped.value) xml.attribute(null, "skipped", "true")
        tastes.chosen.value.forEach { id ->
            xml.startTag(null, "taste")
            xml.attribute(null, "id", id)
            xml.endTag(null, "taste")
        }
        xml.endTag(null, "tastes")
        xml.startTag(null, "history")
        WatchHistory.get(context).snapshot().forEach { e ->
            xml.startTag(null, "watched")
            xml.attr("url", e.url).attr("title", e.title).attr("channel", e.channel).attr("channelId", e.channelId)
            xml.attribute(null, "at", e.at.toString())
            if (e.watched >= 0f) xml.attribute(null, "part", e.watched.toString())
            xml.attr("tags", e.tags.filter { '|' !in it }.joinToString("|"))
            xml.endTag(null, "watched")
        }
        xml.endTag(null, "history")
        xml.startTag(null, "positions")
        WatchPositions.all(context).forEach { p ->
            xml.startTag(null, "position")
            xml.attribute(null, "id", p.id)
            xml.attribute(null, "ms", p.positionMs.toString())
            xml.attribute(null, "at", p.at.toString())
            if (p.durationMs > 0) xml.attribute(null, "duration", p.durationMs.toString())
            xml.endTag(null, "position")
        }
        xml.endTag(null, "positions")

        // Ce qu'on a retiré (2.23.0) : la synchro le retire aussi ailleurs. La clé est en base64 (elle
        // peut contenir un nom de playlist, avec n'importe quel caractère).
        xml.startTag(null, "removed")
        Deletions.all().toSortedMap().forEach { (key, mark) ->
            xml.startTag(null, "gone")
            xml.attribute(null, "k", Base64.encodeToString(key.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE))
            xml.attribute(null, "at", mark.at.toString())
            if (!mark.gone) xml.attribute(null, "back", "true")
            xml.endTag(null, "gone")
        }
        xml.endTag(null, "removed")

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
        val shorts = mutableListOf<VideoItem>()
        val tastes = mutableSetOf<String>()
        var tastesSkipped = false
        val history = mutableListOf<WatchHistory.Entry>()
        val positions = mutableListOf<WatchPositions.Saved>()
        val removals = HashMap<String, Deletions.Mark>()

        var section: String? = null // subscriptions, playlists, library, favorites, later, liked, shorts
        var playlistKind: String? = null
        var playlistName: String? = null
        var playlistItems = mutableListOf<VideoItem>()
        fun a(name: String) = parser.getAttributeValue(null, name)?.takeIf { it.isNotEmpty() }

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "subscriptions", "library", "favorites", "later", "liked", "shorts" -> section = parser.name
                    "playlists" -> { section = "playlists"; playlistKind = a("kind") }
                    "playlist" -> { playlistName = a("name"); playlistItems = mutableListOf() }
                    "tastes" -> tastesSkipped = a("skipped") == "true"
                    "taste" -> a("id")?.let { tastes += it }
                    "gone" -> {
                        val key = a("k")?.let { runCatching { String(Base64.decode(it, Base64.NO_WRAP or Base64.URL_SAFE)) }.getOrNull() }
                        val at = a("at")?.toLongOrNull()
                        if (key != null && at != null) removals[key] = Deletions.Mark(at, gone = a("back") != "true")
                    }
                    "watched" -> a("url")?.let { url ->
                        history += WatchHistory.Entry(
                            url = url,
                            title = a("title").orEmpty(),
                            channel = a("channel").orEmpty(),
                            channelId = a("channelId"),
                            at = a("at")?.toLongOrNull() ?: 0L,
                            related = emptyList(),
                            watched = a("part")?.toFloatOrNull() ?: -1f,
                            tags = a("tags")?.split('|')?.filter { it.isNotBlank() }.orEmpty(),
                        )
                    }
                    "position" -> {
                        val id = a("id")
                        val ms = a("ms")?.toLongOrNull()
                        if (id != null && ms != null) positions += WatchPositions.Saved(id, ms, a("at")?.toLongOrNull() ?: 0L, a("duration")?.toLongOrNull() ?: 0L)
                    }
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
                            section == "shorts" -> shorts += v
                        }
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "playlist" -> {
                        val name = playlistName
                        if (name != null) (if (playlistKind == "videos") videosLists else music) += name to playlistItems.toList()
                        playlistName = null
                    }
                    "subscriptions", "playlists", "library", "favorites", "later", "liked", "shorts" -> section = null
                }
            }
        }
        return Content(subscriptions, music, videosLists, albums, artists, favorites, later, liked, shorts, tastes, tastesSkipped, history, positions, removals)
    }

    /**
     * Ajoute le contenu d'un fichier, sans rien effacer.
     *
     * [sync] (2.23.0) : c'est un autre appareil qui parle, pas un fichier qu'on importe exprès. Ce qu'il a
     * retiré est retiré ici aussi, et ce qu'on a retiré ici n'est pas repris de lui ([Deletions]). Un
     * fichier importé à la main, lui, remet tout ce qu'il contient.
     */
    fun import(context: Context, content: Content, sync: Boolean = false): Added =
        if (sync) importSync(context, content) else importAll(context, content)

    private fun importSync(context: Context, theirs: Content): Added {
        Deletions.merge(theirs.removals)
        val music = Playlists.get(context)
        val videoLists = Playlists.videos(context)
        val library = MusicLibrary.get(context)
        val favorites = Favorites.get(context)
        val videos = VideoLibrary.get(context)
        val historyCleared = Deletions.goneAt(Deletions.HISTORY)
        val positionsCleared = Deletions.goneAt(Deletions.POSITIONS)
        // D'abord : ce qui est marqué retiré part d'ici (sans laisser de nouvelle marque)
        Deletions.applying {
            SubscriptionStore.get(context).dropGone()
            music.dropGone()
            videoLists.dropGone()
            library.dropGone()
            favorites.dropGone()
            videos.dropGone()
            Tastes.get(context).dropGone()
            WatchHistory.get(context).dropBefore(historyCleared)
            WatchPositions.dropBefore(context, positionsCleared)
        }
        // Ensuite : on reprend de l'autre tout ce qui n'est pas marqué retiré
        fun lists(store: Playlists, from: List<Pair<String, List<VideoItem>>>) =
            from.mapNotNull { (name, items) -> store.withoutGone(name, items)?.let { name to it } }
        return importAll(
            context,
            Content(
                subscriptions = theirs.subscriptions.filterNot { Deletions.isGone("s:" + it.channelId) },
                musicPlaylists = lists(music, theirs.musicPlaylists),
                videoPlaylists = lists(videoLists, theirs.videoPlaylists),
                albums = theirs.albums.filterNot { Deletions.isGone(library.albumKey(it)) },
                artists = theirs.artists.filterNot { Deletions.isGone(library.artistKey(it)) },
                favorites = theirs.favorites.filterNot { Deletions.isGone(favorites.key(it.url)) },
                later = theirs.later.filterNot { Deletions.isGone(videos.laterKey(it.url)) },
                liked = theirs.liked.filterNot { Deletions.isGone(videos.likedKey(it.url)) },
                shorts = theirs.shorts.filterNot { Deletions.isGone(videos.shortKey(it.url)) },
                tastes = theirs.tastes.filterNot { Deletions.isGone("t:$it") }.toSet(),
                tastesSkipped = theirs.tastesSkipped,
                history = theirs.history.filter { it.at > historyCleared },
                positions = theirs.positions.filter { it.at > positionsCleared },
            ),
        )
    }

    private fun importAll(context: Context, content: Content): Added {
        val store = SubscriptionStore.get(context)
        val before = store.items.value.size
        if (content.subscriptions.isNotEmpty()) store.addAll(content.subscriptions)
        val channels = store.items.value.size - before
        var playlists = 0
        content.musicPlaylists.forEach { (name, items) -> if (Playlists.get(context).merge(name, items)) playlists++ }
        content.videoPlaylists.forEach { (name, items) -> if (Playlists.videos(context).merge(name, items)) playlists++ }
        val library = MusicLibrary.get(context).addAll(content.albums, content.artists)
        val videos = VideoLibrary.get(context)
        val added = Favorites.get(context).addAll(content.favorites) + videos.addLater(content.later) + videos.addLiked(content.liked) + videos.addShorts(content.shorts)
        // Le « Pour toi » (2.19.1) : rien n'est gardé si l'historique est en pause ici
        Tastes.get(context).merge(content.tastes, content.tastesSkipped)
        val history = WatchHistory.get(context).merge(content.history)
        WatchPositions.merge(context, content.positions)
        return Added(channels, playlists, library, added, history)
    }
}
