package app.mikeneko.ui.screens

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.data.GeneratedMix
import app.mikeneko.data.ChannelItem
import app.mikeneko.data.LocalMusic
import app.mikeneko.data.MusicCollection
import app.mikeneko.data.MusicKind
import app.mikeneko.data.Playlists
import app.mikeneko.data.VideoItem
import app.mikeneko.data.formatDuration
import app.mikeneko.player.PlayerHolder
import app.mikeneko.ui.components.shareLink
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.glass.LiquidGlass
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.GlassTextShadow
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.Rounded
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

// ---------------------------------------------------------------------------------------------
// État de la Musique : recherche et pages ouvertes (album, artiste, playlist, téléchargements)
// ---------------------------------------------------------------------------------------------

/** Une page ouverte par-dessus l'accueil de la Musique. */
sealed interface MusicPage {
    data class Album(val collection: MusicCollection) : MusicPage
    data class Artist(val artist: ChannelItem) : MusicPage
    data class MyPlaylist(val id: String) : MusicPage
    /** Ajouter des morceaux à une playlist perso : suggestions d'après ses morceaux, et recherche (2.11.0). */
    data class AddSongs(val id: String) : MusicPage
    data object Downloads : MusicPage
    /** Ta bibliothèque : playlists, albums et artistes sauvegardés, téléchargés (avec des filtres). */
    data object Library : MusicPage
    /** Un mix fabriqué d'après ton écoute (« Tes mix », 2.6.0). */
    data class Generated(val mix: GeneratedMix) : MusicPage
}

/**
 * Ce que la barre du haut reprend d'une page (album, playlist, artiste…) une fois son en-tête passé
 * sous elle : le titre, et le bouton lecture qui reste accroché. [owner] : la page qui l'a posé.
 */
class PageBar(
    val title: String,
    val onPlay: (() -> Unit)?,
    val owner: Any,
    private val passed: State<Float>,
    /** Le gros bouton lecture de l'en-tête, qui vient se ranger dans la pilule (null = pas de bouton). */
    val play: PlayAnchor? = null,
) {
    /** 0 : l'en-tête est encore là ; 1 : il est passé sous la barre. Suit le défilement, au pixel près. */
    val progress: Float get() = passed.value
}

/** Le gros bouton lecture de l'en-tête (58 dp) et sa place dans la pilule de la barre (42 dp). */
val PLAY_BIG = 58.dp
val PLAY_SMALL = 42.dp

/** Sur combien de défilement le bouton glisse de l'en-tête jusqu'à la pilule. */
private val PLAY_TRAVEL = 56.dp

/** Où est le bouton lecture qui voyage : son centre à l'écran (px) ; [t] : 0 = dans l'en-tête, 1 = rangé dans la pilule. */
class PlaySpot(val center: Offset, val t: Float)

/**
 * Un seul bouton lecture : celui de l'en-tête monte avec la page, puis glisse et rétrécit jusqu'à sa
 * place dans la pilule de la barre du haut, où il reste accroché. Tant que [PlaySpot.t] vaut 0, c'est le
 * bouton de l'en-tête qui est dessiné (il suit la liste au pixel près) ; au-delà, c'est celui de
 * [MusicOverlay], au-dessus de tout. Tout est calculé depuis la liste (pas d'`onGloballyPositioned`
 * pendant le défilement), donc sans une image de retard. Positions en px, dans l'écran (root).
 */
