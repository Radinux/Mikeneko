package app.mikeneko.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.glass.LiquidGlass
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Un panneau en verre qui sort d'un bouton ([anchor], en coordonnées de fenêtre) en s'étirant, et s'y
 * referme sur lui-même. C'était l'animation des paroles (2.15.2) ; depuis 2.21.1 la file, les menus
 * de la musique et les commentaires des shorts la partagent.
 *
 * - Sans [anchor] : il sort d'une capsule en bas de l'écran.
 * - [heightFraction] : la part de la hauteur qu'il prend, ouvert ; null = la hauteur de son contenu.
 * - On le tire vers le bas pour le fermer : par la petite barre du haut et la zone qui porte le
 *   modificateur donné à [content], ou en continuant de tirer quand son contenu est déjà en haut
 *   (sauf quand [canPull] dit non : une ligne qu'on déplace, par exemple). Il rapetisse sous le doigt,
 *   puis finit de se refermer si on lâche assez loin ou assez vite, sinon il se rouvre.
 */
@Composable
fun MorphSheet(
    backdrop: Backdrop,
    visible: Boolean,
    anchor: Rect?,
    onClose: () -> Unit,
    closeLabel: String,
    heightFraction: Float? = 0.78f,
    maxWidth: Dp = Dp.Unspecified,
    tint: Color = Color(0xD01A1613),
    frost: Dp = 30.dp,
    canPull: () -> Boolean = { true },
    content: @Composable ColumnScope.(handle: Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val close by rememberUpdatedState(onClose)
    val pullable by rememberUpdatedState(canPull)
    val closeAt = with(density) { 120.dp.toPx() }
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    /** La course du doigt (en px) qui referme tout le panneau : la différence de hauteur avec le bouton. */
    val travel = remember { floatArrayOf(1f) }
    /** La vitesse du doigt au lâcher, pour que la fermeture la continue. */
    val flung = remember { floatArrayOf(0f) }
    fun release(velocity: Float) {
        scope.launch {
            if (drag.value > closeAt || (velocity > 1_500f && drag.value > 0f)) {
                flung[0] = velocity
                close()
            } else {
                drag.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f))
            }
        }
    }
    val pullDown = remember {
        object : NestedScrollConnection {
            // Le panneau descendu remonte d'abord avant que son contenu ne défile
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && drag.value > 0f) {
                    val used = maxOf(available.y, -drag.value)
                    scope.launch { drag.snapTo(drag.value + used) }
                    return Offset(0f, used)
                }
                return Offset.Zero
            }
            // Le contenu est en haut et on tire encore : c'est le panneau qui descend
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput && pullable()) {
                    scope.launch { drag.snapTo(drag.value + available.y) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drag.value <= 0f) return Velocity.Zero
                release(available.y)
                return available
            }
        }
    }
    val handle = Modifier.pointerInput(Unit) {
        val tracker = VelocityTracker()
        detectVerticalDragGestures(
            onDragStart = { tracker.resetTracking() },
            onDragEnd = { release(tracker.calculateVelocity().y) },
            onDragCancel = { release(0f) },
        ) { change, amount ->
            tracker.addPosition(change.uptimeMillis, change.position)
            scope.launch { drag.snapTo((drag.value + amount).coerceAtLeast(0f)) }
        }
    }

    // L'ouverture : 0 = le bouton, 1 = le panneau ouvert. On reste affiché tant qu'il se referme.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            drag.snapTo(0f)
            progress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 260f))
        } else {
            // On repart d'où le doigt l'a laissé (déjà en partie refermé), à sa vitesse
            val from = progress.value * (1f - (drag.value / travel[0]).coerceIn(0f, 1f))
            val speed = -flung[0] / travel[0]
            flung[0] = 0f
            progress.snapTo(from)
            drag.snapTo(0f)
            progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 380f), initialVelocity = speed)
        }
    }
    if (!visible && progress.value == 0f) return

    var origin by remember { mutableStateOf(Offset.Zero) }
    var measured by remember { mutableIntStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        val pulled = (drag.value / travel[0]).coerceIn(0f, 1f)
        val p = progress.value * (1f - pulled)
        val margin = with(density) { 10.dp.toPx() }
        val bottomInset = maxOf(WindowInsets.navigationBars.getBottom(density), WindowInsets.ime.getBottom(density)).toFloat()
        val fullW = constraints.maxWidth.toFloat()
        val fullH = constraints.maxHeight.toFloat()
        // Le panneau ouvert : en bas, à 10 dp des bords
        val room = (fullH - bottomInset - 2 * margin).coerceAtLeast(1f)
        val openW = (fullW - 2 * margin).let { w -> if (maxWidth != Dp.Unspecified) minOf(w, with(density) { maxWidth.toPx() }) else w }
        val openH = if (heightFraction != null) room * heightFraction else measured.toFloat().coerceIn(1f, room)
        val openLeft = (fullW - openW) / 2f
        val openTop = fullH - bottomInset - margin - openH
        // Le bouton d'où il sort (sans lui : une capsule en bas de l'écran)
        val from = anchor?.translate(-origin.x, -origin.y)
            ?: Rect(openLeft, fullH - bottomInset - margin - with(density) { 54.dp.toPx() }, openLeft + openW, fullH - bottomInset - margin)
        travel[0] = (openH - from.height).coerceAtLeast(1f)
        fun mix(a: Float, b: Float) = a + (b - a) * p
        val left = mix(from.left, openLeft)
        val top = mix(from.top, openTop)
        val width = mix(from.width, openW).coerceAtLeast(1f)
        val height = mix(from.height, openH).coerceAtLeast(1f)
        val corner = with(density) { mix(minOf(from.height, from.width) / 2f, 30.dp.toPx()).toDp() }

        // Le voile derrière, qui suit l'ouverture
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = p.coerceIn(0f, 1f) }.background(Color.Black.copy(alpha = 0.4f))
                .then(if (visible) Modifier.clickable(interactionSource = null, indication = null, onClickLabel = closeLabel, onClick = close) else Modifier),
        )
        LiquidGlass(
            backdrop = backdrop,
            modifier = Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(with(density) { width.toDp() }, with(density) { height.toDp() })
                .nestedScroll(pullDown),
            cornerRadius = corner,
            bezel = 26.dp,
            refraction = 26.dp,
            tint = tint,
            frost = frost,
        ) {
            // Le contenu garde la taille du panneau ouvert (pas de texte qui se réarrange à chaque image),
            // coupé par la vitre ; il apparaît dans la seconde moitié de l'ouverture
            Box(Modifier.fillMaxSize().clipToBounds().graphicsLayer { alpha = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f) }) {
                Box(Modifier.align(Alignment.TopCenter).wrapContentSize(Alignment.TopCenter, unbounded = true)) {
                    val sized = Modifier.width(with(density) { openW.toDp() }).let {
                        if (heightFraction != null) it.then(Modifier.size(with(density) { openW.toDp() }, with(density) { openH.toDp() }))
                        else it.heightIn(max = with(density) { room.toDp() }).onSizeChanged { size -> measured = size.height }
                    }
                    Column(sized) {
                        // La barre du haut : on l'attrape pour tirer le panneau vers le bas
                        Box(
                            Modifier.align(Alignment.CenterHorizontally).then(handle).padding(top = 8.dp, start = 40.dp, end = 40.dp)
                                .size(40.dp, 4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)),
                        )
                        content(handle)
                    }
                }
            }
        }
    }
}
