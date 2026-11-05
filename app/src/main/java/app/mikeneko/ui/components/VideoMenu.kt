package app.mikeneko.ui.components

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.mikeneko.ui.screens.NewPlaylistField
import app.mikeneko.data.sameTrack
import app.mikeneko.data.Playlists
import app.mikeneko.data.VideoLibrary
import android.widget.Toast
import app.mikeneko.ui.theme.clay
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.data.Feedback
import app.mikeneko.data.VideoItem
import app.mikeneko.data.channelKey
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.glass.LiquidGlass
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

private val Secondary = Color(0xFFCFC6BD)

/** Le menu ⋮ d'une vidéo et la barre « Annuler » qui suit une action. */
object VideoMenu {
    /** Vidéo dont le menu est ouvert (null = fermé). */
    val target = MutableStateFlow<VideoItem?>(null)

    /** Ce qui peut être annulé pendant quelques secondes. */
    sealed interface Undo {
        val id: Long
        data class Hidden(val video: VideoItem, override val id: Long = System.nanoTime()) : Undo
        data class Blocked(val video: VideoItem, override val id: Long = System.nanoTime()) : Undo
    }

    val undo = MutableStateFlow<Undo?>(null)

    /** Le menu montre le choix d'une playlist (au lieu de ses actions). */
    val picking = MutableStateFlow(false)

    fun open(video: VideoItem) {
        picking.value = false
        target.value = video
    }

    /** Ouvre directement le choix de la playlist où ajouter [video] (bouton du lecteur). */
    fun openPlaylists(video: VideoItem) {
        picking.value = true
        target.value = video
    }
}

/**
 * Le menu en verre qui monte du bas : pas intéressé, ne pas recommander la chaîne,
 * télécharger, partager. [bottomSpace] : place laissée au dock pour la barre « Annuler ».
 */
@Composable
fun VideoMenuSheet(backdrop: Backdrop, onDownload: (String) -> Unit, bottomSpace: Dp) {
    val context = LocalContext.current
    val feedback = remember { Feedback.get(context) }
    val target by VideoMenu.target.collectAsState()
    val picking by VideoMenu.picking.collectAsState()
    val library = remember { VideoLibrary.get(context) }
    val laterList by library.later.collectAsState()
    // Garde la vidéo affichée pendant l'animation de fermeture
    var shown by remember { mutableStateOf<VideoItem?>(null) }
    target?.let { shown = it }

    fun close() {
        VideoMenu.target.value = null
    }

    BackHandler(enabled = target != null, onBack = ::close)

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(target != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(interactionSource = null, indication = null, onClickLabel = stringResource(R.string.menu_close), onClick = ::close),
            )
        }

        AnimatedVisibility(
            target != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(300)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(240)) { it } + fadeOut(tween(200)),
        ) {
            val video = shown ?: return@AnimatedVisibility
            LiquidGlass(
                backdrop = backdrop,
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().navigationBarsPadding().padding(10.dp),
                cornerRadius = 30.dp,
                bezel = 26.dp,
                refraction = 26.dp,
                tint = Color(0xC71A1613),
                frost = 28.dp,
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    // La vidéo concernée
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(96.dp).height(54.dp).clip(RoundedCornerShape(10.dp)).background(Pelage.RouxNuit)) {
                            AsyncImage(video.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(video.title, color = Pelage.Texte, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(video.channel, color = Secondary, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                    Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))

                    if (picking) {
                        PlaylistPicker(video, onDone = ::close)
                        return@Column
                    }
                    val later = laterList.any { sameTrack(it.url, video.url) }
                    MenuRow(MkIcons.Clock, stringResource(if (later) R.string.library_later_remove else R.string.library_later_add), null) {
                        val added = library.toggleLater(video)
                        Toast.makeText(context, context.getString(if (added) R.string.library_later_added else R.string.library_later_removed), Toast.LENGTH_SHORT).show()
                        close()
                    }
                    MenuRow(MkIcons.PlaylistAdd, stringResource(R.string.playlist_add), null) { VideoMenu.picking.value = true }
                    MenuRow(MkIcons.EyeOff, stringResource(R.string.not_interested), stringResource(R.string.not_interested_text)) {
                        feedback.notInterested(video)
                        VideoMenu.undo.value = VideoMenu.Undo.Hidden(video)
                        close()
                    }
                    if (video.channelKey() != null && video.channel.isNotBlank()) {
                        MenuRow(MkIcons.Ban, stringResource(R.string.block_channel, video.channel), stringResource(R.string.block_channel_text)) {
                            feedback.blockChannel(video)
                            VideoMenu.undo.value = VideoMenu.Undo.Blocked(video)
                            close()
                        }
                    }
                    MenuRow(MkIcons.Download, stringResource(R.string.download), null) {
                        close()
                        onDownload(video.url)
                    }
                    MenuRow(MkIcons.Share, stringResource(R.string.share), null) {
                        close()
                        shareLink(context, video.title, video.url)
                    }
                }
            }
        }

        UndoBar(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = bottomSpace))
    }
}

