package app.mikeneko.data

import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * Le fichier de sauvegarde `.mikeneko` (3.6.1), qui remplace le `.xml` pour les sauvegardes faites à la
 * main (la synchro entre appareils garde le XML : un appareil pas encore à jour doit pouvoir la lire).
 *
 * Une archive ZIP (compressée : l'historique pèse lourd en texte) avec :
 * - `manifest.json` : ce que c'est (`format`, `version`), l'appli et la date qui l'ont écrit, et combien
 *   il y a de chaque chose (`counts`) : de quoi présenter le fichier avant de l'importer ;
 * - `data.json` : le contenu, en JSON, plus les réglages de l'appli (que le XML n'avait pas).
 *
 * L'import se fait par parties ([Part]) : on coche ce qu'on veut reprendre. Le XML reste lisible.
 */
object BackupFile {
    const val EXTENSION = "mikeneko"
    const val MIME = "application/octet-stream"
    private const val FORMAT = "mikeneko-backup"
    const val VERSION = 2 // (1 : le XML)
    private const val MANIFEST = "manifest.json"
    private const val DATA = "data.json"

    /** Ce qu'on peut cocher à l'import. */
    enum class Part { Subscriptions, MusicPlaylists, VideoPlaylists, Library, Saved, History, Settings }

    class Meta(val app: String?, val date: String?, val version: Int)

    /** Combien d'éléments de [part] le contenu a (une playlist compte pour ses morceaux). */
    fun count(content: MikenekoBackup.Content, part: Part): Int = when (part) {
        Part.Subscriptions -> content.subscriptions.size
        Part.MusicPlaylists -> content.musicPlaylists.sumOf { it.second.size }
        Part.VideoPlaylists -> content.videoPlaylists.sumOf { it.second.size }
        Part.Library -> content.albums.size + content.artists.size
        Part.Saved -> content.favorites.size + content.later.size + content.liked.size + content.shorts.size
        Part.History -> content.history.size + content.positions.size + content.tastes.size
        Part.Settings -> content.settings?.values?.sumOf { it.size } ?: 0
    }

    /** Le contenu réduit aux parties cochées. */
    fun only(content: MikenekoBackup.Content, parts: Set<Part>): MikenekoBackup.Content {
        fun <T> keep(part: Part, list: List<T>) = if (part in parts) list else emptyList()
        val history = Part.History in parts
        return MikenekoBackup.Content(
            subscriptions = keep(Part.Subscriptions, content.subscriptions),
            musicPlaylists = keep(Part.MusicPlaylists, content.musicPlaylists),
            videoPlaylists = keep(Part.VideoPlaylists, content.videoPlaylists),
            albums = keep(Part.Library, content.albums),
            artists = keep(Part.Library, content.artists),
            favorites = keep(Part.Saved, content.favorites),
            later = keep(Part.Saved, content.later),
            liked = keep(Part.Saved, content.liked),
            shorts = keep(Part.Saved, content.shorts),
            tastes = if (history) content.tastes else emptySet(),
            tastesSkipped = history && content.tastesSkipped,
            history = keep(Part.History, content.history),
            positions = keep(Part.History, content.positions),
            settings = if (Part.Settings in parts) content.settings else null,
            app = content.app,
            date = content.date,
        )
    }

    // ------------------------------------------------------------------ écrire

