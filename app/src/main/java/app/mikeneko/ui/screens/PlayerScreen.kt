package app.mikeneko.ui.screens

import app.mikeneko.ui.components.SeekPreview
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import app.mikeneko.ui.components.VideoMenu
import app.mikeneko.data.VideoLibrary
import app.mikeneko.data.sameTrack
import app.mikeneko.ui.components.PLAYLIST_DRAWER_BAR
import app.mikeneko.ui.components.Capsule
import androidx.compose.foundation.border
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import app.mikeneko.data.channelIdFrom
import app.mikeneko.data.Subscription
import app.mikeneko.data.SubscriptionStore
import app.mikeneko.data.ChannelItem
import app.mikeneko.ui.theme.statusBarSpace
import app.mikeneko.ui.theme.statusBarSpacePadding
import app.mikeneko.ui.theme.jellyClickable
import app.mikeneko.ui.theme.clay
import java.time.format.FormatStyle
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.time.Instant
import app.mikeneko.settings.AppLocale
import app.mikeneko.data.formatAgo
import app.mikeneko.data.VideoItem
import app.mikeneko.ui.components.TapToSeekLayer
import app.mikeneko.ui.components.linkedText
import app.mikeneko.ui.components.CompactVideoRow
import app.mikeneko.ui.components.VideoMorph
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.DisposableEffect
import app.mikeneko.ui.components.SegmentMarker
import app.mikeneko.ui.components.SegmentDraft
import app.mikeneko.settings.AppSettings
import app.mikeneko.data.formatViews
import app.mikeneko.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import android.widget.Toast
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.derivedStateOf
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import app.mikeneko.player.SettingsPage
import app.mikeneko.data.Comments
import app.mikeneko.ui.components.CommentsPreview
import app.mikeneko.ui.components.PlayerSettingsSheet
import app.mikeneko.ui.components.ambientSupported
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import app.mikeneko.player.NowPlaying
import app.mikeneko.ui.components.Tag
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.glass.LiquidGlass
import app.mikeneko.ui.theme.GlassTextShadow
import app.mikeneko.ui.theme.Rounded
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.data.SponsorBlock
import app.mikeneko.data.formatCount
import app.mikeneko.data.formatDuration
import app.mikeneko.player.PlayerHolder
import app.mikeneko.ui.components.GlassCircleButton
import app.mikeneko.ui.components.Loading
import app.mikeneko.ui.components.ProgressWithSegments
import app.mikeneko.ui.components.VideoSurface
import app.mikeneko.ui.glass.backdropSource
import app.mikeneko.ui.glass.rememberBackdrop
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage
import kotlinx.coroutines.delay

/** Le lecteur vidéo : boutons en verre posés sur la vidéo, SponsorBlock, dislikes, chapitres. */

