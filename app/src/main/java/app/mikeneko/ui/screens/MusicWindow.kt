package app.mikeneko.ui.screens

import androidx.compose.ui.graphics.lerp
import app.mikeneko.ui.components.rememberCoverColor
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import app.mikeneko.ui.components.DOCK_RAIL_SPACE
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.ui.platform.LocalLayoutDirection
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import app.mikeneko.player.NowPlaying
import app.mikeneko.data.formatCount
import app.mikeneko.data.channelIdFrom
import app.mikeneko.data.cleanArtist
import app.mikeneko.data.Music
import app.mikeneko.data.MusicCollection
import app.mikeneko.data.ChannelItem
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import app.mikeneko.ui.theme.statusBarSpace
import app.mikeneko.ui.theme.statusBarSpacePadding
import app.mikeneko.R
import androidx.compose.ui.res.stringResource
import android.graphics.ColorMatrixColorFilter
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.mikeneko.data.Favorites
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.data.VideoItem
import app.mikeneko.data.sameTrack
import app.mikeneko.data.YouTubeRepository
import app.mikeneko.player.PlayerHolder
import app.mikeneko.player.SettingsPage
import app.mikeneko.player.SleepTimer
import app.mikeneko.ui.components.PlayerSettingsSheet
import app.mikeneko.ui.components.EmptyCat
import app.mikeneko.ui.components.GlassCircleButton
import app.mikeneko.ui.components.Loading
import app.mikeneko.ui.components.Tag
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.glass.LiquidGlass
import app.mikeneko.ui.glass.backdropSource
import app.mikeneko.ui.glass.rememberBackdrop
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.GlassTextShadow
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.Rounded
import coil3.compose.AsyncImage
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.zIndex
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.Job
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import app.mikeneko.data.SearchHistory
import app.mikeneko.data.LocalMusic
import app.mikeneko.player.Repeat
import app.mikeneko.ui.components.ProgressWithSegments
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.CoroutineScope
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Hauteur du dock musique (la pilule du morceau en cours, au-dessus du dock). */
val MUSIC_DOCK_HEIGHT = 64.dp

/** Le dock musique se pose au-dessus du dock : 68 dp de dock + 20 dp de marge + un petit écart. */
val MUSIC_DOCK_BOTTOM = 98.dp

/**
 * Le dock musique et sa fenêtre : la même vitre en verre liquide, en pilule au-dessus du dock, qu'on
 * étire vers le haut jusqu'à une grande fenêtre (et qu'on rabat vers le bas). [expand] : 0 = pilule,
 * 1 = fenêtre ; un peu au-delà pendant les rebonds du ressort.
 */
@Stable
class MusicPlayerState(private val scope: CoroutineScope) {
    val expand = Animatable(0f)

    /** Ouverte (ou en train de s'ouvrir) : le retour la referme, les menus de la musique s'affichent. */
    var open by mutableStateOf(false)
        private set
    var queueOpen by mutableStateOf(false)
    var lyricsOpen by mutableStateOf(false)

    fun expand(velocity: Float = 0f) = settle(true, velocity)

    fun collapse(velocity: Float = 0f) {
        if (!open && expand.value == 0f) return
        settle(false, velocity)
    }

    /** [velocity] : vitesse du doigt (en « fenêtres par seconde ») pour que le ressort la continue. */
    internal fun settle(target: Boolean, velocity: Float = 0f) {
        open = target
        if (!target) {
            queueOpen = false
            lyricsOpen = false
            PlayerHolder.settingsPage.value = null
        }
        scope.launch {
            expand.animateTo(
                if (target) 1f else 0f,
                // Ressort un peu mou : la vitre dépasse, puis tremblote en se posant (comme de la gelée)
                if (target) spring(dampingRatio = 0.66f, stiffness = 58f) else spring(dampingRatio = 0.62f, stiffness = 75f),
                initialVelocity = velocity,
            )
        }
    }

    internal fun dragTo(value: Float) {
        scope.launch { expand.snapTo(value) }
    }

    internal fun stop() {
        scope.launch { expand.stop() }
    }

