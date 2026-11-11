package app.mikeneko.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Les radios en favori (le cœur du grand lecteur), gardées sur le téléphone. */
class Favorites private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("favoris", Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<VideoItem>> = _items.asStateFlow()

    fun contains(url: String) = _items.value.any { it.url == url }

    /** Ajoute (en premier) ou retire. Renvoie true si c'est maintenant en favori. */
    fun toggle(item: VideoItem): Boolean {
        val added = !contains(item.url)
        _items.value = if (added) listOf(item) + _items.value else _items.value.filterNot { it.url == item.url }
        save()
        if (added) Deletions.back(key(item.url)) else Deletions.gone(key(item.url))
        return added
    }

    /** Import : ajoute à la fin ce qui manque. Renvoie le nombre ajouté. */
    fun addAll(list: List<VideoItem>): Int {
        val fresh = list.filter { !contains(it.url) }.distinctBy { it.url }
        if (fresh.isNotEmpty()) {
            _items.value = _items.value + fresh
            save()
            Deletions.back(fresh.map { key(it.url) })
        }
        return fresh.size
    }

    // ---- les suppressions, pour la synchro (2.23.0, voir Deletions)
    fun key(url: String) = "f:" + Deletions.video(url)

    /** La synchro : ce qui a été retiré sur un autre appareil est retiré ici aussi. */
    fun dropGone() {
        val kept = _items.value.filterNot { Deletions.isGone(key(it.url)) }
        if (kept.size == _items.value.size) return
        _items.value = kept
        save()
    }

    private fun load(): List<VideoItem> = runCatching {
        val array = JSONArray(prefs.getString("liste", "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            VideoItem(
                url = o.getString("url"),
                title = o.optString("title"),
                channel = o.optString("channel"),
                thumbnail = o.optString("thumbnail").ifEmpty { null },
                isLive = o.optBoolean("live"),
            )
        }
    }.getOrDefault(emptyList())

    private fun save() {
        val array = JSONArray()
        _items.value.forEach {
            array.put(
                JSONObject().put("url", it.url).put("title", it.title).put("channel", it.channel)
                    .put("thumbnail", it.thumbnail ?: "").put("live", it.isLive),
            )
        }
        prefs.edit().putString("liste", array.toString()).apply()
    }

    companion object {
        @Volatile private var instance: Favorites? = null
        fun get(context: Context): Favorites =
            instance ?: synchronized(this) { instance ?: Favorites(context.applicationContext).also { instance = it } }
    }
}
