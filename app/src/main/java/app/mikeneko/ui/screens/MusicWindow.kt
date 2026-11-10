package app.mikeneko.ui.screens

import androidx.compose.runtime.derivedStateOf
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import app.mikeneko.ui.glass.liquidWaveSupported
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.mikeneko.R
import app.mikeneko.data.ChannelItem
import app.mikeneko.data.MusicCollection
import app.mikeneko.player.PlayerHolder
import app.mikeneko.ui.components.DOCK_RAIL_SPACE
import app.mikeneko.ui.components.PlayerSettingsSheet
import app.mikeneko.ui.components.rememberCoverColor
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.glass.LiquidGlass
import app.mikeneko.ui.theme.statusBarSpace
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Le dock musique (pilule) qui s'étire en fenêtre. À poser sous le dock principal (qui reste visible
 * et utilisable) ; les panneaux de la musique (file, paroles, minuteur) vont dans [MusicPlayerSheets],
 * au-dessus du dock.
 */
@Composable
fun MusicDockWindow(
    state: MusicPlayerState,
    backdrop: Backdrop,
    onDownload: (String) -> Unit,
    onOpenArtist: (ChannelItem) -> Unit,
    onOpenAlbum: (MusicCollection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBarSpace.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    BackHandler(enabled = state.open) { state.collapse() }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val layoutDirection = LocalLayoutDirection.current
        val safeSides = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
        val startInset = safeSides.calculateStartPadding(layoutDirection) + if (landscape) DOCK_RAIL_SPACE else 0.dp
        val endInset = safeSides.calculateEndPadding(layoutDirection)
        val geometry = remember(width, height, statusTop, navBottom, density, landscape, startInset, endInset) {
            with(density) {
                if (landscape) {
                    // Paysage : la pilule en bas à droite (plus étroite), la fenêtre à droite du dock en colonne
                    val pillBottom = height - (navBottom + 14.dp).toPx()
                    val pillRight = width - (endInset + 16.dp).toPx()
                    val pillWidth = minOf(420.dp.toPx(), pillRight - (startInset + 16.dp).toPx())
                    return@with WindowGeometry(
                        pillLeft = pillRight - pillWidth,
                        pillRight = pillRight,
                        pillTop = pillBottom - MUSIC_DOCK_HEIGHT.toPx(),
                        bottom = pillBottom,
                        windowLeft = (startInset + 10.dp).toPx(),
                        windowRight = width - (endInset + 10.dp).toPx(),
                        windowTop = (statusTop + 10.dp).toPx(),
                    )
                }
                val pillBottom = height - (navBottom + MUSIC_DOCK_BOTTOM).toPx()
                WindowGeometry(
                    pillLeft = 16.dp.toPx(),
                    pillRight = width - 16.dp.toPx(),
                    pillTop = pillBottom - MUSIC_DOCK_HEIGHT.toPx(),
                    bottom = pillBottom,
                    windowLeft = 10.dp.toPx(),
                    windowRight = width - 10.dp.toPx(),
                    windowTop = (statusTop + 10.dp).toPx(),
                )
            }
        }

        // Derrière la fenêtre : la page s'assombrit à mesure qu'elle s'ouvre ; un tap dehors la referme.
        // derivedStateOf (2.10.3) : avant, la position était relue ici à chaque image, et tout le dock
        // (vitre, pilule, fenêtre) se recomposait pendant l'animation
        val dimmed by remember(state) { derivedStateOf { state.open || state.expand.value > 0.01f } }
        if (dimmed) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = state.expand.value.coerceIn(0f, 1f) }
                    .background(Color.Black.copy(alpha = 0.22f)) // léger : la page se voit à travers la fenêtre
                    .then(
                        if (state.open) {
                            Modifier.clickable(interactionSource = null, indication = null, onClickLabel = stringResource(R.string.player_minimize)) { state.collapse() }
                        } else {
                            Modifier
                        },
                    ),
            )
        }

        state.travel = geometry.travel
        var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        // La pilule prend la teinte de la pochette en cours
        val playingNow = musicNow()
        val parkedNow = if (playingNow == null) parkedTrack() else null
        val pillColor by rememberCoverColor(playingNow?.thumbnail ?: parkedNow?.thumbnail, fallback = Color(0xFF100D0B))
        // Une vidéo joue et la fenêtre s'ouvre : on remet le morceau qu'elle avait mis de côté (en pause)
        val context = LocalContext.current
        LaunchedEffect(state.open) {
            if (state.open && PlayerHolder.nowPlaying.value?.audioOnly == false && PlayerHolder.parkedMusic.value != null) {
                PlayerHolder.launchPlayback { PlayerHolder.restoreParkedMusic(context) }
            }
        }
        // Le premier dessin de la fenêtre, fait en avance (2.11.11) : sans ça, la première ouverture
        // après le lancement accrochait un peu (toute la fenêtre dessinée pour la première fois, ses
        // images envoyées au GPU, le shader de la vague compilé, au milieu de l'animation). Un peu
        // après le lancement, elle est posée quelques images, invisible (alpha presque nul : à 0, le
        // système ne la dessinerait pas du tout), puis retirée.
        var warm by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(1_500)
            liquidWaveSupported // compile le shader de la vague maintenant, pas à la première ouverture
            if (state.open || state.expand.value != 0f) return@LaunchedEffect
            warm = true
            repeat(4) { withFrameNanos { } }
            warm = false
        }
        LiquidGlass(
            backdrop,
            Modifier
                // La vitre : de la pilule à la fenêtre selon state.expand (lu ici : seulement une remise en page)
                .layout { measurable, constraints ->
                    val r = geometry.at(state.expand.value)
                    val placeable = measurable.measure(Constraints.fixed(r.width.roundToInt(), r.height.roundToInt()))
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(r.left.roundToInt(), r.top.roundToInt()) }
                }
                .onPlaced { coordinates = it }
                .pointerInput(geometry) { stretchGesture(state, geometry) { coordinates } }
                // Au-delà de l'ouverture (on tire encore, ou le rebond) : la vitre et son contenu
                // s'étirent un peu ensemble depuis le bas, au lieu de laisser du verre vide en haut
                .graphicsLayer {
                    val over = ((state.expand.value - 1f) / (OVERPULL - 1f)).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    scaleY = 1f + 0.016f * over
                    scaleX = 1f - 0.008f * over
                },
            cornerRadius = 34.dp,
            bezel = 22.dp,
            refraction = 22.dp,
            tint = pillColor.copy(alpha = 0.5f),
            onClick = if (state.open) null else ({ state.expand() }),
        ) {
            // La pilule : visible au début du geste, elle s'efface vite
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(MUSIC_DOCK_HEIGHT)
                    .placeWhile { state.expand.value < 0.35f }
                    .graphicsLayer { alpha = (1f - state.expand.value * 2.8f).coerceIn(0f, 1f) },
            ) {
                MusicDockRow()
            }
            // La fenêtre : à sa taille finale dès le départ (pas de remise en page pendant le geste),
            // collée en bas, découpée par la vitre ; elle apparaît quand la vitre a assez grandi
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        val w = (geometry.windowRight - geometry.windowLeft).roundToInt()
                        val h = (geometry.bottom - geometry.windowTop).roundToInt()
                        val placeable = measurable.measure(Constraints.fixed(w, h))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            if (state.expand.value > 0.3f || state.pulling || warm) placeable.place((constraints.maxWidth - w) / 2, constraints.maxHeight - h)
                        }
                    }
                    // Regarde seulement quand le doigt se lève (ou que le geste est annulé), sans rien consommer
                    .pointerInput(state) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Final)
                            } while (event.changes.any { it.pressed })
                            state.endPull()
                        }
                    }
                    .graphicsLayer { alpha = if (warm && state.expand.value <= 0.3f) 0.004f else ((state.expand.value - 0.3f) / 0.4f).coerceIn(0f, 1f) },
            ) {
                PlayerWindowContent(state, backdrop, onDownload, onOpenArtist, onOpenAlbum)
            }
        }
    }
}