    /** Hauteur (px) entre la pilule et la fenêtre : fixée par [MusicDockWindow]. */
    internal var travel = 1f

    /**
     * La fenêtre est tirée vers le bas par son défilement (le doigt est dessus). Tant que c'est vrai,
     * la fenêtre reste posée même sous 30 % : sinon Compose ne lui envoie plus le doigt (ni son
     * lâcher), le geste ne finit jamais et la vitre reste figée à mi-course.
     */
    var pulling by mutableStateOf(false)
        private set

    /**
     * La fenêtre défile (artiste, album sous les commandes). Tout en haut, tirer vers le bas la rabat
     * comme avant : ce que le défilement n'utilise pas fait descendre la fenêtre.
     */
    val scrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Fenêtre déjà en train de descendre : remonter le doigt la rouvre avant de faire défiler
            if (!pulling || available.y >= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
            val before = expand.value
            dragTo((before - available.y / travel).coerceAtMost(1f))
            if (expand.value >= 1f) pulling = false
            return Offset(0f, available.y)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
            pulling = true
            dragTo((expand.value - available.y / travel).coerceIn(0f, 1f))
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!pulling) return Velocity.Zero
            pulling = false
            val down = available.y
            settle(if (down > 900f) false else expand.value > 0.75f, velocity = -down / travel)
            return available
        }
    }

    /**
     * Le doigt a quitté la fenêtre. Normalement le défilement a lancé [scrollConnection.onPreFling],
     * qui repose la vitre ; mais un geste annulé (geste système…) ne lance rien. Après une image, si
     * personne ne l'a reposée, on la pose ici (sans élan).
     */
    internal fun endPull() {
        if (!pulling) return
        scope.launch {
            withFrameNanos { } // laisse d'abord passer le lancer du défilement
            if (!pulling) return@launch
            pulling = false
            settle(expand.value > 0.75f)
        }
    }
}

@Composable
fun rememberMusicPlayerState(): MusicPlayerState {
    val scope = rememberCoroutineScope()
    return remember { MusicPlayerState(scope) }
}

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

        // Derrière la fenêtre : la page s'assombrit à mesure qu'elle s'ouvre ; un tap dehors la referme
        if (state.open || state.expand.value > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = state.expand.value.coerceIn(0f, 1f) }
                    .background(Color.Black.copy(alpha = 0.38f))
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
                    .graphicsLayer { alpha = (1f - state.expand.value * 3f).coerceIn(0f, 1f) },
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
                            if (state.expand.value > 0.3f || state.pulling) placeable.place((constraints.maxWidth - w) / 2, constraints.maxHeight - h)
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
                    .graphicsLayer { alpha = ((state.expand.value - 0.3f) / 0.45f).coerceIn(0f, 1f) },
            ) {
                PlayerWindowContent(state, onDownload, onOpenArtist, onOpenAlbum)
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

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

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
    }
}

/**
 * Le contenu de la pilule : pochette, titre, lecture / suivant. Glisser à gauche / à droite change
 * de morceau (le titre suit le doigt).
 */
