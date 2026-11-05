package app.mikeneko.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** Les recherches tapées, la plus récente en premier. Reste sur le téléphone. */
class SearchHistory private constructor(context: Context, file: String) {
    private val prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<String>> = _items.asStateFlow()

    /** Remonte la recherche en tête (sans doublon, majuscules comprises). */
    fun add(query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        update(listOf(clean) + _items.value.filterNot { it.equals(clean, ignoreCase = true) })
    }

    fun remove(query: String) = update(_items.value.filterNot { it == query })

    fun clear() = update(emptyList())

    private fun update(list: List<String>) {
        _items.value = list.take(MAX)
        prefs.edit().putString(KEY, JSONArray(_items.value).toString()).apply()
    }

    private fun load(): List<String> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        List(array.length()) { array.getString(it) }
    }.getOrDefault(emptyList())

    companion object {
        private const val KEY = "liste"
        private const val MAX = 50

        @Volatile private var instance: SearchHistory? = null
        @Volatile private var music: SearchHistory? = null

        /** Les recherches de vidéos (accueil). */
        fun get(context: Context): SearchHistory =
            instance ?: synchronized(this) { instance ?: SearchHistory(context.applicationContext, "recherches").also { instance = it } }

        /** Les recherches de musique (Musique), à part : elles ne se mélangent pas à celles des vidéos. */
        fun music(context: Context): SearchHistory =
            music ?: synchronized(this) { music ?: SearchHistory(context.applicationContext, "recherches_musique").also { music = it } }
    }
}