/** « Vidéo masquée · Annuler », quelques secondes au-dessus du dock. */
@Composable
private fun UndoBar(modifier: Modifier) {
    val context = LocalContext.current
    val undo by VideoMenu.undo.collectAsState()
    var shown by remember { mutableStateOf<VideoMenu.Undo?>(null) }
    undo?.let { shown = it }

    LaunchedEffect(undo?.id) {
        if (undo != null) {
            delay(5_000)
            VideoMenu.undo.value = null
        }
    }

    AnimatedVisibility(
        undo != null,
        modifier = modifier,
        enter = slideInVertically(tween(260)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(200)),
    ) {
        val item = shown ?: return@AnimatedVisibility
        val message = when (item) {
            is VideoMenu.Undo.Hidden -> stringResource(R.string.video_hidden)
            is VideoMenu.Undo.Blocked -> stringResource(R.string.channel_blocked, item.video.channel)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clay(Pelage.Creme, RoundedCornerShape(26.dp), elevation = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(start = 18.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, Modifier.weight(1f), color = Pelage.Encre, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.undo),
                Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button) {
                        val feedback = Feedback.get(context)
                        when (item) {
                            is VideoMenu.Undo.Hidden -> feedback.undoNotInterested(item.video.url)
                            is VideoMenu.Undo.Blocked -> item.video.channelKey()?.let(feedback::unblock)
                        }
                        VideoMenu.undo.value = null
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                color = Pelage.RouxFonce,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Pelage.Texte, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = Secondary, fontSize = 12.sp)
        }
    }
}

/** Dans le menu : les playlists de vidéos où ajouter [video], et « Nouvelle playlist ». */
@Composable
private fun PlaylistPicker(video: VideoItem, onDone: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Playlists.videos(context) }
    val playlists by store.items.collectAsState()
    var creating by remember { mutableStateOf(playlists.isEmpty()) }
    Text(
        stringResource(R.string.playlist_add),
        Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        color = Pelage.Texte,
        fontSize = 16.sp,
        fontWeight = FontWeight.ExtraBold,
    )
    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
        playlists.forEach { playlist ->
            val inside = playlist.items.any { sameTrack(it.url, video.url) }
            MenuRow(if (inside) MkIcons.Check else MkIcons.PlaylistAdd, playlist.name, pluralStringResource(R.plurals.videos_count, playlist.items.size, playlist.items.size)) {
                val added = store.add(playlist.id, video)
                Toast.makeText(context, context.getString(if (added) R.string.playlist_added_to else R.string.playlist_already_in, playlist.name), Toast.LENGTH_SHORT).show()
                onDone()
            }
        }
    }
    if (creating) {
        NewPlaylistField(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { name ->
            store.create(name, first = video)
            Toast.makeText(context, context.getString(R.string.playlist_added_to, name), Toast.LENGTH_SHORT).show()
            onDone()
        }
    } else {
        MenuRow(MkIcons.Plus, stringResource(R.string.playlist_new), null) { creating = true }
    }
}

/** Partager une vidéo avec le menu de partage d'Android. */
fun shareLink(context: Context, title: String, url: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, title)
        .putExtra(Intent.EXTRA_TEXT, "$title\n$url")
    runCatching { context.startActivity(Intent.createChooser(send, context.getString(R.string.share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
