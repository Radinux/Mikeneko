package app.mikeneko.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import app.mikeneko.ui.components.AnimatedAlertDialog
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
import androidx.compose.ui.res.pluralStringResource
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

/**
 * Où en est le téléchargement d'une playlist. Terminé quand chaque morceau est soit téléchargé, soit
 * indisponible (2.11.12 : avant, un seul morceau impossible à télécharger laissait le bouton « en
 * cours » pour toujours).
 */
private class DownloadState(val on: Boolean, val done: Int, val failed: Int, val total: Int, val working: Boolean) {
    val complete get() = on && done + failed >= total
}

@Composable
private fun rememberDownloadState(downloads: PlaylistDownloads, id: String, items: List<VideoItem>): DownloadState {
    val playlists by downloads.playlists.collectAsState()
    val saved by downloads.saved.collectAsState()
    val unavailable by downloads.unavailable.collectAsState()
    val working by downloads.working.collectAsState()
    val ids = remember(items) { items.filter { !it.isLive }.mapNotNull { videoIdOf(it.url) }.distinct() }
    val done = ids.count { it in saved }
    return DownloadState(id in playlists, done, ids.count { it in unavailable && it !in saved }, ids.size, working)
}

@Composable
private fun DownloadState.label(): String = when {
    complete && failed > 0 -> pluralStringResource(R.plurals.playlist_downloaded_partial, failed, failed)
    complete -> stringResource(R.string.playlist_downloaded)
    on -> stringResource(R.string.playlist_downloading, done, total)
    else -> stringResource(R.string.playlist_download)
}

/** Télécharger / retirer : un premier tap télécharge ; téléchargée, un tap demande avant de retirer. */
@Composable
private fun DownloadToggle(downloads: PlaylistDownloads, id: String, state: DownloadState, videos: Boolean, content: @Composable (onClick: () -> Unit) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    content {
        if (state.on) confirm = true else downloads.setDownloaded(id, true)
    }
    if (confirm) {
        AnimatedAlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Pelage.Coussin,
            titleContentColor = Pelage.Texte,
            textContentColor = Pelage.Gris,
            title = { Text(stringResource(R.string.playlist_download_remove_title), fontWeight = FontWeight.ExtraBold) },
            text = { Text(stringResource(if (videos) R.string.playlist_download_remove_text_videos else R.string.playlist_download_remove_text)) },
            confirmButton = { close ->
                TextButton(onClick = {
                    downloads.setDownloaded(id, false)
                    close()
                }) { Text(stringResource(R.string.playlist_download_remove), color = Color(0xFFFF8A80), fontWeight = FontWeight.ExtraBold) }
            },
            dismissButton = { close ->
                TextButton(onClick = { close() }) { Text(stringResource(R.string.cancel), color = Pelage.Texte) }
            },
        )
    }
}

/** Le rond ⤓ des playlists musique (à côté d'Aléatoire), avec l'anneau de progression. */
@Composable
fun PlaylistDownloadCircle(downloads: PlaylistDownloads, id: String, items: List<VideoItem>) {
    if (items.isEmpty()) return
    val state = rememberDownloadState(downloads, id, items)
    val label = state.label()
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
                    progress = { if (state.total == 0) 0f else (state.done + state.failed).toFloat() / state.total },
                    modifier = Modifier.fillMaxSize(),
                    // En attente d'un nouvel essai (pas de réseau…) : l'anneau pâlit
                    color = if (state.working) Pelage.Roux else Pelage.Roux.copy(alpha = 0.45f),
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
            state.label(),
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
    val unavailable by downloads.unavailable.collectAsState()
    val id = videoIdOf(url) ?: return
    when {
        id in saved -> Icon(MkIcons.Download, stringResource(R.string.track_downloaded), tint = Pelage.Roux, modifier = modifier.size(16.dp))
        id == current -> DownloadRing(downloads, modifier)
        // Impossible à télécharger (supprimé, privé, bloqué…) : une petite croix grise
        id in unavailable -> Icon(MkIcons.Close, stringResource(R.string.track_download_unavailable), tint = Pelage.Gris, modifier = modifier.size(14.dp))
    }
}

/**
 * L'anneau du morceau en cours de téléchargement : il se remplit avec la progression. Plus de roue
 * qui tourne sans fin : elle redessinait la page à chaque image (et le verre de la barre du haut
 * avec), ce qui faisait accrocher le défilement, pour toujours si le téléchargement restait coincé.
 * La progression n'est lue qu'au dessin, et seulement sur cette ligne.
 */
@Composable
private fun DownloadRing(downloads: PlaylistDownloads, modifier: Modifier) {
    val progress = downloads.progress.collectAsState()
    CircularProgressIndicator(
        progress = { progress.value },
        modifier = modifier.size(14.dp),
        color = Pelage.Roux,
        trackColor = Pelage.Roux.copy(alpha = 0.25f),
        strokeWidth = 2.dp,
        gapSize = 0.dp,
    )
}
