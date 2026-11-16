package app.mikeneko.ui.screens

import kotlin.math.abs
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.graphics.FilterQuality
import coil3.request.ImageRequest
import android.content.res.Configuration
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.media3.common.util.UnstableApi
import app.mikeneko.R
import app.mikeneko.data.ChannelItem
import app.mikeneko.data.Favorites
import app.mikeneko.data.LocalMusic
import app.mikeneko.data.Music
import app.mikeneko.data.MusicCollection
import app.mikeneko.data.VideoItem
import app.mikeneko.data.channelIdFrom
import app.mikeneko.data.cleanArtist
import app.mikeneko.data.formatClock
import app.mikeneko.data.formatCount
import app.mikeneko.player.AudioSpectrum
import app.mikeneko.player.NowPlaying
import app.mikeneko.player.PlayerHolder
import app.mikeneko.player.Repeat
import app.mikeneko.player.SettingsPage
import app.mikeneko.player.SleepTimer
import app.mikeneko.ui.components.GlassCircleButton
import app.mikeneko.ui.components.ProgressWithSegments
import app.mikeneko.ui.components.Tag
import app.mikeneko.ui.components.neumorphic
import app.mikeneko.ui.components.rememberCoverColor
import app.mikeneko.ui.components.shareLink
import app.mikeneko.ui.glass.LiquidGlass
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.glass.liquidWaveSupported
import app.mikeneko.ui.glass.WAVE_POINTS
import app.mikeneko.ui.glass.LiquidWave
import app.mikeneko.ui.glass.backdropSource
import app.mikeneko.ui.glass.rememberBackdrop
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.GlassTextShadow
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.Rounded
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// L'intérieur de la fenêtre musique ouverte : pochette, titre, progression, modes, actions, détails.

/**
 * La fenêtre ouverte : pochette, titre, progression et toutes les touches. Le fond est la pochette
 * floutée sur un voile sombre (pour lire le texte), et c'est elle que réfractent les boutons en verre.
 */
