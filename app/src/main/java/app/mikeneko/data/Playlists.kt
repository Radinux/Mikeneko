package app.mikeneko.data

import android.content.Context
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Une playlist faite à la main (musique ou vidéos). [cover] : la photo choisie comme pochette (fichier de
 * l'appli). [sort] : son tri automatique ([TrackTags.Sort], 2.15.0 ; null : l'ordre choisi à la main).
 * [pinned] (3.1.0) : épinglée, elle reste en tête des listes (sur ce téléphone : pas dans la synchro).
 */
data class Playlist(
    val id: String,
    val name: String,
    val items: List<VideoItem>,
    val cover: String? = null,
    val sort: String? = null,
    val pinned: Boolean = false,
)

/** Les épinglées d'abord, chaque groupe dans son ordre habituel (3.1.0). */
fun List<Playlist>.pinnedFirst(): List<Playlist> = if (none { it.pinned }) this else sortedBy { !it.pinned }

/**
 * Les playlists perso, gardées sur le téléphone (comme les favoris). Deux carnets séparés : celles
 * de la musique ([get]) et celles des vidéos ([videos], dans la Bibliothèque).
 */
class Playlists private constructor(context: Context, file: String) {
    private val prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE)
    /** « m » musique, « v » vidéos : dans les clés des suppressions ([Deletions]). */
    private val kind = if (file == "playlists") "m" else "v"
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

    /** Épingle (ou désépingle) les playlists [ids] : elles passent en tête des listes (3.1.0). */
    fun setPinned(ids: Set<String>, pinned: Boolean) =
        update(track = false) { list -> list.map { if (it.id in ids) it.copy(pinned = pinned) else it } }

    /** Le tri automatique (null : retour à l'ordre fait à la main). */
    fun setSort(id: String, sort: String?) = change(id) { it.copy(sort = sort) }

    /** Retire le morceau [url] (où qu'il soit : la liste affichée peut être triée autrement). */
    fun removeTrack(id: String, url: String) = change(id) { p -> p.copy(items = p.items.filterNot { it.url == url }) }

    /** Remplace les morceaux (le nouvel ordre après un déplacement, 2.11.13). */
    fun setItems(id: String, items: List<VideoItem>) = change(id) { it.copy(items = items) }

    // (un déplacement ne retire rien : pas la peine de comparer toute la liste à chaque cran du doigt)
    fun move(id: String, from: Int, to: Int) = change(id, track = false) { p ->
        if (from !in p.items.indices || to !in p.items.indices) p
        else p.copy(items = p.items.toMutableList().apply { add(to, removeAt(from)) })
    }

    private fun change(id: String, track: Boolean = true, block: (Playlist) -> Playlist) =
        update(track) { list -> list.map { if (it.id == id) block(it) else it } }

    private fun update(track: Boolean = true, block: (List<Playlist>) -> List<Playlist>) {
        val before = _items.value
        val after = block(before)
        _items.value = after
        save()
        if (track) track(before, after)
    }

    // ---- les suppressions, pour la synchro (2.23.0, voir Deletions)

    private fun listKey(name: String) = "p:$kind:" + name.trim().lowercase()
    private fun itemKey(name: String, url: String) = "i:$kind:" + name.trim().lowercase() + SEP + Deletions.video(url)
    private fun keys(list: List<Playlist>): Set<String> =
        HashSet<String>().apply { list.forEach { p -> add(listKey(p.name)); p.items.forEach { add(itemKey(p.name, it.url)) } } }

    /**
     * Ce qui a disparu est marqué retiré, ce qui est revenu est marqué remis. Une playlist supprimée (ou
     * renommée : son ancien nom) laisse une seule marque, pas une par morceau.
     */
    private fun track(before: List<Playlist>, after: List<Playlist>) {
        val old = keys(before)
        val new = keys(after)
        if (old == new) return
        val alive = after.mapTo(HashSet()) { listKey(it.name) }
        Deletions.gone((old - new).filter { !it.startsWith("i:") || ("p:" + it.substring(2).substringBefore(SEP)) in alive })
        Deletions.back(new - old)
    }

    /** La synchro : ce qui a été retiré sur un autre appareil est retiré ici aussi. */
    fun dropGone() = update(track = false) { list ->
        list.filterNot { Deletions.isGone(listKey(it.name)) }.map { p ->
            val kept = p.items.filterNot { Deletions.isGone(itemKey(p.name, it.url)) }
            if (kept.size == p.items.size) p else p.copy(items = kept)
        }
    }

    /** Ce que la synchro peut reprendre d'une playlist d'un autre appareil : null si elle a été supprimée ici. */
    fun withoutGone(name: String, items: List<VideoItem>): List<VideoItem>? =
        if (Deletions.isGone(listKey(name))) null else items.filterNot { Deletions.isGone(itemKey(name, it.url)) }

    private fun load(): List<Playlist> = runCatching {
        val array = JSONArray(prefs.getString("liste", "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val tracks = o.getJSONArray("items")
            Playlist(o.getString("id"), o.optString("name"), (0 until tracks.length()).map { trackFromJson(tracks.getJSONObject(it)) }, o.optString("cover").ifEmpty { null }, o.optString("sort").ifEmpty { null }, o.optBoolean("pinned"))
        }
    }.getOrDefault(emptyList())

    private fun save() {
        val array = JSONArray()
        _items.value.forEach { p ->
            val tracks = JSONArray()
            p.items.forEach { tracks.put(it.toTrackJson()) }
            array.put(JSONObject().put("id", p.id).put("name", p.name).put("items", tracks).put("cover", p.cover ?: "").put("sort", p.sort ?: "").put("pinned", p.pinned))
        }
        prefs.edit().putString("liste", array.toString()).apply()
    }

    companion object {
        private const val SEP = "\u0001"
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
