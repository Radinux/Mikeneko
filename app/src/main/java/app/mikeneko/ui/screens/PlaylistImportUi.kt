package app.mikeneko.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import app.mikeneko.ui.components.AnimatedAlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.data.PlaylistImport
import app.mikeneko.ui.theme.Pelage
import kotlin.math.ceil

/** L'import en cours (sous la ligne « Importer des playlists ») : progression et « Arrêter ». */
@Composable
fun PlaylistImportProgress() {
    val state by PlaylistImport.state.collectAsState()
    val running = state as? PlaylistImport.State.Running ?: return
    Column(Modifier.fillMaxWidth().padding(start = 52.dp, end = 16.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.import_running, running.done, running.total),
                Modifier.weight(1f),
                color = Pelage.RouxClair,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.import_stop),
                Modifier.clip(CircleShape).clickable(role = Role.Button) { PlaylistImport.stop() }.padding(horizontal = 12.dp, vertical = 8.dp),
                color = Color(0xFFFF8A80),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        LinearProgressIndicator(
            progress = { if (running.total > 0) running.done.toFloat() / running.total else 0f },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            color = Pelage.Roux,
            trackColor = Color.White.copy(alpha = 0.12f),
        )
    }
}

/** Choisir quoi importer : une case par playlist (et pour les artistes, les albums), avec le temps estimé. */
@Composable
fun PlaylistImportPicker(preview: PlaylistImport.Preview, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val groups = preview.groups
    // Un petit fichier : tout est coché ; un gros (des centaines de recherches) : on choisit
    var chosen by remember { mutableStateOf(if (preview.searches(groups.toSet()) <= 150) groups.toSet() else emptySet()) }
    val searches = preview.searches(chosen)
    val minutes = ceil(PlaylistImport.estimateSeconds(searches) / 60.0).toInt()
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pelage.Coussin,
        titleContentColor = Pelage.Texte,
        title = { Text(stringResource(R.string.import_pick_title), fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                PickRow("✨  " + stringResource(R.string.import_pick_all), chosen.size == groups.size) { on ->
                    chosen = if (on) groups.toSet() else emptySet()
                }
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    groups.forEach { group ->
                        val icon = when (group.kind) {
                            PlaylistImport.Kind.Song -> "🎵"
                            PlaylistImport.Kind.Artist -> "🎤"
                            PlaylistImport.Kind.Album -> "💿"
                        }
                        PickRow("$icon  ${group.name} · ${group.count}", group in chosen) { on ->
                            chosen = if (on) chosen + group else chosen - group
                        }
                    }
                }
                Text(
                    stringResource(R.string.import_pick_estimate, searches, minutes),
                    Modifier.padding(top = 10.dp),
                    color = Pelage.Gris,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        confirmButton = { close ->
            TextButton(enabled = chosen.isNotEmpty(), onClick = {
                PlaylistImport.start(context, preview, chosen)
                close()
            }) { Text(stringResource(R.string.import_start), color = if (chosen.isEmpty()) Pelage.Gris else Pelage.RouxClair, fontWeight = FontWeight.ExtraBold) }
        },
        dismissButton = { close -> TextButton(onClick = { close() }) { Text(stringResource(R.string.cancel), color = Pelage.Texte) } },
    )
}

@Composable
private fun PickRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Checkbox) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = Pelage.Roux, checkmarkColor = Pelage.Encre, uncheckedColor = Pelage.Gris),
        )
        Text(
            title,
            Modifier.padding(start = 10.dp),
            color = if (checked) Pelage.Texte else Pelage.Gris,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Le résumé à la fin : ajoutés, doublons fusionnés, et ce qui n'a pas été trouvé. */
@Composable
fun PlaylistImportSummary() {
    val state by PlaylistImport.state.collectAsState()
    val result = (state as? PlaylistImport.State.Done)?.result ?: return
    AnimatedAlertDialog(
        onDismissRequest = { PlaylistImport.dismiss() },
        containerColor = Pelage.Coussin,
        titleContentColor = Pelage.Texte,
        textContentColor = Pelage.Texte,
        title = { Text(stringResource(R.string.import_done_title), fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.import_done_text, result.songsAdded, result.playlists, result.artists, result.albums, result.duplicates),
                    fontSize = 14.sp,
                )
                if (result.refused) Text(stringResource(R.string.import_refused), Modifier.padding(top = 8.dp), color = Color(0xFFFF8A80), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                else if (result.stopped) Text(stringResource(R.string.import_stopped), Modifier.padding(top = 8.dp), color = Pelage.RouxClair, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (result.notFound.isNotEmpty()) {
                    Text(stringResource(R.string.import_not_found, result.notFound.size), Modifier.padding(top = 12.dp, bottom = 4.dp), color = Pelage.Gris, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    result.notFound.take(100).forEach { Text("• $it", color = Pelage.Gris, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        },
        confirmButton = { close -> TextButton(onClick = { close() }) { Text(stringResource(R.string.import_ok), color = Pelage.RouxClair, fontWeight = FontWeight.ExtraBold) } },
    )
}
