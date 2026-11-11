package app.mikeneko.ui.screens

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import app.mikeneko.data.TrackTag
import app.mikeneko.data.TrackTags
import app.mikeneko.data.camelotParts
import app.mikeneko.data.trackKey
import app.mikeneko.player.TrackAnalyzer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Button
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import app.mikeneko.data.videoIdOf
import app.mikeneko.ui.components.CONNECTED_GAP
import app.mikeneko.ui.components.ConnectedChip
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import android.widget.Toast
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
import app.mikeneko.data.GeneratedMix
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
import app.mikeneko.ui.components.PlaylistArt
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
        is MusicPage.AddSongs -> AddSongsContent(state, page.id)
        is MusicPage.EditSongs -> EditSongsContent(state, page.id)
        MusicPage.Downloads -> DownloadsLibraryContent(state)
        MusicPage.Library -> MusicLibraryContent(state)
        is MusicPage.Generated -> GeneratedMixContent(state, page.mix)
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
// Un mix fabriqué d'après ton écoute (« Tes mix ») : ses morceaux, et « Garder » en playlist
// ---------------------------------------------------------------------------------------------

@Composable
private fun GeneratedMixContent(state: MusicState, mix: GeneratedMix) {
    val context = LocalContext.current
    val now by PlayerHolder.nowPlaying.collectAsState()
    val scroll = rememberLazyListState()
    val play = PageBarEffect(state, scroll, mix.title, playInOrder(context, mix.tracks))
    val playlists by remember { Playlists.get(context) }.items.collectAsState()
    val kept = playlists.any { it.name.equals(mix.title, ignoreCase = true) }
    LazyColumn(Modifier.fillMaxSize(), state = scroll, contentPadding = PaddingValues(bottom = BOTTOM_SPACE)) {
        item(key = "entete") {
            PageHeader(mix.title, mix.subtitle + " · " + tracksSummary(mix.tracks), mix.coverModel(), onPlay = playInOrder(context, mix.tracks), play = play) {
                ShuffleButton(mix.tracks)
                // Garder : le mix devient une de tes playlists (il change, elle non)
                Capsule(stringResource(if (kept) R.string.mix_kept_label else R.string.mix_keep), selected = kept, icon = if (kept) MkIcons.Check else MkIcons.Plus) {
                    if (!kept) {
                        Playlists.get(context).merge(mix.title, mix.tracks)
                        Toast.makeText(context, context.getString(R.string.mix_kept, mix.title), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        itemsIndexed(mix.tracks, key = { i, it -> "$i:" + it.url }) { index, track ->
            TrackRow(
                track,
                playing = sameTrack(now?.url, track.url),
                onMore = { MusicSheet.open.value = MusicSheet.Track(track) },
            ) { playTracks(context, mix.tracks, index) }
        }
    }
}

/** La pochette d'un mix : la mosaïque de ses 4 premières pochettes. */
internal fun GeneratedMix.coverModel(): Any? {
    val thumbs = tracks.mapNotNull { it.thumbnail }.distinct()
    return if (thumbs.size >= 4) PlaylistArt(thumbs.take(4)) else thumbs.firstOrNull()
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
// Playlist perso (2.11.0) : « Ajouter », « Modifier » et « Infos » sous l'en-tête (2.11.3). Modifier :
// les morceaux, un bouton ⊖ à gauche pour retirer et la poignée pour réordonner (la liste défile
// toute seule près des bords). Infos : le nom, la pochette et supprimer la playlist. En temps normal,
// rien ne bouge (on déplaçait des morceaux sans le vouloir).
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
    var details by rememberSaveable(id) { mutableStateOf(false) }
    // Le tri automatique (2.15.0) : la liste affichée, et l'ordre de lecture
    val tags by remember { TrackTags.load(context) }.collectAsState()
    val sort = TrackTags.Sort.of(playlist.sort)
    val ordered = remember(playlist.items, sort, tags) { TrackTags.sorted(playlist.items, sort, tags) }
    // Tempo et tonalité des morceaux sur le téléphone, albums lus doucement en fond
    LaunchedEffect(playlist.items) {
        TrackAnalyzer.request(context, playlist.items.map { it.url })
        TrackTags.fillAlbums(context, playlist.items)
    }
    // La recherche dans la playlist (loupe en haut à droite, 2.11.3) : fermée en quittant la page
    DisposableEffect(id) { onDispose { state.playlistFilter = null } }
    val filter = state.playlistFilter?.trim().orEmpty()
    val shown = remember(ordered, filter) {
        val wanted = filter.lowercase()
        ordered.withIndex().filter { (_, t) -> wanted.isEmpty() || wanted in t.title.lowercase() || wanted in t.channel.lowercase() }
    }
    BackHandler(enabled = details) { details = false }
    val play = PageBarEffect(state, list, playlist.name, playInOrder(context, ordered))
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = BOTTOM_SPACE)) {
        item(key = "entete") {
            PageHeader(
                playlist.name,
                tracksSummary(playlist.items),
                playlist.coverModel(),
                onPlay = if (details) null else playInOrder(context, ordered),
                play = play,
                actions = {
                when {
                    details -> PlaylistEditPanel(
                        playlist,
                        onDone = { details = false },
                        onDeleted = { state.pages.removeAll { it == MusicPage.MyPlaylist(id) || it == MusicPage.AddSongs(id) || it == MusicPage.EditSongs(id) } },
                    )
                    else -> {
                        PlaylistDownloadCircle(PlaylistDownloads.music, id, playlist.items)
                        ShuffleButton(playlist.items)
                        if (playlist.items.size > 1) SortButton(id, sort)
                    }
                }
                },
                below = below@{
                if (details) return@below
                // Ajouter | Modifier | Réordonner : une pilule découpée, sous le bouton lecture (2.11.3),
                // sur toute la largeur
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(CONNECTED_GAP)) {
                    ConnectedChip(stringResource(R.string.playlist_add_songs), selected = false, first = true, last = false, modifier = Modifier.weight(1f), icon = MkIcons.Plus, sidePadding = 8.dp) {
                        state.open(MusicPage.AddSongs(id))
                    }
                    // Modifier : les morceaux (retirer, réordonner) ; Infos : nom, pochette, supprimer la playlist (2.11.3)
                    ConnectedChip(stringResource(R.string.playlist_edit), selected = false, first = false, last = false, modifier = Modifier.weight(1f), icon = MkIcons.Pencil, sidePadding = 8.dp) {
                        state.open(MusicPage.EditSongs(id))
                    }
                    ConnectedChip(stringResource(R.string.playlist_details), selected = false, first = false, last = true, modifier = Modifier.weight(1f), icon = MkIcons.Info, sidePadding = 8.dp) { details = true }
                }
                },
            )
        }
        if (playlist.items.isEmpty()) {
            item(key = "vide") { EmptyCat(stringResource(R.string.playlist_empty), stringResource(R.string.playlist_empty_text), Modifier.padding(top = 20.dp)) }
        }
        items(shown, key = { it.value.url }) { (index, track) ->
            TrackRow(
                track,
                modifier = Modifier.animateItem(),
                playing = sameTrack(now?.url, track.url),
                // (le menu retire le morceau par sa place dans la playlist, pas dans la liste triée)
                onMore = if (details) null else ({ MusicSheet.open.value = MusicSheet.Track(track, playlistId = id, index = playlist.items.indexOfFirst { it.url == track.url }) }),
                trailing = {
                    if (!details) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Trié par tempo ou tonalité : la valeur qui sert au tri, au bout de la ligne
                            if (sort == TrackTags.Sort.Bpm || sort == TrackTags.Sort.Camelot) TrackTagChips(tags[trackKey(track.url)], compact = true)
                            DownloadedMark(PlaylistDownloads.music, track.url)
                        }
                    }
                },
            ) { if (!details) playTracks(context, ordered, index) }
        }
    }
}