@Composable
private fun MusicDockRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Une vidéo qui joue n'est pas de la musique : on montre le morceau qu'elle a mis de côté (s'il y en a)
    val now = musicNow()
    val parked = if (now == null) parkedTrack() else null
    val playing = PlayerHolder.isPlaying.collectAsState().value && now != null
    val queue by PlayerHolder.queue.collectAsState()
    val queueIndex by PlayerHolder.queueIndex.collectAsState()
    val loading = PlayerHolder.loading.collectAsState().value && now != null
    val haptic = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    val canSwipe = now != null && queue.size > 1
    /** Décalage horizontal de la pochette + titre pendant un glissé. */
    val swipe = remember { Animatable(0f) }

    // Pendant le chargement du morceau suivant, on montre déjà son nom et sa pochette
    val pending = if (loading) queue.getOrNull(queueIndex)?.takeIf { it.url != now?.url } else null
    val shownTitle = pending?.title ?: now?.title ?: parked?.title
    val shownThumbnail = pending?.thumbnail ?: now?.thumbnail ?: parked?.thumbnail
    val shownSubtitle = pending?.let { stringResource(R.string.radio_loading_suffix, it.channel) }
        ?: now?.let { if (it.live) stringResource(R.string.radio_live_suffix, it.channel) else it.channel }
        ?: parked?.channel
    val nextLabel = stringResource(R.string.radio_next)
    val previousLabel = stringResource(R.string.radio_previous)

    /** Glissé validé : le titre sort d'un côté, le morceau change, le nouveau entre de l'autre. */
    fun skipBySwipe(forward: Boolean, width: Float) {
        scope.launch {
            val out = if (width > 0f) width else 400f
            swipe.animateTo(if (forward) -out else out, tween(140))
            launch {
                runCatching { if (forward) PlayerHolder.next(context) else PlayerHolder.previous(context) }
                    .onFailure { Toast.makeText(context, context.getString(R.string.radio_station_failed), Toast.LENGTH_SHORT).show() }
            }
            swipe.snapTo(if (forward) out * 0.6f else -out * 0.6f)
            swipe.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .pointerInput(canSwipe) {
                if (!canSwipe) return@pointerInput
                var crossed = false
                detectHorizontalDragGestures(
                    onDragStart = {
                        crossed = false
                        scope.launch { swipe.stop() }
                    },
                    onDragEnd = {
                        val distance = swipe.value
                        when {
                            distance <= -threshold -> skipBySwipe(forward = true, width = size.width.toFloat())
                            distance >= threshold -> skipBySwipe(forward = false, width = size.width.toFloat())
                            else -> scope.launch { swipe.animateTo(0f, spring(dampingRatio = 0.65f)) }
                        }
                    },
                    onDragCancel = { scope.launch { swipe.animateTo(0f, spring(dampingRatio = 0.65f)) } },
                ) { change, dx ->
                    change.consume()
                    val next = swipe.value + dx * 0.9f
                    // Petit retour sous le doigt au moment où le glissé « compte »
                    val past = abs(next) >= threshold
                    if (past && !crossed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    crossed = past
                    scope.launch { swipe.snapTo(next) }
                }
            }
            .semantics {
                if (canSwipe) {
                    customActions = listOf(
                        CustomAccessibilityAction(nextLabel) { skipBySwipe(true, 0f); true },
                        CustomAccessibilityAction(previousLabel) { skipBySwipe(false, 0f); true },
                    )
                }
            }
            .padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // La pochette et le titre suivent le doigt (les boutons restent en place)
        Row(
            Modifier
                .weight(1f)
                .clipToBounds()
                .graphicsLayer {
                    translationX = swipe.value
                    alpha = 1f - (abs(swipe.value) / (size.width * 0.7f)).coerceIn(0f, 0.85f)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Pelage.RouxNuit)) {
                AsyncImage(shownThumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    shownTitle ?: stringResource(R.string.radio_pick),
                    style = TextStyle(fontFamily = Rounded, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, shadow = GlassTextShadow),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    shownSubtitle ?: stringResource(R.string.radio_tap),
                    style = TextStyle(fontFamily = Rounded, color = Color(0xFFEDE6DF), fontSize = 12.sp, shadow = GlassTextShadow),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            now != null -> IconTap(if (playing) MkIcons.Pause else MkIcons.Play, stringResource(if (playing) R.string.pause else R.string.play)) { PlayerHolder.togglePlay() }
            // Pendant une vidéo : ▶ reprend le morceau mis de côté (la vidéo s'arrête), pas la vidéo
            parked != null -> IconTap(MkIcons.Play, stringResource(R.string.play)) {
                PlayerHolder.launchPlayback { PlayerHolder.restoreParkedMusic(context, autoPlay = true) }
            }
        }
        if (now != null && queue.size > 1) IconTap(MkIcons.Next, nextLabel) { scope.launch { runCatching { PlayerHolder.next(context) } } }
    }
}

@Composable
private fun IconTap(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

/**
 * Les panneaux de la musique (file, paroles, minuteur) : au-dessus du dock principal, en plein écran.
 */
@Composable
fun MusicPlayerSheets(state: MusicPlayerState, backdrop: Backdrop) {
    QueueSheet(backdrop, visible = state.queueOpen, onClose = { state.queueOpen = false })
    LyricsSheet(backdrop, visible = state.lyricsOpen, onClose = { state.lyricsOpen = false })
    if (state.open) PlayerSettingsSheet(backdrop, audioOnly = true)
}

/**
 * La fenêtre ouverte : pochette, titre, progression et toutes les touches. Le fond est la pochette
 * floutée sur un voile sombre (pour lire le texte), et c'est elle que réfractent les boutons en verre.
 */
@Composable
private fun PlayerWindowContent(
    state: MusicPlayerState,
    onDownload: (String) -> Unit,
    onOpenArtist: (ChannelItem) -> Unit,
    onOpenAlbum: (MusicCollection) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val now = musicNow()
    val playing = PlayerHolder.isPlaying.collectAsState().value && now != null
    val favorites = remember { Favorites.get(context) }
    val favoriteList by favorites.items.collectAsState()
    val liked = now?.url?.let { url -> favoriteList.any { it.url == url } } == true
    val canSkip = PlayerHolder.queue.collectAsState().value.size > 1 || PlayerHolder.autoMix.collectAsState().value
    val shuffle by PlayerHolder.shuffle.collectAsState()
    val repeat by PlayerHolder.repeat.collectAsState()
    val timer by PlayerHolder.sleepTimer.collectAsState()
    val local = LocalMusic.isLocal(now?.url)
    val live = now?.live == true

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun skip(block: suspend () -> Unit) {
        scope.launch { runCatching { block() }.onFailure { toast(context.getString(R.string.radio_station_failed)) } }
    }
    /** Le morceau en cours, comme élément de liste (favoris, playlists). */
    fun current(): VideoItem? = now?.let { n ->
        PlayerHolder.queue.value.firstOrNull { it.url == n.url }
            ?: VideoItem(n.url, n.title, n.channel, n.thumbnail, isLive = n.live)
    }
    val glass = rememberBackdrop()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(Modifier.fillMaxSize()) {
        // Ce que réfractent les boutons : la pochette floutée sur un voile sombre (la page reste
        // devinable derrière, à travers la vitre)
        // Le fond : un dégradé de la couleur de la pochette vers la nuit (il change avec le morceau),
        // avec un soupçon de la pochette très floutée pour la matière
        val coverColor by rememberCoverColor(now?.thumbnail)
        Box(
            Modifier
                .fillMaxSize()
                .backdropSource(glass)
                .background(Brush.verticalGradient(0f to coverColor, 0.6f to lerp(coverColor, Pelage.Nuit, 0.6f), 1f to Pelage.Nuit)),
        ) {
            AsyncImage(
                now?.thumbnail,
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(60.dp).graphicsLayer { alpha = 0.14f },
            )
        }

        // Sous les commandes : l'artiste et l'album (on fait défiler la fenêtre)
        val scroll = rememberScrollState()
        LaunchedEffect(state.open) { if (!state.open) scroll.scrollTo(0) }
        LaunchedEffect(now?.url) { scroll.animateScrollTo(0) }
        BoxWithConstraints(Modifier.fillMaxSize()) {
        // Les morceaux de la fenêtre, rangés en colonne (portrait) ou en deux parties (paysage)
        val topRow: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassCircleButton(glass, MkIcons.ChevronDown, stringResource(R.string.player_minimize), size = 44.dp) { state.collapse() }
                Text(
                    stringResource(if (PlayerHolder.autoMix.collectAsState().value) R.string.radio_mode_mix else R.string.radio_mode),
                    Modifier.weight(1f),
                    style = TextStyle(fontFamily = Rounded, color = Pelage.Texte, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, shadow = GlassTextShadow, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                )
                GlassCircleButton(
                    glass,
                    MkIcons.Moon,
                    stringResource(if (timer == SleepTimer.Off) R.string.sleep_timer else R.string.sleep_timer_active),
                    size = 44.dp,
                    tint = if (timer == SleepTimer.Off) Color(0x40100D0B) else Pelage.Roux.copy(alpha = 0.7f),
                    iconTint = if (timer == SleepTimer.Off) Color.White else Pelage.Encre,
                ) { PlayerHolder.settingsPage.value = SettingsPage.Minuteur }
            }
        }
        val coverArt: @Composable () -> Unit = {
                Box(
                    Modifier
                        .fillMaxWidth(0.9f)
                        .aspectRatio(1f, matchHeightConstraintsFirst = true)
                        .shadow(22.dp, RoundedCornerShape(26.dp))
                        .border(3.dp, Pelage.Creme, RoundedCornerShape(26.dp))
                        .clip(RoundedCornerShape(26.dp))
                        .background(Pelage.RouxNuit),
                    contentAlignment = Alignment.Center,
                ) {
                    if (now?.thumbnail == null) {
                        Icon(MkIcons.Music, null, tint = Pelage.Texte.copy(alpha = 0.6f), modifier = Modifier.size(72.dp))
                    }
                    AsyncImage(now?.thumbnail, stringResource(R.string.artwork), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            
        }
        val titleRow: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(now?.title ?: stringResource(R.string.radio_pick), style = TextStyle(fontFamily = Rounded, color = Pelage.Texte, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, shadow = GlassTextShadow), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(now?.channel.orEmpty(), style = TextStyle(fontFamily = Rounded, color = Color(0xFFE3DAD2), fontSize = 14.sp, shadow = GlassTextShadow), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                GlassCircleButton(
                    glass,
                    if (liked) MkIcons.HeartFilled else MkIcons.Heart,
                    stringResource(if (liked) R.string.favorite_remove else R.string.favorite_add),
                    size = 46.dp,
                    iconTint = if (liked) Pelage.Rose else Color.White,
                    enabled = now != null,
                ) {
                    current()?.let { item ->
                        val added = favorites.toggle(item)
                        toast(context.getString(if (added) R.string.favorite_added else R.string.favorite_removed))
                    }
                }
            }
        }
        val controls: @Composable () -> Unit = {
            if (live) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tag(stringResource(R.string.live_badge), background = Pelage.Roux)
                    Text(stringResource(R.string.radio_screen_off), style = TextStyle(fontFamily = Rounded, color = Color(0xFFEDE6DF), fontSize = 13.sp, shadow = GlassTextShadow))
                }
            } else if (now != null) {
                TrackProgress(now?.url, state.open)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ModeToggle(MkIcons.Shuffle, stringResource(if (shuffle) R.string.shuffle_off else R.string.shuffle_on), active = shuffle) {
                    PlayerHolder.setShuffle(!shuffle)
                }
                GlassCircleButton(glass, MkIcons.Previous, stringResource(R.string.radio_previous), size = 58.dp, enabled = canSkip || (now != null && !live)) { skip { PlayerHolder.previous(context) } }
                GlassCircleButton(
                    glass,
                    if (playing) MkIcons.Pause else MkIcons.Play,
                    stringResource(if (playing) R.string.pause else R.string.play),
                    size = 76.dp,
                    tint = Pelage.Roux.copy(alpha = 0.68f),
                    iconTint = Pelage.Encre,
                ) { if (now != null) PlayerHolder.togglePlay() } // pas la vidéo qui joue peut-être en ce moment
                GlassCircleButton(glass, MkIcons.Next, stringResource(R.string.radio_next), size = 58.dp, enabled = canSkip) { skip { PlayerHolder.next(context) } }
                ModeToggle(
                    if (repeat == Repeat.One) MkIcons.RepeatOne else MkIcons.Repeat,
                    stringResource(
                        when (repeat) {
                            Repeat.Off -> R.string.repeat_off
                            Repeat.All -> R.string.repeat_all
                            Repeat.One -> R.string.repeat_one
                        },
                    ),
                    active = repeat != Repeat.Off,
                ) { PlayerHolder.cycleRepeat() }
            }
            Spacer(Modifier.height(16.dp))
            LiquidGlass(glass, Modifier.fillMaxWidth().height(54.dp), cornerRadius = 27.dp, bezel = 18.dp, refraction = 18.dp) {
                Row(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                    ActionSlot(MkIcons.Queue, stringResource(R.string.queue)) {
                        if (PlayerHolder.queue.value.isEmpty()) toast(context.getString(R.string.queue_empty)) else state.queueOpen = true
                    }
                    ActionSlot(MkIcons.Lyrics, stringResource(R.string.lyrics), enabled = !live && now != null) { state.lyricsOpen = true }
                    ActionSlot(MkIcons.PlaylistAdd, stringResource(R.string.playlist_add), enabled = now != null) {
                        current()?.let { MusicSheet.open.value = MusicSheet.AddToPlaylist(it) }
                    }
                    if (!local) {
                        ActionSlot(MkIcons.Share, stringResource(R.string.share), enabled = now != null) { now?.let { share(context, it.title, it.url) } }
                        ActionSlot(MkIcons.Download, stringResource(R.string.download), enabled = now != null && !live) { now?.url?.let(onDownload) }
                    }
                }
            }
        }
        if (landscape) {
            // Paysage : la pochette à gauche, le reste à droite (qui défile : artiste, album)
            Row(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true), contentAlignment = Alignment.Center) { coverArt() }
                Column(Modifier.weight(1f).fillMaxHeight().nestedScroll(state.scrollConnection).verticalScroll(scroll)) {
                    // Peu de hauteur : le titre partage sa ligne avec la lune et la flèche (pas de « MIX »)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { titleRow() }
                        GlassCircleButton(
                            glass,
                            MkIcons.Moon,
                            stringResource(if (timer == SleepTimer.Off) R.string.sleep_timer else R.string.sleep_timer_active),
                            size = 44.dp,
                            tint = if (timer == SleepTimer.Off) Color(0x40100D0B) else Pelage.Roux.copy(alpha = 0.7f),
                            iconTint = if (timer == SleepTimer.Off) Color.White else Pelage.Encre,
                        ) { PlayerHolder.settingsPage.value = SettingsPage.Minuteur }
                        GlassCircleButton(glass, MkIcons.ChevronDown, stringResource(R.string.player_minimize), size = 44.dp) { state.collapse() }
                    }
                    controls()
                    MusicDetails(now, wanted = scroll.value > 0, onOpenArtist, onOpenAlbum)
                }
            }
        } else {
        val page = maxHeight - DETAILS_PEEK
        Column(Modifier.fillMaxSize().nestedScroll(state.scrollConnection).verticalScroll(scroll)) {
        Column(
            Modifier.fillMaxWidth().height(page).padding(start = 18.dp, end = 18.dp, top = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            topRow()
            // La pochette prend la place qui reste
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) { coverArt() }
            titleRow()
            controls()
        }
        // Les détails ne se chargent (recherche de l'album) qu'une fois qu'on fait défiler
        MusicDetails(now, wanted = scroll.value > 0, onOpenArtist, onOpenAlbum)
        }
        }
        }
    }
}

