package app.mikeneko.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import app.mikeneko.R
import app.mikeneko.player.PlayerHolder
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.GlassTextShadow
import app.mikeneko.ui.theme.Rounded
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Après un saut, les taps du même côté ajoutent encore 10 s pendant ce temps. */
private const val SEEK_WINDOW_MS = 800L
private const val SEEK_STEP_MS = 10_000L

/**
 * La surface tactile posée sur la vidéo :
 * - un tap montre / cache les commandes ([onToggleControls]) ;
 * - un double tap à gauche recule de 10 s, à droite avance de 10 s, sans afficher les
 *   commandes. Tant que la bulle est là, chaque tap du même côté ajoute 10 s (comme sur YouTube).
 *
 * [canSeek] faux (direct) : seul le tap reste, sans attendre un éventuel deuxième tap.
 * [onSeek] est appelé à chaque saut (pour relancer le minuteur des commandes, par exemple).
 */
@Composable
fun TapToSeekLayer(
    controlsShown: Boolean,
    canSeek: Boolean,
    onToggleControls: () -> Unit,
    modifier: Modifier = Modifier,
    onSeek: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val toggle by rememberUpdatedState(onToggleControls)
    val seeked by rememberUpdatedState(onSeek)

    var side by remember { mutableIntStateOf(0) } // -1 gauche, 1 droite, 0 aucune bulle
    var shownSide by remember { mutableIntStateOf(1) } // garde le côté pendant le fondu de sortie
    var seconds by remember { mutableIntStateOf(0) }
    var taps by remember { mutableIntStateOf(0) }
    var lastSeekAt by remember { mutableLongStateOf(0L) }

    fun seek(towards: Int, at: Long) {
        if (towards != side || at - lastSeekAt > SEEK_WINDOW_MS) seconds = 0
        side = towards
        shownSide = towards
        seconds += (SEEK_STEP_MS / 1000).toInt()
        lastSeekAt = at
        taps++
        PlayerHolder.seekBy(towards * SEEK_STEP_MS)
        seeked()
    }

    // La bulle disparaît un peu après le dernier tap
    LaunchedEffect(taps) {
        if (taps == 0) return@LaunchedEffect
        delay(SEEK_WINDOW_MS)
        side = 0
    }

    val toggleLabel = stringResource(if (controlsShown) R.string.controls_hide else R.string.controls_show)
    val rewindLabel = stringResource(R.string.rewind_10)
    val forwardLabel = stringResource(R.string.forward_10)

    Box(
        modifier
            .fillMaxSize()
            .semantics {
                onClick(toggleLabel) { toggle(); true }
                if (canSeek) customActions = listOf(
                    CustomAccessibilityAction(rewindLabel) { PlayerHolder.seekBy(-SEEK_STEP_MS); seeked(); true },
                    CustomAccessibilityAction(forwardLabel) { PlayerHolder.seekBy(SEEK_STEP_MS); seeked(); true },
                )
            }
            .pointerInput(canSeek) {
                var pendingTap: Job? = null
                var lastUpAt = 0L
                var lastUpAtPos = Offset.Zero
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    // Le doigt reste posé (2.21.0) : ×2 jusqu'à ce qu'il se lève, et ce n'est pas un tap
                    var held = false
                    val quick = try {
                        withTimeout(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        held = true
                        null
                    }
                    if (held && canSeek) {
                        PlayerHolder.holdSpeed(true)
                        try {
                            waitForUpOrCancellation()?.consume()
                        } finally {
                            PlayerHolder.holdSpeed(false)
                        }
                        return@awaitEachGesture
                    }
                    // null : le doigt a glissé (défilement de la page) ou est sorti de la vidéo
                    val up = (if (held) waitForUpOrCancellation() else quick) ?: return@awaitEachGesture
                    up.consume()
                    if (!canSeek) {
                        toggle()
                        return@awaitEachGesture
                    }
                    val at = up.uptimeMillis
                    val towards = if (up.position.x < size.width / 2f) -1 else 1
                    val streak = side == towards && at - lastSeekAt <= SEEK_WINDOW_MS
                    val double = pendingTap?.isActive == true &&
                        at - lastUpAt <= viewConfiguration.doubleTapTimeoutMillis &&
                        (up.position - lastUpAtPos).getDistance() <= viewConfiguration.touchSlop * 4
                    if (streak || double) {
                        pendingTap?.cancel()
                        pendingTap = null
                        seek(towards, at)
                    } else {
                        // Peut-être le début d'un double tap : on attend avant de basculer les commandes
                        pendingTap = scope.launch {
                            delay(viewConfiguration.doubleTapTimeoutMillis)
                            toggle()
                        }
                    }
                    lastUpAt = at
                    lastUpAtPos = up.position
                }
            },
    ) {
        HoldSpeedBadge(Modifier.align(Alignment.TopCenter).padding(top = 14.dp))
        AnimatedVisibility(
            visible = side != 0,
            modifier = Modifier.fillMaxHeight().fillMaxWidth(0.42f).align(if (shownSide < 0) Alignment.CenterStart else Alignment.CenterEnd),
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(320)),
        ) {
            SeekBubble(forward = shownSide > 0, seconds = seconds, taps = taps)
        }
    }
}

/** « 2× » en haut de l'image tant que le doigt maintenu accélère la lecture. */
@Composable
fun HoldSpeedBadge(modifier: Modifier = Modifier) {
    val boosted by PlayerHolder.boosted.collectAsState()
    AnimatedVisibility(boosted, modifier, enter = fadeIn(tween(120)), exit = fadeOut(tween(200))) {
        Row(
            Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(percent = 50)).padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(MkIcons.Speed, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text("2×", style = TextStyle(fontFamily = Rounded, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold))
        }
    }
}

/** La demi-bulle claire du côté tapé, avec la flèche qui rebondit à chaque tap. */
@Composable
private fun SeekBubble(forward: Boolean, seconds: Int, taps: Int) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(taps) {
        pop.snapTo(0.78f)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    val round = RoundedCornerShape(percent = 50)
    val shape = if (forward) RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
    else RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.14f), shape), contentAlignment = Alignment.Center) {
        Column(
            Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.28f), round), contentAlignment = Alignment.Center) {
                Icon(if (forward) MkIcons.Forward else MkIcons.Rewind, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Text(
                stringResource(if (forward) R.string.seek_forward_amount else R.string.seek_back_amount, seconds),
                style = TextStyle(fontFamily = Rounded, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, shadow = GlassTextShadow),
            )
        }
    }
}
