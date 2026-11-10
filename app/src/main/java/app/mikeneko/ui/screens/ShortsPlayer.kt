package app.mikeneko.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import app.mikeneko.ui.components.HoldSpeedBadge
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.data.ChannelItem
import app.mikeneko.data.Comments
import app.mikeneko.data.ShortsFeed
import app.mikeneko.data.VideoItem
import app.mikeneko.data.VideoLibrary
import app.mikeneko.data.channelIdFrom
import app.mikeneko.data.formatCount
import app.mikeneko.data.sameTrack
import app.mikeneko.player.NowPlaying
import app.mikeneko.player.PlayerHolder
import app.mikeneko.player.ShortsPreload
import app.mikeneko.ui.components.GlassCircleButton
import app.mikeneko.ui.components.Loading
import app.mikeneko.ui.components.VideoSurface
import app.mikeneko.ui.components.shareLink
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.glass.backdropSource
import app.mikeneko.ui.glass.rememberBackdrop
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.GlassTextShadow
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.Rounded
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException

/**
 * Les Shorts (2.2.0) : un short s'ouvre en plein écran, en hauteur, et tourne en boucle. Glisser
 * vers le haut / le bas passe au short suivant / précédent : ceux de la liste d'où on l'a ouvert
 * (onglet Shorts d'une chaîne, recherche, accueil, abonnements), puis les shorts suggérés par YouTube.
 *
 * Les écrans de listes offrent ce qu'ils montrent ([offer]) ; à l'ouverture d'une vidéo, [start]
 * retrouve la liste qui la contient et dit si c'est un short.
 */
object Shorts {
    /** Les dernières listes montrées par les écrans, la plus récente en premier. */
    private val recent = ArrayDeque<List<VideoItem>>()
    private const val MAX_LISTS = 8

    /** Les shorts en cours de lecture (null = pas en mode Shorts). */
    var session by mutableStateOf<Session?>(null)
        private set

    @Stable
    class Session(
        val items: SnapshotStateList<VideoItem>,
        start: Int,
        /** L'onglet Shorts du dock (2.20.0) : rien n'est encore lancé à l'ouverture, et le bouton en haut est un retour. */
        val tab: Boolean = false,
    ) {
        var index by mutableIntStateOf(start)
    }

    /** Un écran montre cette liste : si elle contient des shorts, on la garde pour pouvoir glisser dedans. */
    fun offer(list: List<VideoItem>?) {
        if (list.isNullOrEmpty() || recent.firstOrNull() === list || list.none { it.isShort }) return
        recent.removeAll { it === list }
        recent.addFirst(list)
        while (recent.size > MAX_LISTS) recent.removeLast()
    }

    /**
     * Une vidéo va s'ouvrir : si c'est un short, la session commence (avec les shorts de sa liste
     * d'origine) et on renvoie true.
     */
    fun start(url: String): Boolean {
        val list = recent.firstOrNull { l -> l.any { it.isShort && sameTrack(it.url, url) } }
        if (list == null && "/shorts/" !in url) {
            session = null
            return false
        }
        val shorts = list?.filter { it.isShort }.orEmpty()
        val index = shorts.indexOfFirst { sameTrack(it.url, url) }
        session = if (index >= 0) {
            Session(mutableStateListOf<VideoItem>().apply { addAll(shorts) }, index)
        } else {
            // Un lien /shorts/ partagé : seul au début, les suggestions suivent
            Session(mutableStateListOf(VideoItem(url, "", "", null, isShort = true)), 0)
        }
        return true
    }

    /**
     * La vidéo ouverte dans le lecteur normal s'avère être un short (sa liste ne le savait pas : flux
     * des abonnements, « Pour toi »…) : la session commence avec elle, et les shorts connus de sa liste.
     */
    fun adopt(now: NowPlaying) {
        if (session != null) return
        val list = recent.firstOrNull { l -> l.any { sameTrack(it.url, now.url) } }.orEmpty()
        val shorts = list.filter { it.isShort || sameTrack(it.url, now.url) }
        val index = shorts.indexOfFirst { sameTrack(it.url, now.url) }
        session = if (index >= 0) {
            Session(mutableStateListOf<VideoItem>().apply { addAll(shorts) }, index)
        } else {
            Session(mutableStateListOf(VideoItem(now.url, now.title, now.channel, now.thumbnail, isShort = true)), 0)
        }
    }

