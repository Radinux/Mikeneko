package app.mikeneko.ui.screens

import app.mikeneko.ui.components.columnsFor
import app.mikeneko.ui.components.adaptiveItems
import app.mikeneko.ui.components.Tag
import androidx.compose.ui.res.pluralStringResource
import app.mikeneko.player.PlayerHolder
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.DisposableEffect
import app.mikeneko.data.PlaylistItem
import androidx.compose.ui.layout.layout
import app.mikeneko.data.formatViews
import app.mikeneko.ui.theme.jellyClickable
import androidx.compose.material3.Icon
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.aspectRatio
import app.mikeneko.data.channelTabKind
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.Page
import androidx.compose.runtime.mutableStateMapOf
import app.mikeneko.ui.theme.statusBarSpace
import app.mikeneko.ui.theme.statusBarSpacePadding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import app.mikeneko.data.ChannelPage
import app.mikeneko.data.Subscription
import app.mikeneko.data.SubscriptionStore
import app.mikeneko.data.VideoItem
import app.mikeneko.data.YouTubeRepository
import app.mikeneko.data.formatCount
import app.mikeneko.ui.components.Capsule
import app.mikeneko.ui.components.EmptyCat
import app.mikeneko.ui.components.GlassCircleButton
import app.mikeneko.ui.components.Loading
import app.mikeneko.ui.components.VideoCard
import app.mikeneko.ui.components.VideoMenu
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.GlassTextShadow
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.Rounded
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException

/** « 1,2 M abonnés », ou rien si YouTube ne le dit pas. */
@Composable
private fun subscribersText(count: Long): String? =
    formatCount(count).ifEmpty { null }?.let { stringResource(R.string.subscribers_count, it) }

