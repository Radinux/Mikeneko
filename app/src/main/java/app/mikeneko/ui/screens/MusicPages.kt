package app.mikeneko.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.mikeneko.R
import app.mikeneko.data.ChannelItem
import app.mikeneko.data.LocalMusic
import app.mikeneko.data.Music
import app.mikeneko.data.MusicCollection
import app.mikeneko.data.MusicKind
import app.mikeneko.data.MusicLibrary
import app.mikeneko.data.Playlists
import app.mikeneko.data.VideoItem
import app.mikeneko.data.sameTrack
import app.mikeneko.player.PlayerHolder
import app.mikeneko.player.PlaylistDownloads
import app.mikeneko.ui.components.Capsule
import app.mikeneko.ui.components.EmptyCat
import app.mikeneko.ui.components.Loading
import app.mikeneko.ui.components.coverModel
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.statusBarSpace

/** Place laissée en bas pour le dock et le dock musique. */
internal val BOTTOM_SPACE = 200.dp

/** Le haut des pages sous la barre en verre (retour / recherche). */
@Composable
internal fun pageTop() = WindowInsets.statusBarSpace.asPaddingValues().calculateTopPadding() + 74.dp

/** La page Musique : l'accueil (ta musique, favoris, lives par ambiance), la recherche ou une page ouverte. */
@Composable
fun MusicContent(state: MusicState, onPlayStations: (List<VideoItem>, Int) -> Unit) {
    when (val page = state.page) {
        null -> if (state.searching) MusicSearchContent(state) else MusicHome(state, onPlayStations)
        is MusicPage.Album -> AlbumContent(state, page.collection)
        is MusicPage.Artist -> ArtistContent(state, page.artist)
        is MusicPage.MyPlaylist -> MyPlaylistContent(state, page.id)
        MusicPage.Downloads -> DownloadsLibraryContent(state)
        MusicPage.Library -> MusicLibraryContent(state)
    }
}

// ---------------------------------------------------------------------------------------------
// Album / playlist de YouTube Music
// ---------------------------------------------------------------------------------------------

