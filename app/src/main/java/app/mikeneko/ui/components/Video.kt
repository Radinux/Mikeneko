package app.mikeneko.ui.components

import app.mikeneko.data.formatDuration
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import coil3.toBitmap
import coil3.request.SuccessResult
import coil3.request.ImageRequest
import coil3.SingletonImageLoader
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import org.schabi.newpipe.extractor.stream.Frameset
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import android.view.TextureView
import androidx.compose.ui.res.stringResource
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.CaptioningManager
import androidx.core.content.res.ResourcesCompat
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import app.mikeneko.ui.glass.LiquidGlass
import kotlin.math.roundToInt
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.mikeneko.R
import app.mikeneko.player.PlayerHolder
import app.mikeneko.ui.glass.backdropSource
import app.mikeneko.ui.glass.rememberBackdrop
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage
import kotlinx.coroutines.delay

/**
 * La vidéo du lecteur partagé (TextureView, pour que le verre puisse la réfracter).
 * [captionLift] : part du bas de l'image interdite aux sous-titres (0 à 1), pour les
 * remonter au-dessus des boutons posés sur la vidéo.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(modifier: Modifier = Modifier, zoom: Boolean = false, captionLift: Float = 0f) {
    val context = LocalContext.current
    // La vidéo passe d'un écran à l'autre (mini-lecteur → grand lecteur, plein écran…) : le temps
    // qu'elle se rebranche, on montre sa dernière image au lieu d'un carré noir.
    val placeholder = remember { PlayerHolder.snapshot()?.asImageBitmap() }
    val framesAtStart = remember { PlayerHolder.firstFrames.value }
    val seeksAtStart = remember { PlayerHolder.seeks.value }
    val frames by PlayerHolder.firstFrames.collectAsState()
    val seeks by PlayerHolder.seeks.collectAsState()
    val wantsToPlay by PlayerHolder.playRequested.collectAsState()
    var covering by remember { mutableStateOf(placeholder != null) }
    LaunchedEffect(frames, wantsToPlay, seeks) {
        // En pause, la première image sur ce nouvel écran est la suivante : on garde la photo exacte
        // tant que la lecture ne repart pas (ou qu'on ne saute pas ailleurs dans la vidéo).
        if (frames > framesAtStart && (wantsToPlay || seeks != seeksAtStart)) {
            delay(120) // la première image met encore quelques images d'écran à apparaître
            covering = false
        }
    }
    LaunchedEffect(Unit) {
        delay(1_000) // au cas où (vidéo en pause : pas de nouvelle image tant qu'on ne relance pas)
        if (PlayerHolder.isPlaying.value) covering = false
    }

    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.player_texture, null) as PlayerView).apply {
                    resizeMode = if (zoom) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                    subtitleView?.let { captions ->
                        styleCaptions(ctx, captions)
                        // La hauteur de référence est celle de l'image (le parent), pas celle des sous-titres
                        (captions.parent as? View)?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> liftCaptions(captions) }
                    }
                }
            },
            update = { view ->
                view.player = PlayerHolder.get(context)
                PlayerHolder.attachTexture(view.videoSurfaceView as? TextureView)
                view.subtitleView?.let { captions ->
                    captions.tag = captionLift
                    liftCaptions(captions)
                }
            },
            onRelease = { view ->
                PlayerHolder.detachTexture(view.videoSurfaceView as? TextureView)
                view.player = null
            },
        )
        if (covering && placeholder != null) {
            Image(
                placeholder,
                contentDescription = null,
                contentScale = if (zoom) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Les sous-titres de YouTube (TTML) arrivent souvent avec leur propre position, que le réglage
 * « bottomPaddingFraction » ignore. Une marge en bas raccourcit la zone où TOUS les sous-titres
 * se placent : ils remontent donc tous, positionnés ou non. (Un padding les fait disparaître.)
 */