@Composable
internal fun PlayerWindowContent(
    state: MusicPlayerState,
    /** La page derrière la fenêtre : c'est elle que réfracte la vague en verre (2.6.6). */
    page: Backdrop,
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

    var windowRoot by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(Modifier.fillMaxSize().onPlaced { windowRoot = it }) {
        // Le fond, que réfractent les boutons : un dégradé de la couleur de la pochette vers la nuit (il
        // change avec le morceau). Plus d'image de la pochette dessous (2.11.5) : la couleur suffit, et
        // l'image agrandie se voyait en pixels
        val coverColor by rememberCoverColor(now?.thumbnail)
        Box(
            Modifier
                .fillMaxSize()
                .backdropSource(glass)
                // Plus transparent (2.4.0) : le voile laisse voir la page derrière la vitre, assez sombre
                // en bas pour lire les détails
                .background(
                    Brush.verticalGradient(
                        0f to coverColor.copy(alpha = 0.55f),
                        0.6f to lerp(coverColor, Pelage.Nuit, 0.6f).copy(alpha = 0.66f),
                        1f to Pelage.Nuit.copy(alpha = 0.8f),
                    ),
                ),
        )

        // Sous les commandes : l'artiste et l'album (on fait défiler la fenêtre)
        val scroll = rememberScrollState()
        LaunchedEffect(state.open) { if (!state.open) scroll.scrollTo(0) }
        LaunchedEffect(now?.url) { scroll.animateScrollTo(0) }
        // Le fond des boutons neumorphiques (3.9.0) : le milieu du dégradé de la fenêtre, opaque
        val neuBase = lerp(coverColor, Pelage.Nuit, 0.62f)
        androidx.compose.runtime.CompositionLocalProvider(app.mikeneko.ui.components.LocalNeuBase provides neuBase) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Les morceaux de la fenêtre, rangés en colonne (portrait) ou en deux parties (paysage)
            val sleepButton: @Composable () -> Unit = {
                TonalButton(
                    MkIcons.Moon,
                    stringResource(if (timer == SleepTimer.Off) R.string.sleep_timer else R.string.sleep_timer_active),
                    container = if (timer == SleepTimer.Off) null else Pelage.Roux,
                    content = if (timer == SleepTimer.Off) Color.White else Pelage.Encre,
                ) { PlayerHolder.settingsPage.value = SettingsPage.Minuteur }
            }
            val topRow: @Composable () -> Unit = {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TonalButton(MkIcons.ChevronDown, stringResource(R.string.player_minimize)) { state.collapse() }
                    Text(
                        stringResource(if (PlayerHolder.autoMix.collectAsState().value) R.string.radio_mode_mix else R.string.radio_mode),
                        Modifier.weight(1f),
                        style = TextStyle(fontFamily = Rounded, color = Pelage.Texte, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, shadow = GlassTextShadow, textAlign = TextAlign.Center),
                    )
                    // Le Chromecast (3.4.0)
                    app.mikeneko.ui.components.CastButton(glass, modifier = Modifier.padding(end = 16.dp), material = true) // (3.9.1 : 8 → 16 dp, les reliefs se touchaient)
                    sleepButton()
                }
            }
            val coverArt: @Composable () -> Unit = {
                // Derrière la pochette (3.9.0) : des lumières qui tapent avec la basse et les temps forts
                Box(contentAlignment = Alignment.Center) {
                if (now != null && !live) CoverLights(coverColor, Modifier.matchParentSize())
                Box(
                    Modifier
                        .fillMaxWidth(0.9f)
                        .aspectRatio(1f, matchHeightConstraintsFirst = true)
                        // Sa place dans la fenêtre : la pochette de la pilule vole jusqu'ici (2.22.0), et
                        // celle-ci ne se montre qu'une fois la fenêtre ouverte
                        .onGloballyPositioned { c ->
                            windowRoot?.takeIf { it.isAttached }?.let { root ->
                                val box = root.localBoundingBoxOf(c, clipBounds = false)
                                val known = state.coverInWindow
                                if (known == null || abs(known.left - box.left) > 0.5f || abs(known.top - box.top) > 0.5f || abs(known.width - box.width) > 0.5f) state.coverInWindow = box
                            }
                        }
                        .graphicsLayer {
                            alpha = if (state.coverFlying) 0f else 1f
                            // Elle « tape » sur les temps forts (lu au dessin : rien ne se recompose)
                            val beat = beatScale()
                            scaleX = beat
                            scaleY = beat
                        }
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
            
            }
            val titleRow: @Composable () -> Unit = {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(now?.title ?: stringResource(R.string.radio_pick), style = TextStyle(fontFamily = Rounded, color = Pelage.Texte, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, shadow = GlassTextShadow), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(now?.channel.orEmpty(), style = TextStyle(fontFamily = Rounded, color = Color(0xFFE3DAD2), fontSize = 14.sp, shadow = GlassTextShadow), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TonalButton(
                        if (liked) MkIcons.HeartFilled else MkIcons.Heart,
                        stringResource(if (liked) R.string.favorite_remove else R.string.favorite_add),
                        size = 46.dp,
                        content = if (liked) Pelage.Rose else Color.White,
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
                if (now != null) SoundInfo(now?.url)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    ModeToggle(MkIcons.Shuffle, stringResource(if (shuffle) R.string.shuffle_off else R.string.shuffle_on), active = shuffle) {
                        PlayerHolder.setShuffle(!shuffle)
                    }
                    TonalButton(MkIcons.Previous, stringResource(R.string.radio_previous), size = 58.dp, enabled = canSkip || (now != null && !live)) { skip { PlayerHolder.previous(context) } }
                    PlayButton(playing) { if (now != null) PlayerHolder.togglePlay() } // pas la vidéo qui joue peut-être en ce moment
                    TonalButton(MkIcons.Next, stringResource(R.string.radio_next), size = 58.dp, enabled = canSkip) { skip { PlayerHolder.next(context) } }
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
                Spacer(Modifier.height(if (landscape) 10.dp else 16.dp))
                // 3.9.0 : un plateau creusé dans la fenêtre (neumorphisme), les boutons dedans
                Box(
                    Modifier.fillMaxWidth().height(54.dp).onGloballyPositioned { state.lyricsAnchor = it.boundsInWindow() }
                        .neumorphic(RoundedCornerShape(27.dp), app.mikeneko.ui.components.LocalNeuBase.current, pressed = true),
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                        ActionSlot(MkIcons.Queue, stringResource(R.string.queue)) {
                            if (PlayerHolder.queue.value.isEmpty()) toast(context.getString(R.string.queue_empty)) else state.queueOpen = true
                        }
                        ActionSlot(MkIcons.Lyrics, stringResource(R.string.lyrics), enabled = !live && now != null) { state.lyricsOpen = true }
                        // Les effets audio (3.3.0)
                        ActionSlot(MkIcons.Equalizer, stringResource(R.string.audio_effects), enabled = !live && now != null) { state.effectsOpen = true }
                        ActionSlot(MkIcons.PlaylistAdd, stringResource(R.string.playlist_add), enabled = now != null) {
                            current()?.let { MusicSheet.open.value = MusicSheet.AddToPlaylist(it) }
                        }
                        if (!local) {
                            // (Le bouton « Vidéo » de 2.11.0 est parti en 2.11.14 : peu utile, et le passage
                            // de la musique à la vidéo marchait mal)
                            ActionSlot(MkIcons.Share, stringResource(R.string.share), enabled = now != null) { now?.let { shareLink(context, it.title, it.url) } }
                            ActionSlot(MkIcons.Download, stringResource(R.string.download), enabled = now != null && !live) { now?.url?.let(onDownload) }
                        }
                    }
                }
            }
            // En fond de la fenêtre : le spectre en vague continue qui monte du bas, derrière les
            // boutons (2.6.2 ; avant, des barres au-dessus de la progression qui poussaient tout vers le bas)
            if (now != null && !live) {
                SpectrumWave(
                    state.open,
                    coverColor,
                    page,
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(maxHeight * if (landscape) 0.46f else 0.28f),
                )
            }
            if (landscape) {
                // Paysage : la pochette à gauche, le reste à droite (qui défile : artiste, album)
                Row(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    // 3.8.0 : réduire, Cast et minuteur sous la pochette (avant, sur la ligne du titre, qui
                    // n'avait plus que quelques lettres) ; la pochette prend le reste de la hauteur
                    Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f).aspectRatio(1f, matchHeightConstraintsFirst = true), contentAlignment = Alignment.Center) { coverArt() }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            TonalButton(MkIcons.ChevronDown, stringResource(R.string.player_minimize)) { state.collapse() }
                            app.mikeneko.ui.components.CastButton(glass, material = true)
                            sleepButton()
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxHeight().nestedScroll(state.scrollConnection).verticalScroll(scroll)) {
                        titleRow()
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
}

/** Une nouvelle image de la vague au plus toutes les 33 ms (30 par seconde). */
private const val WAVE_FRAME_NANOS = 33_000_000L

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

/**
 * Sous la barre (3.2.0) : la qualité du son qui joue (« Opus · 160 kb/s · 48 kHz ») et où il part
 * (le nom du casque Bluetooth, casque filaire, haut-parleur).
 */
@Composable
private fun SoundInfo(key: String?) {
    val details by PlayerHolder.audioDetails.collectAsState()
    val castSession by app.mikeneko.cast.Cast.session.collectAsState()
    val local = app.mikeneko.ui.components.rememberAudioOutput(key)
    // Pendant le cast (3.4.0) : le son part sur l'appareil
    val output = castSession?.takeIf { it.phase == app.mikeneko.cast.Cast.Phase.Connected }
        ?.let { app.mikeneko.ui.components.AudioOutput(it.device.name, MkIcons.Cast) } ?: local
    val style = TextStyle(fontFamily = Rounded, color = Color(0xFFE3DAD2), fontSize = 12.sp, fontWeight = FontWeight.Bold, shadow = GlassTextShadow)
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            details?.let { d ->
                val khz = d.sampleRate.takeIf { it > 0 }?.let { rate ->
                    val k = rate / 1000.0
                    if (k % 1.0 == 0.0) "${k.toInt()} kHz" else String.format(java.util.Locale.getDefault(), "%.1f kHz", k)
                }
                Text(listOfNotNull(d.codec, d.kbps?.let { "$it kb/s" }, khz).joinToString(" · "), style = style, maxLines = 1)
            }
            output?.let { o ->
                Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
                    Icon(o.icon, null, tint = Color(0xFFE3DAD2), modifier = Modifier.size(15.dp))
                    Text(o.label, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 5.dp))
                }
            }
        }
        // (L'effet audio en cours est sur la ligne des temps, au-dessus : voir TrackProgress)
        // Pendant le cast (3.6.0) : où part le son, bien visible
        app.mikeneko.ui.components.CastIndicator(Modifier.padding(top = 8.dp))
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
        val preset by PlayerHolder.audioPreset.collectAsState()
        val clock = TextStyle(fontFamily = Rounded, color = Color(0xFFE3DAD2), fontSize = 12.sp, fontWeight = FontWeight.Bold, shadow = GlassTextShadow)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(formatClock(shown), style = clock)
            // L'effet audio en cours (3.3.0), au milieu de la ligne des temps, de la couleur du thème.
            // 3.7.0 : ici plutôt que sur une ligne à lui, qui poussait les boutons hors de la fenêtre
            Box(Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                if (preset != app.mikeneko.player.AudioPreset.Normal) {
                    Text(
                        app.mikeneko.ui.components.presetName(preset),
                        style = clock.copy(color = Pelage.RouxClair, textAlign = TextAlign.Center),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(if (duration > 0) formatClock(duration) else "", style = clock)
        }
    }
}

// ---- Les boutons du lecteur musique : Material 3 (3.8.0), puis neumorphiques (3.9.0) : de la couleur
// du fond de la fenêtre, en relief, qui s'enfoncent à l'appui (voir NeuButton)

/** Un bouton rond en relief ; [container] : une autre couleur que le fond (le minuteur en marche). */
@Composable
private fun TonalButton(
    icon: ImageVector,
    label: String,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    container: Color? = null,
    content: Color = Color.White,
    enabled: Boolean = true,
    onClick: () -> Unit,
) = app.mikeneko.ui.components.NeuButton(icon, label, size = size, fill = container, content = content, enabled = enabled, onClick = onClick)

/** Lecture / pause : en relief, de la couleur du thème ; rond en pause, carré arrondi pendant la lecture. */
@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    val corner by androidx.compose.animation.core.animateDpAsState(if (playing) 24.dp else 38.dp, label = "lecture")
    app.mikeneko.ui.components.NeuButton(
        if (playing) MkIcons.Pause else MkIcons.Play,
        stringResource(if (playing) R.string.pause else R.string.play),
        size = 76.dp,
        shape = RoundedCornerShape(corner),
        fill = Pelage.Roux,
        content = Pelage.Encre,
        iconSize = 34.dp,
        onClick = onClick,
    )
}

/** Aléatoire / répéter : actif, le bouton reste enfoncé, l'icône de la couleur du thème (et un point dessous). */
@Composable
private fun ModeToggle(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Box(Modifier.semantics { stateDescription = if (active) "✓" else "" }, contentAlignment = Alignment.Center) {
        app.mikeneko.ui.components.NeuButton(
            icon,
            label,
            size = 46.dp,
            content = if (active) Pelage.RouxClair else Color.White.copy(alpha = 0.85f),
            iconSize = 22.dp,
            checked = active,
            onClick = onClick,
        )
        if (active) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp).size(4.dp).clip(CircleShape).background(Pelage.RouxClair))
    }
}