/**
 * Modifier les morceaux d'une playlist perso (2.11.13) : une page à elle, comme l'ajout. Le titre et
 * « Terminé » restent en haut, hors de la liste : un morceau tiré ne passe plus sur la pochette et le
 * titre de la playlist, et la liste défile dès que le doigt monte vers eux. ⊖ retire en un clic,
 * ≡ déplace. Pendant un déplacement, l'ordre change dans une liste locale, enregistré une seule fois
 * au lâcher (avant : toutes les playlists réécrites à chaque échange, la ligne prenait du retard sur
 * le doigt).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditSongsContent(state: MusicState, id: String) {
    val context = LocalContext.current
    val store = remember { Playlists.get(context) }
    val playlists by store.items.collectAsState()
    val playlist = playlists.firstOrNull { it.id == id } ?: return
    val now by PlayerHolder.nowPlaying.collectAsState()
    // Les morceaux dans l'ordre affiché (le tri automatique s'il y en a un) ; les tags BPM et Camelot à droite (2.15.0)
    val tags by remember { TrackTags.load(context) }.collectAsState()
    val sort = TrackTags.Sort.of(playlist.sort)
    val ordered = remember(playlist.items, sort, tags) { TrackTags.sorted(playlist.items, sort, tags) }
    LaunchedEffect(playlist.items) { TrackAnalyzer.request(context, playlist.items.map { it.url }) }
    val songs = remember(id) { androidx.compose.runtime.mutableStateListOf<VideoItem>().apply { addAll(ordered) } }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val bottomInset = with(density) { (BOTTOM_SPACE - 40.dp).toPx() }
    val reorder = remember(id, bottomInset) {
        ReorderState(
            list,
            firstIndex = 0,
            count = { songs.size },
            scope = scope,
            bottomInset = bottomInset,
            edge = with(density) { 110.dp.toPx() },
            // Déplacer un morceau, c'est choisir son ordre : le tri automatique s'arrête, avec cet ordre-là
            onDrop = {
                store.setItems(id, songs.toList())
                if (playlist.sort != null) store.setSort(id, null)
            },
        ) { from, to -> if (from in songs.indices && to in songs.indices) songs.add(to, songs.removeAt(from)) }
    }
    // La playlist changée ailleurs (un ajout depuis le lecteur…), hors déplacement : on se recale
    LaunchedEffect(ordered) {
        if (reorder.dragging == null && songs.toList() != ordered) {
            songs.clear()
            songs.addAll(ordered)
        }
    }
    Column(Modifier.fillMaxSize().padding(top = pageTop())) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.edit_songs_title, playlist.name),
                Modifier.weight(1f),
                color = Pelage.Texte,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Button(onClick = { state.goBack() }, modifier = Modifier.padding(start = 10.dp)) {
                Text(stringResource(R.string.playlist_done), fontWeight = FontWeight.ExtraBold)
            }
        }
        Text(
            stringResource(R.string.playlist_reorder_hint) + if (sort != null) "\n" + stringResource(R.string.sort_reorder_custom) else "",
            Modifier.padding(horizontal = 18.dp).padding(bottom = 8.dp),
            color = Pelage.Gris,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Box(Modifier.padding(horizontal = 18.dp).padding(bottom = 6.dp)) { SortButton(id, sort) }
        if (songs.isEmpty()) {
            EmptyCat(stringResource(R.string.playlist_empty), stringResource(R.string.playlist_empty_text), Modifier.padding(top = 20.dp))
            return@Column
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = list, contentPadding = PaddingValues(bottom = BOTTOM_SPACE)) {
            itemsIndexed(songs, key = { _, it -> it.url }) { index, track ->
                val dragged = reorder.dragging == track.url
                TrackRow(
                    track,
                    modifier = if (dragged) {
                        Modifier.zIndex(1f).graphicsLayer { translationY = reorder.offset }.background(Pelage.Coussin)
                    } else {
                        Modifier.animateItem()
                    },
                    playing = sameTrack(now?.url, track.url),
                    leading = {
                        IconButton(
                            onClick = {
                                if (index in songs.indices) {
                                    songs.removeAt(index)
                                    store.removeTrack(id, track.url) // (par son adresse : la liste peut être triée)
                                }
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(MkIcons.MinusCircle, stringResource(R.string.playlist_remove_song, track.title), tint = Color(0xFFFF8A80), modifier = Modifier.size(24.dp))
                        }
                    },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TrackTagChips(tags[trackKey(track.url)])
                            DragHandle(reorder, track.url)
                        }
                    },
                ) { }
            }
        }
    }
}

/**
 * Ajouter des morceaux à une playlist perso (2.11.0) : des suggestions tirées des Mix YouTube Music
 * de 3 morceaux de la playlist (sans ceux qui y sont déjà), et une recherche. Un « + » par morceau
 * (« ✓ » une fois ajouté, qui le retire) ; toucher le morceau l'écoute.
 */