@OptIn(UnstableApi::class)
private fun liftCaptions(view: SubtitleView) {
    val parentHeight = (view.parent as? View)?.height ?: return
    val px = ((view.tag as? Float ?: 0f) * parentHeight).toInt()
    val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
    if (params.bottomMargin != px) {
        params.bottomMargin = px
        view.post { view.layoutParams = params } // pas de nouvelle mise en page pendant une mise en page
    }
}

/**
 * Sous-titres à la Mikeneko (police arrondie, fond sombre adouci), sauf si la personne a
 * réglé ses sous-titres dans les paramètres d'accessibilité d'Android : on respecte alors les siens.
 */
@OptIn(UnstableApi::class)
private fun styleCaptions(context: Context, view: SubtitleView) {
    val captioning = context.getSystemService(CaptioningManager::class.java)
    if (captioning?.isEnabled == true) {
        view.setUserDefaultStyle()
        view.setUserDefaultTextSize()
        return
    }
    view.setStyle(
        CaptionStyleCompat(
            android.graphics.Color.WHITE,
            0xB3141210.toInt(), // fond « nuit » à 70 %
            android.graphics.Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_NONE,
            android.graphics.Color.TRANSPARENT,
            ResourcesCompat.getFont(context, R.font.mplus_rounded_bold),
        ),
    )
}

/**
 * Le mini-lecteur PiP dans l'app : la vidéo joue dans une petite fenêtre 16:9
 * avec des oreilles de chat. Un tap montre les commandes en liquid glass.
 */