@Composable
private fun RowScope.ActionSlot(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(48.dp),
            colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.35f)),
        ) { Icon(icon, label, modifier = Modifier.size(23.dp)) }
    }
}

/**
 * Le spectre en fréquence de ce qui joue ([AudioSpectrum]), en vague continue qui monte du bas de la
 * fenêtre (graves à gauche, aigus à droite), de la couleur de la pochette (comme la fenêtre, en plus
 * vif, 2.6.4) et qui s'efface vers le bas. Android 13+ : en liquid glass ([LiquidWave]) ; avant, dessinée. Elle retombe en un
 * filet en pause. Elle ne montre pas la progression : c'est le rôle de la barre (2.6.3).
 */
@OptIn(UnstableApi::class)
@Composable
private fun SpectrumWave(active: Boolean, cover: Color, backdrop: Backdrop, modifier: Modifier) {
    DisposableEffect(active) {
        AudioSpectrum.listening = active
        onDispose { AudioSpectrum.listening = false }
    }
    val playing by PlayerHolder.isPlaying.collectAsState()
    val level by animateFloatAsState(if (playing) 1f else 0f, tween(500), label = "spectre")
    // La couleur de la fenêtre est la pochette assombrie : on la ravive pour la vague
    val bright = remember(cover) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(cover.toArgb(), hsv)
        hsv[1] = (hsv[1] * 0.9f).coerceAtMost(0.75f)
        hsv[2] = 0.95f
        Color(android.graphics.Color.HSVToColor(hsv))
    }
    val color by animateColorAsState(bright, tween(600), label = "vague")
    // Le son arrive par gros paquets (~4 par seconde) : on le garde, et à chaque image de l'écran on
    // analyse l'endroit où en est une tête de lecture qui avance en temps réel, une demi-seconde
    // derrière le dernier son reçu (il est encore dans le tampon du haut-parleur). Fluide (2.5.2).
    val bands = remember { FloatArray(AudioSpectrum.BANDS) }
    val smooth = remember { FloatArray(AudioSpectrum.BANDS) }
    val levels = remember { FloatArray(WAVE_POINTS) }
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var playhead = -1.0
        var epoch = -1
        var last = 0L
        var drawn = 0L
        while (true) {
            withFrameNanos { now ->
                val written = AudioSpectrum.written
                val rate = AudioSpectrum.sampleRate.toDouble()
                val dt = if (last == 0L) 0.0 else (now - last) / 1e9
                last = now
                val behind = rate * 0.5
                if (AudioSpectrum.epoch != epoch || playhead < 0) {
                    epoch = AudioSpectrum.epoch
                    playhead = written - behind
                }
                if (PlayerHolder.isPlaying.value) playhead += dt * rate
                if (playhead > written) playhead = written.toDouble() // rattrapé : on attend le paquet suivant
                if (playhead < written - 2.4 * behind) playhead = written - behind // trop en retard (saut)
                // 30 images par seconde suffisent à la vague (2.10.4) : à chaque image d'écran, elle
                // occupait la puce graphique en continu (les animations du système ramaient, fenêtre ouverte)
                if (now - drawn < WAVE_FRAME_NANOS) return@withFrameNanos
                drawn = now
                AudioSpectrum.bandsAt(playhead.toLong(), bands)
                BeatLights.feed(bands, PlayerHolder.isPlaying.value, now) // les lumières de la pochette (3.9.0)
                // Lissée avec ses voisines : une vague, pas des pics isolés
                for (i in bands.indices) {
                    val l = bands[(i - 1).coerceAtLeast(0)]
                    val r = bands[(i + 1).coerceAtMost(bands.size - 1)]
                    smooth[i] = l * 0.25f + bands[i] * 0.5f + r * 0.25f
                    levels[i] = smooth[i] * level
                }
                frame++
            }
        }
    }
    if (liquidWaveSupported) {
        LiquidWave(backdrop, levels, frame = { frame }, color = { color }, modifier = modifier)
        return
    }
    val line = remember { Path() }
    val wave = remember { Path() }
    Canvas(modifier) {
        frame // relu ici : seulement un nouveau dessin à chaque image, pas une recomposition
        val count = smooth.size
        val min = 3.dp.toPx()
        val w = size.width
        val h = size.height
        fun x(i: Int) = i * w / (count - 1)
        fun y(i: Int) = h - (min + levels[i] * (h - min)).coerceAtMost(h)
        // La crête (seule à avoir un trait), puis la vague pleine fermée par le bas de la fenêtre
        line.reset()
        line.moveTo(x(0), y(0))
        line.lineTo((x(0) + x(1)) / 2, (y(0) + y(1)) / 2)
        for (i in 1 until count - 1) {
            line.quadraticTo(x(i), y(i), (x(i) + x(i + 1)) / 2, (y(i) + y(i + 1)) / 2)
        }
        line.lineTo(x(count - 1), y(count - 1))
        wave.reset()
        wave.addPath(line)
        wave.lineTo(w, h)
        wave.lineTo(0f, h)
        wave.close()
        // Le dégradé part de la crête la plus haute : la vague reste colorée même quand elle est basse,
        // et s'efface vers le bas de la fenêtre (2.6.3)
        var crest = h
        for (i in 0 until count) crest = minOf(crest, y(i))
        drawPath(wave, Brush.verticalGradient(
            0f to color.copy(alpha = 0.8f),
            0.4f to color.copy(alpha = 0.4f),
            1f to color.copy(alpha = 0f),
            startY = crest,
            endY = crest + (h - crest) * 0.9f + 1f,
        ))
        drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