/** Jusqu'où on peut tirer la fenêtre ouverte (au-delà de 1 : élastique). */
private const val OVERPULL = 1.08f

/** Les deux formes de la vitre (en px) : la pilule et la fenêtre, toutes deux posées sur [bottom]. */
@Immutable
private class WindowGeometry(
    val pillLeft: Float,
    val pillRight: Float,
    val pillTop: Float,
    val bottom: Float,
    val windowLeft: Float,
    val windowRight: Float,
    val windowTop: Float,
) {
    val travel get() = pillTop - windowTop

    class Box(val left: Float, val top: Float, val width: Float, val height: Float)

    /**
     * La vitre pour une ouverture [p]. Au-delà de 1, elle garde la taille de la fenêtre (l'étirement
     * est fait par un graphicsLayer, contenu compris) ; sous 0 (en se posant), la pilule s'écrase : plus large, moins haute, comme une goutte.
     */
    fun at(p: Float): Box {
        if (p < 0f) {
            val s = p.coerceAtLeast(-0.12f) / 0.12f // 0 → -1
            val pillHeight = bottom - pillTop
            val h = pillHeight * (1f + 0.22f * s)
            val grow = -s * 10f
            return Box(pillLeft - grow, bottom - h, pillRight - pillLeft + 2 * grow, h)
        }
        val t = p.coerceAtMost(1f)
        val left = lerp(pillLeft, windowLeft, t)
        val right = lerp(pillRight, windowRight, t)
        val top = lerp(pillTop, windowTop, t)
        return Box(left, top, right - left, bottom - top)
    }
}