@Composable
fun MiniPip(modifier: Modifier = Modifier, onExpand: () -> Unit, onClose: () -> Unit) {
    var controls by remember { mutableStateOf(false) }
    val playing by PlayerHolder.playRequested.collectAsState() // pas isPlaying : faux pendant un chargement
    var progress by remember { mutableFloatStateOf(0f) }
    val backdrop = rememberBackdrop()
    val hideLabel = stringResource(R.string.pip_hide_controls)
    val showLabel = stringResource(R.string.pip_show_controls)
    val scope = rememberCoroutineScope()
    // Photo de la vidéo posée par-dessus juste avant d'agrandir / fermer : sinon la surface
    // vidéo du mini-lecteur, qui perd sa source, ressort en partie noire le temps du changement.
    var frozen by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var leaving by remember { mutableStateOf(false) }
    // Fermeture : 0 → 1, la bulle se gonfle puis éclate en gouttelettes
    val popping = remember { Animatable(0f) }

    /** Fermer en éclatant comme une bulle (le son se coupe tout de suite). */
    fun pop() {
        if (leaving) return
        leaving = true
        controls = false
        frozen = PlayerHolder.snapshot()?.asImageBitmap()
        PlayerHolder.pause()
        scope.launch {
            popping.animateTo(1f, tween(POP_MS, easing = LinearEasing))
            onClose()
        }
    }

    fun leave(action: () -> Unit) {
        if (leaving) return
        leaving = true
        frozen = PlayerHolder.snapshot()?.asImageBitmap()
        scope.launch {
            // Deux images : la photo est vraiment à l'écran avant que la vidéo change de surface
            withFrameNanos { }
            withFrameNanos { }
            action()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val (position, duration) = PlayerHolder.progress()
            progress = if (duration > 0) position.toFloat() / duration else 0f
            delay(500)
        }
    }

    DisposableEffect(Unit) { onDispose { VideoMorph.miniBounds = null } }

    // Pendant le morph vers le mini-lecteur, c'est la photo qui arrive : on le cache dessous
    Box(modifier.size(MiniPipSize).graphicsLayer { alpha = if (VideoMorph.hidesMini()) 0f else 1f }) {
    // Le corps du mini-lecteur : il se gonfle puis disparaît quand la bulle éclate
    Box(
        Modifier.matchParentSize().graphicsLayer {
            val p = popping.value
            val inflate = (p / POP_INFLATE).coerceIn(0f, 1f)
            val burst = ((p - POP_INFLATE) / (POP_BURST_END - POP_INFLATE)).coerceIn(0f, 1f)
            val grown = 1f + 0.16f * inflate * inflate
            scaleX = grown * (1f - burst)
            scaleY = (grown - 0.05f * inflate) * (1f - burst)
            alpha = 1f - burst
        },
    ) {
        // Les oreilles poussent en « boing » quand le mini-lecteur atterrit (après le morph)
        val hidden = VideoMorph.hidesMini()
        val ears = remember { Animatable(if (hidden) 0f else 1f) }
        LaunchedEffect(hidden) {
            if (!hidden && ears.value < 1f) ears.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 240f))
        }
        CatEars(
            Modifier.fillMaxWidth().height(24.dp).graphicsLayer {
                scaleY = ears.value
                scaleX = 0.6f + 0.4f * ears.value
                transformOrigin = TransformOrigin(0.5f, 1f)
            },
            // Écartées vers les coins, penchées vers l'extérieur et bien arrondies
            earWidth = 30.dp,
            inset = 5.dp,
            tip = -0.06f,
            round = 0.3f,
        )
        val shape = RoundedCornerShape(18.dp)
        Box(
            Modifier
                .padding(start = 2.dp, top = 14.dp)
                .size(176.dp, 99.dp)
                .onGloballyPositioned { VideoMorph.miniBounds = it.boundsInRoot() }
                .shadow(16.dp, shape)
                .border(2.5.dp, Pelage.Creme, shape)
                .clip(shape)
                .background(Pelage.Nuit),
        ) {
            Box(Modifier.fillMaxSize().backdropSource(backdrop)) {
                VideoSurface(Modifier.fillMaxSize(), zoom = true)
                frozen?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = if (controls) hideLabel else showLabel }
                    .clickable { controls = !controls },
            )
            if (controls) {
                Box(Modifier.fillMaxSize().background(Color(0x61141210)))
                GlassCircleButton(backdrop, MkIcons.Expand, stringResource(R.string.expand_video), Modifier.align(Alignment.TopStart).padding(6.dp), size = 44.dp) { leave(onExpand) }
                GlassCircleButton(backdrop, if (playing) MkIcons.Pause else MkIcons.Play, stringResource(if (playing) R.string.pause else R.string.play), Modifier.align(Alignment.Center), size = 44.dp) {
                    PlayerHolder.togglePlay()
                }
                GlassCircleButton(backdrop, MkIcons.Close, stringResource(R.string.close_mini_player), Modifier.align(Alignment.TopEnd).padding(6.dp), size = 44.dp) { pop() }
            }
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.3f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(Pelage.Creme))
            }
        }
    }
        // Les gouttelettes de l'éclatement (dessinées autour, hors du cadre)
        Canvas(Modifier.matchParentSize()) {
            val p = popping.value
            if (p <= POP_INFLATE) return@Canvas
            val d = ((p - POP_INFLATE) / (1f - POP_INFLATE)).coerceIn(0f, 1f)
            val travel = 1f - (1f - d) * (1f - d) // part vite, ralentit
            val center = Offset(size.width / 2f, size.height * 0.56f)
            val reach = size.width * 0.75f
            for (i in 0 until POP_DROPS) {
                val angle = (i / POP_DROPS.toFloat()) * 2f * PI.toFloat() + 0.3f
                val far = reach * (0.7f + 0.3f * ((i * 37) % 10) / 10f) // pas toutes à la même distance
                val dropCenter = center + Offset(cos(angle), sin(angle) * 0.75f) * (far * travel)
                val radius = 9.dp.toPx() * (1f - d) * (if (i % 2 == 0) 1f else 0.7f)
                if (radius > 0f) {
                    drawCircle(if (i % 3 == 0) Pelage.Roux else Pelage.Creme, radius, dropCenter, alpha = 1f - d * d)
                }
            }
        }
    }
}

