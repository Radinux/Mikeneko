package app.mikeneko.ui.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.data.VideoItem
import app.mikeneko.data.sameTrack
import app.mikeneko.player.PlayerHolder
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.glass.LiquidGlass
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.GlassTextShadow
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.statusBarSpace
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Hauteur de la barre du tiroir quand il est fermé. */
val PLAYLIST_DRAWER_BAR = 64.dp

/** Les onglets du tiroir du lecteur. */
private enum class DrawerTab { Playlist, UpNext }

/**
 * Sur le lecteur, pour une vidéo d'une playlist : un tiroir en liquid glass dépoli en bas, avec deux
 * onglets, les vidéos de la playlist et les suggestions (« À suivre »). Fermé, c'est une barre (nom
 * de la playlist et « 3 / 63 ») ; on le tire vers le haut (ou on le touche) et il s'ouvre jusque sous
 * la vidéo. Une vidéo seule n'a pas de tiroir : ses suggestions sont dans la page (1.17.1).
 */
@Composable
fun PlaylistDrawer(backdrop: Backdrop, onOpen: (VideoItem) -> Unit) {
    val now by PlayerHolder.nowPlaying.collectAsState()
    val videos by PlayerHolder.videoPlaylist.collectAsState()
    val title by PlayerHolder.videoPlaylistTitle.collectAsState()
    val current = now ?: return
    val index = videos.indexOfFirst { sameTrack(it.url, current.url) }
    val related = current.related
    val inPlaylist = index >= 0
    // Seulement pour une vidéo d'une playlist : sinon les suggestions sont dans la page du lecteur
    // (choix de l'utilisateur, 1.17.1)
    if (current.audioOnly || !inPlaylist) return
    var chosen by remember { mutableStateOf(DrawerTab.Playlist) }
    // L'onglet choisi, s'il existe encore (plus de playlist : les suggestions)
    val tab = when {
        !inPlaylist -> DrawerTab.UpNext
        related.isEmpty() -> DrawerTab.Playlist
        else -> chosen
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    /** 0 = fermé (la barre), 1 = ouvert. Un seul Animatable, animé d'un seul endroit à la fois. */
    val open = remember { Animatable(0f) }
    var expanded by remember { mutableStateOf(false) }
    fun settle(target: Boolean, velocity: Float = 0f) {
        expanded = target
        scope.launch { open.animateTo(if (target) 1f else 0f, spring(dampingRatio = 0.78f, stiffness = 90f), initialVelocity = velocity) }
    }
    BackHandler(enabled = expanded) { settle(false) }

    val list = rememberLazyListState()
    // À l'ouverture : la vidéo en cours est en haut de la liste (avec la précédente au-dessus)
    LaunchedEffect(expanded, index, tab) { if (expanded && tab == DrawerTab.Playlist && index > 0) list.scrollToItem(index - 1) }
    val upNext = rememberLazyListState()
    // Une autre vidéo : ses suggestions repartent du haut
    LaunchedEffect(current.url) { upNext.scrollToItem(0) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        // Ouvert : juste sous la vidéo (barre d'état + « Lecture en cours » + image en 16:9), comme les commentaires
        val statusBar = with(density) { WindowInsets.statusBarSpace.getTop(density).toDp() }
        val top = statusBar + 56.dp + maxWidth * 9f / 16f + 6.dp
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val barPx = with(density) { PLAYLIST_DRAWER_BAR.toPx() }
        val bottomPx = with(density) { (navBottom + 8.dp).toPx() }
        val sidePx = with(density) { 8.dp.roundToPx() }
        val fullPx = constraints.maxHeight - with(density) { top.toPx() } - bottomPx
        val travel = (fullPx - barPx).coerceAtLeast(1f)
        var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

        LiquidGlass(
            backdrop = backdrop,
            modifier = Modifier.layout { measurable, c ->
                // La hauteur suit l'ouverture (lue ici : seulement une remise en page), collée en bas
                val h = (barPx + travel * open.value.coerceIn(0f, 1.03f)).roundToInt()
                val w = c.maxWidth - 2 * sidePx
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(c.maxWidth, c.maxHeight) { placeable.place(sidePx, c.maxHeight - bottomPx.roundToInt() - h) }
            },
            cornerRadius = 28.dp,
            bezel = 22.dp,
            refraction = 22.dp,
            // Verre clair, sans dépoli : la page se voit à travers, déformée sur les bords ; la teinte
            // et l'ombre du texte gardent les titres lisibles
            tint = Color(0x9E120F0D),
        ) {
            // Sans dépoli, une ombre sous tous les textes du tiroir (titres des vidéos compris)
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(shadow = GlassTextShadow),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(PLAYLIST_DRAWER_BAR)
                            .onPlaced { coordinates = it }
                            .pointerInput(travel) {
                                // Tirer la barre : positions prises à l'écran (la barre bouge sous le doigt)
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var vertical = false
                                    val start = awaitTouchSlopOrCancellation(down.id) { change, over ->
                                        if (abs(over.y) > abs(over.x)) {
                                            vertical = true
                                            change.consume()
                                        }
                                    }
                                    if (start == null || !vertical) return@awaitEachGesture
                                    fun screenY(position: Offset) = coordinates?.localToRoot(position)?.y ?: position.y
                                    scope.launch { open.stop() }
                                    val tracker = VelocityTracker()
                                    var last = screenY(start.position)
                                    var p = open.value
                                    tracker.addPosition(start.uptimeMillis, Offset(0f, last))
                                    drag(start.id) { change ->
                                        val y = screenY(change.position)
                                        p = (p - (y - last) / travel).coerceIn(0f, 1f)
                                        last = y
                                        scope.launch { open.snapTo(p) }
                                        tracker.addPosition(change.uptimeMillis, Offset(0f, y))
                                        change.consume()
                                    }
                                    val vy = tracker.calculateVelocity().y
                                    settle(if (abs(vy) > 800f) vy < 0f else p > 0.5f, velocity = -vy / travel)
                                }
                            }
                            .clickable(role = Role.Button, onClickLabel = stringResource(if (expanded) R.string.playlist_drawer_close else R.string.playlist_drawer_open)) { settle(!expanded) }
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (tab == DrawerTab.Playlist) MkIcons.Queue else MkIcons.Next, null, tint = Pelage.RouxClair, modifier = Modifier.size(22.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            if (tab == DrawerTab.Playlist) {
                                Text(title ?: stringResource(R.string.channel_tab_playlists), color = Pelage.Texte, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${index + 1} / ${videos.size}", color = Color(0xFFCFC6BD), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text(stringResource(R.string.up_next), color = Pelage.Texte, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                                Text(related.first().title, color = Color(0xFFCFC6BD), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        // La flèche vers le haut (tirer pour ouvrir), vers le bas une fois ouvert
                        Icon(
                            MkIcons.ChevronDown,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = 180f - 180f * open.value.coerceIn(0f, 1f) },
                        )
                    }
                    // Les deux onglets, quand la vidéo vient d'une playlist et a des suggestions
                    if (inPlaylist && related.isNotEmpty()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .graphicsLayer { alpha = (open.value * 1.6f).coerceIn(0f, 1f) }
                                .padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DrawerTabChip(stringResource(R.string.playlist_tab), tab == DrawerTab.Playlist) { chosen = DrawerTab.Playlist }
                            DrawerTabChip(stringResource(R.string.up_next), tab == DrawerTab.UpNext) { chosen = DrawerTab.UpNext }
                        }
                    }
                    if (tab == DrawerTab.UpNext) {
                        LazyColumn(
                            Modifier.weight(1f).fillMaxWidth().graphicsLayer { alpha = (open.value * 1.6f).coerceIn(0f, 1f) },
                            state = upNext,
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        ) {
                            items(related.size, key = { related[it].url }) { i ->
                                CompactVideoRow(related[i]) {
                                    settle(false) // la nouvelle vidéo se voit tout de suite
                                    onOpen(related[i])
                                }
                            }
                        }
                    } else LazyColumn(
                        Modifier.weight(1f).fillMaxWidth().graphicsLayer { alpha = (open.value * 1.6f).coerceIn(0f, 1f) },
                        state = list,
                        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 12.dp),
                    ) {
                        itemsIndexed(videos, key = { _, video -> video.url }) { i, video ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (i == index) Pelage.Roux.copy(alpha = 0.22f) else Color.Transparent)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                CompactVideoRow(video) {
                                    if (i == index) return@CompactVideoRow
                                    PlayerHolder.launchPlayback(onError = {
                                        Toast.makeText(context, context.getString(R.string.error_play_video, it.message.orEmpty()), Toast.LENGTH_SHORT).show()
                                    }) { PlayerHolder.play(context, video.url, audioOnly = false) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Un onglet du tiroir (dans le verre : une capsule pleine quand il est choisi). */
@Composable
private fun DrawerTabChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Pelage.Roux else Color.White.copy(alpha = 0.12f))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = if (selected) Pelage.Encre else Color.White,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
    )
}