/** Pose le contenu seulement tant que [visible] (sinon : ni dessiné, ni touchable). */
private fun Modifier.placeWhile(visible: () -> Boolean) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { if (visible()) placeable.place(0, 0) }
}

/**
 * Tirer la vitre vers le haut / le bas. Les glissés horizontaux sont laissés à la pilule (changer de
 * morceau) et aux réglettes ; un tap reste un tap. Les positions sont prises à l'écran : la vitre
 * bouge sous le doigt pendant le geste.
 */
private suspend fun PointerInputScope.stretchGesture(state: MusicPlayerState, geometry: WindowGeometry, coordinates: () -> LayoutCoordinates?) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var vertical = false
        val start = awaitTouchSlopOrCancellation(down.id) { change, over ->
            if (abs(over.y) > abs(over.x)) {
                vertical = true
                change.consume()
            }
        }
        if (start == null || !vertical) return@awaitEachGesture
        state.stop()
        // Le geste coupé en route (2.22.0 : rotation, vitre reconstruite…) ne posait jamais la vitre :
        // elle restait à mi-course, pilule effacée et fenêtre pas encore là. On la pose quoi qu'il arrive.
        var settled = false
        try {
        val tracker = VelocityTracker()
        fun screenY(position: Offset) = coordinates()?.localToRoot(position)?.y ?: position.y
        var lastY = screenY(start.position)
        var p = state.expand.value
        tracker.addPosition(start.uptimeMillis, Offset(0f, lastY))
        drag(start.id) { change ->
            val y = screenY(change.position)
            val dy = y - lastY
            lastY = y
            // Au-delà des bouts, la vitre résiste (élastique)
            val resist = if ((p >= 1f && dy < 0f) || (p <= 0f && dy > 0f)) 0.25f else 1f
            p = (p - dy * resist / geometry.travel).coerceIn(-0.12f, OVERPULL)
            state.dragTo(p)
            tracker.addPosition(change.uptimeMillis, Offset(0f, y))
            change.consume()
        }
        val vy = tracker.calculateVelocity().y // px/s, positif vers le bas
        val open = when {
            vy < -900f -> true
            vy > 900f -> false
            state.open -> p > 0.75f // ouverte : il faut la rabattre d'un bon quart pour la fermer
            else -> p > 0.25f
        }
        state.settle(open, velocity = -vy / geometry.travel)
        settled = true
        } finally {
            if (!settled) state.settle(state.expand.value > 0.5f)
        }
    }
}

/**
 * Les panneaux de la musique (file, paroles, minuteur) : au-dessus du dock principal, en plein écran.
 */
@Composable
fun MusicPlayerSheets(state: MusicPlayerState, backdrop: Backdrop) {
    QueueSheet(backdrop, visible = state.queueOpen, anchor = state.lyricsAnchor, onClose = { state.queueOpen = false })
    LyricsSheet(backdrop, visible = state.lyricsOpen, anchor = state.lyricsAnchor, onClose = { state.lyricsOpen = false })
    if (state.open) PlayerSettingsSheet(backdrop, audioOnly = true)
}