private const val POP_MS = 520
/** Part de l'animation où la bulle se gonfle, puis où son corps disparaît. */
private const val POP_INFLATE = 0.32f
private const val POP_BURST_END = 0.55f
private const val POP_DROPS = 9

/**
 * Barre de progression, avec les segments sponsor marqués en doré (fractions 0..1).
 *
 * Avec [onSeek] : un tap ou un glissé déplace la lecture, et le curseur devient une pilule
 * en liquid glass qui grossit la barre en dessous (et s'agrandit pendant qu'on la tient).
 * [onScrub] : la position visée pendant qu'on glisse (0..1), puis null quand on lâche
 * (pour afficher le temps visé et ne pas masquer les commandes pendant le geste).
 */
@Composable
fun ProgressWithSegments(
    progress: Float,
    segments: List<Pair<Float, Float>>,
    modifier: Modifier = Modifier,
    color: Color = Pelage.Roux,
    onSeek: ((Float) -> Unit)? = null,
    onScrub: ((Float?) -> Unit)? = null,
    /** Passage en cours de marquage (fractions 0..1), dessiné en blanc. */
    draft: Pair<Float, Float>? = null,
    /** Ce qui est déjà chargé (0..1), en blanc transparent sous la progression. */
    buffered: Float = 0f,
    /** L'aperçu montré au-dessus du curseur pendant qu'on le fait glisser (la fraction visée). */
    preview: (@Composable (Float) -> Unit)? = null,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val seek by rememberUpdatedState(onSeek)
    val scrub by rememberUpdatedState(onScrub)
    val shown = (dragging ?: progress).coerceIn(0f, 1f)
    val seekLabel = stringResource(R.string.seek_to)

    if (onSeek == null) {
        Canvas(modifier.fillMaxWidth().height(6.dp)) { drawTrack(shown, segments, color, draft, buffered) }
        return
    }

    val track = rememberBackdrop()
    val held = dragging != null
    val thumbWidth by animateDpAsState(if (held) 48.dp else 34.dp, spring(dampingRatio = 0.6f), label = "pilule")
    val thumbHeight by animateDpAsState(if (held) 28.dp else 20.dp, spring(dampingRatio = 0.6f), label = "pilule")

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(Unit) {
                detectTapGestures { seek?.invoke((it.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = (it.x / size.width).coerceIn(0f, 1f)
                        scrub?.invoke(dragging)
                    },
                    onDragEnd = {
                        dragging?.let { f -> seek?.invoke(f) }
                        dragging = null
                        scrub?.invoke(null)
                    },
                    onDragCancel = {
                        dragging = null
                        scrub?.invoke(null)
                    },
                ) { change, _ ->
                    dragging = (change.position.x / size.width).coerceIn(0f, 1f)
                    scrub?.invoke(dragging)
                }
            }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                setProgress(seekLabel) { target -> seek?.invoke(target.coerceIn(0f, 1f)); true }
            },
    ) {
        val widthPx = constraints.maxWidth.toFloat()

        // La barre elle-même : c'est elle que la pilule réfracte (la pilule n'est pas dedans)
        Box(Modifier.matchParentSize().backdropSource(track), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().height(6.dp)) { drawTrack(shown, segments, color, draft, buffered) }
        }

        // Pendant le glissé : la vignette de la vidéo à cet endroit, au-dessus du curseur (sans changer la taille de la barre)
        val target = dragging
        if (preview != null && target != null) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .layout { measurable, _ ->
                        val placeable = measurable.measure(Constraints())
                        val gap = 6.dp.roundToPx()
                        val x = (target * widthPx - placeable.width / 2f).coerceIn(0f, (widthPx - placeable.width).coerceAtLeast(0f)).roundToInt()
                        layout(0, 0) { placeable.place(x, -placeable.height - gap) }
                    },
            ) { preview(target) }
        }

        LiquidGlass(
            backdrop = track,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset {
                    val w = thumbWidth.toPx()
                    IntOffset((shown * widthPx - w / 2f).coerceIn(0f, (widthPx - w).coerceAtLeast(0f)).roundToInt(), 0)
                }
                .size(thumbWidth, thumbHeight),
            cornerRadius = thumbHeight / 2,
            // Bord fin et loupe au centre : la barre paraît grossie sous la pilule, sans se dédoubler
            bezel = thumbHeight * 0.5f,
            refraction = thumbHeight * 0.08f,
            magnification = if (held) 1.9f else 1.5f,
            tint = Color.White.copy(alpha = 0.18f),
            shadowElevation = 4.dp,
        )
    }
}