    fun write(content: MikenekoBackup.Content, appVersion: String, date: String, out: OutputStream) {
        val manifest = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("app", appVersion)
            .put("date", date)
            .put("counts", JSONObject().also { counts -> Part.entries.forEach { counts.put(it.name, count(content, it)) } })
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest.toString(2).toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(DATA))
            zip.write(encode(content).toString().toByteArray())
            zip.closeEntry()
        }
    }

    internal fun encode(c: MikenekoBackup.Content): JSONObject {
        fun video(v: VideoItem) = JSONObject().put("url", v.url).put("title", v.title).put("channel", v.channel)
            .putOpt("thumbnail", v.thumbnail).apply {
                if (v.durationSec > 0) put("duration", v.durationSec)
                if (v.isLive) put("live", true)
            }
        fun videos(list: List<VideoItem>) = JSONArray().also { a -> list.forEach { a.put(video(it)) } }
        fun playlists(list: List<Pair<String, List<VideoItem>>>) =
            JSONArray().also { a -> list.forEach { (name, items) -> a.put(JSONObject().put("name", name).put("items", videos(items))) } }
        return JSONObject()
            .put("subscriptions", JSONArray().also { a ->
                c.subscriptions.forEach { a.put(JSONObject().put("id", it.channelId).put("name", it.name).put("url", it.url).putOpt("avatar", it.avatar)) }
            })
            .put("musicPlaylists", playlists(c.musicPlaylists))
            .put("videoPlaylists", playlists(c.videoPlaylists))
            .put("albums", JSONArray().also { a ->
                c.albums.forEach { a.put(JSONObject().put("url", it.url).put("title", it.title).put("artist", it.artist).putOpt("thumbnail", it.thumbnail).put("count", it.count)) }
            })
            .put("artists", JSONArray().also { a ->
                c.artists.forEach { a.put(JSONObject().put("url", it.url).put("name", it.name).putOpt("avatar", it.avatar).putOpt("channelId", it.channelId)) }
            })
            .put("favorites", videos(c.favorites))
            .put("later", videos(c.later))
            .put("liked", videos(c.liked))
            .put("shorts", videos(c.shorts))
            .put("tastes", JSONObject().put("chosen", JSONArray(c.tastes.sorted())).put("skipped", c.tastesSkipped))
            .put("history", JSONArray().also { a ->
                c.history.forEach { e ->
                    a.put(
                        JSONObject().put("url", e.url).put("title", e.title).put("channel", e.channel).putOpt("channelId", e.channelId)
                            .put("at", e.at).apply {
                                if (e.watched >= 0f) put("part", e.watched.toDouble())
                                if (e.tags.isNotEmpty()) put("tags", JSONArray(e.tags))
                            },
                    )
                }
            })
            .put("positions", JSONArray().also { a ->
                c.positions.forEach { p -> a.put(JSONObject().put("id", p.id).put("ms", p.positionMs).put("at", p.at).put("duration", p.durationMs)) }
            })
            .apply { c.settings?.let { put("settings", encodeSettings(it)) } }
    }

    /** Les réglages, par fichier de préférences, chaque valeur avec son type (b, i, l, f, s, set). */
    private fun encodeSettings(settings: Map<String, Map<String, Any>>) = JSONObject().also { files ->
        settings.forEach { (file, values) ->
            files.put(file, JSONObject().also { obj ->
                values.forEach { (key, value) ->
                    val typed = when (value) {
                        is Boolean -> JSONObject().put("b", value)
                        is Int -> JSONObject().put("i", value)
                        is Long -> JSONObject().put("l", value)
                        is Float -> JSONObject().put("f", value.toDouble())
                        is String -> JSONObject().put("s", value)
                        is Set<*> -> JSONObject().put("set", JSONArray(value.filterIsInstance<String>().sorted()))
                        else -> null
                    }
                    if (typed != null) obj.put(key, typed)
                }
            })
        }
    }

    // ------------------------------------------------------------------ lire

    /** Commence comme une archive ZIP. */
    fun isZip(bytes: ByteArray) = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 3.toByte() && bytes[3] == 4.toByte()

    /** Un fichier `.mikeneko` : son en-tête et son contenu ; null si ce n'en est pas un (ou s'il est abîmé). */
    fun read(bytes: ByteArray): Pair<Meta, MikenekoBackup.Content>? {
        if (!isZip(bytes)) return null
        return runCatching {
            var manifest: JSONObject? = null
            var data: JSONObject? = null
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when (entry.name) {
                        MANIFEST -> manifest = JSONObject(zip.readBytes().decodeToString())
                        DATA -> data = JSONObject(zip.readBytes().decodeToString())
                    }
                }
            }
            val m = manifest?.takeIf { it.optString("format") == FORMAT } ?: return null
            val meta = Meta(m.optString("app").ifEmpty { null }, m.optString("date").ifEmpty { null }, m.optInt("version", VERSION))
            meta to decode(data ?: return null, meta)
        }.getOrNull()
    }

    internal fun decode(o: JSONObject, meta: Meta? = null): MikenekoBackup.Content {
        fun JSONObject.str(key: String) = optString(key).takeIf { has(key) && !isNull(key) && it.isNotEmpty() }
        fun objects(key: String, from: JSONObject = o): List<JSONObject> {
            val a = from.optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
        }
        fun video(v: JSONObject): VideoItem? {
            val url = v.str("url") ?: return null
            return VideoItem(
                url = url,
                title = v.str("title").orEmpty(),
                channel = v.str("channel").orEmpty(),
                thumbnail = v.str("thumbnail") ?: thumbnailFor(url),
                durationSec = v.optLong("duration", -1),
                isLive = v.optBoolean("live", false),
            )
        }
        fun videos(key: String, from: JSONObject = o) = objects(key, from).mapNotNull(::video)
        fun playlists(key: String) = objects(key).mapNotNull { p -> p.str("name")?.let { it to videos("items", p) } }
        val tastes = o.optJSONObject("tastes")
        return MikenekoBackup.Content(
            subscriptions = objects("subscriptions").mapNotNull { s ->
                val id = s.str("id") ?: s.str("url")?.let(::channelIdFrom) ?: return@mapNotNull null
                Subscription(s.str("name") ?: id, s.str("url") ?: "https://www.youtube.com/channel/$id", id, s.str("avatar"))
            },
            musicPlaylists = playlists("musicPlaylists"),
            videoPlaylists = playlists("videoPlaylists"),
            albums = objects("albums").mapNotNull { a ->
                a.str("url")?.let { MusicCollection(it, a.str("title").orEmpty(), a.str("artist").orEmpty(), a.str("thumbnail"), a.optLong("count", -1)) }
            },
            artists = objects("artists").mapNotNull { a -> a.str("name")?.let { ChannelItem(a.str("url").orEmpty(), it, a.str("avatar"), a.str("channelId")) } },
            favorites = videos("favorites"),
            later = videos("later"),
            liked = videos("liked"),
            shorts = videos("shorts"),
            tastes = tastes?.optJSONArray("chosen")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).ifEmpty { null } }.toSet() }.orEmpty(),
            tastesSkipped = tastes?.optBoolean("skipped", false) ?: false,
            history = objects("history").mapNotNull { e ->
                val url = e.str("url") ?: return@mapNotNull null
                WatchHistory.Entry(
                    url = url,
                    title = e.str("title").orEmpty(),
                    channel = e.str("channel").orEmpty(),
                    channelId = e.str("channelId"),
                    at = e.optLong("at", 0L),
                    related = emptyList(),
                    watched = if (e.has("part")) e.optDouble("part", -1.0).toFloat() else -1f,
                    tags = e.optJSONArray("tags")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).ifBlank { null } } }.orEmpty(),
                )
            },
            positions = objects("positions").mapNotNull { p ->
                val id = p.str("id") ?: return@mapNotNull null
                if (!p.has("ms")) return@mapNotNull null
                WatchPositions.Saved(id, p.optLong("ms"), p.optLong("at", 0L), p.optLong("duration", 0L))
            },
            settings = o.optJSONObject("settings")?.let(::decodeSettings),
            app = meta?.app,
            date = meta?.date,
        )
    }

    private fun decodeSettings(files: JSONObject): Map<String, Map<String, Any>> =
        files.keys().asSequence().associateWith { file ->
            val obj = files.optJSONObject(file) ?: return@associateWith emptyMap<String, Any>()
            obj.keys().asSequence().mapNotNull { key ->
                val t = obj.optJSONObject(key) ?: return@mapNotNull null
                val value: Any? = when {
                    t.has("b") -> t.optBoolean("b")
                    t.has("i") -> t.optInt("i")
                    t.has("l") -> t.optLong("l")
                    t.has("f") -> t.optDouble("f").toFloat()
                    t.has("s") -> t.optString("s")
                    t.has("set") -> t.optJSONArray("set")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() }
                    else -> null
                }
                value?.let { key to it }
            }.toMap()
        }
}