/** Ce qui dépasse sous les commandes : le titre de la première section, pour montrer qu'on peut faire défiler. */
private val DETAILS_PEEK = 44.dp

/** Sous le lecteur : l'artiste (vers sa page) et l'album du morceau (vers l'album). */
@Composable
private fun MusicDetails(now: NowPlaying?, wanted: Boolean, onOpenArtist: (ChannelItem) -> Unit, onOpenAlbum: (MusicCollection) -> Unit) {
    if (now == null || LocalMusic.isLocal(now.url)) {
        Spacer(Modifier.height(DETAILS_PEEK))
        return
    }
    val artist = cleanArtist(now.channel)
    // L'album : cherché une fois qu'on a fait défiler (null = pas trouvé ou pas encore cherché)
    var asked by remember(now.url) { mutableStateOf(false) }
    if (wanted) asked = true
    val album by produceState<MusicCollection?>(null, now.url, asked) {
        value = null
        if (asked && !now.live) value = runCatching { Music.albumOf(artist, now.description) }.getOrNull()
    }
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailsTitle(stringResource(R.string.music_about_artist))
        DetailsCard(now.channelAvatar, round = true, title = artist, subtitle = formatCount(now.channelSubscribers).ifEmpty { null }?.let { stringResource(R.string.subscribers_count, it) }) {
            onOpenArtist(ChannelItem(now.channelUrl.orEmpty(), artist, now.channelAvatar, now.channelUrl?.let(::channelIdFrom), now.channelSubscribers))
        }
        album?.let { found ->
            DetailsTitle(stringResource(R.string.music_about_album), Modifier.padding(top = 8.dp))
            DetailsCard(found.thumbnail, round = false, title = found.title, subtitle = found.artist.ifEmpty { null }) { onOpenAlbum(found) }
        }
    }
}

