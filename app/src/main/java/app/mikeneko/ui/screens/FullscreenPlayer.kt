package app.mikeneko.ui.screens

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import app.mikeneko.ui.components.ambientSupported
import kotlin.math.roundToInt
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.data.formatClock
import app.mikeneko.player.PlayerHolder
import app.mikeneko.player.SettingsPage
import app.mikeneko.ui.components.GlassCircleButton
import app.mikeneko.ui.components.Loading
import app.mikeneko.ui.components.PlayerSettingsSheet
import app.mikeneko.ui.components.ProgressWithSegments
import app.mikeneko.ui.components.RotationMorph
import app.mikeneko.ui.components.SeekPreview
import app.mikeneko.ui.components.Tag
import app.mikeneko.ui.components.TapToSeekLayer
import app.mikeneko.ui.components.VideoSurface
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.glass.LiquidGlass
import app.mikeneko.ui.glass.backdropSource
import app.mikeneko.ui.glass.rememberBackdrop
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.GlassTextShadow
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.Rounded
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Le lecteur en plein écran (paysage) : commandes en verre, « À suivre », réglages.

/**
 * Le plein écran, version classique : titre en haut, reculer / lecture / avancer au centre,
 * barre de progression et temps en bas. Un tap affiche ou masque les commandes ; elles se
 * masquent seules après quelques secondes de lecture (jamais en pause ni pendant un glissé).
 */
