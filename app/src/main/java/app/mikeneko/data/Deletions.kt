package app.mikeneko.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ce qu'on a retiré (2.23.0), pour que la synchro le retire aussi sur les autres appareils : avant, elle
 * ne faisait qu'ajouter, et un abonnement ou une playlist supprimés revenaient depuis l'appareil qui
 * les avait encore.
 *
 * Chaque chose retirée laisse une marque : sa clé, la date, et « retiré ». Si on la remet plus tard, la
 * marque devient « remis », avec la nouvelle date. À la synchro, les marques des deux appareils sont
 * réunies (la plus récente gagne), puis ce qui est marqué « retiré » est enlevé ici et n'est plus repris
 * de l'autre. Rien n'est marqué tant qu'on ne retire rien : un appareil neuf reçoit tout, comme avant.
 *
 * Les clés : `s:` abonnement, `p:` playlist (par nom), `i:` morceau d'une playlist, `al:` album,
 * `ar:` artiste, `f:` favori, `l:` à regarder plus tard, `k:` vidéo aimée, `h:` short gardé,
 * `t:` sujet du « Pour toi » ; [HISTORY] et [POSITIONS] : « tout effacé à cette date ».
 */
object Deletions {
    class Mark(val at: Long, val gone: Boolean)

    const val HISTORY = "H*"
    const val POSITIONS = "P*"
    /** Une marque est gardée un an : au-delà, un appareil resté éteint tout ce temps pourrait ramener la chose. */
    private const val KEEP_MS = 365L * 24 * 3600 * 1000
    private const val MAX = 5000

    private var prefs: SharedPreferences? = null
    private val marks = HashMap<String, Mark>()
    /** La synchro est en train de retirer ce que l'autre a retiré : ces retraits-là ne sont pas à marquer. */
    @Volatile private var applying = false

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("suppressions", Context.MODE_PRIVATE)
        prefs = p
        runCatching {
            val o = JSONObject(p.getString("marques", "{}") ?: "{}")
            o.keys().forEach { key ->
                val a = o.getJSONArray(key)
                marks[key] = Mark(a.getLong(0), a.getInt(1) == 1)
            }
        }
    }

    /** La clé d'une vidéo ou d'un morceau : son identifiant YouTube, sinon son adresse (fichier local). */
    fun video(url: String): String = videoIdOf(url) ?: url

    /** Retiré maintenant. */
    fun gone(key: String) = gone(listOf(key))

    @Synchronized
    fun gone(keys: Collection<String>) {
        if (applying || prefs == null || keys.isEmpty()) return
        val now = System.currentTimeMillis()
        keys.forEach { marks[it] = Mark(now, true) }
        save()
    }

    /** (Re)mis maintenant : ne compte que pour ce qui avait été retiré. */
    fun back(key: String) = back(listOf(key))

    @Synchronized
    fun back(keys: Collection<String>) {
        if (applying || prefs == null) return
        val revived = keys.filter { marks[it]?.gone == true }
        if (revived.isEmpty()) return
        val now = System.currentTimeMillis()
        revived.forEach { marks[it] = Mark(now, false) }
        save()
    }

    @Synchronized
    fun isGone(key: String): Boolean = marks[key]?.gone == true

    /** La date du dernier « tout effacer » ([HISTORY], [POSITIONS]) ; 0 : jamais. */
    @Synchronized
    fun goneAt(key: String): Long = marks[key]?.takeIf { it.gone }?.at ?: 0L

    @Synchronized
    fun all(): Map<String, Mark> = HashMap(marks)

    /** Les marques d'un autre appareil : pour chaque clé, la plus récente gagne. */
    @Synchronized
    fun merge(theirs: Map<String, Mark>) {
        if (prefs == null) return
        var changed = false
        theirs.forEach { (key, mark) ->
            val ours = marks[key]
            if (ours == null || mark.at > ours.at) {
                marks[key] = mark
                changed = true
            }
        }
        if (changed) save()
    }

    /** Pendant [block], les retraits et les ajouts ne laissent pas de marque (c'est la synchro qui les fait). */
    fun <T> applying(block: () -> T): T {
        applying = true
        try {
            return block()
        } finally {
            applying = false
        }
    }

    private fun save() {
        val limit = System.currentTimeMillis() - KEEP_MS
        marks.entries.removeAll { it.value.at < limit }
        if (marks.size > MAX) {
            val keep = marks.entries.sortedByDescending { it.value.at }.take(MAX).map { it.key }.toSet()
            marks.keys.retainAll(keep)
        }
        val o = JSONObject()
        marks.forEach { (key, mark) -> o.put(key, JSONArray().put(mark.at).put(if (mark.gone) 1 else 0)) }
        prefs?.edit()?.putString("marques", o.toString())?.apply()
    }
}