    /** L'onglet Shorts s'ouvre sur son fil ([app.mikeneko.data.ShortsFeed]). */
    fun startFeed(list: List<VideoItem>) {
        session = Session(mutableStateListOf<VideoItem>().apply { addAll(list) }, 0, tab = true)
    }

    fun end() {
        session = null
        ShortsPreload.clear()
    }
}

/**
 * L'onglet Shorts du dock (2.20.0) : son fil se charge, puis le lecteur Shorts joue le premier.
 * Quitter l'onglet arrête la lecture (voir MikenekoRoot).
 */
@Composable
fun ShortsTab(onClose: () -> Unit, onOpenChannel: (ChannelItem) -> Unit) {
    val context = LocalContext.current
    var empty by remember { mutableStateOf(false) }
    val ready = Shorts.session?.tab == true
    LaunchedEffect(Unit) {
        if (Shorts.session?.tab == true) return@LaunchedEffect
        val list = ShortsFeed.load(context)
        if (list.isEmpty()) empty = true else Shorts.startFeed(list)
    }
    if (ready) {
        ShortsPlayer(onClose = onClose, onOpenChannel = onOpenChannel)
    } else {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Box(
                Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.statusBars).padding(12.dp).size(44.dp).clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.back), onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(MkIcons.ChevronLeft, stringResource(R.string.back), tint = Color.White, modifier = Modifier.size(26.dp))
            }
            if (empty) {
                Text(stringResource(R.string.shorts_empty), Modifier.padding(32.dp), style = ShortText.copy(fontSize = 15.sp, textAlign = TextAlign.Center))
            } else {
                Loading()
            }
        }
    }
}