/** En haut des résultats de recherche : les chaînes qui correspondent, en rangée. */
@Composable
fun ChannelsRow(channels: List<ChannelItem>, onOpen: (ChannelItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.channels_title),
            Modifier.padding(horizontal = 20.dp),
            color = Pelage.Texte,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(channels, key = { it.url }) { channel ->
                Column(
                    Modifier
                        .width(114.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                        .clickable(role = Role.Button) { onOpen(channel) }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Avatar(channel.avatar, 76.dp)
                    Text(
                        channel.name,
                        color = Pelage.Texte,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp,
                    )
                    subscribersText(channel.subscribers)?.let {
                        Text(it, color = Pelage.Gris, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun Avatar(url: String?, size: androidx.compose.ui.unit.Dp) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(size).clip(CircleShape).background(Pelage.Coussin).border(2.dp, Pelage.Creme, CircleShape),
    )
}

/** La page d'une chaîne : ses infos, le bouton s'abonner, ses dernières vidéos (la suite en défilant). */
@Composable
fun ChannelContent(channel: ChannelItem, onOpen: (VideoItem) -> Unit) {
    val context = LocalContext.current
    val store = remember { SubscriptionStore.get(context) }
    val subscriptions by store.items.collectAsState()

    var page by remember(channel.url) { mutableStateOf<ChannelPage?>(null) }
    var failed by remember(channel.url) { mutableStateOf(false) }
    /** L'onglet affiché (ChannelTabs.VIDEOS, SHORTS, LIVESTREAMS). */
    var tab by remember(channel.url) { mutableStateOf(ChannelTabs.VIDEOS) }
    // Par onglet : les vidéos déjà chargées et de quoi charger la suite
    val tabVideos = remember(channel.url) { mutableStateMapOf<String, List<VideoItem>>() }
    val tabNext = remember(channel.url) { mutableStateMapOf<String, Page>() }
    val tabFailed = remember(channel.url) { mutableStateMapOf<String, Boolean>() }
    var loadingMore by remember(channel.url) { mutableStateOf(false) }
    // L'onglet Playlists (des playlists, pas des vidéos)
    var playlists by remember(channel.url) { mutableStateOf<List<PlaylistItem>?>(null) }
    var playlistsNext by remember(channel.url) { mutableStateOf<Page?>(null) }
    val list = rememberLazyListState()
    // Une playlist ouverte s'affiche à la place de la chaîne ; elle se referme avec la chaîne
    val openPlaylist by ChannelPlaylist.open.collectAsState()
    DisposableEffect(channel.url) { onDispose { ChannelPlaylist.open.value = null } }

    LaunchedEffect(channel.url) {
        val loaded = runCatching { YouTubeRepository.channel(channel.url) }.getOrNull()
        if (loaded == null) {
            failed = true
        } else {
            tabVideos[ChannelTabs.VIDEOS] = loaded.videos
            // Une chaîne suivie sans image (importée) : elle reçoit celle-ci au passage
            app.mikeneko.data.SubscriptionStore.get(context).setAvatar(loaded.id, loaded.avatar)
            loaded.next?.let { tabNext[ChannelTabs.VIDEOS] = it }
            // Une chaîne sans onglet Vidéos (que des shorts…) : on ouvre son premier onglet
            if (loaded.tabs.none { it.channelTabKind() == ChannelTabs.VIDEOS }) {
                loaded.tabs.firstOrNull()?.channelTabKind()?.let { tab = it }
            }
            page = loaded
        }
    }
    fun handlerOf(kind: String) = page?.tabs?.firstOrNull { it.channelTabKind() == kind }

    // Un autre onglet : chargé la première fois qu'on l'ouvre, puis gardé
    LaunchedEffect(page, tab) {
        val loaded = page ?: return@LaunchedEffect
        if (tab == ChannelTabs.PLAYLISTS) {
            if (playlists != null) return@LaunchedEffect
            val handler = handlerOf(tab) ?: return@LaunchedEffect
            val result = runCatching { YouTubeRepository.channelPlaylists(handler) }.getOrNull()
            if (result == null) {
                tabFailed[tab] = true
            } else {
                playlists = result.first
                playlistsNext = result.second
            }
            return@LaunchedEffect
        }
        if (tab in tabVideos) return@LaunchedEffect
        val handler = handlerOf(tab) ?: return@LaunchedEffect
        val result = runCatching { YouTubeRepository.channelTab(handler, loaded.name) }.getOrNull()
        if (result == null) {
            tabFailed[tab] = true
        } else {
            tabVideos[tab] = result.first
            result.second?.let { tabNext[tab] = it }
        }
    }

    val nearEnd by remember {
        derivedStateOf {
            val info = list.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(channel.url, tab) {
        snapshotFlow { nearEnd to (if (tab == ChannelTabs.PLAYLISTS) playlistsNext else tabNext[tab]) }.collect { (near, more) ->
            val loaded = page
            val handler = handlerOf(tab)
            if (!near || more == null || loaded == null || handler == null) return@collect
            loadingMore = true
            if (tab == ChannelTabs.PLAYLISTS) {
                val result = runCatching { YouTubeRepository.channelPlaylistsMore(handler, more) }.getOrNull()
                playlistsNext = result?.second
                result?.let { playlists = (playlists.orEmpty() + it.first).distinctBy { p -> p.url } }
                loadingMore = false
                return@collect
            }
            val result = try {
                YouTubeRepository.channelMore(handler, loaded.name, more)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            tabNext.remove(tab)
            if (result != null) {
                tabVideos[tab] = (tabVideos[tab].orEmpty() + result.first).distinctBy { it.url }
                result.second?.let { tabNext[tab] = it }
            }
            loadingMore = false
        }
    }

    openPlaylist?.let {
        PlaylistContent(it, onOpen)
        return
    }

    val cardColumns = columnsFor(300.dp) // une colonne en portrait, plusieurs en paysage
    val shortColumns = columnsFor(150.dp).coerceAtLeast(2)
    val rowColumns = columnsFor(330.dp)
    LazyColumn(
        Modifier.fillMaxSize(),
        state = list,
        contentPadding = PaddingValues(bottom = 200.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "haut") { Spacer(Modifier.statusBarSpacePadding().height(72.dp)) }
        item(key = "chaine") {
            val info = page
            val id = info?.id ?: channel.channelId
            val subscribed = id != null && subscriptions.any { it.channelId == id }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // La bannière, puis l'avatar qui déborde un peu dessus
                val banner = info?.banner
                if (banner != null) {
                    AsyncImage(
                        model = banner,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(4f).clip(RoundedCornerShape(18.dp)).background(Pelage.Coussin),
                    )
                }
                Box(if (banner != null) Modifier.overlapAbove(42.dp) else Modifier) {
                    Avatar(info?.avatar ?: channel.avatar, 104.dp)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(info?.name ?: channel.name, color = Pelage.Texte, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                        if (info?.verified == true || channel.verified) {
                            Box(
                                Modifier.padding(start = 8.dp).size(20.dp).clip(CircleShape).background(Pelage.Gris),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(MkIcons.Check, stringResource(R.string.channel_verified), tint = Pelage.Encre, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    subscribersText(info?.subscribers ?: channel.subscribers)?.let { Text(it, color = Pelage.Gris, fontSize = 13.sp) }
                    if (id != null) {
                        Capsule(
                            stringResource(if (subscribed) R.string.subscribed else R.string.subscribe),
                            selected = subscribed,
                            icon = if (subscribed) MkIcons.Check else MkIcons.Plus,
                        ) {
                            if (subscribed) {
                                store.remove(id)
                            } else {
                                store.addAll(listOf(Subscription(info?.name ?: channel.name, "https://www.youtube.com/channel/$id", id, info?.avatar ?: channel.avatar)))
                            }
                        }
                    }
                    info?.description?.takeIf { it.isNotBlank() }?.let { ChannelDescription(it) }
                }
            }
        }
        // Les onglets (seulement s'il y en a plusieurs)
        val tabs = page?.tabs.orEmpty().mapNotNull { it.channelTabKind() }
        if (tabs.size > 1) {
            item(key = "onglets") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(tabs, key = { it }) { kind ->
                        Capsule(stringResource(channelTabLabel(kind)), selected = tab == kind) { tab = kind }
                    }
                }
            }
        }
        val shown = tabVideos[tab]
        when {
            failed -> item { EmptyCat(stringResource(R.string.nothing_found), stringResource(R.string.channel_failed), Modifier.padding(top = 40.dp)) }
            tabFailed[tab] == true -> item(key = "rate") { EmptyCat(stringResource(R.string.nothing_found), stringResource(R.string.channel_failed), Modifier.padding(top = 20.dp)) }
            page == null -> item(key = "charge") { Loading(Modifier.padding(top = 40.dp)) }
            tab == ChannelTabs.PLAYLISTS -> {
                val found = playlists
                when {
                    found == null -> item(key = "charge") { Loading(Modifier.padding(top = 40.dp)) }
                    found.isEmpty() -> item(key = "vide") { EmptyCat(stringResource(R.string.nothing_found), stringResource(R.string.nothing_found_text), Modifier.padding(top = 20.dp)) }
                    else -> adaptiveItems(found, rowColumns, key = { it.url }, horizontalPadding = 0.dp) { playlist, modifier ->
                        PlaylistRow(playlist, modifier) { ChannelPlaylist.open.value = playlist }
                    }
                }
            }
            shown == null -> item(key = "charge") { Loading(Modifier.padding(top = 40.dp)) }
            shown.isEmpty() -> item(key = "vide") { EmptyCat(stringResource(R.string.nothing_found), stringResource(R.string.nothing_found_text), Modifier.padding(top = 20.dp)) }
            tab == ChannelTabs.SHORTS -> {
                // Les shorts en grille, en hauteur (deux en portrait, plus en paysage)
                adaptiveItems(shown, shortColumns, key = { it.url }, spacing = 12.dp) { video, modifier ->
                    ShortCard(video, modifier) { onOpen(video) }
                }
            }
            else -> adaptiveItems(shown, cardColumns, key = { it.url }) { video, modifier ->
                VideoCard(video, modifier, onMore = { VideoMenu.open(video) }) { onOpen(video) }
            }
        }
        if (loadingMore) item(key = "suite") { Loading(Modifier.size(64.dp)) }
    }
}

/** Remonte l'élément de [by] sur ce qui est au-dessus (l'avatar sur la bannière), sans laisser de trou dessous. */
private fun Modifier.overlapAbove(by: androidx.compose.ui.unit.Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = by.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) { placeable.place(0, -shift) }
}

private fun channelTabLabel(kind: String) = when (kind) {
    ChannelTabs.SHORTS -> R.string.channel_tab_shorts
    ChannelTabs.LIVESTREAMS -> R.string.channel_tab_live
    ChannelTabs.PLAYLISTS -> R.string.channel_tab_playlists
    else -> R.string.channel_tab_videos
}

/** La playlist ouverte depuis une page de chaîne (null = la chaîne). Le retour et l'en-tête s'en servent. */
object ChannelPlaylist {
    val open = MutableStateFlow<PlaylistItem?>(null)
}

/** Une playlist dans l'onglet Playlists : miniature (avec le nombre de vidéos), titre. */
@Composable
private fun PlaylistRow(playlist: PlaylistItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp).jellyClickable(role = null, strength = 0.35f, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(168.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(Pelage.Coussin)) {
            AsyncImage(playlist.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (playlist.count >= 0) {
                Tag(pluralStringResource(R.plurals.videos_count, playlist.count.toInt(), playlist.count.toInt()), Modifier.align(Alignment.BottomEnd).padding(8.dp))
            }
        }
        Text(playlist.name, Modifier.weight(1f), color = Pelage.Texte, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** La page d'une playlist : en-tête, « Tout lire », et ses vidéos (la suite en défilant). */
@Composable
private fun PlaylistContent(playlist: PlaylistItem, onOpen: (VideoItem) -> Unit) {
    var videos by remember(playlist.url) { mutableStateOf<List<VideoItem>?>(null) }
    var next by remember(playlist.url) { mutableStateOf<Page?>(null) }
    var failed by remember(playlist.url) { mutableStateOf(false) }
    var loadingMore by remember(playlist.url) { mutableStateOf(false) }
    val list = rememberLazyListState()

    LaunchedEffect(playlist.url) {
        runCatching { YouTubeRepository.playlist(playlist.url) }
            .onSuccess {
                videos = it.first
                next = it.second
            }
            .onFailure { failed = true }
    }
    val nearEnd by remember {
        derivedStateOf {
            val info = list.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(playlist.url) {
        snapshotFlow { nearEnd to next }.collect { (near, more) ->
            if (!near || more == null) return@collect
            loadingMore = true
            val result = runCatching { YouTubeRepository.playlistMore(playlist.url, more) }.getOrNull()
            next = result?.second
            result?.let { videos = (videos.orEmpty() + it.first).distinctBy { v -> v.url } }
            // Une vidéo de cette playlist est en cours : l'enchaînement connaît aussi la suite chargée
            val playing = PlayerHolder.videoPlaylist.value
            val all = videos.orEmpty()
            if (playing.isNotEmpty() && playing.first().url == all.firstOrNull()?.url) PlayerHolder.videoPlaylist.value = all
            loadingMore = false
        }
    }
    /** Une vidéo de la playlist : « suivante » dans le lecteur suivra l'ordre de la playlist. */
    fun open(video: VideoItem) {
        PlayerHolder.videoPlaylist.value = videos.orEmpty()
        PlayerHolder.videoPlaylistTitle.value = playlist.name
        onOpen(video)
    }

    val cardColumns = columnsFor(300.dp) // une colonne en portrait, plusieurs en paysage
    val shortColumns = columnsFor(150.dp).coerceAtLeast(2)
    val rowColumns = columnsFor(330.dp)
    LazyColumn(
        Modifier.fillMaxSize(),
        state = list,
        contentPadding = PaddingValues(bottom = 200.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "haut") { Spacer(Modifier.statusBarSpacePadding().height(72.dp)) }
        item(key = "playlist") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AsyncImage(
                    playlist.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(22.dp)).background(Pelage.Coussin),
                )
                Text(playlist.name, color = Pelage.Texte, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                val count = playlist.count.takeIf { it >= 0 }?.toInt()
                Text(
                    listOfNotNull(playlist.uploader.ifEmpty { null }, count?.let { pluralStringResource(R.plurals.videos_count, it, it) }).joinToString(" · "),
                    color = Pelage.Gris,
                    fontSize = 13.sp,
                )
                videos?.firstOrNull()?.let { first ->
                    Capsule(stringResource(R.string.play_all), selected = true, icon = MkIcons.Play) { open(first) }
                }
            }
        }
        val shown = videos
        when {
            failed -> item(key = "rate") { EmptyCat(stringResource(R.string.nothing_found), stringResource(R.string.playlist_failed), Modifier.padding(top = 20.dp)) }
            shown == null -> item(key = "charge") { Loading(Modifier.padding(top = 40.dp)) }
            shown.isEmpty() -> item(key = "vide") { EmptyCat(stringResource(R.string.nothing_found), stringResource(R.string.nothing_found_text), Modifier.padding(top = 20.dp)) }
            else -> adaptiveItems(shown, cardColumns, key = { it.url }) { video, modifier ->
                VideoCard(video, modifier, onMore = { VideoMenu.open(video) }) { open(video) }
            }
        }
        if (loadingMore) item(key = "suite") { Loading(Modifier.size(64.dp)) }
    }
}

/** La description de la chaîne : 3 lignes, et un tap pour tout lire (ou replier). */
@Composable
private fun ChannelDescription(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = overflows || expanded, role = Role.Button) { expanded = !expanded }
            .animateContentSize()
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            color = Color(0xFFCFC6BD),
            fontSize = 13.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) {
            Text(
                stringResource(if (expanded) R.string.show_less else R.string.show_more),
                Modifier.padding(top = 4.dp),
                color = Pelage.RouxClair,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

/** Un short : miniature en hauteur, titre et vues. */
@Composable
private fun ShortCard(video: VideoItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.jellyClickable(role = null, strength = 0.35f, onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AsyncImage(
            model = video.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(18.dp)).background(Pelage.Coussin),
        )
        Text(video.title, color = Pelage.Texte, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        formatViews(video.views)?.let { Text(it, color = Pelage.Gris, fontSize = 12.sp, maxLines = 1) }
    }
}

/** L'en-tête en verre d'une chaîne : retour et nom. */
@Composable
fun ChannelOverlay(channel: ChannelItem, backdrop: Backdrop, onBack: () -> Unit) {
    val playlist by ChannelPlaylist.open.collectAsState()
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBarSpace).padding(start = 16.dp, end = 16.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassCircleButton(backdrop, MkIcons.ChevronLeft, stringResource(R.string.back)) {
            if (ChannelPlaylist.open.value != null) ChannelPlaylist.open.value = null else onBack()
        }
        Spacer(Modifier.width(12.dp))
        Text(
            playlist?.name ?: channel.name,
            Modifier.weight(1f),
            style = TextStyle(fontFamily = Rounded, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, shadow = GlassTextShadow),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
