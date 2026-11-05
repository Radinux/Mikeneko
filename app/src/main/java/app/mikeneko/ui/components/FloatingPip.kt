package app.mikeneko.ui.components

import app.mikeneko.ui.theme.statusBarSpace
import app.mikeneko.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import kotlinx.coroutines.launch

/** Taille du mini-lecteur, oreilles comprises. */
val MiniPipSize = DpSize(180.dp, 115.dp)

/**
 * Où le mini-lecteur est rangé : côté droit ou gauche, et à quelle hauteur
 * (0 = tout en haut, 1 = tout en bas, juste au-dessus du dock).
 * Gardé en fraction pour rester juste quand le dock apparaît/disparaît ou que l'écran tourne.
 */
@Stable
class PipPlacement(right: Boolean = true, height: Float = 1f) {
    var right by mutableStateOf(right)
    var height by mutableFloatStateOf(height)

    companion object {
        val Saver = listSaver<PipPlacement, Any>(
            save = { listOf(it.right, it.height) },
            restore = { PipPlacement(it[0] as Boolean, it[1] as Float) },
        )
    }
}

@Composable
fun rememberPipPlacement(): PipPlacement = rememberSaveable(saver = PipPlacement.Saver) { PipPlacement() }

/**
 * Le mini-lecteur flottant qu'on peut déplacer au doigt. Au lâcher, il file vers le bord
 * le plus proche (ou dans la direction du lancer) en gardant sa hauteur, comme le PiP d'Android.
 *
 * Occupe tout l'écran sans intercepter les touches : seul le mini-lecteur lui-même réagit.
 * [bottomMargin] : place réservée en bas (dock), au-dessus de la barre de navigation.
 */
@Composable
fun FloatingMiniPip(
    placement: PipPlacement,
    bottomMargin: Dp,
    onExpand: () -> Unit,
    onClose: () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val moveLeft = stringResource(R.string.pip_move_left)
    val moveRight = stringResource(R.string.pip_move_right)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val margin = with(density) { 16.dp.toPx() }
        val pipWidth = with(density) { MiniPipSize.width.toPx() }
        val pipHeight = with(density) { MiniPipSize.height.toPx() }

        // Zone où il peut se ranger : sous la barre d'état, au-dessus du dock
        val minX = margin
        val maxX = (width - margin - pipWidth).coerceAtLeast(minX)
        val minY = WindowInsets.statusBarSpace.getTop(density) + with(density) { 12.dp.toPx() }
        val maxY = (height - WindowInsets.navigationBars.getBottom(density) - with(density) { bottomMargin.toPx() } - pipHeight)
            .coerceAtLeast(minY)

        fun restingPlace() = Offset(
            x = if (placement.right) maxX else minX,
            y = minY + (maxY - minY) * placement.height.coerceIn(0f, 1f),
        )

        val offset = remember { Animatable(restingPlace(), Offset.VectorConverter) }
        var dragging by remember { mutableStateOf(false) }
        var settling by remember { mutableStateOf(false) }

        // Le dock monte/descend, l'écran tourne… : on suit, sauf pendant un geste
        val resting = restingPlace()
        LaunchedEffect(resting) {
            if (!dragging && !settling) offset.snapTo(resting)
        }

        fun settle(velocity: Offset) {
            // On projette le lancer un peu plus loin pour choisir le bord et la hauteur
            val projected = offset.value + velocity * 0.12f
            placement.right = projected.x + pipWidth / 2f > width / 2f
            placement.height = if (maxY > minY) ((projected.y - minY) / (maxY - minY)).coerceIn(0f, 1f) else 1f
            settling = true
            scope.launch {
                offset.animateTo(
                    targetValue = restingPlace(),
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                    initialVelocity = velocity,
                )
                settling = false
            }
        }

        fun moveTo(right: Boolean) {
            placement.right = right
            settle(Offset.Zero)
        }

        Box(
            Modifier
                .offset { offset.value.round() }
                .pointerInput(Unit) {
                    val tracker = VelocityTracker()
                    detectDragGestures(
                        onDragStart = {
                            dragging = true
                            tracker.resetTracking()
                            scope.launch { offset.stop() }
                        },
                        onDragEnd = {
                            dragging = false
                            val v = tracker.calculateVelocity()
                            settle(Offset(v.x, v.y))
                        },
                        onDragCancel = {
                            dragging = false
                            settle(Offset.Zero)
                        },
                    ) { change, amount ->
                        // Le geste consomme le toucher : le tap du mini-lecteur ne se déclenche pas
                        change.consume()
                        val next = offset.value + amount
                        // Position à l'écran (et pas dans le mini-lecteur, qui bouge) pour la vitesse
                        tracker.addPosition(change.uptimeMillis, next + change.position)
                        scope.launch {
                            offset.snapTo(
                                Offset(
                                    next.x.coerceIn(0f, (width - pipWidth).coerceAtLeast(0f)),
                                    next.y.coerceIn(0f, (height - pipHeight).coerceAtLeast(0f)),
                                ),
                            )
                        }
                    }
                }
                .semantics {
                    customActions = listOf(
                        if (placement.right) {
                            CustomAccessibilityAction(moveLeft) { moveTo(right = false); true }
                        } else {
                            CustomAccessibilityAction(moveRight) { moveTo(right = true); true }
                        },
                    )
                },
        ) {
            MiniPip(onExpand = onExpand, onClose = onClose)
        }
    }
}
