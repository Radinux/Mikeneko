package app.mikeneko.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mikeneko.R
import app.mikeneko.data.VideoItem
import app.mikeneko.data.videoIdOf
import app.mikeneko.player.PlaylistDownloads
import app.mikeneko.ui.components.Capsule
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.jellyClickable

/** Où en est le téléchargement d'une playlist. */
private class DownloadState(val on: Boolean, val done: Int, val total: Int) {
    val complete get() = on && done >= total
}

@Composable
private fun rememberDownloadState(downloads: PlaylistDownloads, id: String, items: List<VideoItem>): DownloadState {
    val playlists by downloads.playlists.collectAsState()
    val saved by downloads.saved.collectAsState()
    val ids = items.filter { !it.isLive }.mapNotNull { videoIdOf(it.url) }.distinct()
    return DownloadState(id in playlists, ids.count { it in saved }, ids.size)
}

/** Télécharger / retirer : un premier tap télécharge ; téléchargée, un tap demande avant de retirer. */
@Composable
private fun DownloadToggle(downloads: PlaylistDownloads, id: String, state: DownloadState, videos: Boolean, content: @Composable (onClick: () -> Unit) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    content {
        if (state.on) confirm = true else downloads.setDownloaded(id, true)
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Pelage.Coussin,
            titleContentColor = Pelage.Texte,
            textContentColor = Pelage.Gris,
            title = { Text(stringResource(R.string.playlist_download_remove_title), fontWeight = FontWeight.ExtraBold) },
            text = { Text(stringResource(if (videos) R.string.playlist_download_remove_text_videos else R.string.playlist_download_remove_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    downloads.setDownloaded(id, false)
                }) { Text(stringResource(R.string.playlist_download_remove), color = Color(0xFFFF8A80), fontWeight = FontWeight.ExtraBold) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel), color = Pelage.Texte) }
            },
        )
    }
}

/** Le rond ⤓ des playlists musique (à côté d'Aléatoire), avec l'anneau de progression. */
@Composable
fun PlaylistDownloadCircle(downloads: PlaylistDownloads, id: String, items: List<VideoItem>) {
    if (items.isEmpty()) return
    val state = rememberDownloadState(downloads, id, items)
    val label = when {
        state.complete -> stringResource(R.string.playlist_downloaded)
        state.on -> stringResource(R.string.playlist_downloading, state.done, state.total)
        else -> stringResource(R.string.playlist_download)
    }
    DownloadToggle(downloads, id, state, videos = false) { onClick ->
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (state.complete) Pelage.Roux else Pelage.Creme)
                .jellyClickable(role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = label
                    stateDescription = label
                },
            contentAlignment = Alignment.Center,
        ) {
            if (state.on && !state.complete) {
                CircularProgressIndicator(
                    progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
                    modifier = Modifier.fillMaxSize(),
                    color = Pelage.Roux,
                    trackColor = Pelage.Roux.copy(alpha = 0.2f),
                    strokeWidth = 3.dp,
                )
            }
            Icon(MkIcons.Download, null, tint = Pelage.Encre, modifier = Modifier.size(22.dp))
        }
    }
}

/** La capsule « Télécharger » des playlists vidéo (dans la rangée de boutons de la playlist). */
@Composable
fun PlaylistDownloadCapsule(downloads: PlaylistDownloads, id: String, items: List<VideoItem>) {
    if (items.isEmpty()) return
    val state = rememberDownloadState(downloads, id, items)
    DownloadToggle(downloads, id, state, videos = true) { onClick ->
        Capsule(
            when {
                state.complete -> stringResource(R.string.playlist_downloaded)
                state.on -> stringResource(R.string.playlist_downloading, state.done, state.total)
                else -> stringResource(R.string.playlist_download)
            },
            selected = state.complete,
            icon = if (state.complete) MkIcons.Check else MkIcons.Download,
            onClick = onClick,
        )
    }
}

/** Le petit ⤓ d'un élément hors ligne (ou un anneau pendant son téléchargement). */
@Composable
fun DownloadedMark(downloads: PlaylistDownloads, url: String, modifier: Modifier = Modifier) {
    val saved by downloads.saved.collectAsState()
    val current by downloads.current.collectAsState()
    val id = videoIdOf(url) ?: return
    when {
        id in saved -> Icon(MkIcons.Download, stringResource(R.string.track_downloaded), tint = Pelage.Roux, modifier = modifier.size(16.dp))
        id == current -> CircularProgressIndicator(modifier.size(14.dp), color = Pelage.Roux, strokeWidth = 2.dp)
    }
}