@Composable
private fun DetailsTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.height(DETAILS_PEEK - 10.dp).wrapContentHeight(Alignment.CenterVertically),
        style = TextStyle(fontFamily = Rounded, color = Pelage.Texte, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, shadow = GlassTextShadow),
    )
}

@Composable
private fun DetailsCard(image: String?, round: Boolean, title: String, subtitle: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(if (round) CircleShape else RoundedCornerShape(12.dp)).background(Pelage.RouxNuit),
        )
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, color = Pelage.Texte, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = Color(0xFFE3DAD2), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(MkIcons.ChevronRight, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
    }
}

/** La barre de progression du morceau, qu'on peut faire glisser, avec le temps écoulé et la durée. */
@Composable
private fun TrackProgress(key: String?, active: Boolean) {
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var scrub by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(key, active) {
        do { // caché : une seule lecture, pas de mise à jour continue
            val (p, d) = PlayerHolder.progress()
            position = p
            duration = d
            buffered = PlayerHolder.bufferedPosition()
            delay(250)
        } while (active)
    }
    val shown = scrub?.let { (it * duration).toLong() } ?: position
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        ProgressWithSegments(
            progress = if (duration > 0) position.toFloat() / duration else 0f,
            segments = emptyList(),
            buffered = if (duration > 0) buffered.toFloat() / duration else 0f,
            onSeek = { f -> if (duration > 0) PlayerHolder.seekTo((f * duration).toLong()) },
            onScrub = { scrub = it },
        )
        Row(Modifier.fillMaxWidth()) {
            Text(clock(shown), style = TextStyle(fontFamily = Rounded, color = Color(0xFFE3DAD2), fontSize = 12.sp, fontWeight = FontWeight.Bold, shadow = GlassTextShadow))
            Spacer(Modifier.weight(1f))
            Text(if (duration > 0) clock(duration) else "", style = TextStyle(fontFamily = Rounded, color = Color(0xFFE3DAD2), fontSize = 12.sp, fontWeight = FontWeight.Bold, shadow = GlassTextShadow))
        }
    }
}

