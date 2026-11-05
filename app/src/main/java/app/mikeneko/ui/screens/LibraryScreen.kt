package app.mikeneko.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.data.Downloads
import app.mikeneko.data.Playlist
import app.mikeneko.data.Playlists
import app.mikeneko.data.VideoItem
import app.mikeneko.data.VideoLibrary
import app.mikeneko.data.WatchHistory
import app.mikeneko.data.formatAgo
import app.mikeneko.data.thumbnailFor
import app.mikeneko.player.PlayerHolder
import app.mikeneko.player.PlaylistDownloads
import app.mikeneko.ui.components.Capsule
import app.mikeneko.ui.components.CompactVideoRow
import app.mikeneko.ui.components.EmptyCat
import app.mikeneko.ui.components.GlassCircleButton
import app.mikeneko.ui.components.WatchProgressBar
import app.mikeneko.ui.components.adaptiveItems
import app.mikeneko.ui.components.columnsFor
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.GlassTextShadow
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.Rounded
import app.mikeneko.ui.theme.jellyClickable
import app.mikeneko.ui.theme.statusBarSpace
import app.mikeneko.ui.theme.statusBarSpacePadding
import coil3.compose.AsyncImage
import java.time.Instant
import kotlinx.coroutines.delay

/** Les pages de la Bibliothèque (null = son accueil). */
sealed interface LibraryPage {
    data object History : LibraryPage
    data object Later : LibraryPage
    data object Liked : LibraryPage
    data class MyPlaylist(val id: String) : LibraryPage
    data object Downloads : LibraryPage
}

/** L'onglet Bibliothèque : la page ouverte (le retour y revient d'abord). */
class LibraryState {
    var page by mutableStateOf<LibraryPage?>(null)
}

/** Les vidéos de l'historique, sous forme de vidéos (la miniature vient de l'identifiant). */
@Composable
private fun historyVideos(): List<VideoItem> {
    val context = LocalContext.current
    val entries by WatchHistory.get(context).entries.collectAsState()
    return remember(entries) {
        entries.map { VideoItem(it.url, it.title, it.channel, thumbnailFor(it.url), channelId = it.channelId, uploaded = formatAgo(Instant.ofEpochMilli(it.at))) }
    }
}

/**
 * Ouvre une vidéo d'une liste de la Bibliothèque : la liste devient la « playlist » du lecteur
 * (tiroir, « suivante », enchaînement automatique).
 */
private fun openFromList(list: List<VideoItem>, title: String, video: VideoItem, onOpen: (VideoItem) -> Unit) {
    PlayerHolder.videoPlaylist.value = list
    PlayerHolder.videoPlaylistTitle.value = title
    onOpen(video)
}

@Composable
fun LibraryContent(state: LibraryState, downloadLink: String, onOpen: (VideoItem) -> Unit) {
    when (val page = state.page) {
        null -> LibraryHome(state, onOpen)
        LibraryPage.Downloads -> DownloadsContent(downloadLink, top = 72.dp)
        LibraryPage.History -> HistoryPage(onOpen)
        LibraryPage.Later -> LaterPage(onOpen)
        LibraryPage.Liked -> LikedPage(onOpen)
        is LibraryPage.MyPlaylist -> MyVideoPlaylistPage(page.id, state, onOpen)
    }
}

