package app.mikeneko.ui.screens

import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
import app.mikeneko.ui.theme.plopOnSelect
import app.mikeneko.ui.components.columnsFor
import app.mikeneko.data.MusicHistory
import app.mikeneko.data.MusicLibrary
import app.mikeneko.ui.theme.jellyClickable
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import app.mikeneko.ui.components.rememberCoverColor
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.mikeneko.R
import app.mikeneko.data.ChannelItem
import app.mikeneko.data.Favorites
import app.mikeneko.data.LocalMusic
import app.mikeneko.data.Music
import app.mikeneko.data.MusicCollection
import app.mikeneko.data.MusicKind
import app.mikeneko.data.Playlists
import app.mikeneko.data.VideoItem
import app.mikeneko.data.formatCount
import app.mikeneko.data.sameTrack
import app.mikeneko.player.PlayerHolder
import app.mikeneko.ui.components.Capsule
import app.mikeneko.ui.components.EmptyCat
import app.mikeneko.ui.components.Loading
import app.mikeneko.ui.components.Tag
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.statusBarSpace
import coil3.compose.AsyncImage

/** Place laissée en bas pour le dock et le dock musique. */
private val BOTTOM_SPACE = 200.dp

/** Le haut des pages sous la barre en verre (retour / recherche). */
@Composable
private fun pageTop() = WindowInsets.statusBarSpace.asPaddingValues().calculateTopPadding() + 74.dp

/** La page Radio : l'accueil (ta musique, favoris, lives par ambiance), la recherche ou une page ouverte. */
@Composable
fun RadioContent(state: RadioState, onPlayStations: (List<VideoItem>, Int) -> Unit) {
    when (val page = state.page) {
        null -> if (state.searching) MusicSearchContent(state) else RadioHome(state, onPlayStations)
        is RadioPage.Album -> AlbumContent(state, page.collection)
        is RadioPage.Artist -> ArtistContent(state, page.artist)
        is RadioPage.MyPlaylist -> MyPlaylistContent(state, page.id)
        RadioPage.Downloads -> DownloadsLibraryContent(state)
        RadioPage.Library -> MusicLibraryContent(state)
    }
}

// ---------------------------------------------------------------------------------------------
// Accueil de la Radio
// ---------------------------------------------------------------------------------------------

