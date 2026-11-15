package app.mikeneko.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.data.BackupFile
import app.mikeneko.data.Importer
import app.mikeneko.data.MikenekoBackup
import app.mikeneko.data.Subscription
import app.mikeneko.data.SubscriptionStore
import app.mikeneko.ui.components.AnimatedAlertDialog
import app.mikeneko.ui.theme.Pelage
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Le bouton « Importer un fichier » (3.6.1) : renvoie de quoi ouvrir le sélecteur de fichiers. Le
 * fichier choisi est reconnu ([Importer]) puis présenté avant d'importer : les parties d'une sauvegarde
 * à cocher, le nombre d'abonnements, ou le choix des playlists d'un export de musique.
 */
@Composable
fun rememberImportFile(): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var found by remember { mutableStateOf<Importer.Found?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { readFile(context, uri)?.let { (bytes, name) -> Importer.detect(bytes, name) } }.getOrNull()
            } ?: Importer.Found.Unknown
            if (result is Importer.Found.Unknown) toast(context, context.getString(R.string.import_unknown)) else found = result
        }
    }
    when (val f = found) {
        is Importer.Found.Backup -> BackupPreview(f.content) { found = null }
        is Importer.Found.Subscriptions -> SubscriptionsPreview(f.list) { found = null }
        is Importer.Found.Playlists -> PlaylistImportPicker(f.preview) { found = null }
        else -> Unit
    }
    PlaylistImportSummary()
    return { pick.launch(arrayOf("*/*")) }
}

/** Le bouton « Exporter » (3.6.1) : tout dans un fichier `.mikeneko`. */
@Composable
fun rememberExportBackup(): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupFile.MIME)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val content = MikenekoBackup.collect(context)
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
                    context.contentResolver.openOutputStream(uri)?.use { BackupFile.write(content, version, LocalDate.now().toString(), it) } ?: error("")
                }.isSuccess
            }
            toast(context, context.getString(if (ok) R.string.backup_exported else R.string.backup_export_failed))
        }
    }
    return { export.launch("mikeneko-${LocalDate.now()}.${BackupFile.EXTENSION}") }
}

/** Le fichier et son nom ; null s'il est trop gros pour en être un qu'on sait lire. */
private fun readFile(context: Context, uri: Uri): Pair<ByteArray, String>? {
    val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }.orEmpty()
    val out = ByteArrayOutputStream()
    context.contentResolver.openInputStream(uri)?.use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > Importer.MAX_BYTES) return null
        }
    } ?: return null
    return out.toByteArray() to name
}

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