@Composable
fun FullscreenPlayer(onExit: () -> Unit) {
    val now by PlayerHolder.nowPlaying.collectAsState()
    val playing by PlayerHolder.isPlaying.collectAsState()
    val loading by PlayerHolder.loading.collectAsState()
    val buffering by PlayerHolder.buffering.collectAsState()
    val wantsToPlay by PlayerHolder.playRequested.collectAsState()
    val progress = rememberPlaybackProgress()
    val video = rememberBackdrop()
    // Arrivé en tournant le téléphone : les commandes restent cachées pendant que la vidéo pivote
    var visible by remember { mutableStateOf(!RotationMorph.running) }
    var scrubbing by remember { mutableStateOf<Float?>(null) } // position visée pendant un glissé
    var poke by remember { mutableIntStateOf(0) } // chaque interaction relance le minuteur

    val settings by PlayerHolder.settingsPage.collectAsState()
    val caption by PlayerHolder.caption.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(visible, playing, scrubbing, poke, settings) {
        if (visible && playing && scrubbing == null && settings == null) {
            delay(3_500)
            visible = false
        }
    }

    fun act(block: () -> Unit) {
        block()
        poke++
    }

    val info = now
    val live = info?.live == true
    val waiting = loading || buffering

    // « À suivre » : glisser vers le haut sur la vidéo sort le panneau des suggestions
    val suggestions = rememberSuggestionsState()
    val suggestionsScope = rememberCoroutineScope()
    val suggestionsShown by remember { derivedStateOf { suggestions.shown } }
    val related = info?.related.orEmpty()
    val density = LocalDensity.current
    val sheetPx = with(density) { SuggestionsHeight.toPx() }
    val fastPx = with(density) { 700.dp.toPx() }
    LaunchedEffect(suggestionsShown) { if (suggestionsShown) visible = false }
    BackHandler(enabled = suggestionsShown) { suggestionsScope.launch { suggestions.close() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .suggestionsDrag(suggestions, enabled = settings == null && related.isNotEmpty(), heightPx = sheetPx, fastPx = fastPx, scope = suggestionsScope),
    ) {
        // Sous-titres : remontés au-dessus de la barre de progression quand les commandes sont là
        val captionLift by animateFloatAsState(if (visible) 0.2f else 0f, tween(250), label = "sous-titres")
        // Arrivée en tournant le téléphone : la vidéo pivote depuis sa place du portrait (RotationMorph)
        Box(Modifier.fillMaxSize().graphicsLayer { with(RotationMorph) { fullscreenVideo() } }.backdropSource(video)) {
            VideoSurface(Modifier.fillMaxSize(), captionLift = captionLift)
        }
        // Mode ambiant (3.0.2) : la lueur de la vidéo dans les bandes noires, jamais sur l'image
        BarsGlow(video)

        // Toute la surface : un tap montre / cache les commandes, un double tap recule / avance de 10 s
        TapToSeekLayer(
            controlsShown = visible,
            canSeek = info != null && !live,
            onToggleControls = { act { visible = !visible } },
            onSeek = { poke++ },
        )

        AnimatedVisibility(waiting && !visible, Modifier.align(Alignment.Center), enter = ControlsFadeIn, exit = ControlsFadeOut) {
            Loading()
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.fillMaxSize(),
            enter = ControlsFadeIn,
            exit = ControlsFadeOut,
        ) {
            Box(Modifier.fillMaxSize()) {
                // Voiles sombres pour lire le texte sur n'importe quelle vidéo
                Box(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().height(120.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))),
                )
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))),
                )

                // Haut : titre et chaîne, sous-titres et réglages
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            info?.title.orEmpty(),
                            style = TextStyle(fontFamily = Rounded, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, shadow = GlassTextShadow),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            info?.channel.orEmpty(),
                            style = TextStyle(fontFamily = Rounded, color = Color(0xFFE3DAD2), fontSize = 13.sp, shadow = GlassTextShadow),
                            maxLines = 1,
                        )
                    }
                    if (!live) {
                        AutoplaySwitch { act { } }
                        Spacer(Modifier.width(12.dp))
                        CaptionsButton(video, caption != null) {
                            act { if (!PlayerHolder.toggleCaptions()) Toast.makeText(context, context.getString(R.string.no_captions), Toast.LENGTH_SHORT).show() }
                        }
                        Spacer(Modifier.width(10.dp))
                    }
                    GlassCircleButton(video, MkIcons.Gear, stringResource(R.string.video_settings), size = 44.dp) { act { PlayerHolder.settingsPage.value = SettingsPage.Menu } }
                }

                // Centre : vidéo précédente, lecture/pause, vidéo suivante
                // (reculer / avancer de 10 s se fait au double tap sur la vidéo)
                TransportControls(
                    video, wantsToPlay, waiting, hasNext = info?.related?.isNotEmpty() == true,
                    playSize = 76.dp, skipSize = 58.dp, spacing = 40.dp, modifier = Modifier.align(Alignment.Center),
                ) { poke++ }

                // Bas : barre de progression, temps, sortie du plein écran
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                        .padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                ) {
                    if (!live && info != null) {
                        ProgressWithSegments(
                            progress = progress.fraction,
                            segments = sponsorFractions(info, progress.duration),
                            buffered = progress.bufferedFraction,
                            preview = { f -> SeekPreview(info.previewFrames, (f * progress.duration).toLong()) },
                            onSeek = { f -> act { progress.seekTo(f) } },
                            onScrub = { scrubbing = it; poke++ },
                        )
                    }
                    // Le temps à gauche, la sortie à droite, « Up next » au milieu de l'écran (sous les
                    // commandes), et pas au milieu de la place qui reste entre les deux
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (live) {
                            Tag(stringResource(R.string.live_badge), Modifier.align(Alignment.CenterStart), background = Pelage.Roux)
                        } else {
                            Text(
                                "${formatClock(progress.shownPosition(scrubbing))} / ${formatClock(progress.duration)}",
                                Modifier.align(Alignment.CenterStart),
                                style = TextStyle(fontFamily = Rounded, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, shadow = GlassTextShadow),
                            )
                        }
                        if (related.isNotEmpty()) {
                            // Les marges de l'encoche ne sont pas les mêmes des deux côtés : on recentre sur l'écran
                            val sides = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                            val density = LocalDensity.current
                            val layoutDirection = LocalLayoutDirection.current
                            val shift = (sides.getRight(density, layoutDirection) - sides.getLeft(density, layoutDirection)) / 2
                            Box(Modifier.offset { IntOffset(shift, 0) }) {
                                UpNextHint(video) { suggestionsScope.launch { suggestions.open() } }
                            }
                        }
                        GlassCircleButton(video, MkIcons.ExitFullscreen, stringResource(R.string.exit_fullscreen), size = 46.dp, modifier = Modifier.align(Alignment.CenterEnd), onClick = onExit)
                    }
                }
            }
        }

        SuggestionsSheet(
            suggestions,
            video,
            related,
            onPick = { picked ->
                suggestionsScope.launch { suggestions.close() }
                suggestionsScope.launch {
                    runCatching { PlayerHolder.play(context, picked.url, audioOnly = false) }
                        .onFailure { Toast.makeText(context, context.getString(R.string.error_play_video, it.message.orEmpty()), Toast.LENGTH_SHORT).show() }
                }
            },
            onDismiss = { suggestionsScope.launch { suggestions.close() } },
        )

        PlayerSettingsSheet(video)
    }
}

