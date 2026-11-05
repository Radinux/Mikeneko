package app.mikeneko.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * « Récemment écoutés » (onglet Musique) : les derniers morceaux et directs joués en audio, le plus
 * récent en premier, sans doublon. Gardé sur le téléphone, séparé de l'historique des vidéos.
 */
class MusicHistory private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("musique_recente", Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<VideoItem>> = _items.asStateFlow()

    fun add(item: VideoItem) {
        val list = listOf(item) + _items.value.filterNot { sameTrack(it.url, item.url) }
        _items.value = list.take(MAX)
        val array = JSONArray()
        _items.value.forEach { array.put(it.toTrackJson()) }
        prefs.edit().putString("liste", array.toString()).apply()
    }

    fun clear() {
        _items.value = emptyList()
        prefs.edit().remove("liste").apply()
    }

    private fun load(): List<VideoItem> = runCatching {
        val array = JSONArray(prefs.getString("liste", "[]"))
        (0 until array.length()).map { trackFromJson(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    companion object {
        private const val MAX = 30

        @Volatile private var instance: MusicHistory? = null
        fun get(context: Context): MusicHistory =
            instance ?: synchronized(this) { instance ?: MusicHistory(context.applicationContext).also { instance = it } }
    }
}