@Composable
private fun AddSongsContent(state: MusicState, id: String) {
    val context = LocalContext.current
    val store = remember { Playlists.get(context) }
    val playlists by store.items.collectAsState()
    val playlist = playlists.firstOrNull { it.id == id } ?: return
    var query by rememberSaveable(id) { mutableStateOf("") }
    val initial = remember(id) { playlist.items.mapNotNull { videoIdOf(it.url) }.toSet() }
    val seeds = remember(id) { playlist.items.shuffled().take(3).mapNotNull { videoIdOf(it.url) } }
    val suggestions by produceState<List<VideoItem>?>(null, id) {
        value = coroutineScope {
            seeds.map { vid -> async { runCatching { Music.mix(vid).tracks }.getOrDefault(emptyList()) } }.awaitAll()
        }.let { lists ->
            val longest = lists.maxOfOrNull { it.size } ?: 0
            (0 until longest).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } }
        }.filter { (videoIdOf(it.url) ?: it.url) !in initial }.distinctBy { videoIdOf(it.url) ?: it.url }.take(40)
    }
    val results by produceState<List<VideoItem>?>(null, query) {
        value = null
        if (query.isBlank()) return@produceState
        delay(400) // on attend que la frappe se calme
        value = runCatching { Music.songs(query.trim()) }.getOrDefault(emptyList())
    }
    val searching = query.isNotBlank()
    val shown = if (searching) results else suggestions
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pageTop(), bottom = BOTTOM_SPACE)) {
        item(key = "titre") {
            Column(Modifier.padding(horizontal = 18.dp)) {
                Text(stringResource(R.string.add_songs_title, playlist.name), color = Pelage.Texte, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    placeholder = { Text(stringResource(R.string.add_songs_search)) },
                    leadingIcon = { Icon(MkIcons.Search, null, modifier = Modifier.size(20.dp)) },
                    trailingIcon = if (searching) ({
                        IconButton(onClick = { query = "" }) { Icon(MkIcons.Close, stringResource(R.string.cancel), modifier = Modifier.size(18.dp)) }
                    }) else null,
                    singleLine = true,
                    shape = CircleShape,
                )
                if (!searching && seeds.isNotEmpty()) {
                    Text(stringResource(R.string.add_songs_suggested), Modifier.padding(top = 18.dp, bottom = 4.dp), color = Pelage.Gris, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        when {
            !searching && seeds.isEmpty() -> item(key = "indice") {
                EmptyCat(stringResource(R.string.add_songs_search), stringResource(R.string.add_songs_empty_hint), Modifier.padding(top = 20.dp))
            }
            shown == null -> item(key = "chargement") { Loading(Modifier.padding(top = 40.dp)) }
            else -> itemsIndexed(shown, key = { _, it -> "ajout:" + it.url }) { index, track ->
                val added = playlist.items.any { sameTrack(it.url, track.url) }
                TrackRow(
                    track,
                    trailing = {
                        if (added) {
                            FilledIconButton(onClick = {
                                val at = playlist.items.indexOfFirst { sameTrack(it.url, track.url) }
                                if (at >= 0) store.remove(id, at)
                            }) { Icon(MkIcons.Check, stringResource(R.string.add_songs_added, track.title), modifier = Modifier.size(20.dp)) }
                        } else {
                            FilledTonalIconButton(onClick = { store.add(id, track) }) {
                                Icon(MkIcons.Plus, stringResource(R.string.add_songs_add, track.title), modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                ) { playTracks(context, shown, index) }
            }
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

    // Plusieurs playlists à la fois (3.0.1) : un appui long en coche une et ouvre la sélection
    var selection by remember { mutableStateOf<Set<String>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = selection != null) { selection = null }
    fun toggle(id: String) {
        val now = selection ?: return
        selection = (if (id in now) now - id else now + id).ifEmpty { null }
    }
    // Le filtre change : la sélection s'en va avec les playlists qu'on ne voit plus
    LaunchedEffect(filter) { if (filter != LibraryFilter.All && filter != LibraryFilter.Playlists) selection = null }

    // Le titre est dans la barre du haut (pilule en verre), les filtres en verre juste dessous
    FixedPageBar(state, stringResource(R.string.music_library))
    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pageTop() + CHIPS_SPACE + if (selection != null) 62.dp else 0.dp, bottom = BOTTOM_SPACE)) {
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
                CollectionRow(
                    pl.name,
                    pluralStringResource(R.plurals.track_count, pl.items.size, pl.items.size),
                    pl.coverModel(),
                    icon = MkIcons.Music,
                    selected = selection?.let { pl.id in it },
                    onLongClick = { if (selection == null) selection = setOf(pl.id) else toggle(pl.id) },
                ) {
                    if (selection != null) toggle(pl.id) else state.open(MusicPage.MyPlaylist(pl.id))
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
        // La barre de la sélection, sous les filtres
        androidx.compose.animation.AnimatedVisibility(
            selection != null,
            Modifier.align(Alignment.TopCenter).padding(top = pageTop() + CHIPS_SPACE).padding(horizontal = 14.dp),
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { -it / 2 },
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { -it / 2 },
        ) {
            val chosen = selection.orEmpty()
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(Pelage.Coussin)
                    .border(1.5.dp, Pelage.Bord, RoundedCornerShape(26.dp)).padding(start = 4.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = stringResource(R.string.cancel)) { selection = null },
                    contentAlignment = Alignment.Center,
                ) { Icon(MkIcons.Close, stringResource(R.string.cancel), tint = Pelage.Texte, modifier = Modifier.size(22.dp)) }
                Text(
                    pluralStringResource(R.plurals.selected_count, chosen.size, chosen.size),
                    Modifier.weight(1f).padding(start = 4.dp),
                    color = Pelage.Texte,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
                androidx.compose.material3.TextButton(onClick = { selection = playlists.map { it.id }.toSet().ifEmpty { null } }) {
                    Text(stringResource(R.string.select_all), color = Pelage.Texte, fontWeight = FontWeight.Bold)
                }
                androidx.compose.material3.TextButton(onClick = { if (chosen.isNotEmpty()) confirmDelete = true }) {
                    Icon(MkIcons.Trash, null, tint = Color(0xFFFF8A80), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.delete), color = Color(0xFFFF8A80), fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
    if (confirmDelete) {
        val chosen = selection.orEmpty()
        app.mikeneko.ui.components.AnimatedAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(pluralStringResource(R.plurals.playlists_delete_title, chosen.size, chosen.size), fontWeight = FontWeight.ExtraBold) },
            text = { Text(stringResource(R.string.playlists_delete_text)) },
            confirmButton = { close ->
                androidx.compose.material3.TextButton(onClick = {
                    val store = Playlists.get(context)
                    chosen.forEach { id ->
                        // Comme la suppression d'une seule (pochette choisie comprise)
                        store.get(id)?.cover?.let { java.io.File(it).delete() }
                        store.delete(id)
                    }
                    state.pages.removeAll { page -> page is MusicPage.MyPlaylist && page.id in chosen }
                    selection = null
                    close()
                }) { Text(stringResource(R.string.delete), color = Color(0xFFFF8A80), fontWeight = FontWeight.ExtraBold) }
            },
            dismissButton = { close ->
                androidx.compose.material3.TextButton(onClick = { close() }) { Text(stringResource(R.string.cancel), color = Pelage.Texte) }
            },
        )
    }
}


/**
 * Les tags d'un morceau : son tempo (« 128 BPM ») et sa case de la roue Camelot (« 8A »), dans la
 * couleur de sa case (la roue va du rouge au rose, case par case ; mineur plus sombre que majeur).
 * [compact] : une seule ligne, pour la liste de la playlist.
 */
@Composable
internal fun TrackTagChips(tag: TrackTag?, compact: Boolean = false) {
    val bpm = tag?.bpm
    val camelot = tag?.camelot
    if (bpm == null && camelot == null) return
    @Composable
    fun Chip(text: String, background: Color, color: Color) {
        Box(
            Modifier.clip(RoundedCornerShape(8.dp)).background(background).padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
    val content: @Composable () -> Unit = {
        if (bpm != null) Chip("$bpm BPM", Color.White.copy(alpha = 0.10f), Pelage.Texte)
        camelotParts(camelot)?.let { (number, letter) ->
            val hue = ((number - 1) * 30f + 0f) % 360f
            val base = Color.hsv(hue, 0.55f, if (letter == 'A') 0.75f else 0.92f)
            Chip("$number$letter", base, Color(0xFF15110E))
        }
    }
    // Sur une seule ligne seulement s'il y a la place (2.23.3) : sur un écran étroit, ou avec le texte
    // agrandi dans les réglages du téléphone, les deux pastilles côte à côte ne laissaient au titre que
    // quelques lettres (« tesla ··· »). Empilées, elles tiennent dans la hauteur de la pochette.
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val roomy = config.screenWidthDp / config.fontScale >= 380f
    if (compact && roomy) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    } else {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) { content() }
    }
}