/** Le lecteur Shorts, en plein écran par-dessus tout (à la place de la page lecteur). */
@Composable
fun ShortsPlayer(onClose: () -> Unit, onOpenChannel: (ChannelItem) -> Unit) {
    val session = Shorts.session ?: return
    val tab = session.tab
    val context = LocalContext.current
    val items = session.items
    val pager = rememberPagerState(initialPage = session.index) { items.size }
    val now by PlayerHolder.nowPlaying.collectAsState()

    BackHandler(onBack = onClose)

    // Le short posé à l'écran est celui qui joue (en boucle, depuis le début)
    // (le premier, lui, est déjà lancé par l'ouverture : pas de deuxième demande à YouTube)
    // (dans l'onglet Shorts, rien n'est encore lancé : le premier part d'ici)
    val opened = remember { intArrayOf(if (tab) -1 else session.index) }
    // (la clé compte aussi l'adresse : un short retiré laisse sa place au suivant, sur la même page)
    LaunchedEffect(pager.settledPage, items.getOrNull(pager.settledPage)?.url) {
        val item = items.getOrNull(pager.settledPage) ?: return@LaunchedEffect
        session.index = pager.settledPage
        if (pager.settledPage == opened[0]) {
            opened[0] = -1
            return@LaunchedEffect
        }
        opened[0] = -1
        if (sameTrack(PlayerHolder.nowPlaying.value?.url, item.url)) return@LaunchedEffect
        try {
            PlayerHolder.play(context, item.url, audioOnly = false, short = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ContentNotAvailableException) {
            // Un short supprimé ou privé (2.21.0) : il sort de la liste et le suivant prend sa place,
            // au lieu de rester figé sur sa miniature. Le dernier de la liste, lui, le dit.
            if (items.size > 1 && items.getOrNull(pager.settledPage)?.url == item.url) {
                items.removeAt(pager.settledPage)
            } else {
                Toast.makeText(context, context.getString(R.string.error_play_video, e.message.orEmpty()), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.error_play_video, e.message.orEmpty()), Toast.LENGTH_SHORT).show()
        }
    }
    // Les 3 suivants se préparent, les 2 précédents restent prêts (2.20.0)
    LaunchedEffect(pager.settledPage, items.size) { ShortsPreload.window(context, items.toList(), pager.settledPage) }
    // Presque au bout de la liste : les shorts suggérés à partir de celui en cours viennent derrière
    LaunchedEffect(now?.url, pager.settledPage, items.size) {
        val info = now ?: return@LaunchedEffect
        if (pager.settledPage < items.size - 1 - ShortsPreload.AHEAD) return@LaunchedEffect
        if (!sameTrack(info.url, items.getOrNull(pager.settledPage)?.url)) return@LaunchedEffect
        items += info.related.filter { r -> r.isShort && items.none { sameTrack(it.url, r.url) } }
    }
    // Un lien /shorts/ ouvert seul : son titre et sa chaîne arrivent avec la lecture
    LaunchedEffect(now?.url) {
        val info = now ?: return@LaunchedEffect
        val i = items.indexOfFirst { sameTrack(it.url, info.url) }
        if (i >= 0 && items[i].title.isEmpty()) {
            items[i] = items[i].copy(title = info.title, channel = info.channel, thumbnail = info.thumbnail)
        }
    }
    LaunchedEffect(now?.url) { now?.url?.let { Comments.load(it) } }

    VerticalPager(
        state = pager,
        modifier = Modifier.fillMaxSize().background(Color.Black),
        beyondViewportPageCount = 1,
        key = { items.getOrNull(it)?.url ?: it },
    ) { page ->
        val item = items.getOrNull(page) ?: return@VerticalPager
        val playing = page == pager.settledPage && sameTrack(now?.url, item.url)
        ShortPage(item, playing, current = page == pager.settledPage, tab, onClose, onOpenChannel)
    }
}

@Composable
private fun ShortPage(item: VideoItem, playing: Boolean, current: Boolean, tab: Boolean, onClose: () -> Unit, onOpenChannel: (ChannelItem) -> Unit) {
    val context = LocalContext.current
    val video = rememberBackdrop()
    val now by PlayerHolder.nowPlaying.collectAsState()
    val loading by PlayerHolder.loading.collectAsState()
    val buffering by PlayerHolder.buffering.collectAsState()
    val wantsToPlay by PlayerHolder.playRequested.collectAsState()
    val info = now?.takeIf { playing }

    Box(Modifier.fillMaxSize()) {
        // Dessous : la miniature (le temps que la vidéo arrive), puis la vidéo elle-même
        Box(Modifier.fillMaxSize().backdropSource(video)) {
            AsyncImage(item.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (playing) VideoSurface(Modifier.fillMaxSize(), lastFrame = false)
        }

        // Un tap : pause / lecture ; doigt maintenu : ×2 tant qu'il reste posé (2.21.0)
        if (playing) {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            tryAwaitRelease()
                            PlayerHolder.holdSpeed(false)
                        },
                        onLongPress = { PlayerHolder.holdSpeed(true) },
                        onTap = { PlayerHolder.togglePlay() },
                    )
                },
            )
            HoldSpeedBadge(Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(top = 18.dp))
        }
        // (le short à l'écran qui se charge encore : la roue aussi, pas seulement la miniature figée)
        AnimatedVisibility((playing && buffering) || (current && loading), Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
            Loading()
        }
        AnimatedVisibility(playing && !wantsToPlay && !loading, Modifier.align(Alignment.Center), enter = fadeIn() + scaleIn(initialScale = 1.3f), exit = fadeOut()) {
            Icon(MkIcons.Play, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(72.dp))
        }

        // Voiles pour lire le texte sur n'importe quelle vidéo
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(110.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(260.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))),
        )

        GlassCircleButton(
            video,
            if (tab) MkIcons.ChevronLeft else MkIcons.ChevronDown,
            stringResource(if (tab) R.string.back else R.string.player_minimize),
            Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.statusBars).padding(12.dp),
            size = 44.dp,
            onClick = onClose,
        )

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, bottom = 14.dp), verticalAlignment = Alignment.Bottom) {
                // En bas à gauche : la chaîne (vers sa page) et le titre
                Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val channel = info?.channel?.takeIf { it.isNotEmpty() } ?: item.channel
                    Row(
                        Modifier.clip(CircleShape).clickable(enabled = info?.channelUrl != null, role = Role.Button) {
                            info?.channelUrl?.let { url -> onOpenChannel(ChannelItem(url = url, name = info.channel, avatar = info.channelAvatar, channelId = channelIdFrom(url))) }
                        }.padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (info?.channelAvatar != null) {
                            AsyncImage(info.channelAvatar, null, Modifier.size(32.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                            Box(Modifier.size(8.dp))
                        }
                        Text(channel, style = ShortText.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        info?.title?.takeIf { it.isNotEmpty() } ?: item.title,
                        style = ShortText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // À droite : J'aime, commentaires, partager
                if (playing && info != null) ShortActions(video, info.url, info.title, info.channel, info.thumbnail, info.likes, info.channelUrl)
            }
            // Tout en bas : où on en est (fin trait roux)
            if (playing) ShortProgress()
        }
    }
}

private val ShortText = TextStyle(fontFamily = Rounded, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, shadow = GlassTextShadow)

@Composable
private fun ShortActions(video: Backdrop, url: String, title: String, channel: String, thumbnail: String?, likes: Long, channelUrl: String?) {
    val context = LocalContext.current
    val library = remember { VideoLibrary.get(context) }
    val likedList by library.liked.collectAsState()
    val liked = likedList.any { sameTrack(it.url, url) }
    val comments by Comments.state.collectAsState()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ShortAction(
            video,
            if (liked) MkIcons.ThumbUpFilled else MkIcons.ThumbUp,
            stringResource(if (liked) R.string.library_liked_remove else R.string.library_liked_add),
            if (likes > 0) formatCount(likes) else null,
            active = liked,
        ) {
            library.toggleLiked(VideoItem(url, title, channel, thumbnail, channelId = channelUrl?.let(::channelIdFrom), isShort = true))
        }
        ShortAction(video, MkIcons.Bubble, stringResource(R.string.comments_open), if (comments.count > 0) formatCount(comments.count.toLong()) else null) {
            Comments.open.value = true
        }
        // Garder : dans « Tes shorts » de la Bibliothèque (à part des vidéos)
        val savedList by library.shorts.collectAsState()
        val saved = savedList.any { sameTrack(it.url, url) }
        ShortAction(
            video,
            if (saved) MkIcons.BookmarkFilled else MkIcons.Bookmark,
            stringResource(if (saved) R.string.short_unsave else R.string.short_save),
            null,
            active = saved,
        ) {
            val added = library.toggleShort(VideoItem(url, title, channel, thumbnail, channelId = channelUrl?.let(::channelIdFrom), isShort = true))
            Toast.makeText(context, context.getString(if (added) R.string.short_saved else R.string.short_removed), Toast.LENGTH_SHORT).show()
        }
        ShortAction(video, MkIcons.Share, stringResource(R.string.share), null) { shareLink(context, title, url) }
    }
}

@Composable
private fun ShortAction(video: Backdrop, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, count: String?, active: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassCircleButton(video, icon, label, size = 48.dp, tint = if (active) Pelage.Roux.copy(alpha = 0.75f) else Color(0x40100D0B), onClick = onClick)
        if (count != null) Text(count, Modifier.padding(top = 3.dp), style = ShortText.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold))
    }
}

/** La progression du short : un fin trait roux tout en bas, sans poignée (on ne cherche pas dans un short). */
@Composable
private fun ShortProgress() {
    val progress = rememberPlaybackProgress()
    Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.25f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.fraction.coerceIn(0f, 1f)).background(Pelage.Roux))
    }
}