/**
 * La vignette d'aperçu : l'image de la vidéo à [positionMs], découpée dans les planches de YouTube
 * (une planche = des dizaines d'images ; chacune n'est téléchargée qu'une fois), et le temps dessous.
 */
@Composable
fun SeekPreview(frames: Frameset?, positionMs: Long) {
    val context = LocalContext.current
    val bounds = frames?.let { runCatching { it.getFrameBoundsAt(positionMs) }.getOrNull() }
    val url = bounds?.let { frames.urls.getOrNull(it[0]) }
    val pages = remember(frames) { mutableStateMapOf<String, ImageBitmap>() }
    // La dernière image affichée reste pendant qu'une nouvelle planche arrive
    var shown by remember(frames) { mutableStateOf<Pair<ImageBitmap, IntArray>?>(null) }
    LaunchedEffect(url) {
        if (url == null || url in pages) return@LaunchedEffect
        val result = runCatching { SingletonImageLoader.get(context).execute(ImageRequest.Builder(context).data(url).build()) }.getOrNull()
        (result as? SuccessResult)?.image?.toBitmap()?.asImageBitmap()?.let { pages[url] = it }
    }
    val page = url?.let { pages[it] }
    if (page != null && bounds != null) shown = page to bounds
    val shape = RoundedCornerShape(12.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (frames != null) {
            Box(Modifier.size(160.dp, 90.dp).clip(shape).background(Color.Black).border(2.dp, Pelage.Creme, shape)) {
                shown?.let { (bitmap, b) ->
                    Canvas(Modifier.fillMaxSize()) {
                        val left = b[1].coerceIn(0, bitmap.width - 1)
                        val top = b[2].coerceIn(0, bitmap.height - 1)
                        val w = (b[3] - b[1]).coerceIn(1, bitmap.width - left)
                        val h = (b[4] - b[2]).coerceIn(1, bitmap.height - top)
                        drawImage(bitmap, srcOffset = IntOffset(left, top), srcSize = IntSize(w, h), dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
                    }
                }
            }
        }
        Text(
            formatDuration(positionMs / 1000).ifEmpty { "0:00" },
            Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 2.dp),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

private fun DrawScope.drawTrack(progress: Float, segments: List<Pair<Float, Float>>, color: Color, draft: Pair<Float, Float>? = null, buffered: Float = 0f) {
    val radius = CornerRadius(size.height / 2)
    drawRoundRect(Color.White.copy(alpha = 0.16f), cornerRadius = radius)
    // Déjà chargé : blanc transparent, devant le fond et derrière la progression
    if (buffered > 0f) drawRoundRect(Color.White.copy(alpha = 0.32f), size = Size(size.width * buffered.coerceIn(0f, 1f), size.height), cornerRadius = radius)
    drawRoundRect(color, size = Size(size.width * progress, size.height), cornerRadius = radius)
    segments.forEach { (start, end) ->
        drawRect(Pelage.Dore, topLeft = Offset(size.width * start, 0f), size = Size(size.width * (end - start), size.height))
    }
    draft?.let { (start, end) ->
        drawRect(Color.White.copy(alpha = 0.85f), topLeft = Offset(size.width * start, 0f), size = Size(size.width * (end - start).coerceAtLeast(0.004f), size.height))
    }
}