@Composable
private fun AlbumContent(state: MusicState, collection: MusicCollection) {
    val context = LocalContext.current
    val now by PlayerHolder.nowPlaying.collectAsState()
    val tracks by produceState(initialValue = Music.cachedAlbum(collection.url)?.tracks, collection.url) {
        value = Music.cachedAlbum(collection.url)?.tracks
        if (value != null) return@produceState
        val first = runCatching { Music.album(collection) }.getOrNull()
        value = first?.tracks.orEmpty()
        // Les longues playlists : la suite, jusqu'à ~300 morceaux
        var next = first?.next
        var pages = 0
        while (next != null && pages < 3) {
            val more = runCatching { Music.albumMore(collection, next) }.getOrNull() ?: break
            value = (value.orEmpty() + more.tracks).distinctBy { it.url }
            next = more.next
            pages++
        }
    }
    val scroll = rememberLazyListState()
    val play = PageBarEffect(state, scroll, collection.title, playInOrder(context, tracks.orEmpty()))
    LazyColumn(Modifier.fillMaxSize(), state = scroll, contentPadding = PaddingValues(bottom = BOTTOM_SPACE)) {
        item(key = "entete") {
            // Une fois les morceaux chargés : leur nombre et la durée totale
            val subtitle = tracks?.takeIf { it.isNotEmpty() }?.let { listOfNotNull(collection.artist.ifBlank { null }, tracksSummary(it)).joinToString(" · ") }
                ?: collectionSubtitle(collection)
            PageHeader(collection.title, subtitle, collection.thumbnail, icon = MkIcons.Album, onPlay = playInOrder(context, tracks.orEmpty()), play = play) {
                SaveAlbumButton(collection)
                ShuffleButton(tracks.orEmpty())
            }
        }
        results(tracks) { list ->
            itemsIndexed(list, key = { i, it -> "$i:" + it.url }) { index, track ->
                // Pas de numéro devant les morceaux : au-delà de 99 il était coupé
                TrackRow(
                    track,
                    playing = sameTrack(now?.url, track.url),
                    onMore = { MusicSheet.open.value = MusicSheet.Track(track) },
                ) { playTracks(context, list, index) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Artiste : ses morceaux et ses albums (deux recherches YouTube Music)
// ---------------------------------------------------------------------------------------------

@Composable
private fun ArtistContent(state: MusicState, artist: ChannelItem) {
    val context = LocalContext.current
    val now by PlayerHolder.nowPlaying.collectAsState()
    val name = artist.name
    val songs by produceState(initialValue = Music.cachedSongs(name)?.let { byArtist(it, name) }, name) {
        value = Music.cachedSongs(name)?.let { byArtist(it, name) }
        if (value == null) value = runCatching { byArtist(Music.songs(name), name) }.getOrDefault(emptyList())
    }
    val albums by produceState(initialValue = Music.cachedCollections(name, MusicKind.Albums)?.let { byArtistAlbums(it, name) }, name) {
        value = Music.cachedCollections(name, MusicKind.Albums)?.let { byArtistAlbums(it, name) }
        if (value == null) value = runCatching { byArtistAlbums(Music.collections(name, MusicKind.Albums), name) }.getOrDefault(emptyList())
    }
    val scroll = rememberLazyListState()
    val play = PageBarEffect(state, scroll, name, playInOrder(context, songs.orEmpty()))
    LazyColumn(Modifier.fillMaxSize(), state = scroll, contentPadding = PaddingValues(bottom = BOTTOM_SPACE)) {
        item(key = "entete") {
            val list = songs.orEmpty()
            PageHeader(name, stringResource(R.string.music_about_artist), artist.avatar, round = true, onPlay = playInOrder(context, list), play = play) {
                SaveArtistButton(artist)
                if (list.isNotEmpty()) {
                    Capsule(stringResource(R.string.mix), icon = MkIcons.Mix) { playMix(context, list.first()) }
                }
            }
        }
        item(key = "titre_morceaux") { MusicSectionTitle(stringResource(R.string.music_songs), Modifier.padding(horizontal = 20.dp)) }
        results(songs, section = ":morceaux") { list ->
            itemsIndexed(list, key = { _, it -> "morceau:" + it.url }) { index, track ->
                TrackRow(
                    track,
                    playing = sameTrack(now?.url, track.url),
                    onMore = { MusicSheet.open.value = MusicSheet.Track(track) },
                ) { playTracks(context, list, index) }
            }
        }
        val albumList = albums
        if (albumList == null || albumList.isNotEmpty()) {
            item(key = "titre_albums") { MusicSectionTitle(stringResource(R.string.music_albums), Modifier.padding(horizontal = 20.dp)) }
            results(albumList, section = ":albums") { list ->
                items(list, key = { "album:" + it.url }) { album ->
                    CollectionRow(album.title, collectionSubtitle(album), album.thumbnail, icon = MkIcons.Album) { state.open(MusicPage.Album(album)) }
                }
            }
        }
    }
}

/** Les résultats de cet artiste d'abord (la recherche par nom ramène aussi d'autres artistes). */
private fun byArtist(list: List<VideoItem>, name: String): List<VideoItem> {
    val mine = list.filter { it.channel.contains(name, ignoreCase = true) }
    return if (mine.size >= 3) mine else list
}

private fun byArtistAlbums(list: List<MusicCollection>, name: String): List<MusicCollection> {
    val mine = list.filter { it.artist.contains(name, ignoreCase = true) }
    return if (mine.isNotEmpty()) mine else list
}

// ---------------------------------------------------------------------------------------------
// Playlist perso : réordonner en glissant, retirer avec ⋮
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MyPlaylistContent(state: MusicState, id: String) {
    val context = LocalContext.current
    val store = remember { Playlists.get(context) }
    val playlists by store.items.collectAsState()
    val playlist = playlists.firstOrNull { it.id == id } ?: return
    val now by PlayerHolder.nowPlaying.collectAsState()
    val list = rememberLazyListState()
    val reorder = remember(id) { ReorderState(list, firstIndex = 1, count = { store.get(id)?.items?.size ?: 0 }) { from, to -> store.move(id, from, to) } }
    val play = PageBarEffect(state, list, playlist.name, playInOrder(context, playlist.items))
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = BOTTOM_SPACE)) {
        item(key = "entete") {
            PageHeader(
                playlist.name,
                tracksSummary(playlist.items),
                playlist.coverModel(),
                onPlay = playInOrder(context, playlist.items),
                play = play,
            ) {
                PlaylistDownloadCircle(PlaylistDownloads.music, id, playlist.items)
                ShuffleButton(playlist.items)
                PlaylistCoverButtons(playlist)
            }
        }
        if (playlist.items.isEmpty()) {
            item(key = "vide") { EmptyCat(stringResource(R.string.playlist_empty), stringResource(R.string.playlist_empty_text), Modifier.padding(top = 20.dp)) }
        }
        itemsIndexed(playlist.items, key = { _, it -> it.url }) { index, track ->
            val dragged = reorder.dragging == track.url
            TrackRow(
                track,
                modifier = if (dragged) {
                    Modifier.zIndex(1f).graphicsLayer { translationY = reorder.offset }.background(Pelage.Coussin)
                } else {
                    Modifier.animateItem()
                },
                playing = sameTrack(now?.url, track.url),
                onMore = { MusicSheet.open.value = MusicSheet.Track(track, playlistId = id, index = index) },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DownloadedMark(PlaylistDownloads.music, track.url)
                        DragHandle(reorder, track.url)
                    }
                },
            ) { playTracks(context, playlist.items, index) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Téléchargés : les fichiers audio de yt-dlp, hors ligne
// ---------------------------------------------------------------------------------------------

@Composable
private fun DownloadsLibraryContent(state: MusicState) {
    val context = LocalContext.current
    val tracks by LocalMusic.tracks.collectAsState()
    val now by PlayerHolder.nowPlaying.collectAsState()
    LaunchedEffect(Unit) { LocalMusic.refresh(context) }
    val scroll = rememberLazyListState()
    val play = PageBarEffect(state, scroll, stringResource(R.string.music_downloaded), playInOrder(context, tracks.orEmpty()))
    LazyColumn(Modifier.fillMaxSize(), state = scroll, contentPadding = PaddingValues(bottom = BOTTOM_SPACE)) {
        item(key = "entete") {
            val count = tracks?.size ?: 0
            PageHeader(
                stringResource(R.string.music_downloaded),
                pluralStringResource(R.plurals.track_count, count, count),
                null,
                icon = MkIcons.Download,
                onPlay = playInOrder(context, tracks.orEmpty()),
                play = play,
            ) { ShuffleButton(tracks.orEmpty()) }
        }
        val list = tracks
        when {
            list == null -> item(key = "chargement") { Loading(Modifier.padding(top = 30.dp)) }
            list.isEmpty() -> item(key = "vide") {
                EmptyCat(stringResource(R.string.music_downloaded_none), stringResource(R.string.music_downloaded_none_text), Modifier.padding(top = 20.dp))
            }
            else -> itemsIndexed(list, key = { _, it -> it.url }) { index, track ->
                TrackRow(
                    track,
                    playing = now?.url == track.url,
                    onMore = { MusicSheet.open.value = MusicSheet.Track(track) },
                ) { playTracks(context, list, index) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Ta bibliothèque : playlists, albums et artistes sauvegardés, téléchargés — avec des filtres
// ---------------------------------------------------------------------------------------------

/** « Sauvegarder » / « Sauvegardé » sur la page d'un album (ou d'une playlist YouTube Music). */
@Composable
private fun SaveAlbumButton(album: MusicCollection) {
    val context = LocalContext.current
    val library = remember { MusicLibrary.get(context) }
    val albums by library.albums.collectAsState()
    SaveCapsule(albums.any { it.url == album.url }) { library.toggleAlbum(album) }
}

/** Pareil pour un artiste. */
@Composable
private fun SaveArtistButton(artist: ChannelItem) {
    val context = LocalContext.current
    val library = remember { MusicLibrary.get(context) }
    val artists by library.artists.collectAsState()
    val saved = remember(artists, artist) { library.hasArtist(artist) }
    SaveCapsule(saved) { library.toggleArtist(artist) }
}

@Composable
private fun SaveCapsule(saved: Boolean, onClick: () -> Unit) {
    Capsule(
        stringResource(if (saved) R.string.music_saved else R.string.music_save),
        selected = saved,
        icon = if (saved) MkIcons.Check else MkIcons.Plus,
        onClick = onClick,
    )
}

/** Le nom de chaque filtre de « Ta musique » (capsules en verre). */
internal fun libraryFilterLabel(filter: LibraryFilter) = when (filter) {
    LibraryFilter.All -> R.string.music_library_all
    LibraryFilter.Playlists -> R.string.music_playlists
    LibraryFilter.Albums -> R.string.music_albums
    LibraryFilter.Artists -> R.string.music_artists
    LibraryFilter.Downloaded -> R.string.music_downloaded
}

@Composable
private fun MusicLibraryContent(state: MusicState) {
    val context = LocalContext.current
    val library = remember { MusicLibrary.get(context) }
    val albums by library.albums.collectAsState()
    val artists by library.artists.collectAsState()
    val playlists by remember { Playlists.get(context) }.items.collectAsState()
    val downloaded by LocalMusic.tracks.collectAsState()
    val now by PlayerHolder.nowPlaying.collectAsState()
    LaunchedEffect(Unit) { LocalMusic.refresh(context) }
    val filter = state.libraryFilter
    val all = filter == LibraryFilter.All
    val count = downloaded?.size ?: 0

    // Le titre est dans la barre du haut (pilule en verre), les filtres en verre juste dessous
    FixedPageBar(state, stringResource(R.string.music_library))
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pageTop() + CHIPS_SPACE, bottom = BOTTOM_SPACE)) {
        if (filter == LibraryFilter.Downloaded) {
            val list = downloaded
            when {
                list == null -> item(key = "chargement") { Loading(Modifier.padding(top = 30.dp)) }
                list.isEmpty() -> item(key = "vide") {
                    EmptyCat(stringResource(R.string.music_downloaded_none), stringResource(R.string.music_downloaded_none_text), Modifier.padding(top = 20.dp))
                }
                else -> {
                    item(key = "aleatoire") { Row(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) { ShuffleButton(list) } }
                    itemsIndexed(list, key = { _, it -> "dl:" + it.url }) { index, track ->
                        TrackRow(
                            track,
                            playing = now?.url == track.url,
                            onMore = { MusicSheet.open.value = MusicSheet.Track(track) },
                        ) { playTracks(context, list, index) }
                    }
                }
            }
            return@LazyColumn
        }
        if (all || filter == LibraryFilter.Playlists) {
            item(key = "nouvelle") {
                CollectionRow(stringResource(R.string.playlist_new), "", null, icon = MkIcons.Plus) { MusicSheet.open.value = MusicSheet.NewPlaylist() }
            }
            item(key = "telecharges") {
                CollectionRow(stringResource(R.string.music_downloaded), pluralStringResource(R.plurals.track_count, count, count), null, icon = MkIcons.Download) {
                    state.open(MusicPage.Downloads)
                }
            }
            items(playlists, key = { "pl:" + it.id }) { pl ->
                CollectionRow(pl.name, pluralStringResource(R.plurals.track_count, pl.items.size, pl.items.size), pl.coverModel(), icon = MkIcons.Music) {
                    state.open(MusicPage.MyPlaylist(pl.id))
                }
            }
        }
        if (all || filter == LibraryFilter.Albums) {
            if (albums.isEmpty() && !all) {
                item(key = "vide_albums") {
                    EmptyCat(stringResource(R.string.music_library_no_albums), stringResource(R.string.music_library_no_albums_text), Modifier.padding(top = 20.dp))
                }
            }
            items(albums, key = { "album:" + it.url }) { album ->
                CollectionRow(album.title, collectionSubtitle(album), album.thumbnail, icon = MkIcons.Album) { state.open(MusicPage.Album(album)) }
            }
        }
        if (all || filter == LibraryFilter.Artists) {
            if (artists.isEmpty() && !all) {
                item(key = "vide_artistes") {
                    EmptyCat(stringResource(R.string.music_library_no_artists), stringResource(R.string.music_library_no_artists_text), Modifier.padding(top = 20.dp))
                }
            }
            items(artists, key = { "artiste:" + it.url + it.name }) { artist ->
                CollectionRow(artist.name, stringResource(R.string.music_artist), artist.avatar, round = true) { state.open(MusicPage.Artist(artist)) }
            }
        }
        if (all && albums.isEmpty() && artists.isEmpty()) {
            item(key = "astuce") {
                Text(
                    stringResource(R.string.music_library_hint),
                    Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
                    color = Pelage.Gris,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
