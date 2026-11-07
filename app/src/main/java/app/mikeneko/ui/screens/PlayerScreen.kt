package app.mikeneko.ui.screens

import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.mikeneko.R
import app.mikeneko.data.ChannelItem
import app.mikeneko.data.Comments
import app.mikeneko.data.SponsorBlock
import app.mikeneko.data.Subscription
import app.mikeneko.data.SubscriptionStore
import app.mikeneko.data.VideoItem
import app.mikeneko.data.VideoLibrary
import app.mikeneko.data.channelIdFrom
import app.mikeneko.data.formatAgo
import app.mikeneko.data.formatClock
import app.mikeneko.data.formatCount
import app.mikeneko.data.formatViews
import app.mikeneko.data.sameTrack
import app.mikeneko.player.NowPlaying
import app.mikeneko.player.PlayerHolder
import app.mikeneko.player.SettingsPage
import app.mikeneko.settings.AppLocale
import app.mikeneko.ui.components.Capsule
import app.mikeneko.ui.components.CommentsPreview
import app.mikeneko.ui.components.CompactVideoRow
import app.mikeneko.ui.components.GlassCircleButton
import app.mikeneko.ui.components.Loading
import app.mikeneko.ui.components.PLAYLIST_DRAWER_BAR
import app.mikeneko.ui.components.ProgressWithSegments
import app.mikeneko.ui.components.RotationMorph
import app.mikeneko.ui.components.SeekPreview
import app.mikeneko.ui.components.SegmentDraft
import app.mikeneko.ui.components.SegmentMarker
import app.mikeneko.ui.components.TapToSeekLayer
import app.mikeneko.ui.components.VideoMenu
import app.mikeneko.ui.components.VideoMorph
import app.mikeneko.ui.components.VideoSurface
import app.mikeneko.ui.components.ambientSupported
import app.mikeneko.ui.components.linkedText
import app.mikeneko.ui.components.shareLink
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.glass.backdropSource
import app.mikeneko.ui.glass.rememberBackdrop
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.GlassTextShadow
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.Rounded
import app.mikeneko.ui.theme.clay
import app.mikeneko.ui.theme.jellyClickable
import app.mikeneko.ui.theme.statusBarSpace
import app.mikeneko.ui.theme.statusBarSpacePadding
import coil3.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Le lecteur vidéo : boutons en verre posés sur la vidéo, SponsorBlock, dislikes, chapitres.

/** Sous le titre : l'avatar et le nom de la chaîne (vers sa page), et « S'abonner » juste à droite. */
@Composable
private fun VideoChannelRow(info: NowPlaying, onOpenChannel: (ChannelItem) -> Unit) {
    val context = LocalContext.current
    val store = remember { SubscriptionStore.get(context) }
    val subscriptions by store.items.collectAsState()
    val id = info.channelUrl?.let(::channelIdFrom)
    val channel = info.channelUrl?.let { ChannelItem(it, info.channel, info.channelAvatar, id, info.channelSubscribers) }
    val subscribed = id != null && subscriptions.any { it.channelId == id }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(26.dp))
                .then(if (channel != null) Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.channel_open)) { onOpenChannel(channel) } else Modifier)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                info.channelAvatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(CircleShape).background(Pelage.Coussin).border(2.dp, Pelage.Creme, CircleShape),
            )
            Column(Modifier.padding(start = 12.dp, end = 8.dp)) {
                Text(info.channel, color = Pelage.Texte, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                formatCount(info.channelSubscribers).ifEmpty { null }?.let {
                    Text(stringResource(R.string.subscribers_count, it), color = Color(0xFFCFC6BD), fontSize = 12.sp, maxLines = 1)
                }
            }
        }
        if (id != null) {
            Capsule(
                stringResource(if (subscribed) R.string.subscribed else R.string.subscribe),
                selected = subscribed,
                icon = if (subscribed) MkIcons.Check else MkIcons.Plus,
            ) {
                if (subscribed) {
                    store.remove(id)
                } else {
                    store.addAll(listOf(Subscription(info.channel, "https://www.youtube.com/channel/$id", id, info.channelAvatar)))
                }
            }
        }
    }
}

