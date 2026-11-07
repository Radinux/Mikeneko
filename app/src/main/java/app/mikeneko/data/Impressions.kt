package app.mikeneko.data

import android.content.Context
import kotlin.math.pow
import org.json.JSONObject

/**
 * La « fatigue » du « Pour toi » (2.7.0, idée tirée de l'algorithme publié de X) : une vidéo que le
 * fil a montrée plusieurs fois sans qu'on l'ouvre descend, puis disparaît. Une vue compte au plus
 * une fois par demi-heure (faire défiler le fil plusieurs fois ne la fatigue pas plus). Les traces
 * s'effacent au bout de 10 jours. Reste sur le téléphone.
 */
class Impressions private constructor(context: Context) {
    private class Seen(var count: Int, var last: Long)

    private val prefs = context.getSharedPreferences("impressions", Context.MODE_PRIVATE)
    private val seen = HashMap<String, Seen>()
    private var dirty = false

    init {
        runCatching {
            val o = JSONObject(prefs.getString("vues", "{}").orEmpty())
            val now = System.currentTimeMillis()
            o.keys().forEach { url ->
                val e = o.getJSONObject(url)
                val last = e.optLong("t")
                if (now - last < FORGET_MS) seen[url] = Seen(e.optInt("n"), last)
            }
        }
    }

    /** La carte de [url] vient d'apparaître à l'écran dans le « Pour toi ». */
    @Synchronized
    fun shown(url: String) {
        val now = System.currentTimeMillis()
        val e = seen.getOrPut(url) { Seen(0, 0L) }
        if (now - e.last < ONE_VIEW_MS) return
        e.count++
        e.last = now
        dirty = true
    }

    /** Multiplicateur de score : 1 pour une vidéo jamais montrée, puis ×0,55 à chaque vue de plus. */
    @Synchronized
    fun fatigue(url: String): Double {
        val n = seen[url]?.count ?: return 1.0
        return if (n <= 1) 1.0 else 0.55.pow(n - 1)
    }

    /** Montrée trop souvent sans être ouverte : on ne la propose plus (pendant 10 jours). */
    @Synchronized
    fun tired(url: String): Boolean = (seen[url]?.count ?: 0) >= TIRED_AFTER

    /** Écrit les vues (appelé quand le fil n'est plus à l'écran). */
    @Synchronized
    fun save() {
        if (!dirty) return
        val o = JSONObject()
        seen.entries.sortedByDescending { it.value.last }.take(MAX_KEPT).forEach { (url, e) ->
            o.put(url, JSONObject().put("n", e.count).put("t", e.last))
        }
        prefs.edit().putString("vues", o.toString()).apply()
        dirty = false
    }

    @Synchronized
    fun clear() {
        seen.clear()
        prefs.edit().remove("vues").apply()
        dirty = false
    }

    companion object {
        private const val ONE_VIEW_MS = 30 * 60_000L
        private const val FORGET_MS = 10 * 86_400_000L
        private const val TIRED_AFTER = 5
        private const val MAX_KEPT = 1500

        @Volatile private var instance: Impressions? = null
        fun get(context: Context): Impressions =
            instance ?: synchronized(this) { instance ?: Impressions(context.applicationContext).also { instance = it } }
    }
}
