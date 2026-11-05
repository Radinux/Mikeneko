package app.mikeneko.data

import android.content.Context
import kotlin.math.pow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ce que tu dis à l'algo : « pas intéressé » (une vidéo) et « ne pas recommander la chaîne ».
 * Reste sur le téléphone. S'applique à « Pour toi » et aux rubriques, pas aux recherches
 * ni aux abonnements (comme sur YouTube).
 */
class Feedback private constructor(context: Context) {
    data class Hidden(val url: String, val channelKey: String?, val at: Long)
    data class Blocked(val key: String, val name: String)

    private val prefs = context.getSharedPreferences("retours", Context.MODE_PRIVATE)
    private val _hidden = MutableStateFlow(loadHidden())
    private val _blocked = MutableStateFlow(loadBlocked())
    val hidden: StateFlow<List<Hidden>> = _hidden.asStateFlow()
    val blocked: StateFlow<List<Blocked>> = _blocked.asStateFlow()

    /** Change à chaque retour : sert de clé au cache du « Pour toi ». */
    val version: Int get() = _hidden.value.size * 31 + _blocked.value.size + (_hidden.value.firstOrNull()?.at?.toInt() ?: 0)

    fun allows(video: VideoItem): Boolean {
        if (_hidden.value.any { it.url == video.url }) return false
        val key = video.channelKey() ?: return true
        return _blocked.value.none { it.key == key }
    }

    /**
     * Malus d'une chaîne : chaque « pas intéressé » récent sur ses vidéos divise son score
     * (×0,6 par vidéo, l'effet s'estompe en 2 mois environ).
     */
    fun penalty(channelKey: String?): Double {
        if (channelKey == null) return 1.0
        val now = System.currentTimeMillis()
        val weight = _hidden.value.filter { it.channelKey == channelKey }
            .sumOf { (1.0 - (now - it.at) / (60 * 86_400_000.0)).coerceAtLeast(0.0) }
        return 0.6.pow(weight)
    }

    fun notInterested(video: VideoItem) {
        _hidden.value = (listOf(Hidden(video.url, video.channelKey(), System.currentTimeMillis())) +
            _hidden.value.filterNot { it.url == video.url }).take(MAX_HIDDEN)
        saveHidden()
    }

    fun undoNotInterested(url: String) {
        _hidden.value = _hidden.value.filterNot { it.url == url }
        saveHidden()
    }

    fun blockChannel(video: VideoItem) {
        val key = video.channelKey() ?: return
        if (_blocked.value.any { it.key == key }) return
        _blocked.value = listOf(Blocked(key, video.channel)) + _blocked.value
        saveBlocked()
    }

    fun unblock(key: String) {
        _blocked.value = _blocked.value.filterNot { it.key == key }
        saveBlocked()
    }

    fun clearHidden() {
        _hidden.value = emptyList()
        saveHidden()
    }

    private fun saveHidden() {
        val array = JSONArray()
        _hidden.value.forEach { array.put(JSONObject().put("url", it.url).put("chaine", it.channelKey ?: "").put("at", it.at)) }
        prefs.edit().putString("masquees", array.toString()).apply()
    }

    private fun saveBlocked() {
        val array = JSONArray()
        _blocked.value.forEach { array.put(JSONObject().put("cle", it.key).put("nom", it.name)) }
        prefs.edit().putString("bloquees", array.toString()).apply()
    }

    private fun loadHidden(): List<Hidden> = runCatching {
        val array = JSONArray(prefs.getString("masquees", "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Hidden(o.getString("url"), o.optString("chaine").ifEmpty { null }, o.optLong("at"))
        }
    }.getOrDefault(emptyList())

    private fun loadBlocked(): List<Blocked> = runCatching {
        val array = JSONArray(prefs.getString("bloquees", "[]"))
        (0 until array.length()).map { i -> array.getJSONObject(i).let { Blocked(it.getString("cle"), it.optString("nom")) } }
    }.getOrDefault(emptyList())

    companion object {
        private const val MAX_HIDDEN = 500

        @Volatile private var instance: Feedback? = null
        fun get(context: Context): Feedback =
            instance ?: synchronized(this) { instance ?: Feedback(context.applicationContext).also { instance = it } }
    }
}