@Composable
fun PlayerContent(onMinimize: () -> Unit, onDownload: (String) -> Unit, onRadio: () -> Unit, onFullscreen: () -> Unit, onOpen: (VideoItem) -> Unit, onOpenChannel: (ChannelItem) -> Unit) {
    val now by PlayerHolder.nowPlaying.collectAsState()
    val playing by PlayerHolder.isPlaying.collectAsState()
    val loading by PlayerHolder.loading.collectAsState()
    val buffering by PlayerHolder.buffering.collectAsState()
    val autoSkip by PlayerHolder.autoSkip.collectAsState()
    val progress = rememberPlaybackProgress()
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    // « Marquer un passage » pour SponsorBlock (remis à zéro à chaque vidéo)
    var marking by remember { mutableStateOf(false) }
    val draft = remember { SegmentDraft() }
    LaunchedEffect(now?.videoId) {
        marking = false
        draft.reset()
    }
    val video = rememberBackdrop()
    val scroll = rememberScrollState()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Une autre vidéo (une suggestion touchée en bas de la page) : on repart du haut
    LaunchedEffect(now?.videoId) { scroll.scrollTo(0) }
    val ambient by PlayerHolder.ambient.collectAsState()
    val caption by PlayerHolder.caption.collectAsState()
    val settings by PlayerHolder.settingsPage.collectAsState()
    val context = LocalContext.current

    // Commandes posées sur la vidéo : elles se masquent après 3 s de lecture (jamais en pause,
    // pendant un chargement ou menu ouvert) ; un tap sur l'image les fait revenir.
    var controls by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }
    LaunchedEffect(controls, playing, loading, buffering, settings, poke, scrubbing, marking) {
        if (controls && playing && !loading && !buffering && settings == null && scrubbing == null && !marking) {
            delay(3_000)
            controls = false
        }
    }
    // Une vraie pause (ou la fin) montre les commandes, pas le chargement qui suit un saut
    val wantsToPlay by PlayerHolder.playRequested.collectAsState()
    LaunchedEffect(wantsToPlay) { if (!wantsToPlay) controls = true }

    // Glisser vers le bas depuis « Lecture en cours » : le lecteur suit le doigt, puis part en bulle
    val pull = remember { Animatable(0f) }
    val pullScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val minimizeDistance = with(density) { 110.dp.toPx() }
    val minimizeSpeed = with(density) { 900.dp.toPx() } // par seconde : un petit coup rapide suffit
    val minimize by rememberUpdatedState(onMinimize)
    val portraitPage by rememberUpdatedState(!landscape)
    // Seulement un glissé vers le bas (2.9.0) : vers le haut, la page défile comme avant. En portrait
    // il marche aussi sur la vidéo, tant que la page est tout en haut (sinon, on la fait redescendre).
    val pullToMinimize = Modifier.pointerInput(Unit) {
        val tracker = VelocityTracker()
        fun release(cancelled: Boolean) {
            val speed = tracker.calculateVelocity().y
            tracker.resetTracking()
            if (!cancelled && (pull.value > minimizeDistance || speed > minimizeSpeed)) minimize()
            pullScope.launch { pull.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
        }
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (portraitPage && scroll.value > 0) return@awaitEachGesture
            val start = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                if (over > 0) change.consume() // vers le bas seulement ; vers le haut, la page prend le geste
            } ?: return@awaitEachGesture
            tracker.resetTracking()
            var offset = 0f
            val finished = verticalDrag(start.id) { change ->
                offset = (offset + change.positionChange().y).coerceAtLeast(0f)
                change.consume()
                pullScope.launch { pull.snapTo(offset) }
                tracker.addPosition(change.uptimeMillis, Offset(0f, offset))
            }
            release(cancelled = !finished)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = pull.value
                val shrink = 1f - 0.08f * (pull.value / minimizeDistance).coerceIn(0f, 1f)
                scaleX = shrink
                scaleY = shrink
            },
    ) {
        // Mode ambiant : la vidéo floutée et agrandie déborde derrière le lecteur
        if (ambient && ambientSupported && now?.audioOnly == false) {
            AmbientGlow(video, Modifier.offset { IntOffset(0, if (landscape) 0 else -scroll.value) })
        }

        // En paysage (2.0.0) : la vidéo et ses commandes à gauche, tout le reste à droite, qui défile.
        // Sortir du plein écran en tenant le téléphone en paysage mène ici (plus de retour forcé en portrait).
        val header: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth().then(pullToMinimize).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onMinimize), contentAlignment = Alignment.Center) {
                    Icon(MkIcons.ChevronDown, stringResource(R.string.player_minimize), tint = Pelage.Texte, modifier = Modifier.size(26.dp))
                }
                Text(stringResource(R.string.now_playing), Modifier.weight(1f), color = Color(0xFFCFC6BD), fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Box(Modifier.size(44.dp))
            }
        }
        val videoArea: @Composable (Modifier, Shape) -> Unit = { area, shape ->
            DisposableEffect(Unit) { onDispose { VideoMorph.playerBounds = null } }
            val screen = LocalWindowInfo.current.containerSize
            Box(
                area
                    .aspectRatio(16f / 9f)
                    .onGloballyPositioned {
                        VideoMorph.playerBounds = it.boundsInRoot()
                        // La place de la vidéo de la page, d'où part la rotation (portrait) ou le zoom (paysage)
                        if (landscape) RotationMorph.landscapeVideo = it.boundsInRoot() else RotationMorph.portraitVideo = it.boundsInRoot()
                    }
                    // Retour du plein écran en tournant le téléphone : la vidéo pivote jusqu'ici par-dessus
                    // la page, qui apparaît derrière un voile noir en fondu (RotationMorph)
                    .zIndex(if (RotationMorph.running) 1f else 0f)
                    .drawBehind {
                        val scrim = RotationMorph.scrim
                        val placed = RotationMorph.pageVideo(landscape)
                        if (scrim > 0f && placed != null) {
                            drawRect(Color.Black.copy(alpha = scrim), topLeft = -placed.topLeft, size = Size(screen.width.toFloat(), screen.height.toFloat()))
                        }
                    }
                    // Pendant le morph depuis le mini-lecteur, c'est la photo qui arrive à cette place
                    .graphicsLayer {
                        alpha = if (VideoMorph.hidesPlayer()) 0f else 1f
                        with(RotationMorph) {
                            portraitVideo(screen.height.toFloat())
                            landscapeVideo(screen.width.toFloat(), screen.height.toFloat())
                        }
                    }
                    // Coins arrondis de la page paysage, après la transformation (sinon le zoom serait découpé)
                    .clip(shape)
                    .background(Pelage.Nuit),
            ) {
                // Sous-titres remontés au-dessus des boutons quand ils sont affichés
                val captionLift by animateFloatAsState(if (controls) 0.34f else 0f, tween(250), label = "sous-titres")
                Box(Modifier.fillMaxSize().backdropSource(video)) { VideoSurface(Modifier.fillMaxSize(), captionLift = captionLift) }

                // Toute l'image : un tap montre / cache les commandes, un double tap recule / avance de 10 s
                TapToSeekLayer(
                    controlsShown = controls,
                    canSeek = now?.live == false,
                    onToggleControls = { controls = !controls; poke++ },
                    onSeek = { poke++ },
                )

                // Commandes cachées : une fine ligne rousse au ras du bas de la vidéo montre où on en est
            if (now?.live == false) {
                val line by animateFloatAsState(if (controls) 0f else 1f, tween(300), label = "ligne")
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .graphicsLayer { alpha = line }
                        .background(Color.White.copy(alpha = 0.25f)),
                ) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress.fraction.coerceIn(0f, 1f)).background(Pelage.Roux))
                }
            }

            // Commandes cachées : le chargement se voit quand même au centre
                androidx.compose.animation.AnimatedVisibility((loading || buffering) && !controls, Modifier.align(Alignment.Center), enter = ControlsFadeIn, exit = ControlsFadeOut) {
                    Loading()
                }

                androidx.compose.animation.AnimatedVisibility( // la version « libre », pas celle de la Column
                    visible = controls,
                    modifier = Modifier.fillMaxSize(),
                    enter = ControlsFadeIn,
                    exit = ControlsFadeOut,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        // Les boutons du centre restent au milieu de la vidéo, et remontent seulement de ce
                        // qu'il faut pour ne pas toucher la rangée du bas : en portrait (vidéo de ~200 dp de
                        // haut), « suivant » collait le bouton des réglages
                        val density = LocalDensity.current
                        var videoHeight by remember { mutableStateOf(0.dp) }
                        var bottomBar by remember { mutableStateOf(0.dp) }
                        val playSize = 64.dp
                        val lift = (videoHeight / 2 + playSize / 2 + 6.dp - (videoHeight - bottomBar)).coerceAtLeast(0.dp)
                        Box(
                            Modifier.fillMaxSize().onSizeChanged { videoHeight = with(density) { it.height.toDp() } }.offset(y = -lift),
                            contentAlignment = Alignment.Center,
                        ) {
                            TransportControls(
                                video, wantsToPlay, waiting = loading || buffering, hasNext = now?.related?.isNotEmpty() == true,
                                playSize = playSize, skipSize = 46.dp, spacing = 26.dp,
                            ) { poke++ }
                        }
                        AutoplaySwitch(Modifier.align(Alignment.TopEnd).padding(12.dp)) { poke++ }
                        // En paysage, pas de ligne « Now playing » au-dessus : la flèche pour réduire est ici
                        if (landscape) {
                            GlassCircleButton(video, MkIcons.ChevronDown, stringResource(R.string.player_minimize), Modifier.align(Alignment.TopStart).padding(12.dp), size = 44.dp, onClick = onMinimize)
                        }
                        // En bas de la vidéo (2.0.1, comme YouTube) : le temps à gauche, les boutons à droite,
                        // et dessous la barre de progression (sponsors, aperçu au glissé)
                        val shown = now
                        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { bottomBar = with(density) { it.height.toDp() } }.padding(start = 14.dp, end = 10.dp, bottom = 2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (shown != null && !shown.live) {
                                    Text(
                                        "${formatClock(progress.shownPosition(scrubbing))} / ${formatClock(progress.duration)}",
                                        style = TextStyle(fontFamily = Rounded, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, shadow = GlassTextShadow),
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CaptionsButton(video, caption != null) {
                                        if (!PlayerHolder.toggleCaptions()) Toast.makeText(context, context.getString(R.string.no_captions), Toast.LENGTH_SHORT).show()
                                        poke++
                                    }
                                    GlassCircleButton(video, MkIcons.Gear, stringResource(R.string.video_settings), size = 44.dp) { PlayerHolder.settingsPage.value = SettingsPage.Menu }
                                    GlassCircleButton(video, MkIcons.Fullscreen, stringResource(R.string.fullscreen), size = 44.dp, onClick = onFullscreen)
                                }
                            }
                            if (shown != null && !shown.live) {
                                ProgressWithSegments(
                                    progress.fraction,
                                    sponsorFractions(shown, progress.duration),
                                    buffered = progress.bufferedFraction,
                                    preview = { f -> SeekPreview(shown.previewFrames, (f * progress.duration).toLong()) },
                                    onSeek = { f -> progress.seekTo(f); poke++ },
                                    onScrub = { scrubbing = it; poke++ },
                                    draft = if (marking) draft.fractions(progress.duration) else null,
                                )
                            }
                        }
                    }
                }
            }
        }
        val details: @Composable () -> Unit = details@{
            val info = now ?: return@details
            val segments = sponsorFractions(info, progress.duration)

            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // La barre de progression est dans la vidéo (2.0.1) ; ici, seulement le marquage SponsorBlock
                if (!info.live) {
                    if (marking) {
                        SegmentMarker(draft, info.videoId, progress.position, progress.duration, onClose = { marking = false })
                    }
                }

                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.07f)).clickable(role = Role.Checkbox) { PlayerHolder.setAutoSkip(!autoSkip) }.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Pelage.Dore.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Icon(MkIcons.Paw, null, tint = Pelage.Dore, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            if (segments.isEmpty()) stringResource(R.string.sponsor_none) else pluralStringResource(R.plurals.sponsors_count, segments.size, segments.size),
                            color = Pelage.Texte,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(stringResource(R.string.sponsor_auto), color = Color(0xFFCFC6BD), fontSize = 13.sp)
                    }
                    Checkbox(checked = autoSkip, onCheckedChange = { PlayerHolder.setAutoSkip(it) }, colors = CheckboxDefaults.colors(checkedColor = Pelage.Roux, checkmarkColor = Pelage.Encre))
                }

                Text(info.title, Modifier.padding(top = 8.dp), color = Pelage.Texte, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    listOfNotNull(formatViews(info.views), info.uploadedAt?.let { formatAgo(Instant.ofEpochMilli(it)) }).joinToString(" · "),
                    color = Color(0xFFCFC6BD),
                    fontSize = 13.sp,
                )
                VideoChannelRow(info, onOpenChannel)

                // La Bibliothèque : J'aime (le pouce), À regarder plus tard, ajouter à une playlist
                val library = remember { VideoLibrary.get(context) }
                val likedList by library.liked.collectAsState()
                val laterList by library.later.collectAsState()
                val asVideo = VideoItem(info.url, info.title, info.channel, info.thumbnail, channelId = info.channelUrl?.let(::channelIdFrom))
                val liked = likedList.any { sameTrack(it.url, info.url) }
                val later = laterList.any { sameTrack(it.url, info.url) }
                Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // J'aime / je n'aime pas : un bouton Material 3 en deux parties (2.9.0) ; plein de la
                    // couleur d'accent quand la vidéo est aimée
                    val likeColors = if (liked) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()
                    Row(
                        Modifier.height(40.dp).clip(CircleShape).background(likeColors.containerColor),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            Modifier.fillMaxHeight().clickable(role = Role.Button, onClickLabel = stringResource(if (liked) R.string.library_liked_remove else R.string.library_liked_add)) {
                                library.toggleLiked(asVideo)
                            }.padding(start = 16.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(if (liked) MkIcons.ThumbUpFilled else MkIcons.ThumbUp, stringResource(R.string.like), tint = likeColors.contentColor, modifier = Modifier.size(18.dp))
                            Text(" ${formatCount(info.likes)}", color = likeColors.contentColor, fontSize = 14.sp, fontWeight = if (liked) FontWeight.ExtraBold else FontWeight.Bold)
                        }
                        Box(Modifier.size(1.dp, 22.dp).background(likeColors.contentColor.copy(alpha = 0.3f)))
                        Row(Modifier.padding(start = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(MkIcons.ThumbUp, stringResource(R.string.dislike_ryd), tint = likeColors.contentColor, modifier = Modifier.size(18.dp).rotate(180f))
                            Text(" ${info.dislikes?.let(::formatCount) ?: "…"}", color = likeColors.contentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    WhiteAction(MkIcons.Clock, stringResource(if (later) R.string.library_later_saved else R.string.library_later_short)) {
                        val added = library.toggleLater(asVideo)
                        Toast.makeText(context, context.getString(if (added) R.string.library_later_added else R.string.library_later_removed), Toast.LENGTH_SHORT).show()
                    }
                    WhiteAction(MkIcons.PlaylistAdd, stringResource(R.string.playlist_short)) { VideoMenu.openPlaylists(asVideo) }
                    WhiteAction(MkIcons.Share, stringResource(R.string.share)) { shareLink(context, info.title, info.url) }
                    WhiteAction(MkIcons.Download, stringResource(R.string.download)) { onDownload(info.url) }
                    WhiteAction(MkIcons.Radio, stringResource(R.string.radio), onClick = onRadio)
                    if (!info.live) WhiteAction(MkIcons.Paw, stringResource(R.string.mark_segment)) { marking = !marking }
                }

                DescriptionCard(info)

                LaunchedEffect(info.url) { Comments.load(info.url) }
                CommentsPreview(Modifier.padding(top = 12.dp))

                if (info.chapters.isNotEmpty()) {
                    Text(stringResource(R.string.chapters), Modifier.padding(top = 12.dp), color = Pelage.Texte, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    info.chapters.forEach { chapter ->
                        val sponsor = info.segments.any { it.category == "sponsor" && chapter.startMs in it.startMs..it.endMs }
                        Row(
                            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).clickable { PlayerHolder.seekTo(chapter.startMs) }.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                formatClock(chapter.startMs),
                                Modifier.clip(RoundedCornerShape(8.dp)).background(Pelage.Creme).padding(horizontal = 8.dp, vertical = 4.dp),
                                color = if (sponsor) Pelage.DoreFonce else Pelage.Encre,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                chapter.title,
                                color = if (sponsor) Color(0xFFCFC6BD) else Pelage.Texte,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = if (sponsor) TextDecoration.LineThrough else null,
                            )
                        }
                    }
                }

                // Les suggestions aussi dans la page, sous les commentaires (demandé le 05/11) ; le tiroir
                // en verre du bas (PlaylistDrawer) les garde à portée sans descendre
                if (info.related.isNotEmpty()) {
                    Text(stringResource(R.string.up_next), Modifier.padding(top = 16.dp, bottom = 6.dp), color = Pelage.Texte, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        info.related.forEach { suggestion ->
                            androidx.compose.runtime.key(suggestion.url) {
                                CompactVideoRow(suggestion) { onOpen(suggestion) }
                            }
                        }
                    }
                }

                // Le tiroir en verre du bas (vidéo d'une playlist seulement) : sa barre ne doit rien cacher
                val playlist by PlayerHolder.videoPlaylist.collectAsState()
                if (playlist.any { sameTrack(it.url, info.url) }) Spacer(Modifier.height(PLAYLIST_DRAWER_BAR + 12.dp))
            }
        }
        if (landscape) {
            Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                // Pas de ligne « Now playing » en paysage (elle prenait trop de place) : la vidéo seule, centrée
                // sur toute la hauteur de l'écran (la marge de la barre d'état n'est que pour la colonne d'infos).
                // Glisser la vidéo vers le bas la réduit en mini-lecteur, comme la ligne « Now playing » en portrait.
                // Au-dessus de la colonne d'infos pendant le zoom du retour du plein écran
                Column(Modifier.weight(0.6f).fillMaxHeight().zIndex(if (RotationMorph.running) 1f else 0f), verticalArrangement = Arrangement.Center) {
                    videoArea(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp).then(pullToMinimize), RoundedCornerShape(16.dp))
                }
                Column(Modifier.weight(0.4f).fillMaxHeight().verticalScroll(scroll).statusBarSpacePadding().padding(bottom = 48.dp)) { details() }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(scroll).statusBarSpacePadding().padding(bottom = 48.dp)) {
                header()
                videoArea(Modifier.fillMaxWidth().then(pullToMinimize), RectangleShape) // glisser vers le bas : mini-lecteur (2.9.0)
                details()
            }
        }
    }
}