@Composable
private fun RadioHome(state: RadioState, onPlayStations: (List<VideoItem>, Int) -> Unit) {
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
    val top = WindowInsets.statusBarSpace.asPaddingValues().calculateTopPadding() + 30.dp
    val quickColumns = columnsFor(170.dp).coerceIn(2, 4)

    /** Un morceau récent : son mix (un direct, lui, se relance tel quel). */
    fun replay(item: VideoItem) = if (item.isLive) playTracks(context, listOf(item), 0) else playMix(context, item)

    // L'accès rapide : ta bibliothèque, téléchargés, tes playlists, puis tes derniers morceaux (6 tuiles au plus)
    val count = downloaded?.size ?: 0
    val quick = buildList {
        add(QuickTile(context.getString(R.string.music_library), null, MkIcons.Library) { state.open(RadioPage.Library) })
        add(QuickTile(context.getString(R.string.music_downloaded), null, MkIcons.Download) { state.open(RadioPage.Downloads) })
        playlists.take(3).forEach { pl -> add(QuickTile(pl.name, pl.items.firstOrNull()?.thumbnail, MkIcons.Music) { state.open(RadioPage.MyPlaylist(pl.id)) }) }
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
            Shelf(stringResource(R.string.music_library), onMore = { state.open(RadioPage.Library) }) {
                item(key = "nouvelle") {
                    SquareCard(null, stringResource(R.string.playlist_new), "", icon = MkIcons.Plus) { MusicSheet.open.value = MusicSheet.NewPlaylist() }
                }
                items(playlists, key = { "pl:" + it.id }) { pl ->
                    SquareCard(pl.items.firstOrNull()?.thumbnail, pl.name, pluralStringResource(R.plurals.track_count, pl.items.size, pl.items.size)) {
                        state.open(RadioPage.MyPlaylist(pl.id))
                    }
                }
                items(savedAlbums, key = { "album:" + it.url }) { album ->
                    SquareCard(album.thumbnail, album.title, album.artist, icon = MkIcons.Album) { state.open(RadioPage.Album(album)) }
                }
                items(savedArtists, key = { "artiste:" + it.url + it.name }) { artist ->
                    SquareCard(artist.avatar, artist.name, stringResource(R.string.music_artist), round = true) { state.open(RadioPage.Artist(artist)) }
                }
                item(key = "telecharges") {
                    SquareCard(null, stringResource(R.string.music_downloaded), pluralStringResource(R.plurals.track_count, count, count), icon = MkIcons.Download) {
                        state.open(RadioPage.Downloads)
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
private fun greeting(): Int = when (java.time.LocalTime.now().hour) {
    in 5..11 -> R.string.greeting_morning
    in 12..17 -> R.string.greeting_afternoon
    in 18..22 -> R.string.greeting_evening
    else -> R.string.greeting_night
}

/** Une tuile de l'accès rapide. */
private class QuickTile(val title: String, val cover: String?, val icon: ImageVector, val onClick: () -> Unit)

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
    cover: String?,
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

// ---------------------------------------------------------------------------------------------
// Recherche musique
// ---------------------------------------------------------------------------------------------

@Composable
private fun MusicSearchContent(state: RadioState) {
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

    // (les genres de résultat sont en verre au-dessus, voir RadioHeader)
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
                    CollectionRow(album.title, collectionSubtitle(album), album.thumbnail, icon = MkIcons.Album) { state.open(RadioPage.Album(album)) }
                }
            }
            MusicKind.Artists -> results(artists) { list ->
                items(list, key = { "artiste:" + it.url }) { artist ->
                    CollectionRow(
                        artist.name,
                        if (artist.subscribers > 0) stringResource(R.string.subscribers_count, formatCount(artist.subscribers)) else "",
                        artist.avatar,
                        round = true,
                    ) { state.open(RadioPage.Artist(artist)) }
                }
            }
        }
    }
}

/**
 * Chargement, rien trouvé, ou la liste. [section] : à donner quand une page a plusieurs listes (artiste :
 * morceaux et albums), sinon leurs « chargement » / « vide » auraient la même clé et la liste planterait.
 */
private fun <T> LazyListScope.results(list: List<T>?, section: String = "", content: LazyListScope.(List<T>) -> Unit) {
    when {
        list == null -> item(key = "chargement$section") { Loading(Modifier.padding(top = 30.dp)) }
        list.isEmpty() -> item(key = "vide$section") {
            EmptyCat(stringResource(R.string.music_nothing), stringResource(R.string.music_nothing_text), Modifier.padding(top = 20.dp))
        }
        else -> content(list)
    }
}

/** La place des capsules en verre sous la barre du haut (44 dp + marges). */
private val CHIPS_SPACE = 56.dp

internal fun musicKindLabel(kind: MusicKind) = kindLabel(kind)

private fun kindLabel(kind: MusicKind) = when (kind) {
    MusicKind.Songs -> R.string.music_songs
    MusicKind.Albums -> R.string.music_albums
    MusicKind.Artists -> R.string.music_artists
    MusicKind.Playlists -> R.string.music_playlists
}

@Composable
private fun collectionSubtitle(album: MusicCollection): String = listOfNotNull(
    album.artist.ifBlank { null },
    album.count.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.track_count, it.toInt(), it.toInt()) },
).joinToString(" · ")

// ---------------------------------------------------------------------------------------------
// En-tête commun : pochette, titre, « Tout lire » et « Aléatoire »
// ---------------------------------------------------------------------------------------------