/** L'en-tête en verre d'une page de la Bibliothèque : retour et titre. */
@Composable
fun LibraryOverlay(state: LibraryState, backdrop: Backdrop) {
    val page = state.page ?: return
    val context = LocalContext.current
    val playlists by Playlists.videos(context).items.collectAsState()
    val title = when (page) {
        LibraryPage.History -> stringResource(R.string.library_history)
        LibraryPage.Later -> stringResource(R.string.library_later)
        LibraryPage.Liked -> stringResource(R.string.library_liked)
        LibraryPage.Downloads -> "" // la page a déjà son grand titre
        is LibraryPage.MyPlaylist -> playlists.firstOrNull { it.id == page.id }?.name.orEmpty()
    }
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBarSpace).padding(start = 16.dp, end = 16.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassCircleButton(backdrop, MkIcons.ChevronLeft, stringResource(R.string.back)) { state.page = null }
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            Modifier.weight(1f),
            style = TextStyle(fontFamily = Rounded, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, shadow = GlassTextShadow),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LibraryHome(state: LibraryState, onOpen: (VideoItem) -> Unit) {
    val context = LocalContext.current
    val library = remember { VideoLibrary.get(context) }
    val later by library.later.collectAsState()
    val liked by library.liked.collectAsState()
    val playlistStore = remember { Playlists.videos(context) }
    val playlists by playlistStore.items.collectAsState()
    val tasks by Downloads.tasks.collectAsState()
    val history = historyVideos()
    var creating by remember { mutableStateOf(false) }
    val historyTitle = stringResource(R.string.library_history)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 200.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "titre") {
            Text(
                stringResource(R.string.library_title),
                Modifier.statusBarSpacePadding().padding(start = 20.dp, end = 20.dp, top = 30.dp),
                color = Pelage.Texte,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        // L'historique : les dernières vidéos en rangée, « Tout voir » pour la liste
        item(key = "historique") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(historyTitle, action = if (history.isNotEmpty()) stringResource(R.string.see_all) else null) { state.page = LibraryPage.History }
                if (history.isEmpty()) {
                    Text(stringResource(R.string.library_history_empty), Modifier.padding(horizontal = 20.dp), color = Pelage.Gris, fontSize = 13.sp)
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(history.take(15), key = { it.url }) { video ->
                            SmallVideoCard(video) { onOpen(video) }
                        }
                    }
                }
            }
        }

        item(key = "listes") {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LibraryTile(MkIcons.Clock, stringResource(R.string.library_later), pluralStringResource(R.plurals.videos_count, later.size, later.size)) { state.page = LibraryPage.Later }
                LibraryTile(MkIcons.ThumbUp, stringResource(R.string.library_liked), pluralStringResource(R.plurals.videos_count, liked.size, liked.size)) { state.page = LibraryPage.Liked }
                val running = tasks.count { it.status == Downloads.Status.EN_COURS || it.status == Downloads.Status.EN_ATTENTE }
                LibraryTile(
                    MkIcons.Download,
                    stringResource(R.string.downloads_title),
                    if (running > 0) stringResource(R.string.library_downloads_running, running) else stringResource(R.string.library_downloads_text),
                ) { state.page = LibraryPage.Downloads }
            }
        }

        // Les playlists de vidéos
        item(key = "playlists_titre") {
            SectionTitle(stringResource(R.string.channel_tab_playlists), action = stringResource(R.string.playlist_new)) { creating = !creating }
        }
        if (creating) {
            item(key = "nouvelle") {
                NewPlaylistField(Modifier.padding(horizontal = 20.dp)) { name ->
                    playlistStore.create(name)
                    creating = false
                }
            }
        }
        if (playlists.isEmpty() && !creating) {
            item(key = "aucune") {
                Text(stringResource(R.string.library_playlists_empty), Modifier.padding(horizontal = 20.dp), color = Pelage.Gris, fontSize = 13.sp)
            }
        }
        items(playlists, key = { it.id }) { playlist ->
            PlaylistTile(playlist, Modifier.padding(horizontal = 20.dp)) { state.page = LibraryPage.MyPlaylist(playlist.id) }
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = Pelage.Texte, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        if (action != null) {
            Text(
                action,
                Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onAction).padding(horizontal = 10.dp, vertical = 8.dp),
                color = Pelage.RouxClair,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

/** Une petite carte (historique) : miniature et titre. */
@Composable
private fun SmallVideoCard(video: VideoItem, onClick: () -> Unit) {
    Column(Modifier.width(168.dp).jellyClickable(role = null, strength = 0.35f, onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(Pelage.Coussin)) {
            AsyncImage(
                video.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            WatchProgressBar(video.url, Modifier.align(Alignment.BottomCenter))
        }
        Text(video.title, color = Pelage.Texte, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
        Text(video.channel, color = Pelage.Gris, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Une ligne de la Bibliothèque : icône, titre, détail, flèche. */
@Composable
private fun LibraryTile(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Pelage.Coussin)
            .border(1.5.dp, Pelage.Bord, RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Pelage.Roux.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Pelage.RouxClair, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = Pelage.Texte, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text(detail, color = Pelage.Gris, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(MkIcons.ChevronRight, null, tint = Pelage.Gris, modifier = Modifier.size(20.dp))
    }
}

/** Une playlist perso : miniature de sa première vidéo, nom, nombre de vidéos. */
@Composable
private fun PlaylistTile(playlist: Playlist, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().jellyClickable(role = null, strength = 0.35f, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(140.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Pelage.Coussin), contentAlignment = Alignment.Center) {
            val cover = playlist.items.firstOrNull()?.thumbnail
            if (cover != null) {
                AsyncImage(cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(MkIcons.PlaylistAdd, null, tint = Pelage.Gris, modifier = Modifier.size(28.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(playlist.name, color = Pelage.Texte, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(pluralStringResource(R.plurals.videos_count, playlist.items.size, playlist.items.size), color = Pelage.Gris, fontSize = 13.sp)
        }
    }
}

/** Le champ « nom de la nouvelle playlist » et le bouton Créer. Le clavier s'ouvre tout de suite. */
@Composable
fun NewPlaylistField(modifier: Modifier = Modifier, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun create() {
        if (name.isNotBlank()) onCreate(name.trim())
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(Pelage.Nuit).border(1.5.dp, Pelage.Bord, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = TextStyle(color = Pelage.Texte, fontSize = 15.sp, fontFamily = Rounded, fontWeight = FontWeight.Bold),
                cursorBrush = SolidColor(Pelage.RouxClair),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { create() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                decorationBox = { inner ->
                    if (name.isEmpty()) Text(stringResource(R.string.playlist_name_hint), color = Pelage.Gris, fontSize = 15.sp)
                    inner()
                },
            )
        }
        Capsule(stringResource(R.string.playlist_create), selected = name.isNotBlank()) { create() }
    }
}

/** Une liste de vidéos de la Bibliothèque, avec un ✕ pour retirer chacune. */
@Composable
private fun VideoListPage(
    videos: List<VideoItem>,
    title: String,
    empty: String,
    onOpen: (VideoItem) -> Unit,
    header: @Composable () -> Unit = {},
    onRemove: ((Int, VideoItem) -> Unit)?,
) {
    val rowColumns = columnsFor(330.dp) // une colonne en portrait, deux en paysage
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 200.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "haut") { Spacer(Modifier.statusBarSpacePadding().height(72.dp)) }
        item(key = "entete") { header() }
        if (videos.isEmpty()) {
            item(key = "vide") { EmptyCat(stringResource(R.string.nothing_found), empty, Modifier.padding(top = 30.dp)) }
        }
        adaptiveItems(videos.withIndex().toList(), rowColumns, key = { it.value.url }, horizontalPadding = 0.dp) { (index, video), modifier ->
            Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { CompactVideoRow(video) { openFromList(videos, title, video, onOpen) } }
                if (onRemove != null) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = stringResource(R.string.library_remove)) { onRemove(index, video) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(MkIcons.Close, stringResource(R.string.library_remove), tint = Pelage.Gris, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryPage(onOpen: (VideoItem) -> Unit) {
    val context = LocalContext.current
    val history = historyVideos()
    var confirming by remember { mutableStateOf(false) }
    LaunchedEffect(confirming) {
        if (confirming) {
            delay(4_000)
            confirming = false
        }
    }
    val historyColumns = columnsFor(330.dp) // une colonne en portrait, deux en paysage
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 200.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "haut") { Spacer(Modifier.statusBarSpacePadding().height(72.dp)) }
        if (history.isNotEmpty()) {
            item(key = "effacer") {
                // Effacer : un premier tap demande confirmation (4 s), le second efface
                Capsule(
                    stringResource(if (confirming) R.string.unsubscribe_confirm else R.string.settings_history_clear),
                    selected = confirming,
                    icon = if (confirming) MkIcons.Check else MkIcons.Trash,
                ) {
                    if (confirming) {
                        WatchHistory.get(context).clear()
                        confirming = false
                    } else {
                        confirming = true
                    }
                }
            }
        } else {
            item(key = "vide") { EmptyCat(stringResource(R.string.nothing_found), stringResource(R.string.library_history_empty), Modifier.padding(top = 30.dp)) }
        }
        adaptiveItems(history, historyColumns, key = { it.url }, horizontalPadding = 0.dp) { video, modifier ->
            Box(modifier) { CompactVideoRow(video) { onOpen(video) } }
        }
    }
}

@Composable
private fun LaterPage(onOpen: (VideoItem) -> Unit) {
    val context = LocalContext.current
    val library = remember { VideoLibrary.get(context) }
    val later by library.later.collectAsState()
    val title = stringResource(R.string.library_later)
    VideoListPage(
        later, title, stringResource(R.string.library_later_empty), onOpen,
        header = { PlayButtons(later, title, onOpen) },
        onRemove = { _, video -> library.removeLater(video.url) },
    )
}

@Composable
private fun LikedPage(onOpen: (VideoItem) -> Unit) {
    val context = LocalContext.current
    val library = remember { VideoLibrary.get(context) }
    val liked by library.liked.collectAsState()
    val title = stringResource(R.string.library_liked)
    VideoListPage(
        liked, title, stringResource(R.string.library_liked_empty), onOpen,
        header = { PlayButtons(liked, title, onOpen) },
        onRemove = { _, video -> library.removeLiked(video.url) },
    )
}

@Composable
private fun MyVideoPlaylistPage(id: String, state: LibraryState, onOpen: (VideoItem) -> Unit) {
    val context = LocalContext.current
    val store = remember { Playlists.videos(context) }
    val playlists by store.items.collectAsState()
    val playlist = playlists.firstOrNull { it.id == id }
    if (playlist == null) {
        LaunchedEffect(Unit) { state.page = null } // supprimée
        return
    }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(confirmDelete) {
        if (confirmDelete) {
            delay(4_000)
            confirmDelete = false
        }
    }
    VideoListPage(
        playlist.items, playlist.name, stringResource(R.string.library_playlist_empty), onOpen,
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (playlist.items.isNotEmpty()) {
                        PlayAllButton { openFromList(playlist.items, playlist.name, playlist.items.first(), onOpen) }
                        ShuffleVideosButton(playlist.items, playlist.name, onOpen)
                    }
                    PlaylistDownloadCapsule(PlaylistDownloads.videos, id, playlist.items)
                    Capsule(stringResource(R.string.playlist_rename)) { renaming = !renaming }
                    // Supprimer : un premier tap demande confirmation (4 s)
                    Capsule(
                        stringResource(if (confirmDelete) R.string.unsubscribe_confirm else R.string.playlist_delete),
                        selected = confirmDelete,
                        icon = MkIcons.Trash,
                    ) {
                        if (confirmDelete) {
                            store.delete(id)
                            state.page = null
                        } else {
                            confirmDelete = true
                        }
                    }
                }
                if (renaming) {
                    NewPlaylistField { name ->
                        store.rename(id, name)
                        renaming = false
                    }
                }
            }
        },
        onRemove = { index, _ -> store.remove(id, index) },
    )
}

/** « Tout lire » et « Aléatoire », côte à côte (rien quand la liste est vide). */
@Composable
private fun PlayButtons(list: List<VideoItem>, title: String, onOpen: (VideoItem) -> Unit) {
    if (list.isEmpty()) return
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlayAllButton { openFromList(list, title, list.first(), onOpen) }
        ShuffleVideosButton(list, title, onOpen)
    }
}

/** Aléatoire : la liste mélangée devient la playlist en cours (le tiroir la montre dans ce nouvel ordre). */
@Composable
private fun ShuffleVideosButton(list: List<VideoItem>, title: String, onOpen: (VideoItem) -> Unit) {
    Capsule(stringResource(R.string.shuffle), icon = MkIcons.Shuffle) {
        val mixed = list.shuffled()
        openFromList(mixed, title, mixed.first(), onOpen)
    }
}

@Composable
private fun PlayAllButton(onClick: () -> Unit) {
    Capsule(stringResource(R.string.play_all), selected = true, icon = MkIcons.Play, onClick = onClick)
}
