package app.mikeneko.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.data.Music
import app.mikeneko.data.MusicCollection
import app.mikeneko.data.MusicKind
import app.mikeneko.data.Playlists
import app.mikeneko.data.formatCount
import app.mikeneko.data.sameTrack
import app.mikeneko.player.PlayerHolder
import app.mikeneko.ui.components.EmptyCat
import app.mikeneko.ui.components.Loading
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage

// ---------------------------------------------------------------------------------------------
// Recherche musique
// ---------------------------------------------------------------------------------------------

@Composable
internal fun MusicSearchContent(state: MusicState) {
    val context = LocalContext.current
    val query = state.submitted
    val kind = state.kind
    val now by PlayerHolder.nowPlaying.collectAsState()

    // (produceState garde sa valeur quand les clés changent : on repart à chaque fois du cache)
    val songs by produceState(initialValue = query?.let { Music.cachedSongs(it) }, query, kind) {
        value = query?.let { Music.cachedSongs(it) }
        if (query != null && kind == MusicKind.Songs && value == null) value = runCatching { Music.songs(query) }.getOrDefault(emptyList())
    }
    val collections by produceState(initialValue = query?.let { Music.cachedCollections(it, kind) }, query, kind) {
        value = query?.let { Music.cachedCollections(it, kind) }
        if (query != null && (kind == MusicKind.Albums || kind == MusicKind.Playlists) && value == null) {
            value = runCatching { Music.collections(query, kind) }.getOrDefault(emptyList())
        }
    }
    val artists by produceState(initialValue = query?.let { Music.cachedArtists(it) }, query, kind) {
        value = query?.let { Music.cachedArtists(it) }
        if (query != null && kind == MusicKind.Artists && value == null) value = runCatching { Music.artists(query) }.getOrDefault(emptyList())
    }

    // (les genres de résultat sont en verre au-dessus, voir MusicHeader)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pageTop() + CHIPS_SPACE, bottom = BOTTOM_SPACE)) {
        if (query == null) {
            item(key = "aide") {
                Text(
                    stringResource(R.string.music_search_help),
                    Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
                    color = Pelage.Gris,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }
            return@LazyColumn
        }
        when (kind) {
            MusicKind.Songs -> results(songs) { list ->
                itemsIndexed(list, key = { _, it -> "morceau:" + it.url }) { _, track ->
                    TrackRow(
                        track,
                        playing = sameTrack(now?.url, track.url),
                        onMore = { MusicSheet.open.value = MusicSheet.Track(track) },
                    ) { playMix(context, track) } // un morceau trouvé : il joue, puis son mix enchaîne
                }
            }
            MusicKind.Albums, MusicKind.Playlists -> results(collections) { list ->
                items(list, key = { "album:" + it.url }) { album ->
                    CollectionRow(album.title, collectionSubtitle(album), album.thumbnail, icon = MkIcons.Album) { state.open(MusicPage.Album(album)) }
                }
            }
            MusicKind.Artists -> results(artists) { list ->
                items(list, key = { "artiste:" + it.url }) { artist ->
                    CollectionRow(
                        artist.name,
                        if (artist.subscribers > 0) stringResource(R.string.subscribers_count, formatCount(artist.subscribers)) else "",
                        artist.avatar,
                        round = true,
                    ) { state.open(MusicPage.Artist(artist)) }
                }
            }
        }
    }
}

/**
 * Chargement, rien trouvé, ou la liste. [section] : à donner quand une page a plusieurs listes (artiste :
 * morceaux et albums), sinon leurs « chargement » / « vide » auraient la même clé et la liste planterait.
 */
internal fun <T> LazyListScope.results(list: List<T>?, section: String = "", content: LazyListScope.(List<T>) -> Unit) {
    when {
        list == null -> item(key = "chargement$section") { Loading(Modifier.padding(top = 30.dp)) }
        list.isEmpty() -> item(key = "vide$section") {
            EmptyCat(stringResource(R.string.music_nothing), stringResource(R.string.music_nothing_text), Modifier.padding(top = 20.dp))
        }
        else -> content(list)
    }
}

/** La place des capsules en verre sous la barre du haut (44 dp + marges). */
internal val CHIPS_SPACE = 56.dp

/** Le nom de chaque genre de résultat (capsules en verre de la recherche). */
internal fun musicKindLabel(kind: MusicKind) = when (kind) {
    MusicKind.Songs -> R.string.music_songs
    MusicKind.Albums -> R.string.music_albums
    MusicKind.Artists -> R.string.music_artists
    MusicKind.Playlists -> R.string.music_playlists
}

@Composable
internal fun collectionSubtitle(album: MusicCollection): String = listOfNotNull(
    album.artist.ifBlank { null },
    album.count.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.track_count, it.toInt(), it.toInt()) },
).joinToString(" · ")
