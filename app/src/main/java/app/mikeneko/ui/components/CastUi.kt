package app.mikeneko.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.cast.Cast
import app.mikeneko.player.PlayerHolder
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage

/**
 * Le bouton Chromecast (3.4.0) : roux quand une session est ouverte. Il ouvre [CastPicker].
 */
@Composable
fun CastButton(backdrop: Backdrop, size: Dp = 44.dp, modifier: Modifier = Modifier, material: Boolean = false, onOpen: () -> Unit = {}) {
    val session by Cast.session.collectAsState()
    var open by remember { mutableStateOf(false) }
    val connected = session?.phase == Cast.Phase.Connected
    // Le lecteur musique (3.8.0) : un bouton Material 3 (sur son fond uni, le verre ne se voyait pas)
    if (material) {
        androidx.compose.material3.FilledTonalIconButton(
            onClick = { onOpen(); open = true },
            modifier = modifier.size(size),
            colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (connected) Pelage.Roux else Color.White.copy(alpha = 0.14f),
                contentColor = if (connected) Pelage.Encre else Color.White,
            ),
        ) { Icon(MkIcons.Cast, stringResource(R.string.cast), modifier = Modifier.size(size * 0.46f)) }
        if (open) CastPicker { open = false }
        return
    }
    GlassCircleButton(
        backdrop,
        MkIcons.Cast,
        stringResource(R.string.cast),
        modifier,
        size = size,
        tint = if (connected) Pelage.Roux.copy(alpha = 0.7f) else Color(0x40100D0B),
        iconTint = if (connected) Pelage.Encre else Color.White,
    ) {
        onOpen()
        open = true
    }
    if (open) CastPicker { open = false }
}

/** Choisir l'appareil (trouvé sur le Wi-Fi), ou arrêter et régler le volume de celui où l'on caste. */
@Composable
fun CastPicker(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val devices by Cast.devices.collectAsState()
    val session by Cast.session.collectAsState()
    val volume by Cast.volume.collectAsState()
    val now by PlayerHolder.nowPlaying.collectAsState()
    DisposableEffect(Unit) {
        Cast.startDiscovery(context)
        onDispose { Cast.stopDiscovery(context) }
    }
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cast_title), fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val current = session
                when {
                    current != null && current.phase == Cast.Phase.Connected -> {
                        Text(stringResource(R.string.cast_playing_on, current.device.name), color = Pelage.RouxClair, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(MkIcons.Speaker, stringResource(R.string.cast_volume), tint = Pelage.Texte, modifier = Modifier.size(20.dp))
                            Slider(
                                value = volume,
                                onValueChange = { Cast.setVolume(it) },
                                modifier = Modifier.padding(start = 8.dp),
                                colors = SliderDefaults.colors(thumbColor = Pelage.Roux, activeTrackColor = Pelage.Roux),
                            )
                        }
                    }
                    current != null && current.error ->
                        Text(stringResource(R.string.cast_connect_failed, current.device.name), color = Color(0xFFFF8A80), fontSize = 14.sp)
                    current != null ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Pelage.Roux)
                            Text(stringResource(R.string.cast_connecting, current.device.name), Modifier.padding(start = 10.dp), color = Pelage.Texte, fontSize = 14.sp)
                        }
                    devices.isEmpty() ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Pelage.Roux)
                            Text(stringResource(R.string.cast_searching), Modifier.padding(start = 10.dp), color = Pelage.Gris, fontSize = 14.sp)
                        }
                }
                if (current?.phase != Cast.Phase.Connected) {
                    devices.forEach { device ->
                        val video = now?.audioOnly == false
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(role = Role.Button) {
                                    Cast.connect(context, device)
                                }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(if (device.audioOnly) MkIcons.Speaker else MkIcons.Cast, null, tint = Pelage.Texte, modifier = Modifier.size(22.dp))
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(device.name, color = Pelage.Texte, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                val detail = listOfNotNull(
                                    device.model.ifBlank { null },
                                    if (device.audioOnly && video) stringResource(R.string.cast_sound_only) else null,
                                ).joinToString(" · ")
                                if (detail.isNotEmpty()) Text(detail, color = Pelage.Gris, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { close ->
            if (session?.phase == Cast.Phase.Connected) {
                TextButton(onClick = { Cast.disconnect(); close() }) {
                    Text(stringResource(R.string.cast_stop), color = Color(0xFFFF8A80), fontWeight = FontWeight.ExtraBold)
                }
            } else {
                TextButton(onClick = { close() }) { Text(stringResource(R.string.sync_close), color = Pelage.Texte) }
            }
        },
    )
}

/**
 * Pendant le cast (3.6.0) : une capsule « Lecture sur … » de la couleur du thème, pour voir d'un coup
 * d'œil où part le son. Un tap ouvre [CastPicker] (volume, arrêter).
 */
@Composable
fun CastIndicator(modifier: Modifier = Modifier) {
    val session by Cast.session.collectAsState()
    val current = session ?: return
    if (current.phase != Cast.Phase.Connected) return
    var open by remember { mutableStateOf(false) }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Pelage.Roux.copy(alpha = 0.28f))
            .clickable(role = Role.Button) { open = true }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (current.device.audioOnly) MkIcons.Speaker else MkIcons.Cast, null, tint = Pelage.RouxClair, modifier = Modifier.size(16.dp))
        Text(
            stringResource(R.string.cast_playing_on, current.device.name),
            Modifier.padding(start = 6.dp),
            color = Pelage.RouxClair,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
    if (open) CastPicker { open = false }
}

/** Sur la vidéo pendant le cast : « Lecture sur … » (le téléphone n'affiche rien d'autre). */
@Composable
fun CastingOverlay(modifier: Modifier = Modifier) {
    val session by Cast.session.collectAsState()
    val current = session ?: return
    if (current.phase != Cast.Phase.Connected) return
    Column(
        modifier.background(Color(0xCC100D0B)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(MkIcons.Cast, null, tint = Pelage.RouxClair, modifier = Modifier.size(36.dp))
        Text(stringResource(R.string.cast_playing_on, current.device.name), Modifier.padding(top = 8.dp), color = Pelage.Texte, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
