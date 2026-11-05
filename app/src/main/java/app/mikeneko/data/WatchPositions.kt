package app.mikeneko.data

import android.content.Context
import app.mikeneko.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Où on en était dans les [MAX] dernières vidéos, pour reprendre au même endroit, même après avoir
 * fermé l'appli, et pour la barre de progression des miniatures. Les vidéos seulement (pas la
 * musique ni les directs). Une vidéo finie, ou à peine commencée, repart du début. Reste sur le
 * téléphone ; « Effacer l'historique » l'efface aussi, et rien n'est gardé quand l'historique est en pause.
 *
 * Une entrée : `position;date;durée` (ms). Les entrées de 1.16.0 n'ont pas la durée : elles servent
 * à la reprise, pas à la barre.
 */
object WatchPositions {
    /** Combien de vidéos on garde (les plus anciennes sortent). */
    private const val MAX = 10
    /** Moins loin que ça : on repartira du début. */
    private const val MIN_MS = 10_000L
    /** Plus près de la fin que ça : la vidéo est vue, on repartira du début. */
    private const val END_MS = 20_000L

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("reprise_videos", Context.MODE_PRIVATE)

    /** Où on en est (0 → 1) des vidéos commencées, par identifiant : la barre sous les miniatures. */
    fun progress(context: Context): StateFlow<Map<String, Float>> {
        if (!loaded) {
            loaded = true
            refresh(context)
        }
        return _progress.asStateFlow()
    }

    private fun refresh(context: Context) {
        _progress.value = prefs(context).all.mapNotNull { (id, value) ->
            val parts = (value as? String)?.split(';') ?: return@mapNotNull null
            val position = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val duration = parts.getOrNull(2)?.toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            id to (position.toFloat() / duration).coerceIn(0f, 1f)
        }.toMap()
    }

    /** Retient la position de [videoId] (ou l'oublie si elle est trop près du début ou de la fin). */
    fun save(context: Context, videoId: String, positionMs: Long, durationMs: Long) {
        if (videoId.isBlank()) return
        val prefs = prefs(context)
        val finished = durationMs > 0 && positionMs >= durationMs - END_MS
        if (AppSettings.historyPaused.value || positionMs < MIN_MS || finished) {
            if (prefs.contains(videoId)) {
                prefs.edit().remove(videoId).apply()
                refresh(context)
            }
            return
        }
        val edit = prefs.edit().putString(videoId, "$positionMs;${System.currentTimeMillis()};$durationMs")
        // Les plus anciennes sortent au-delà de MAX
        val others = prefs.all
            .filterKeys { it != videoId }
            .mapNotNull { (id, value) -> (value as? String)?.split(';')?.getOrNull(1)?.toLongOrNull()?.let { id to it } }
            .sortedByDescending { it.second }
        others.drop(MAX - 1).forEach { edit.remove(it.first) }
        edit.apply()
        refresh(context)
    }

    /** La position où reprendre [videoId], ou null pour partir du début. */
    fun get(context: Context, videoId: String?): Long? {
        if (videoId.isNullOrBlank()) return null
        return prefs(context).getString(videoId, null)?.substringBefore(';')?.toLongOrNull()?.takeIf { it >= MIN_MS }
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        _progress.value = emptyMap()
    }
}