/** « 3:07 » / « 1:02:45 ». */
private fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Aléatoire / répéter : une icône qui s'allume en roux (avec un point dessous) quand c'est actif. */
@Composable
private fun ModeToggle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label; stateDescription = if (active) "✓" else "" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (active) Pelage.RouxClair else Color.White.copy(alpha = 0.85f), modifier = Modifier.size(24.dp))
        if (active) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(5.dp).clip(CircleShape).background(Pelage.RouxClair))
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ActionSlot(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).fillMaxHeight().clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(23.dp))
    }
}

/** Partager une vidéo / radio avec le menu de partage d'Android. */
private fun share(context: android.content.Context, title: String, url: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, title)
        .putExtra(Intent.EXTRA_TEXT, "$title\n$url")
    runCatching { context.startActivity(Intent.createChooser(send, context.getString(R.string.share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * La file, en verre, qui monte du bas. Un tap lance l'élément ; la poignée ≡ le déplace ; la croix
 * le retire. En haut, « Mix auto » : la file se remplit toute seule de titres proches.
 */
@Composable
private fun QueueSheet(backdrop: Backdrop, visible: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val queue by PlayerHolder.queue.collectAsState()
    val current by PlayerHolder.queueIndex.collectAsState()
    val mix by PlayerHolder.autoMix.collectAsState()
    val list = rememberLazyListState()
    val reorder = remember { ReorderState(list, firstIndex = 0, count = { PlayerHolder.queue.value.size }) { from, to -> PlayerHolder.moveInQueue(from, to) } }
    BackHandler(enabled = visible, onBack = onClose)
    LaunchedEffect(visible) { if (visible && current > 0) list.scrollToItem((current - 1).coerceAtLeast(0)) }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
                    .clickable(interactionSource = null, indication = null, onClickLabel = stringResource(R.string.queue_close), onClick = onClose),
            )
        }
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
        ) {
            LiquidGlass(
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(10.dp).fillMaxHeight(0.7f),
                cornerRadius = 30.dp,
                bezel = 26.dp,
                refraction = 26.dp,
                tint = Color(0xC71A1613),
                frost = 28.dp,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.queue), Modifier.weight(1f).semantics { heading() }, color = Pelage.Texte, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        // Mix auto : une petite pastille qui s'allume
                        Row(
                            Modifier.clip(RoundedCornerShape(18.dp))
                                .background(if (mix) Pelage.Roux else Color.White.copy(alpha = 0.1f))
                                .clickable(role = Role.Switch, onClickLabel = stringResource(R.string.mix_auto)) {
                                    PlayerHolder.setAutoMix(!mix)
                                    if (!mix) Toast.makeText(context, context.getString(R.string.mix_auto_on), Toast.LENGTH_SHORT).show()
                                }
                                .semantics { stateDescription = if (mix) "✓" else "" }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(MkIcons.Mix, null, tint = if (mix) Pelage.Encre else Color.White, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.mix_auto), color = if (mix) Pelage.Encre else Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose), contentAlignment = Alignment.Center) {
                            Icon(MkIcons.Close, stringResource(R.string.queue_close), tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(vertical = 6.dp)) {
                        itemsIndexed(queue, key = { _, it -> it.url }) { index, station ->
                            val playing = index == current
                            val dragged = reorder.dragging == station.url
                            Row(
                                Modifier
                                    .then(
                                        if (dragged) Modifier.zIndex(1f).graphicsLayer { translationY = reorder.offset }.background(Color(0xFF2A2420))
                                        else Modifier.animateItem(),
                                    )
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button) {
                                        if (!playing) scope.launch {
                                            runCatching { PlayerHolder.playAt(context, index) }
                                                .onFailure { Toast.makeText(context, context.getString(R.string.radio_station_failed), Toast.LENGTH_SHORT).show() }
                                        }
                                    }
                                    .background(if (playing) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                                    .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Cover(station.thumbnail, 50.dp, 12.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        station.title,
                                        color = if (playing) Pelage.RouxClair else Pelage.Texte,
                                        fontSize = 14.sp,
                                        fontWeight = if (playing) FontWeight.ExtraBold else FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(station.channel, color = Pelage.Gris, fontSize = 12.sp, maxLines = 1)
                                }
                                if (playing) {
                                    Icon(MkIcons.Waves, stringResource(R.string.playing_now), tint = Pelage.RouxClair, modifier = Modifier.size(22.dp))
                                } else {
                                    Box(
                                        Modifier.size(36.dp).clip(CircleShape).clickable(role = Role.Button) { PlayerHolder.removeFromQueue(index) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(MkIcons.Close, stringResource(R.string.queue_remove), tint = Pelage.Gris, modifier = Modifier.size(18.dp))
                                    }
                                }
                                DragHandle(reorder, station.url)
                            }
                        }
                    }
                }
            }
        }
    }
}


/** Le morceau en cours, pour la musique : rien quand c'est une vidéo qui joue (elle a son mini-lecteur). */
@Composable
private fun musicNow(): NowPlaying? = PlayerHolder.nowPlaying.collectAsState().value?.takeIf { it.audioOnly }

/** Le morceau qu'une vidéo a mis de côté (montré dans la pilule tant que la vidéo est là). */
@Composable
private fun parkedTrack(): VideoItem? {
    val parked by PlayerHolder.parkedMusic.collectAsState()
    val queue by PlayerHolder.queue.collectAsState()
    return parked?.let { p -> queue.firstOrNull { sameTrack(it.url, p.url) } }
}
