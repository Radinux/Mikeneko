package app.mikeneko.ui

import android.os.Build
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.mikeneko.player.PlayerHolder
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * La fréquence de l'écran (2.26.1) : 90 Hz pour l'appli ; pendant une vidéo, celle qui tombe juste
 * avec ses images par seconde (30 / 60 → 60 Hz, 25 / 50 → 50 Hz si l'écran l'a, 24 → 120 Hz), sinon
 * 60 Hz. Une vidéo à 30 i/s sur un écran à 90 Hz montre ses images 3 fois chacune : régulier, mais
 * pour rien ; une à 25 i/s sur 90 Hz saccade. Ce n'est qu'une préférence : le système (ou la gestion
 * de la fréquence du fabricant) peut en décider autrement.
 */
object DisplayRate {
    private const val APP_HZ = 90f
    private const val VIDEO_FALLBACK_HZ = 60f

    fun attach(activity: ComponentActivity) {
        activity.lifecycleScope.launch {
            combine(PlayerHolder.nowPlaying, PlayerHolder.videoFps) { now, fps ->
                // 0 : pas de vidéo (rien, ou de la musique) ; -1 : une vidéo dont on ne connaît pas le rythme
                if (now == null || now.audioOnly) 0 else fps.takeIf { it > 0 } ?: -1
            }.distinctUntilChanged().collect { apply(activity, it) }
        }
    }

    private fun apply(activity: ComponentActivity, videoFps: Int) {
        val display = (if (Build.VERSION.SDK_INT >= 30) activity.display else null)
            ?: @Suppress("DEPRECATION") activity.windowManager.defaultDisplay
            ?: return
        val current = display.mode
        // Mêmes dimensions que maintenant : on ne change que la fréquence
        val modes = display.supportedModes.filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        if (modes.isEmpty()) return
        val mode = when {
            videoFps == 0 -> closestBelow(modes, APP_HZ)
            videoFps < 0 -> closestBelow(modes, VIDEO_FALLBACK_HZ)
            else -> modes.filter { multipleOf(it.refreshRate, videoFps) }.minByOrNull { it.refreshRate }
                ?: closestBelow(modes, VIDEO_FALLBACK_HZ)
        }
        val window = activity.window
        if (window.attributes.preferredDisplayModeId != mode.modeId) {
            window.attributes = window.attributes.apply { preferredDisplayModeId = mode.modeId }
        }
        // Le mode seul ne suffit pas (vu sur un Honor) : le système garde son plafond par défaut de 60 Hz
        // pour l'affichage. Une fréquence demandée par la vue (Android 15+) est un vote d'appli, que ce
        // plafond par défaut ne borne pas
        if (Build.VERSION.SDK_INT >= 35) {
            // Compté pour les vues qui se redessinent : la racine, et la vue Compose qui dessine tout
            window.decorView.requestedFrameRate = mode.refreshRate
            val content = window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content)
            content?.requestedFrameRate = mode.refreshRate
            content?.getChildAt(0)?.requestedFrameRate = mode.refreshRate
        }
    }

    /** [rate] Hz montre chaque image de la vidéo un nombre entier de fois (à 0,5 Hz près). */
    private fun multipleOf(rate: Float, fps: Int): Boolean {
        val times = rate / fps
        return times >= 0.99f && abs(times - times.roundToInt()) * fps < 0.5f
    }

    /** Le mode le plus rapide qui ne dépasse pas [hz] (sinon le plus lent qui existe). */
    private fun closestBelow(modes: List<Display.Mode>, hz: Float): Display.Mode =
        modes.filter { it.refreshRate <= hz + 0.5f }.maxByOrNull { it.refreshRate } ?: modes.minBy { it.refreshRate }
}