/** Les commandes sur la vidéo apparaissent et s'effacent en douceur (adouci, plus lent à partir). */
private val ControlsFadeIn = fadeIn(tween(340, easing = FastOutSlowInEasing))
private val ControlsFadeOut = fadeOut(tween(480, easing = FastOutSlowInEasing))

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
    LaunchedEffect(controls, playing, loading, buffering, settings, poke) {
        if (controls && playing && !loading && !buffering && settings == null) {
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
    val pullToMinimize = Modifier.pointerInput(Unit) {
        val tracker = VelocityTracker()
        fun release(cancelled: Boolean) {
            val speed = tracker.calculateVelocity().y
            tracker.resetTracking()
            if (!cancelled && (pull.value > minimizeDistance || speed > minimizeSpeed)) minimize()
            pullScope.launch { pull.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
        }
        detectVerticalDragGestures(
            onDragEnd = { release(cancelled = false) },
            onDragCancel = { release(cancelled = true) },
        ) { change, dy ->
            change.consume()
            pullScope.launch { pull.snapTo((pull.value + dy).coerceAtLeast(0f)) }
            tracker.addPosition(change.uptimeMillis, Offset(0f, pull.value + dy))
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
        AmbientGlow(video, Modifier.offset { IntOffset(0, -scroll.value) })
    }

    Column(Modifier.fillMaxSize().verticalScroll(scroll).statusBarSpacePadding().padding(bottom = 48.dp)) {
        Row(Modifier.fillMaxWidth().then(pullToMinimize).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onMinimize), contentAlignment = Alignment.Center) {
                Icon(MkIcons.ChevronDown, stringResource(R.string.player_minimize), tint = Pelage.Texte, modifier = Modifier.size(26.dp))
            }
            Text(stringResource(R.string.now_playing), Modifier.weight(1f), color = Color(0xFFCFC6BD), fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Box(Modifier.size(44.dp))
        }

        DisposableEffect(Unit) { onDispose { VideoMorph.playerBounds = null } }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .onGloballyPositioned { VideoMorph.playerBounds = it.boundsInRoot() }
                // Pendant le morph depuis le mini-lecteur, c'est la photo qui arrive à cette place
                .graphicsLayer { alpha = if (VideoMorph.hidesPlayer()) 0f else 1f }
                .background(Pelage.Nuit),
        ) {
            // Sous-titres remontés au-dessus des boutons quand ils sont affichés
            val captionLift by animateFloatAsState(if (controls) 0.24f else 0f, tween(250), label = "sous-titres")
            Box(Modifier.fillMaxSize().backdropSource(video)) { VideoSurface(Modifier.fillMaxSize(), captionLift = captionLift) }

            // Toute l'image : un tap montre / cache les commandes, un double tap recule / avance de 10 s
            TapToSeekLayer(
                controlsShown = controls,
                canSeek = now?.live == false,
                onToggleControls = { controls = !controls; poke++ },
                onSeek = { poke++ },
            )

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
                    TransportControls(
                        video, wantsToPlay, waiting = loading || buffering, hasNext = now?.related?.isNotEmpty() == true,
                        playSize = 64.dp, skipSize = 46.dp, spacing = 26.dp, modifier = Modifier.align(Alignment.Center),
                    ) { poke++ }
                    AutoplaySwitch(Modifier.align(Alignment.TopEnd).padding(12.dp)) { poke++ }
                    Row(Modifier.align(Alignment.BottomEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CaptionsButton(video, caption != null) {
                            if (!PlayerHolder.toggleCaptions()) Toast.makeText(context, context.getString(R.string.no_captions), Toast.LENGTH_SHORT).show()
                            poke++
                        }
                        GlassCircleButton(video, MkIcons.Gear, stringResource(R.string.video_settings), size = 44.dp) { PlayerHolder.settingsPage.value = SettingsPage.Menu }
                        GlassCircleButton(video, MkIcons.Fullscreen, stringResource(R.string.fullscreen), size = 44.dp, onClick = onFullscreen)
                    }
                }
            }
        }

        val info = now ?: return@Column
        val segments = sponsorFractions(info, progress.duration)

        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!info.live) {
                ProgressWithSegments(
                    progress.fraction,
                    segments,
                    buffered = progress.bufferedFraction,
                    preview = { f -> SeekPreview(info.previewFrames, (f * progress.duration).toLong()) },
                    onSeek = progress::seekTo,
                    onScrub = { scrubbing = it },
                    draft = if (marking) draft.fractions(progress.duration) else null,
                )
                Row {
                    Text(formatDuration(progress.shownPosition(scrubbing) / 1000).ifEmpty { "0:00" }, Modifier.weight(1f), color = Color(0xFFCFC6BD), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(formatDuration(progress.duration / 1000), color = Color(0xFFCFC6BD), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
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
                Row(Modifier.height(44.dp).clay(if (liked) Pelage.Roux else Pelage.Creme).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.clip(CircleShape).clickable(role = Role.Button, onClickLabel = stringResource(if (liked) R.string.library_liked_remove else R.string.library_liked_add)) {
                            library.toggleLiked(asVideo)
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(MkIcons.ThumbUp, stringResource(R.string.like), tint = Pelage.Encre, modifier = Modifier.size(20.dp))
                        Text(" ${formatCount(info.likes)}", color = Pelage.Encre, fontSize = 14.sp, fontWeight = if (liked) FontWeight.ExtraBold else FontWeight.Bold)
                    }
                    Box(Modifier.padding(horizontal = 12.dp).size(1.dp, 22.dp).background(Color(0xFFD9CFC4)))
                    Icon(MkIcons.ThumbUp, stringResource(R.string.dislike_ryd), tint = Pelage.Encre, modifier = Modifier.size(20.dp).rotate(180f))
                    Text(" ${info.dislikes?.let(::formatCount) ?: "…"}", color = Pelage.Encre, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                WhiteAction(MkIcons.Clock, stringResource(if (later) R.string.library_later_saved else R.string.library_later_short)) {
                    val added = library.toggleLater(asVideo)
                    Toast.makeText(context, context.getString(if (added) R.string.library_later_added else R.string.library_later_removed), Toast.LENGTH_SHORT).show()
                }
                WhiteAction(MkIcons.PlaylistAdd, stringResource(R.string.playlist_short)) { VideoMenu.openPlaylists(asVideo) }
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
                            formatDuration(chapter.startMs / 1000).ifEmpty { "0:00" },
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
    }
}

@Composable
private fun WhiteAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.jellyClickable(onClick = onClick).height(44.dp).clay(Pelage.Creme).padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = Pelage.Encre, modifier = Modifier.size(20.dp))
        Text(label, color = Pelage.Encre, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** Position / durée du lecteur partagé, rafraîchies 4 fois par seconde. */
@Stable
class PlaybackProgress {
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var buffered by mutableLongStateOf(0L)

    /** Ce qui est déjà chargé, en fraction de la durée. */
    val bufferedFraction: Float
        get() = if (duration > 0) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f

    val fraction: Float
        get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    /** Le temps à afficher : celui visé pendant un glissé, sinon la position réelle. */
    fun shownPosition(scrub: Float?): Long = scrub?.let { (it * duration).toLong() } ?: position

    fun seekTo(fraction: Float) {
        val target = (fraction * duration).toLong()
        PlayerHolder.seekTo(target)
        position = target
    }
}

@Composable
fun rememberPlaybackProgress(): PlaybackProgress {
    // Partir de la vraie position (sinon « 0:00 » s'affiche une image en ouvrant le lecteur)
    val progress = remember {
        PlaybackProgress().apply {
            val (p, d) = PlayerHolder.progress()
            position = p
            duration = d
        }
    }
    LaunchedEffect(progress) {
        while (true) {
            val (position, duration) = PlayerHolder.progress()
            progress.position = position
            progress.duration = duration
            progress.buffered = PlayerHolder.bufferedPosition()
            delay(250)
        }
    }
    return progress
}

/** Les segments sautés automatiquement, en fractions de la durée (pour la barre de progression). */
private fun sponsorFractions(info: NowPlaying, duration: Long): List<Pair<Float, Float>> =
    if (duration <= 0) emptyList()
    else info.segments
        .filter { it.category in AppSettings.skipCategories.value }
        .map { it.startMs.toFloat() / duration to it.endMs.toFloat() / duration }

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
    var visible by remember { mutableStateOf(true) }
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
        Box(Modifier.fillMaxSize().backdropSource(video)) { VideoSurface(Modifier.fillMaxSize(), captionLift = captionLift) }

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
                                "${formatDuration(progress.shownPosition(scrubbing) / 1000).ifEmpty { "0:00" }} / ${formatDuration(progress.duration / 1000).ifEmpty { "0:00" }}",
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
 * Le centre des commandes posées sur la vidéo : vidéo précédente, lecture / pause, vidéo suivante.
 * Suivante = la première suggestion ; précédente = la vidéo regardée juste avant.
 * [onInteract] est appelé à chaque appui (pour relancer le minuteur qui masque les commandes).
 */
@Composable
private fun TransportControls(
    backdrop: Backdrop,
    wantsToPlay: Boolean,
    waiting: Boolean,
    hasNext: Boolean,
    playSize: Dp,
    skipSize: Dp,
    spacing: Dp,
    modifier: Modifier = Modifier,
    onInteract: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history by PlayerHolder.videoHistory.collectAsState()

    fun switchVideo(load: suspend (Context) -> Unit) {
        onInteract()
        scope.launch {
            runCatching { load(context) }
                .onFailure { Toast.makeText(context, context.getString(R.string.error_play_video, it.message.orEmpty()), Toast.LENGTH_SHORT).show() }
        }
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(spacing), verticalAlignment = Alignment.CenterVertically) {
        GlassCircleButton(backdrop, MkIcons.Previous, stringResource(R.string.video_previous), size = skipSize, enabled = history.isNotEmpty()) {
            switchVideo { PlayerHolder.previousVideo(it) }
        }
        PlayPauseButton(backdrop, wantsToPlay, waiting, size = playSize) {
            PlayerHolder.togglePlay()
            onInteract()
        }
        GlassCircleButton(backdrop, MkIcons.Next, stringResource(R.string.video_next), size = skipSize, enabled = hasNext) {
            switchVideo { PlayerHolder.nextVideo(it) }
        }
    }
}

/**
 * Lecture / pause. L'icône suit ce qui est demandé ([wantsToPlay]), même pendant un chargement
 * (changement de vidéo, saut) : le bouton reste là, entouré d'un anneau qui tourne.
 */
@Composable
private fun PlayPauseButton(backdrop: Backdrop, wantsToPlay: Boolean, waiting: Boolean, size: Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.size(size + 14.dp), contentAlignment = Alignment.Center) {
        GlassCircleButton(
            backdrop,
            if (wantsToPlay) MkIcons.Pause else MkIcons.Play,
            stringResource(if (wantsToPlay) R.string.pause else R.string.play),
            size = size,
            onClick = onClick,
        )
        androidx.compose.animation.AnimatedVisibility(waiting, enter = fadeIn(tween(150)), exit = fadeOut(tween(250))) {
            CircularProgressIndicator(
                Modifier.size(size + 14.dp),
                color = Pelage.Roux,
                strokeWidth = 3.dp,
                trackColor = Color.White.copy(alpha = 0.15f),
            )
        }
    }
}

/**
 * L'interrupteur « Lecture automatique » sur la vidéo (comme celui de YouTube) : une pilule avec un
 * rond qui glisse, orange et ▶ quand c'est activé, sombre et ⏸ sinon. [onTouched] : garder les
 * commandes affichées.
 */
@Composable
private fun AutoplaySwitch(modifier: Modifier = Modifier, onTouched: () -> Unit) {
    val context = LocalContext.current
    val on by PlayerHolder.autoplay.collectAsState()
    val knob by animateDpAsState(if (on) 22.dp else 0.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "lecture auto")
    val track by animateColorAsState(if (on) Pelage.Roux.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.45f), label = "piste")
    Box(
        modifier
            .size(50.dp, 28.dp)
            .clip(CircleShape)
            .background(track)
            .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .clickable(role = Role.Switch, onClickLabel = stringResource(R.string.autoplay)) {
                PlayerHolder.setAutoplay(!on)
                Toast.makeText(context, context.getString(if (!on) R.string.autoplay_on else R.string.autoplay_off), Toast.LENGTH_SHORT).show()
                onTouched()
            }
            .semantics { stateDescription = context.getString(if (on) R.string.autoplay_on else R.string.autoplay_off) }
            .padding(3.dp),
    ) {
        Box(
            Modifier.offset(x = knob).size(22.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (on) MkIcons.Play else MkIcons.Pause, null, tint = Pelage.Encre, modifier = Modifier.size(13.dp))
        }
    }
}

/** Bouton CC : orange quand les sous-titres sont affichés. */
@Composable
private fun CaptionsButton(backdrop: Backdrop, on: Boolean, onClick: () -> Unit) {
    GlassCircleButton(
        backdrop,
        MkIcons.Captions,
        stringResource(if (on) R.string.captions_hide else R.string.captions_show),
        size = 44.dp,
        tint = if (on) Pelage.Roux.copy(alpha = 0.7f) else Color(0x40100D0B),
        iconTint = if (on) Pelage.Encre else Color.White,
        onClick = onClick,
    )
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