/** Une action sous la vidéo : un bouton tonal Material 3 avec son icône (2.9.0, avant en pâte crème). */
@Composable
private fun WhiteAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
        Icon(icon, null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Mode ambiant : l'image de la vidéo (déjà enregistrée pour le verre), agrandie et très floutée,
 * qui déborde au-dessus et en dessous du lecteur puis s'efface vers le bas.
 * Se place en haut de l'écran, derrière le contenu du lecteur.
 */
@Composable
private fun AmbientGlow(video: Backdrop, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val top = WindowInsets.statusBarSpace.getTop(density) + with(density) { 56.dp.toPx() } // barre d'état + « Lecture en cours »
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val videoHeight = maxWidth * 9f / 16f
        val glowHeight = with(density) { top.toDp() } + videoHeight + 220.dp
        Box(
            Modifier
                .fillMaxWidth()
                .height(glowHeight)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    alpha = 0.75f
                }
                .drawWithContent {
                    drawContent()
                    // Fondu vers le bas (et un peu en haut) pour que la lueur n'ait pas de bord
                    drawRect(
                        Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.7f), 0.3f to Color.Black, 0.62f to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        renderEffect = BlurEffect(70.dp.toPx(), 70.dp.toPx(), TileMode.Decal)
                    }
                    .drawBehind {
                        val layer = video.layer
                        if (layer.size.width == 0) return@drawBehind
                        val w = size.width
                        val h = w * 9f / 16f
                        translate(top = top) {
                            // Plus large et surtout plus haute que la vidéo : la lueur déborde
                            scale(scaleX = 1.25f, scaleY = 1.9f, pivot = Offset(w / 2f, h / 2f)) {
                                scale(w / layer.size.width, h / layer.size.height, pivot = Offset.Zero) { drawLayer(layer) }
                            }
                        }
                    },
            )
        }
    }
}

/**
 * La description : date de mise en ligne, puis le texte (liens et horodatages cliquables).
 * Repliée sur 3 lignes ; un tap la déplie.
 */
@Composable
private fun DescriptionCard(info: NowPlaying) {
    if (info.description.isBlank() && info.uploadedAt == null) return
    var open by remember(info.videoId) { mutableStateOf(false) }
    val text = linkedText(info.videoId, info.description, info.descriptionHtml)
    val date = info.uploadedAt?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(AppLocale.locale()))
    }
    Column(
        Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .clickable(role = Role.Button, onClickLabel = stringResource(if (open) R.string.description_less else R.string.description_more)) { open = !open }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (date != null) {
            Text(stringResource(R.string.published_on, date), color = Pelage.Texte, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
        if (info.description.isNotBlank()) {
            Text(
                text,
                style = TextStyle(fontFamily = Rounded, color = Color(0xFFE3DAD2), fontSize = 13.sp, lineHeight = 18.sp),
                maxLines = if (open) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(if (open) R.string.description_less else R.string.description_more),
                color = Pelage.RouxClair,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}