/** Petite capsule « À suivre ⌃ » en bas du plein écran : ouvre le panneau (comme le glissé vers le haut). */
@Composable
private fun UpNextHint(backdrop: Backdrop, onClick: () -> Unit) {
    LiquidGlass(
        backdrop = backdrop,
        modifier = Modifier.height(36.dp),
        cornerRadius = 18.dp,
        bezel = 10.dp,
        refraction = 10.dp,
        tint = Color(0x40100D0B),
        onClick = onClick,
    ) {
        Row(Modifier.align(Alignment.Center).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(MkIcons.ChevronDown, null, tint = Color.White, modifier = Modifier.size(18.dp).rotate(180f))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.up_next),
                style = TextStyle(fontFamily = Rounded, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, shadow = GlassTextShadow),
            )
        }
    }
}

/**
 * Le mode ambiant du plein écran (3.0.2) : quand la vidéo n'a pas le format de l'écran, ses bandes
 * noires prennent une lueur de ses couleurs. La vidéo est étirée jusqu'aux bords, floutée en tout
 * petit puis agrandie (comme [AmbientGlow] de la page vidéo, pour rester légère : elle est refaite à
 * chaque image de la vidéo), et n'est dessinée qu'en dehors de l'image.
 */
@Composable
private fun BarsGlow(video: Backdrop) {
    val ambient by PlayerHolder.ambient.collectAsState()
    val aspect by PlayerHolder.videoAspect.collectAsState()
    if (!ambient || !ambientSupported || aspect <= 0f) return
    // Pendant la rotation vers le plein écran, la vidéo pivote : la lueur arrive ensuite
    val shown by animateFloatAsState(if (RotationMorph.running) 0f else 1f, tween(600), label = "lueur")
    val blurred = rememberGraphicsLayer()
    val faded = rememberGraphicsLayer()
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = shown }
            .drawBehind {
                val layer = video.layer
                if (layer.size.width == 0 || shown == 0f) return@drawBehind
                // La place de l'image (ajustée à l'écran, centrée)
                val screenRatio = size.width / size.height
                val videoW = if (aspect > screenRatio) size.width else size.height * aspect
                val videoH = if (aspect > screenRatio) size.width / aspect else size.height
                val left = (size.width - videoW) / 2f
                val top = (size.height - videoH) / 2f
                if (left < 4.dp.toPx() && top < 4.dp.toPx()) return@drawBehind // pas de bandes
                val small = IntSize(
                    (size.width / BARS_GLOW_SCALE).roundToInt().coerceAtLeast(1),
                    (size.height / BARS_GLOW_SCALE).roundToInt().coerceAtLeast(1),
                )
                val sx = small.width / size.width
                val sy = small.height / size.height
                // 1. L'image étirée jusqu'aux bords de l'écran (et un peu au-delà), en petit, floutée
                val radius = 36.dp.toPx() / BARS_GLOW_SCALE
                blurred.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                blurred.record(small) {
                    scale(sx, sy, pivot = Offset.Zero) {
                        scale(size.width / videoW * 1.1f, size.height / videoH * 1.1f, pivot = center) {
                            scale(size.width / layer.size.width, size.height / layer.size.height, pivot = Offset.Zero) { drawLayer(layer) }
                        }
                    }
                }
                // 2. Plus forte contre l'image, plus douce vers les bords de l'écran
                faded.compositingStrategy = CompositingStrategy.Offscreen
                faded.alpha = 0.8f
                faded.record(small) {
                    drawLayer(blurred)
                    val fade = if (left >= top) {
                        val edge = left / size.width
                        Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.35f), edge to Color.Black, 1f - edge to Color.Black, 1f to Color.Black.copy(alpha = 0.35f))
                    } else {
                        val edge = top / size.height
                        Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), edge to Color.Black, 1f - edge to Color.Black, 1f to Color.Black.copy(alpha = 0.35f))
                    }
                    drawRect(fade, blendMode = BlendMode.DstIn)
                }
                // 3. Agrandie, seulement dans les bandes : l'image elle-même reste intacte
                clipRect(left, top, left + videoW, top + videoH, clipOp = ClipOp.Difference) {
                    scale(1f / sx, 1f / sy, pivot = Offset.Zero) { drawLayer(faded) }
                }
            },
    )
}

/** De combien la lueur des bandes est réduite avant d'être floutée. */
private const val BARS_GLOW_SCALE = 8f