@Stable
class PlayAnchor(
    /** Le haut de l'en-tête dans la liste (null : il est sorti de la liste, tout en haut). */
    val headerTop: State<Float?>,
    private val passed: State<Float>,
    /** La pilule de la barre du haut, sans son animation (null tant qu'elle n'est pas posée). */
    private val pill: () -> Rect?,
) {
    /** Le coin haut-gauche du bouton dans l'en-tête (fixe). */
    var inHeader by mutableStateOf<Offset?>(null)
    /** Le bord gauche du bouton et le haut de la liste, à l'écran (fixes pendant le défilement). */
    var left by mutableFloatStateOf(0f)
    var listTop by mutableFloatStateOf(0f)

    fun spot(density: Density): PlaySpot? = with(density) {
        val inHeader = inHeader ?: return null
        val pill = pill() ?: return null
        // La place dans la pilule, avec l'animation d'arrivée de la pilule (voir MusicHeader)
        val p = passed.value
        val scale = 0.9f + 0.1f * p
        val c = pill.center
        val slot = Offset(
            c.x + (pill.right - (4.dp + PLAY_SMALL / 2).toPx() - c.x) * scale,
            c.y + (1f - p) * 14.dp.toPx(),
        )
        val top = headerTop.value ?: return PlaySpot(slot, 1f)
        val half = PLAY_BIG.toPx() / 2f
        val free = Offset(left + half, listTop + top + inHeader.y + half)
        val travel = PLAY_TRAVEL.toPx()
        val t = ((slot.y + travel - free.y) / travel).coerceIn(0f, 1f)
        // Linéaire en t : le bouton ralentit en arrivant (y = place + travel·(1−t)²)
        PlaySpot(androidx.compose.ui.geometry.lerp(free, slot, t), t)
    }
}

/** Les filtres de la bibliothèque musique. */
enum class LibraryFilter { All, Playlists, Albums, Artists, Downloaded }

/** Gardé par MikenekoRoot : on retrouve la Musique comme on l'a laissée en changeant d'onglet. */
class MusicState(context: Context) {
    private val prefs = context.getSharedPreferences("radio", Context.MODE_PRIVATE)

    /** L'ambiance choisie pour les lives (gardée entre les lancements). */
    var ambiance by mutableStateOf(prefs.getString("ambiance", null) ?: "lofi")
        private set
    var searching by mutableStateOf(false)
    var query by mutableStateOf("")
    var submitted by mutableStateOf<String?>(null)
    /** Le panneau historique / suggestions sous le champ est ouvert. */
    var suggesting by mutableStateOf(false)
    var kind by mutableStateOf(MusicKind.Songs)
    /** La recherche dans la playlist perso ouverte (2.11.3) : null = fermée. */
    var playlistFilter by mutableStateOf<String?>(null)
    /** Le titre et le bouton lecture de la page ouverte, pour la barre du haut. */
    var bar by mutableStateOf<PageBar?>(null)
    /** La pilule de la barre du haut à l'écran, sans son animation : la place du bouton lecture. */
    var barPill by mutableStateOf<Rect?>(null)
    /** Où en est l'en-tête de la page sous la barre (0 → 1, voir [PageBar.progress]). */
    val barProgress: Float get() = if (page == null) 0f else bar?.progress ?: 0f
    /** Le filtre choisi dans la bibliothèque (gardé en revenant d'un album). */
    var libraryFilter by mutableStateOf(LibraryFilter.All)
    val pages = mutableStateListOf<MusicPage>()
    val page: MusicPage? get() = pages.lastOrNull()

    val canGoBack: Boolean get() = pages.isNotEmpty() || searching

    fun chooseAmbiance(id: String) {
        ambiance = id
        prefs.edit().putString("ambiance", id).apply()
    }

    fun open(page: MusicPage) {
        pages += page
    }

    fun goBack() {
        when {
            playlistFilter != null && page is MusicPage.MyPlaylist -> playlistFilter = null // d'abord fermer la recherche
            pages.isNotEmpty() -> pages.removeAt(pages.lastIndex)
            // Suggestions ouvertes par-dessus des résultats : on revient d'abord aux résultats
            suggesting && submitted != null -> {
                suggesting = false
                query = submitted.orEmpty() // on retrouve le texte cherché
            }
            else -> closeSearch()
        }
    }

    /** Pareil que pour l'accueil : on refait défiler la page, les suggestions se rangent. */
    fun dismissSuggestions() {
        if (!suggesting || pages.isNotEmpty()) return
        when {
            submitted != null -> {
                suggesting = false
                query = submitted.orEmpty()
            }
            query.isEmpty() -> closeSearch()
            else -> suggesting = false
        }
    }

