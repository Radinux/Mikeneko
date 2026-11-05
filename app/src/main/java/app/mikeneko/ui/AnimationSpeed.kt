package app.mikeneko.ui

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.PausableMonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Vitesse des animations de l'appli, la même sur tous les téléphones. 1 = les durées et ressorts écrits
 * dans le code ; 2 = deux fois plus vite (le rendu choisi : celui d'un téléphone à « échelle 0,5× »).
 * Sans ça, Compose suit l'« échelle d'animation » des options développeur de chaque téléphone.
 */
const val ANIMATION_SPEED = 2f

private class FixedMotionScale(override val scaleFactor: Float) : MotionDurationScale

/**
 * Le recomposeur de l'écran, à passer à `setContent(parent = …)`. Comme celui qu'Android crée tout
 * seul (images en pause quand l'appli n'est plus à l'écran), mais avec une échelle d'animation fixe.
 * Seule exception : si les animations sont coupées dans le téléphone (accessibilité), on les coupe aussi.
 */
fun ComponentActivity.fixedSpeedRecomposer(): Recomposer {
    val animationsOff = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val main = AndroidUiDispatcher.Main
    val clock = PausableMonotonicFrameClock(main[MonotonicFrameClock]!!)
    val context = main + clock + FixedMotionScale(if (animationsOff) 0f else 1f / ANIMATION_SPEED)
    val recomposer = Recomposer(context)
    val scope = CoroutineScope(context)
    scope.launch(start = CoroutineStart.UNDISPATCHED) { recomposer.runRecomposeAndApplyChanges() }
    lifecycle.addObserver(
        LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE, Lifecycle.Event.ON_STOP -> clock.pause()
                Lifecycle.Event.ON_START -> clock.resume()
                Lifecycle.Event.ON_DESTROY -> {
                    recomposer.cancel()
                    scope.cancel()
                }
                else -> Unit
            }
        },
    )
    return recomposer
}