@Composable
private fun PageHeader(
    title: String,
    subtitle: String,
    cover: String?,
    round: Boolean = false,
    icon: ImageVector = MkIcons.Music,
    /** Le gros bouton lecture rond, à droite (null = pas de bouton). */
    onPlay: (() -> Unit)? = null,
    /** Le bouton lecture vient se ranger dans la barre du haut en défilant (voir [PlayAnchor]). */
    play: PlayAnchor? = null,
    actions: @Composable () -> Unit = {},
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var headerCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    // Un halo de la couleur de la pochette derrière l'en-tête, qui s'efface vers la page
    val glow by rememberCoverColor(cover)
    Column(
        Modifier
            .fillMaxWidth()
            .onPlaced { headerCoords = it }
            // Le halo part du haut de l'écran (derrière la barre et le bouton retour), sans coupure
            .background(Brush.verticalGradient(0f to glow.copy(alpha = 0.85f), 0.55f to glow.copy(alpha = 0.4f), 1f to Color.Transparent))
            .padding(top = pageTop() + 12.dp)
            .padding(horizontal = 20.dp),
    ) {
        Cover(
            cover,
            220.dp,
            if (round) 110.dp else 14.dp,
            Modifier.align(Alignment.CenterHorizontally).shadow(18.dp, if (round) CircleShape else RoundedCornerShape(14.dp)),
            icon = icon,
        )
        Text(
            title,
            Modifier.padding(top = 18.dp),
            color = Pelage.Texte,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) Text(subtitle, Modifier.padding(top = 2.dp), color = Color(0xFFCFC6BD), fontSize = 14.sp, maxLines = 2)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) { actions() }
            if (onPlay != null) {
                Box(
                    Modifier
                        .then(
                            if (play == null) {
                                Modifier
                            } else {
                                Modifier
                                    .onGloballyPositioned { c ->
                                        val header = headerCoords?.takeIf { it.isAttached } ?: return@onGloballyPositioned
                                        val inHeader = header.localPositionOf(c, Offset.Zero)
                                        val root = c.positionInRoot()
                                        play.inHeader = inHeader
                                        play.left = root.x
                                        play.headerTop.value?.let { top -> play.listTop = root.y - top - inHeader.y }
                                    }
                                    // Parti vers la barre : c'est le bouton de RadioOverlay qui est dessiné
                                    .graphicsLayer { alpha = if ((play.spot(density)?.t ?: 0f) > 0f) 0f else 1f }
                            },
                        )
                        .size(PLAY_BIG).shadow(10.dp, CircleShape).clip(CircleShape).background(Pelage.Roux).jellyClickable(role = Role.Button, onClickLabel = stringResource(R.string.play_all), onClick = onPlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(MkIcons.Play, stringResource(R.string.play_all), tint = Pelage.Encre, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

/**
 * Donne à la barre du haut le titre et le bouton lecture de la page ; ils y apparaissent quand le
 * titre de l'en-tête passe sous la barre (comme sur Spotify).
 */
@Composable
private fun PageBarEffect(state: RadioState, list: androidx.compose.foundation.lazy.LazyListState, title: String, onPlay: (() -> Unit)?): PlayAnchor {
    val owner = remember { Any() }
    // Le fondu suit le doigt : il commence quand le bas de l'en-tête (son bouton lecture) arrive à
    // [FADE_LEAD] de la barre, et se fait sur [FADE] de défilement (le bouton de l'en-tête passe dessous)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val bar = with(density) { pageTop().toPx() }
    val fade = with(density) { FADE.toPx() }
    val lead = with(density) { FADE_LEAD.toPx() }
    val passed = remember(list, bar, fade, lead) {
        androidx.compose.runtime.derivedStateOf {
            val header = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
            when {
                list.firstVisibleItemIndex > 0 -> 1f
                header == null -> 0f
                else -> ((list.firstVisibleItemScrollOffset - (header.size - bar - lead)) / fade).coerceIn(0f, 1f)
            }
        }
    }
    // Le haut de l'en-tête dans la liste, pour le bouton lecture qui vient se ranger dans la barre
    val headerTop = remember(list) {
        androidx.compose.runtime.derivedStateOf { list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.offset?.toFloat() }
    }
    val anchor = remember(headerTop, passed) { PlayAnchor(headerTop, passed) { state.barPill } }
    androidx.compose.runtime.SideEffect {
        state.bar = PageBar(title, onPlay, owner, passed, anchor)
    }
    androidx.compose.runtime.DisposableEffect(owner) {
        onDispose {
            if (state.bar?.owner === owner) state.bar = null
        }
    }
    return anchor
}

/** Une page sans grand en-tête (bibliothèque) : son titre est tout de suite dans la barre du haut. */
@Composable
private fun FixedPageBar(state: RadioState, title: String) {
    val owner = remember { Any() }
    val shown = remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    androidx.compose.runtime.SideEffect { state.bar = PageBar(title, null, owner, shown) }
    androidx.compose.runtime.DisposableEffect(owner) {
        onDispose { if (state.bar?.owner === owner) state.bar = null }
    }
}

/** La distance de défilement sur laquelle le titre et le bouton lecture arrivent dans la barre. */
private val FADE = 90.dp

/** À quelle distance de la barre le bas de l'en-tête doit arriver pour que le fondu commence. */
private val FADE_LEAD = 20.dp

/** Lire dans l'ordre (le gros bouton rond de l'en-tête). */
private fun playInOrder(context: android.content.Context, tracks: List<VideoItem>): (() -> Unit)? =
    if (tracks.isEmpty()) null else ({
        PlayerHolder.setShuffle(false)
        playTracks(context, tracks, 0)
    })

/**
 * À côté du bouton lecture : l'aléatoire, un interrupteur. Allumé (orange) quand cette liste joue en
 * aléatoire. Éteint : l'appui mélange la liste en cours si c'est elle qui joue (le morceau continue),
 * sinon il la lance en aléatoire. Allumé : l'appui coupe l'aléatoire (l'ordre revient, le morceau continue).
 */
@Composable
private fun ShuffleButton(tracks: List<VideoItem>) {
    val context = LocalContext.current
    if (tracks.isEmpty()) return
    val shuffle by PlayerHolder.shuffle.collectAsState()
    val queue by PlayerHolder.queue.collectAsState()
    // Cette liste est celle qui joue : tous ses morceaux sont dans la file (qui peut en avoir plus)
    val playingThis = remember(queue, tracks) { tracks.all { track -> queue.any { sameTrack(it.url, track.url) } } }
    val on = shuffle && playingThis
    Capsule(stringResource(R.string.shuffle), selected = on, icon = MkIcons.Shuffle) {
        when {
            on -> PlayerHolder.setShuffle(false)
            playingThis -> PlayerHolder.setShuffle(true)
            else -> shuffleTracks(context, tracks)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Album / playlist de YouTube Music
// ---------------------------------------------------------------------------------------------

@Composable
private fun AlbumContent(state: RadioState, collection: MusicCollection) {
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
            PageHeader(collection.title, collectionSubtitle(collection), collection.thumbnail, icon = MkIcons.Album, onPlay = playInOrder(context, tracks.orEmpty()), play = play) {
                SaveAlbumButton(collection)
                ShuffleButton(tracks.orEmpty())
            }
        }
        results(tracks) { list ->
            itemsIndexed(list, key = { i, it -> "$i:" + it.url }) { index, track ->
                TrackRow(
                    track,
                    number = index + 1,
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
private fun ArtistContent(state: RadioState, artist: ChannelItem) {
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
                    CollectionRow(album.title, collectionSubtitle(album), album.thumbnail, icon = MkIcons.Album) { state.open(RadioPage.Album(album)) }
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
private fun MyPlaylistContent(state: RadioState, id: String) {
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
                pluralStringResource(R.plurals.track_count, playlist.items.size, playlist.items.size),
                playlist.items.firstOrNull()?.thumbnail,
                onPlay = playInOrder(context, playlist.items),
                play = play,
            ) {
                PlaylistDownloadCircle(app.mikeneko.player.PlaylistDownloads.music, id, playlist.items)
                ShuffleButton(playlist.items)
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
                        DownloadedMark(app.mikeneko.player.PlaylistDownloads.music, track.url)
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
private fun DownloadsLibraryContent(state: RadioState) {
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

internal fun libraryFilterLabel(filter: LibraryFilter) = filterLabel(filter)

private fun filterLabel(filter: LibraryFilter) = when (filter) {
    LibraryFilter.All -> R.string.music_library_all
    LibraryFilter.Playlists -> R.string.music_playlists
    LibraryFilter.Albums -> R.string.music_albums
    LibraryFilter.Artists -> R.string.music_artists
    LibraryFilter.Downloaded -> R.string.music_downloaded
}

@Composable
private fun MusicLibraryContent(state: RadioState) {
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
                    state.open(RadioPage.Downloads)
                }
            }
            items(playlists, key = { "pl:" + it.id }) { pl ->
                CollectionRow(pl.name, pluralStringResource(R.plurals.track_count, pl.items.size, pl.items.size), pl.items.firstOrNull()?.thumbnail, icon = MkIcons.Music) {
                    state.open(RadioPage.MyPlaylist(pl.id))
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
                CollectionRow(album.title, collectionSubtitle(album), album.thumbnail, icon = MkIcons.Album) { state.open(RadioPage.Album(album)) }
            }
        }
        if (all || filter == LibraryFilter.Artists) {
            if (artists.isEmpty() && !all) {
                item(key = "vide_artistes") {
                    EmptyCat(stringResource(R.string.music_library_no_artists), stringResource(R.string.music_library_no_artists_text), Modifier.padding(top = 20.dp))
                }
            }
            items(artists, key = { "artiste:" + it.url + it.name }) { artist ->
                CollectionRow(artist.name, stringResource(R.string.music_artist), artist.avatar, round = true) { state.open(RadioPage.Artist(artist)) }
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