    fun openSearch() {
        searching = true
        suggesting = true // à l'ouverture : l'historique des recherches
    }

    fun closeSearch() {
        searching = false
        suggesting = false
        query = ""
        submitted = null
        kind = MusicKind.Songs
    }
}

/** Lancer la musique depuis une page de la Musique, avec un message si ça rate. */
internal fun playTracks(context: Context, items: List<VideoItem>, start: Int, mix: Boolean = false) {
    PlayerHolder.launchPlayback(onError = { failed(context) }) { PlayerHolder.playQueue(context, items, start, mix) }
}

internal fun playMix(context: Context, item: VideoItem) {
    PlayerHolder.launchPlayback(onError = { failed(context) }) { PlayerHolder.startMix(context, item) }
}

/** Tout lire en aléatoire : l'aléatoire s'active et un morceau au hasard commence. */
internal fun shuffleTracks(context: Context, items: List<VideoItem>) {
    if (items.isEmpty()) return
    PlayerHolder.setShuffle(true)
    playTracks(context, items, items.indices.random())
}

private fun failed(context: Context) =
    Toast.makeText(context, context.getString(R.string.music_play_failed), Toast.LENGTH_SHORT).show()

// ---------------------------------------------------------------------------------------------
// Lignes de liste
// ---------------------------------------------------------------------------------------------

/** Un morceau en ligne : pochette carrée, titre, artiste · durée, et le menu ⋮. */
/**
 * Trois petites barres qui montent et descendent : « ça joue ». En pause, elles s'arrêtent : une
 * animation sans fin dans une page redessine la page à chaque image (et le verre de la barre du
 * haut avec), ce qui faisait accrocher le défilement dès qu'un morceau était chargé.
 */
@Composable
fun PlayingBars(modifier: Modifier = Modifier, color: Color = Pelage.RouxClair) {
    val playingLabel = stringResource(R.string.playing_now)
    val running by PlayerHolder.isPlaying.collectAsState()
    val m = modifier.size(16.dp, 14.dp).semantics { contentDescription = playingLabel }
    if (running) MovingBars(m, color) else Bars(m, color) { i -> PAUSED_BARS[i] }
}

private val PAUSED_BARS = floatArrayOf(0.45f, 0.8f, 0.6f)

@Composable
private fun MovingBars(modifier: Modifier, color: Color) {
    val motion = rememberInfiniteTransition(label = "égalisation")
    val bars = listOf(380, 520, 440).mapIndexed { i, period ->
        motion.animateFloat(0.25f, 1f, infiniteRepeatable(tween(period, delayMillis = i * 90), RepeatMode.Reverse), label = "barre $i")
    }
    Bars(modifier, color) { i -> bars[i].value }
}

@Composable
private fun Bars(modifier: Modifier, color: Color, height: (Int) -> Float) {
    Canvas(modifier) {
        val w = size.width / 5f
        repeat(3) { i ->
            val h = size.height * height(i)
            drawRoundRect(color, topLeft = Offset(i * 2 * w, size.height - h), size = Size(w, h), cornerRadius = CornerRadius(w / 2))
        }
    }
}