/** Une sauvegarde : d'où elle vient, et ses parties à cocher (avec ce qui manque ici). */
@Composable
private fun BackupPreview(content: MikenekoBackup.Content, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val parts = remember(content) { BackupFile.Part.entries.filter { BackupFile.count(content, it) > 0 } }
    // Tout est coché, sauf les réglages : ils remplacent ceux de l'appareil, il faut le vouloir
    var chosen by remember(content) { mutableStateOf(parts.filter { it != BackupFile.Part.Settings }.toSet()) }
    var fresh by remember(content) { mutableStateOf<Map<BackupFile.Part, Int>?>(null) }
    LaunchedEffect(content) { fresh = withContext(Dispatchers.Default) { runCatching { MikenekoBackup.newCounts(context, content) }.getOrNull() } }
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pelage.Coussin,
        titleContentColor = Pelage.Texte,
        title = { Text(stringResource(R.string.import_backup_title), fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                if (content.app != null && content.date != null) {
                    Text(stringResource(R.string.import_backup_from, content.app, content.date), color = Pelage.Gris, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                if (parts.isEmpty()) {
                    Text(stringResource(R.string.backup_import_nothing), Modifier.padding(top = 10.dp), color = Pelage.Texte, fontSize = 14.sp)
                }
                Column(Modifier.padding(top = 8.dp).heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    parts.forEach { part ->
                        val total = BackupFile.count(content, part)
                        val detail = when {
                            part == BackupFile.Part.Settings -> stringResource(R.string.import_part_settings_note)
                            fresh != null -> stringResource(R.string.import_part_count, total, fresh?.get(part) ?: 0)
                            else -> total.toString()
                        }
                        CheckRow("${partIcon(part)}  ${stringResource(partLabel(part))}", detail, part in chosen) { on ->
                            chosen = if (on) chosen + part else chosen - part
                        }
                    }
                }
            }
        },
        confirmButton = { close ->
            TextButton(enabled = chosen.isNotEmpty(), onClick = {
                close()
                scope.launch {
                    val added = MikenekoBackup.import(context, BackupFile.only(content, chosen))
                    val text = when {
                        added.total == 0 -> context.getString(R.string.backup_import_nothing)
                        added.total == 1 && added.settings -> context.getString(R.string.settings_restored)
                        added.settings -> addedSummary(context, added) + "\n" + context.getString(R.string.settings_restored)
                        else -> addedSummary(context, added)
                    }
                    toast(context, text)
                }
            }) { Text(stringResource(R.string.import_do), color = if (chosen.isEmpty()) Pelage.Gris else Pelage.RouxClair, fontWeight = FontWeight.ExtraBold) }
        },
        dismissButton = { close -> TextButton(onClick = { close() }) { Text(stringResource(R.string.cancel), color = Pelage.Texte) } },
    )
}

/** Des abonnements d'une autre appli : combien, dont combien de nouveaux. */
@Composable
private fun SubscriptionsPreview(list: List<Subscription>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SubscriptionStore.get(context) }
    val fresh = remember(list) {
        val known = store.items.value.mapTo(HashSet()) { it.channelId }
        list.count { it.channelId !in known }
    }
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pelage.Coussin,
        titleContentColor = Pelage.Texte,
        title = { Text(stringResource(R.string.import_subs_title), fontWeight = FontWeight.ExtraBold) },
        text = {
            Text(
                pluralStringResource(R.plurals.import_subs_found, list.size, list.size, fresh),
                color = Pelage.Texte,
                fontSize = 15.sp,
            )
        },
        confirmButton = { close ->
            TextButton(enabled = fresh > 0, onClick = {
                close()
                store.addAll(list)
                toast(context, context.resources.getQuantityString(R.plurals.subs_imported_count, fresh, fresh))
            }) { Text(stringResource(R.string.import_do), color = if (fresh > 0) Pelage.RouxClair else Pelage.Gris, fontWeight = FontWeight.ExtraBold) }
        },
        dismissButton = { close -> TextButton(onClick = { close() }) { Text(stringResource(R.string.cancel), color = Pelage.Texte) } },
    )
}

@Composable
private fun CheckRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Checkbox) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = Pelage.Roux, checkmarkColor = Pelage.Encre, uncheckedColor = Pelage.Gris),
        )
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, color = if (checked) Pelage.Texte else Pelage.Gris, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = if (checked) Pelage.RouxClair else Pelage.Gris, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun partIcon(part: BackupFile.Part) = when (part) {
    BackupFile.Part.Subscriptions -> "📺"
    BackupFile.Part.MusicPlaylists -> "🎵"
    BackupFile.Part.VideoPlaylists -> "🎬"
    BackupFile.Part.Library -> "💿"
    BackupFile.Part.Saved -> "❤️"
    BackupFile.Part.History -> "🕘"
    BackupFile.Part.Settings -> "⚙️"
}

private fun partLabel(part: BackupFile.Part) = when (part) {
    BackupFile.Part.Subscriptions -> R.string.import_part_subscriptions
    BackupFile.Part.MusicPlaylists -> R.string.import_part_music
    BackupFile.Part.VideoPlaylists -> R.string.import_part_videos
    BackupFile.Part.Library -> R.string.import_part_library
    BackupFile.Part.Saved -> R.string.import_part_saved
    BackupFile.Part.History -> R.string.import_part_history
    BackupFile.Part.Settings -> R.string.import_part_settings
}
