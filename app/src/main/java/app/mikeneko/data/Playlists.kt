package app.mikeneko.data

import android.content.Context
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Une playlist faite à la main (musique ou vidéos). [cover] : la photo choisie comme pochette (fichier de l'appli). */
data class Playlist(val id: String, val name: String, val items: List<VideoItem>, val cover: String? = null)

/**
 * Les playlists perso, gardées sur le téléphone (comme les favoris). Deux carnets séparés : celles
 * de la musique ([get]) et celles des vidéos ([videos], dans la Bibliothèque).
 */
class Playlists private constructor(context: Context, file: String) {
    private val prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Playlist>> = _items.asStateFlow()

    fun get(id: String): Playlist? = _items.value.firstOrNull { it.id == id }

    /** Nouvelle playlist (en premier), avec un premier morceau si on en donne un. */
    fun create(name: String, first: VideoItem? = null): Playlist {
        val playlist = Playlist(UUID.randomUUID().toString(), name.trim(), listOfNotNull(first))
        update { listOf(playlist) + it }
        return playlist
    }

    /**
     * Import : une playlist du même nom reçoit les morceaux qui lui manquent, sinon elle est créée.
     * Renvoie true si quelque chose a changé.
     */
    fun merge(name: String, items: List<VideoItem>): Boolean {
        val existing = _items.value.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
        if (existing == null) {
            val playlist = Playlist(UUID.randomUUID().toString(), name.trim(), items.distinctBy { it.url })
            update { it + playlist }
            return true
        }
        val fresh = items.filter { v -> existing.items.none { sameTrack(it.url, v.url) } }.distinctBy { it.url }
        if (fresh.isNotEmpty()) change(existing.id) { it.copy(items = it.items + fresh) }
        return fresh.isNotEmpty()
    }

    fun rename(id: String, name: String) = change(id) { it.copy(name = name.trim()) }

    /** La photo choisie comme pochette (null : retour à la mosaïque des pochettes des morceaux). */
    fun setCover(id: String, path: String?) = change(id) { it.copy(cover = path) }

    fun delete(id: String) = update { list -> list.filterNot { it.id == id } }

    /** Ajoute en tête (2.11.2 : le dernier ajouté en premier). Renvoie false si le morceau y était déjà. */
    fun add(id: String, item: VideoItem): Boolean {
        val playlist = get(id) ?: return false
        if (playlist.items.any { sameTrack(it.url, item.url) }) return false
        change(id) { it.copy(items = listOf(item) + it.items) }
        return true
    }

    fun remove(id: String, index: Int) = change(id) { p ->
        if (index !in p.items.indices) p else p.copy(items = p.items.toMutableList().apply { removeAt(index) })
    }

    fun move(id: String, from: Int, to: Int) = change(id) { p ->
        if (from !in p.items.indices || to !in p.items.indices) p
        else p.copy(items = p.items.toMutableList().apply { add(to, removeAt(from)) })
    }

    private fun change(id: String, block: (Playlist) -> Playlist) = update { list -> list.map { if (it.id == id) block(it) else it } }

    private fun update(block: (List<Playlist>) -> List<Playlist>) {
        _items.value = block(_items.value)
        save()
    }

    private fun load(): List<Playlist> = runCatching {
        val array = JSONArray(prefs.getString("liste", "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val tracks = o.getJSONArray("items")
            Playlist(o.getString("id"), o.optString("name"), (0 until tracks.length()).map { trackFromJson(tracks.getJSONObject(it)) }, o.optString("cover").ifEmpty { null })
        }
    }.getOrDefault(emptyList())

    private fun save() {
        val array = JSONArray()
        _items.value.forEach { p ->
            val tracks = JSONArray()
            p.items.forEach { tracks.put(it.toTrackJson()) }
            array.put(JSONObject().put("id", p.id).put("name", p.name).put("items", tracks).put("cover", p.cover ?: ""))
        }
        prefs.edit().putString("liste", array.toString()).apply()
    }

    companion object {
        @Volatile private var instance: Playlists? = null
        @Volatile private var videoInstance: Playlists? = null

        /** Les playlists de la musique. */
        fun get(context: Context): Playlists =
            instance ?: synchronized(this) { instance ?: Playlists(context.applicationContext, "playlists").also { instance = it } }

        /** Les playlists de vidéos (Bibliothèque). */
        fun videos(context: Context): Playlists =
            videoInstance ?: synchronized(this) { videoInstance ?: Playlists(context.applicationContext, "playlists_videos").also { videoInstance = it } }
    }
}

internal fun VideoItem.toTrackJson(): JSONObject = JSONObject()
    .put("url", url).put("title", title).put("channel", channel)
    .put("thumbnail", thumbnail ?: "").put("live", isLive).put("duration", durationSec).put("short", isShort)

internal fun trackFromJson(o: JSONObject) = VideoItem(
    url = o.getString("url"),
    title = o.optString("title"),
    channel = o.optString("channel"),
    thumbnail = o.optString("thumbnail").ifEmpty { null },
    isLive = o.optBoolean("live"),
    durationSec = o.optLong("duration", -1),
    isShort = o.optBoolean("short"),
)