@Composable
fun TrackRow(
    track: VideoItem,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    number: Int? = null,
    onMore: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    /** À gauche de la pochette : le bouton « retirer » du mode Modifier d'une playlist (2.11.0). */
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (number != null) {
            // Le numéro, ou la petite égalisation qui danse sur le morceau en cours
            Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                if (playing) PlayingBars() else Text(number.toString(), color = Pelage.Gris, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        leading?.invoke()
        Cover(track.thumbnail, 48.dp, 8.dp)
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = if (playing) Pelage.RouxClair else Pelage.Texte,
                fontSize = 15.sp,
                fontWeight = if (playing) FontWeight.ExtraBold else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(track.channel, formatDuration(track.durationSec)).filter { it.isNotBlank() }.joinToString(" · "),
                color = Pelage.Gris,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (playing && number == null) PlayingBars()
        trailing?.invoke()
        if (onMore != null) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onMore), contentAlignment = Alignment.Center) {
                Icon(MkIcons.More, stringResource(R.string.more_options), tint = Pelage.Gris, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Un album, une playlist ou un artiste en ligne. */
@Composable
fun CollectionRow(title: String, subtitle: String, thumbnail: Any?, round: Boolean = false, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Cover(thumbnail, 60.dp, if (round) 30.dp else 12.dp, icon = icon)
        Column(Modifier.weight(1f)) {
            Text(title, color = Pelage.Texte, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = Pelage.Gris, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(MkIcons.ChevronRight, null, tint = Pelage.Gris, modifier = Modifier.size(18.dp))
    }
}

/** Une pochette (ou une icône rousse sur fond roux sombre quand il n'y en a pas ; plus de violet, 1.19.3). */
@Composable
fun Cover(url: Any?, size: Dp, corner: Dp, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Box(modifier.size(size).clip(RoundedCornerShape(corner)).background(Pelage.RouxNuit), contentAlignment = Alignment.Center) {
        if (url == null) {
            Icon(icon ?: MkIcons.Music, null, tint = Pelage.RouxClair, modifier = Modifier.size(size * 0.42f))
        } else {
            AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun MusicSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 12.dp).semantics { heading() }, color = Pelage.Texte, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
}

// ---------------------------------------------------------------------------------------------
// Glisser pour réordonner (file d'attente, playlists)
// ---------------------------------------------------------------------------------------------

/**
 * Réordonner une liste en tirant la poignée ≡ (2.11.6 : refait). Le morceau tiré reste **sous le
 * doigt**, au pixel près : on suit la position du doigt dans la liste ([finger]), et la ligne est
 * dessinée centrée dessus. C'est aussi la position du doigt qui décide de sa place (échange avec la
 * ligne sous le doigt) et de la vitesse de défilement près des bords : nulle hors des zones du haut
 * et du bas, puis de plus en plus rapide en approchant du bord. Avant, la ligne pouvait se décaler du
 * doigt (et on la bloquait sous la barre du haut), d'où des places fausses et des arrêts.
 */
class ReorderState(
    private val list: LazyListState,
    private val firstIndex: Int,
    private val count: () -> Int,
    /** Pour faire défiler la liste quand le doigt approche d'un bord ; null : jamais. */
    private val scope: CoroutineScope? = null,
    /** Les bords (px) cachés par la barre du haut / les docks du bas : les zones de défilement commencent avant. */
    private val topInset: Float = 0f,
    private val bottomInset: Float = 0f,
    /** La hauteur (px) des zones de défilement, au-dessus / au-dessous de ce qu'on voit vraiment. */
    private val edge: Float = 160f,
    private val onMove: (Int, Int) -> Unit,
) {
    var dragging by mutableStateOf<Any?>(null)
        private set
    /** Le décalage de dessin de la ligne tirée par rapport à sa place dans la liste. */
    var offset by mutableFloatStateOf(0f)
        private set
    /** Où est le doigt, dans la liste (même repère que les `offset` des lignes visibles). */
    private var finger = 0f
    /** Après un échange : la place attendue, tant que la liste n'a pas été remise en page. */
    private var awaiting: Int? = null
    private var awaitingSince = 0L
    private var autoScroll: Job? = null

    fun start(key: Any) {
        dragging = key
        offset = 0f
        awaiting = null
        val current = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
        finger = current?.let { it.offset + it.size / 2f } ?: 0f
    }

    fun drag(dy: Float) {
        finger += dy
        settle()
        followEdges()
    }

    /** La ligne prend la place de celle qui est sous le doigt, et se dessine centrée sur le doigt. */
    private fun settle() {
        val visible = list.layoutInfo.visibleItemsInfo
        val current = visible.firstOrNull { it.key == dragging } ?: return
        // L'échange d'avant n'est pas encore mis en page : pas de nouvel échange (il le défairait)
        awaiting?.let { expected ->
            if (current.index != expected && System.nanoTime() - awaitingSince < 100_000_000L) return
            awaiting = null
        }
        val target = visible.firstOrNull {
            it.key != dragging && it.index - firstIndex in 0 until count() && finger >= it.offset && finger <= it.offset + it.size
        }
        if (target != null) {
            onMove(current.index - firstIndex, target.index - firstIndex)
            awaiting = target.index
            awaitingSince = System.nanoTime()
            // La place de l'autre ligne (la mise en page suit à l'image d'après)
            offset = finger - (target.offset + current.size / 2f)
            return
        }
        offset = finger - (current.offset + current.size / 2f)
    }

    /** Le défilement près des bords, tant que le doigt y reste (la ligne reste sous le doigt). */
    private fun followEdges() {
        val scope = scope ?: return
        if (speed() == 0f || autoScroll?.isActive == true) return
        autoScroll = scope.launch {
            while (dragging != null) {
                val step = speed()
                if (step == 0f) break
                withFrameNanos { }
                val used = list.scrollBy(step)
                if (used == 0f) break // au bout de la liste
                // Le doigt ne bouge pas à l'écran : dans la liste, il a avancé de ce qui a défilé
                settle()
            }
        }
    }

    /** px par image d'après la position du doigt : négatif vers le haut, 0 hors des zones des bords. */
    private fun speed(): Float {
        val info = list.layoutInfo
        val top = info.viewportStartOffset + topInset
        val bottom = info.viewportEndOffset - bottomInset
        val push = when {
            finger < top + edge -> -((top + edge - finger) / edge)
            finger > bottom - edge -> (finger - (bottom - edge)) / edge
            else -> 0f
        }
        // Plus le doigt approche du bord, plus ça va vite ; vitesse maximale dès la barre du haut / les
        // docks (2.11.7 : en haut, le doigt s'arrête vers la barre, où ça n'allait qu'au tiers)
        val sign = if (push < 0) -1f else 1f
        val p = kotlin.math.abs(push).coerceAtMost(1f)
        return sign * p * p * MAX_AUTO_SCROLL
    }

    fun end() {
        autoScroll?.cancel()
        autoScroll = null
        dragging = null
        awaiting = null
        offset = 0f
    }
}

/** Vitesse maximale du défilement automatique en tirant une ligne (px par image, doigt tout au bord). */
private const val MAX_AUTO_SCROLL = 64f

/** La poignée ≡ : la tenir et glisser déplace la ligne. */
@Composable
fun DragHandle(state: ReorderState, key: Any, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(40.dp)
            .pointerInput(key) {
                detectDragGestures(
                    onDragStart = { state.start(key) },
                    onDragEnd = { state.end() },
                    onDragCancel = { state.end() },
                ) { change, amount ->
                    change.consume()
                    state.drag(amount.y)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(MkIcons.DragHandle, stringResource(R.string.reorder), tint = Pelage.Gris, modifier = Modifier.size(22.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Le menu ⋮ des morceaux, le choix d'une playlist et les noms de playlist
// ---------------------------------------------------------------------------------------------

/** Ce qu'affiche le panneau en verre de la musique (null = fermé). */
sealed interface MusicSheet {
    /** Le menu d'un morceau ; [playlistId] / [index] : il est dans cette playlist (pour le retirer). */
    data class Track(val track: VideoItem, val playlistId: String? = null, val index: Int = -1) : MusicSheet
    data class AddToPlaylist(val track: VideoItem) : MusicSheet
    data class NewPlaylist(val first: VideoItem? = null) : MusicSheet
    data class PlaylistOptions(val id: String) : MusicSheet
    data class Rename(val id: String) : MusicSheet

    companion object {
        val open = MutableStateFlow<MusicSheet?>(null)
    }
}

/**
 * Le panneau qui monte du bas pour [MusicSheet] : à poser par-dessus l'écran qui a un [backdrop]
 * (la Musique, ou le grand lecteur).
 */
@Composable
fun MusicSheetHost(backdrop: Backdrop, onPlaylistDeleted: (String) -> Unit = {}) {
    val context = LocalContext.current
    val request by MusicSheet.open.collectAsState()
    var shown by remember { mutableStateOf<MusicSheet?>(null) }
    request?.let { shown = it }

    fun close() {
        MusicSheet.open.value = null
    }

    BackHandler(enabled = request != null, onBack = ::close)

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(request != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                    .clickable(interactionSource = null, indication = null, onClickLabel = stringResource(R.string.menu_close), onClick = ::close),
            )
        }
        AnimatedVisibility(
            request != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(300)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(240)) { it } + fadeOut(tween(200)),
        ) {
            val sheet = shown ?: return@AnimatedVisibility
            LiquidGlass(
                backdrop = backdrop,
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().navigationBarsPadding().imePadding().padding(10.dp),
                cornerRadius = 30.dp,
                bezel = 26.dp,
                refraction = 26.dp,
                tint = Color(0xC71A1613),
                frost = 28.dp,
            ) {
                Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(vertical = 10.dp)) {
                    when (sheet) {
                        is MusicSheet.Track -> TrackMenu(context, sheet, ::close)
                        is MusicSheet.AddToPlaylist -> PlaylistPicker(context, sheet.track, ::close)
                        is MusicSheet.NewPlaylist -> NameEditor(
                            title = stringResource(R.string.playlist_new),
                            initial = "",
                            action = stringResource(R.string.playlist_create),
                        ) { name ->
                            val playlist = Playlists.get(context).create(name, sheet.first)
                            toast(context, if (sheet.first != null) context.getString(R.string.playlist_added_to, playlist.name) else context.getString(R.string.playlist_created))
                            close()
                        }
                        is MusicSheet.Rename -> NameEditor(
                            title = stringResource(R.string.playlist_rename),
                            initial = Playlists.get(context).get(sheet.id)?.name.orEmpty(),
                            action = stringResource(R.string.save),
                        ) { name ->
                            Playlists.get(context).rename(sheet.id, name)
                            close()
                        }
                        is MusicSheet.PlaylistOptions -> PlaylistOptions(context, sheet.id, ::close, onPlaylistDeleted)
                    }
                }
            }
        }
    }
}

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

@Composable
private fun SheetHeader(track: VideoItem) {
    Row(Modifier.padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(track.thumbnail, 54.dp, 10.dp)
        Column(Modifier.padding(start = 12.dp)) {
            Text(track.title, color = Pelage.Texte, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (track.channel.isNotBlank()) Text(track.channel, color = Pelage.Gris, fontSize = 12.sp, maxLines = 1)
        }
    }
    Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
}

@Composable
private fun TrackMenu(context: Context, sheet: MusicSheet.Track, close: () -> Unit) {
    val track = sheet.track
    val local = LocalMusic.isLocal(track.url)
    var confirmDelete by remember(track.url) { mutableStateOf(false) }
    LaunchedEffect(confirmDelete) {
        if (confirmDelete) {
            delay(3_000)
            confirmDelete = false
        }
    }
    SheetHeader(track)
    if (!track.isLive) {
        SheetRow(MkIcons.Next, stringResource(R.string.play_next)) {
            PlayerHolder.playNext(track)
            toast(context, context.getString(R.string.play_next_done))
            close()
        }
        SheetRow(MkIcons.Queue, stringResource(R.string.queue_add)) {
            PlayerHolder.addToQueue(track)
            toast(context, context.getString(R.string.queue_added))
            close()
        }
    }
    SheetRow(MkIcons.PlaylistAdd, stringResource(R.string.playlist_add)) { MusicSheet.open.value = MusicSheet.AddToPlaylist(track) }
    if (!local && !track.isLive) {
        SheetRow(MkIcons.Mix, stringResource(R.string.mix_start)) {
            playMix(context, track)
            close()
        }
    }
    if (sheet.playlistId != null) {
        SheetRow(MkIcons.Trash, stringResource(R.string.playlist_remove_track)) {
            Playlists.get(context).remove(sheet.playlistId, sheet.index)
            close()
        }
    }
    if (local) {
        SheetRow(MkIcons.Trash, stringResource(if (confirmDelete) R.string.confirm_touch_again else R.string.file_delete)) {
            if (!confirmDelete) {
                confirmDelete = true
            } else {
                PlayerHolder.launchPlayback { LocalMusic.delete(context, track.url) }
                close()
            }
        }
    } else {
        SheetRow(MkIcons.Share, stringResource(R.string.share)) {
            close()
            shareLink(context, track.title, track.url)
        }
    }
}

@Composable
private fun PlaylistPicker(context: Context, track: VideoItem, close: () -> Unit) {
    val playlists by remember { Playlists.get(context) }.items.collectAsState()
    SheetHeader(track)
    Text(
        stringResource(R.string.playlist_add),
        Modifier.padding(horizontal = 18.dp, vertical = 4.dp).semantics { heading() },
        color = Pelage.Texte,
        fontSize = 16.sp,
        fontWeight = FontWeight.ExtraBold,
    )
    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
        SheetRow(MkIcons.Plus, stringResource(R.string.playlist_new)) { MusicSheet.open.value = MusicSheet.NewPlaylist(track) }
        playlists.forEach { playlist ->
            SheetRow(MkIcons.Music, playlist.name, subtitle = context.resources.getQuantityString(R.plurals.track_count, playlist.items.size, playlist.items.size)) {
                val added = Playlists.get(context).add(playlist.id, track)
                toast(context, context.getString(if (added) R.string.playlist_added_to else R.string.playlist_already_in, playlist.name))
                close()
            }
        }
    }
}

@Composable
private fun PlaylistOptions(context: Context, id: String, close: () -> Unit, onDeleted: (String) -> Unit) {
    val playlist = Playlists.get(context).get(id) ?: return
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(confirm) {
        if (confirm) {
            delay(3_000)
            confirm = false
        }
    }
    Text(playlist.name, Modifier.padding(horizontal = 18.dp, vertical = 8.dp), color = Pelage.Texte, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
    SheetRow(MkIcons.Settings, stringResource(R.string.playlist_rename)) { MusicSheet.open.value = MusicSheet.Rename(id) }
    SheetRow(MkIcons.Trash, stringResource(if (confirm) R.string.confirm_touch_again else R.string.playlist_delete)) {
        if (!confirm) {
            confirm = true
        } else {
            Playlists.get(context).delete(id)
            close()
            onDeleted(id)
        }
    }
}

/** Un champ de texte en verre pour nommer une playlist. */
@Composable
private fun NameEditor(title: String, initial: String, action: String, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun submit() {
        if (name.isNotBlank()) onDone(name)
    }
    Text(title, Modifier.padding(horizontal = 18.dp, vertical = 8.dp).semantics { heading() }, color = Pelage.Texte, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
    Box(
        Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp)).background(Color.White.copy(alpha = 0.1f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = name,
            onValueChange = { name = it.take(60) },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontFamily = Rounded, fontWeight = FontWeight.Bold, shadow = GlassTextShadow),
            cursorBrush = SolidColor(Pelage.RouxClair),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).focusRequester(focus),
            decorationBox = { inner ->
                if (name.isEmpty()) Text(stringResource(R.string.playlist_name_hint), color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                inner()
            },
        )
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier.clip(RoundedCornerShape(22.dp)).background(if (name.isBlank()) Pelage.Roux.copy(alpha = 0.4f) else Pelage.Roux)
                .clickable(enabled = name.isNotBlank(), role = Role.Button) { submit() }
                .padding(horizontal = 22.dp, vertical = 12.dp),
        ) {
            Text(action, color = Pelage.Encre, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Pelage.Texte, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = Pelage.Gris, fontSize = 12.sp)
        }
    }
}
