package app.mikeneko.ui.screens

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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.data.Favorites
import app.mikeneko.data.LocalMusic
import app.mikeneko.data.Music
import app.mikeneko.data.MusicForYou
import app.mikeneko.data.MusicHistory
import app.mikeneko.data.MusicLibrary
import app.mikeneko.data.Playlists
import app.mikeneko.data.VideoItem
import app.mikeneko.ui.components.Loading
import app.mikeneko.ui.components.Tag
import app.mikeneko.ui.components.columnsFor
import app.mikeneko.ui.components.coverModel
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.jellyClickable
import app.mikeneko.ui.theme.plopOnSelect
import app.mikeneko.ui.theme.statusBarSpace
import coil3.compose.AsyncImage
import java.time.LocalTime

// ---------------------------------------------------------------------------------------------
// Accueil de la Musique
// ---------------------------------------------------------------------------------------------

@Composable
internal fun MusicHome(state: MusicState, onPlayStations: (List<VideoItem>, Int) -> Unit) {
    val context = LocalContext.current
    val favorites by remember { Favorites.get(context) }.items.collectAsState()
    val playlists by remember { Playlists.get(context) }.items.collectAsState()
    val recent by remember { MusicHistory.get(context) }.items.collectAsState()
    val downloaded by LocalMusic.tracks.collectAsState()
    val library = remember { MusicLibrary.get(context) }
    val savedAlbums by library.albums.collectAsState()
    val savedArtists by library.artists.collectAsState()
    LaunchedEffect(Unit) { LocalMusic.refresh(context) }
    val ambiance = Music.ambiance(state.ambiance)
    val stations by produceState(initialValue = Music.cachedLives(ambiance), ambiance) {
        value = Music.cachedLives(ambiance) // autre ambiance : on ne garde pas la liste d'avant
        if (value == null) value = runCatching { Music.lives(ambiance) }.getOrDefault(emptyList())
    }
    // Les suggestions : d'abord ce qui est déjà prêt, puis (si besoin) 3 demandes à YouTube Music
    val suggestions by produceState(initialValue = MusicForYou.cached(context), recent.firstOrNull()?.url) {
        if (recent.isEmpty()) return@produceState
        MusicForYou.cached(context)?.let { value = it; return@produceState }
        runCatching { MusicForYou.load(context) }.onSuccess { value = it }
    }
    val top = WindowInsets.statusBarSpace.asPaddingValues().calculateTopPadding() + 30.dp
    val quickColumns = columnsFor(170.dp).coerceIn(2, 4)

    /** Un morceau récent : son mix (un direct, lui, se relance tel quel). */
    fun replay(item: VideoItem) = if (item.isLive) playTracks(context, listOf(item), 0) else playMix(context, item)

    // L'accès rapide : ta bibliothèque, téléchargés, tes playlists, puis tes derniers morceaux (6 tuiles au plus)
    val count = downloaded?.size ?: 0
    val quick = buildList {
        add(QuickTile(context.getString(R.string.music_library), null, MkIcons.Library) { state.open(MusicPage.Library) })
        add(QuickTile(context.getString(R.string.music_downloaded), null, MkIcons.Download) { state.open(MusicPage.Downloads) })
        playlists.take(3).forEach { pl -> add(QuickTile(pl.name, pl.coverModel(), MkIcons.Music) { state.open(MusicPage.MyPlaylist(pl.id)) }) }
        recent.take((6 - size).coerceAtLeast(0)).forEach { track -> add(QuickTile(track.title, track.thumbnail, MkIcons.Music) { replay(track) }) }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = top, bottom = BOTTOM_SPACE),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item(key = "bonjour") {
            Text(
                stringResource(greeting()),
                Modifier.padding(start = 20.dp, end = 80.dp), // à droite : la loupe en verre
                color = Pelage.Texte,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        item(key = "rapide") {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                quick.chunked(quickColumns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { tile -> QuickTileView(tile, Modifier.weight(1f)) }
                        repeat(quickColumns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        // Pour toi (2.6.0) : des morceaux à découvrir, et des mix thématiques d'après ton écoute
        val forYou = suggestions
        if (forYou != null && forYou.tracks.isNotEmpty()) {
            item(key = "pour_toi") {
                Shelf(stringResource(R.string.music_for_you)) {
                    itemsIndexed(forYou.tracks, key = { _, it -> "suggere:" + it.url }) { index, track ->
                        SquareCard(track.thumbnail, track.title, track.channel) { playTracks(context, forYou.tracks, index) }
                    }
                }
            }
        }
        if (forYou != null && forYou.mixes.isNotEmpty()) {
            item(key = "tes_mix") {
                Shelf(stringResource(R.string.music_your_mixes)) {
                    items(forYou.mixes, key = { "mix:" + it.id }) { mix ->
                        SquareCard(mix.coverModel(), mix.title, pluralStringResource(R.plurals.track_count, mix.tracks.size, mix.tracks.size), icon = MkIcons.Music) {
                            state.open(MusicPage.Generated(mix))
                        }
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item(key = "recents") {
                Shelf(stringResource(R.string.music_recent)) {
                    items(recent, key = { "recent:" + it.url }) { track ->
                        SquareCard(track.thumbnail, track.title, track.channel, live = track.isLive) { replay(track) }
                    }
                }
            }
        }
        item(key = "playlists") {
            Shelf(stringResource(R.string.music_library), onMore = { state.open(MusicPage.Library) }) {
                item(key = "nouvelle") {
                    SquareCard(null, stringResource(R.string.playlist_new), "", icon = MkIcons.Plus) { MusicSheet.open.value = MusicSheet.NewPlaylist() }
                }
                items(playlists, key = { "pl:" + it.id }) { pl ->
                    SquareCard(pl.coverModel(), pl.name, pluralStringResource(R.plurals.track_count, pl.items.size, pl.items.size)) {
                        state.open(MusicPage.MyPlaylist(pl.id))
                    }
                }
                items(savedAlbums, key = { "album:" + it.url }) { album ->
                    SquareCard(album.thumbnail, album.title, album.artist, icon = MkIcons.Album) { state.open(MusicPage.Album(album)) }
                }
                items(savedArtists, key = { "artiste:" + it.url + it.name }) { artist ->
                    SquareCard(artist.avatar, artist.name, stringResource(R.string.music_artist), round = true) { state.open(MusicPage.Artist(artist)) }
                }
                item(key = "telecharges") {
                    SquareCard(null, stringResource(R.string.music_downloaded), pluralStringResource(R.plurals.track_count, count, count), icon = MkIcons.Download) {
                        state.open(MusicPage.Downloads)
                    }
                }
            }
        }
        if (favorites.isNotEmpty()) {
            item(key = "favoris") {
                Shelf(stringResource(R.string.radio_favorites)) {
                    itemsIndexed(favorites, key = { _, it -> "fav:" + it.url }) { index, station ->
                        SquareCard(station.thumbnail, station.title, station.channel, live = station.isLive, favorite = true) { onPlayStations(favorites, index) }
                    }
                }
            }
        }
        item(key = "ambiances") {
            Shelf(stringResource(R.string.music_moods)) {
                itemsIndexed(Music.AMBIANCES, key = { _, it -> "ambiance:" + it.id }) { index, a ->
                    MoodCard(stringResource(a.label), index, selected = a.id == ambiance.id) { state.chooseAmbiance(a.id) }
                }
            }
        }
        item(key = "direct") {
            Shelf(stringResource(R.string.radio_live_in, stringResource(ambiance.label))) {
                val list = stations
                when {
                    list == null -> item(key = "chargement") { Loading(Modifier.size(220.dp, 124.dp)) }
                    list.isEmpty() -> item(key = "vide") { Text(stringResource(R.string.radio_none), Modifier.padding(vertical = 30.dp), color = Pelage.Gris, fontSize = 14.sp) }
                    else -> itemsIndexed(list, key = { _, it -> "live:" + it.url }) { index, station ->
                        LiveCard(station, favorite = favorites.any { it.url == station.url }) { onPlayStations(list, index) }
                    }
                }
            }
        }
    }
}

/** Bonjour, bon après-midi, bonsoir ou bonne nuit, selon l'heure. */
private fun greeting(): Int = when (LocalTime.now().hour) {
    in 5..11 -> R.string.greeting_morning
    in 12..17 -> R.string.greeting_afternoon
    in 18..22 -> R.string.greeting_evening
    else -> R.string.greeting_night
}

/** Une tuile de l'accès rapide. */
private class QuickTile(val title: String, val cover: Any?, val icon: ImageVector, val onClick: () -> Unit)

@Composable
private fun QuickTileView(tile: QuickTile, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Pelage.Coussin)
            .jellyClickable(role = Role.Button, strength = 0.3f, onClick = tile.onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(tile.cover, 56.dp, 0.dp, icon = tile.icon)
        // Le texte rétrécit pour tenir (un long mot comme « Téléchargés » ne se coupe pas en deux)
        BasicText(
            tile.title,
            Modifier.padding(horizontal = 10.dp),
            style = TextStyle(color = Pelage.Texte, fontWeight = FontWeight.ExtraBold, lineHeight = 15.sp),
            // Un seul mot (« Téléchargés ») : une ligne, il rétrécit plutôt que d'être coupé en deux
            maxLines = if (tile.title.trim().contains(' ')) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 13.sp, stepSize = 0.5.sp),
        )
    }
}

/** Une rangée : son titre, puis des cartes qui défilent de côté. */
@Composable
private fun Shelf(title: String, onMore: (() -> Unit)? = null, content: LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (onMore == null) {
            Text(title, Modifier.padding(horizontal = 20.dp), color = Pelage.Texte, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        } else {
            // Le titre ouvre la page entière (« › » à côté)
            Row(
                Modifier.padding(horizontal = 12.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onMore).padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, color = Pelage.Texte, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Icon(MkIcons.ChevronRight, null, tint = Pelage.Gris, modifier = Modifier.padding(start = 4.dp).size(20.dp))
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

/** Une carte carrée : pochette, titre, sous-titre (morceau, playlist, favori). */
@Composable
private fun SquareCard(
    cover: Any?,
    title: String,
    subtitle: String,
    icon: ImageVector = MkIcons.Music,
    live: Boolean = false,
    favorite: Boolean = false,
    /** Rond : un artiste. */
    round: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        Modifier.width(140.dp).jellyClickable(role = Role.Button, strength = 0.35f, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Box {
            Cover(cover, 140.dp, if (round) 70.dp else 12.dp, icon = icon)
            if (live) Tag(stringResource(R.string.live_badge), Modifier.padding(8.dp), background = Pelage.Roux)
            if (favorite) FavoriteBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        Text(title, Modifier.padding(top = 4.dp), color = Pelage.Texte, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotBlank()) Text(subtitle, color = Pelage.Gris, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Un direct : image en 16:9, titre, chaîne. */
@Composable
private fun LiveCard(station: VideoItem, favorite: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(220.dp).jellyClickable(role = Role.Button, strength = 0.35f, onClick = onClick), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Pelage.RouxNuit)) {
            AsyncImage(station.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (station.isLive) Tag(stringResource(R.string.live_badge), Modifier.padding(8.dp), background = Pelage.Roux)
            if (favorite) FavoriteBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        Text(station.title, Modifier.padding(top = 4.dp), color = Pelage.Texte, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
        Text(station.channel, color = Pelage.Gris, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FavoriteBadge(modifier: Modifier) {
    Box(modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
        Icon(MkIcons.HeartFilled, stringResource(R.string.radio_favorite_badge), tint = Pelage.Rose, modifier = Modifier.size(16.dp))
    }
}

/** Les teintes des cartes d'ambiance (dans l'ordre de Music.AMBIANCES, en boucle). */
private val MOOD_COLORS = listOf(
    Color(0xFF8E5BD8) to Color(0xFF3B2A6B),
    Color(0xFF3FA7A0) to Color(0xFF1E4C52),
    Color(0xFFD08A3C) to Color(0xFF6A3A1C),
    Color(0xFF5C7FD6) to Color(0xFF243A6E),
    Color(0xFFD45D7A) to Color(0xFF6B2338),
    Color(0xFF6DB46A) to Color(0xFF2C5530),
    Color(0xFFCF6A4E) to Color(0xFF5E2A1F),
    Color(0xFF8C8F3E) to Color(0xFF3F4120),
)

/** Une ambiance : une carte colorée ; touchée, la rangée « En direct » montre ses lives. */
@Composable
private fun MoodCard(label: String, index: Int, selected: Boolean, onClick: () -> Unit) {
    val (light, dark) = MOOD_COLORS[index % MOOD_COLORS.size]
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .size(150.dp, 84.dp)
            .plopOnSelect(selected)
            .clip(shape)
            .background(Brush.linearGradient(listOf(light, dark)))
            .then(if (selected) Modifier.border(3.dp, Pelage.Creme, shape) else Modifier)
            .jellyClickable(role = Role.Tab, strength = 0.4f, onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
