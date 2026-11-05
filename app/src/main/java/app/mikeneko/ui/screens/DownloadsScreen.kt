package app.mikeneko.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.mikeneko.R
import app.mikeneko.data.Downloads
import app.mikeneko.data.youtubeLinkIn
import app.mikeneko.ui.components.EmptyCat
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.Rounded
import app.mikeneko.ui.theme.clay
import app.mikeneko.ui.theme.jellyClickable
import app.mikeneko.ui.theme.statusBarSpace

@Composable
fun DownloadsContent(prefilledLink: String, top: Dp = 24.dp) {
    val context = LocalContext.current
    val tasks by Downloads.tasks.collectAsState()
    // yt-dlp s'installe dès qu'on ouvre l'onglet : il sera prêt quand on lancera le téléchargement
    LaunchedEffect(Unit) { Downloads.prepare(context) }
    var link by remember { mutableStateOf(prefilledLink) }
    var format by remember { mutableStateOf(Downloads.Format.FLAC) }
    var cutSponsors by remember { mutableStateOf(true) }

    LaunchedEffect(prefilledLink) { if (prefilledLink.isNotEmpty()) link = prefilledLink }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = WindowInsets.statusBarSpace.asPaddingValues().calculateTopPadding() + top, bottom = 200.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.downloads_title), color = Pelage.Texte, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                Text(stringResource(R.string.downloads_subtitle), color = Pelage.Gris, fontSize = 13.sp)
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Pelage.Coussin).border(1.5.dp, Pelage.Bord, RoundedCornerShape(22.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.downloads_link_label), color = Pelage.Texte, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(Pelage.Nuit).border(1.5.dp, Pelage.Bord, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicTextField(
                            value = link,
                            onValueChange = { link = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Pelage.Texte, fontSize = 14.sp, fontFamily = Rounded),
                            cursorBrush = SolidColor(Pelage.Roux),
                            decorationBox = { field ->
                                if (link.isEmpty()) Text("https://youtu.be/…", color = Pelage.Gris, fontSize = 14.sp)
                                field()
                            },
                        )
                    }
                    Box(
                        Modifier.height(48.dp).clip(RoundedCornerShape(14.dp)).background(Pelage.Roux).clickable(role = Role.Button) {
                            val url = youtubeLinkIn(link) ?: link.trim()
                            if (url.startsWith("http")) {
                                Downloads.enqueue(context, url, format, cutSponsors)
                                link = ""
                            } else {
                                Toast.makeText(context, context.getString(R.string.downloads_paste_link), Toast.LENGTH_SHORT).show()
                            }
                        }.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.add), color = Pelage.Encre, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Downloads.Format.entries.forEach { f ->
                        val on = f == format
                        Box(
                            Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Pelage.Roux else Pelage.Creme).clickable(role = Role.RadioButton) { format = f },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(formatLabel(f), color = Pelage.Encre, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
                Row(Modifier.clickable(role = Role.Checkbox) { cutSponsors = !cutSponsors }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = cutSponsors, onCheckedChange = { cutSponsors = it }, colors = CheckboxDefaults.colors(checkedColor = Pelage.Roux, checkmarkColor = Pelage.Encre))
                    Text(stringResource(R.string.downloads_cut_sponsors), color = Pelage.Texte, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Icon(MkIcons.Paw, null, tint = Pelage.Dore, modifier = Modifier.padding(start = 6.dp).size(16.dp))
                }
            }
        }
        if (tasks.isEmpty()) {
            item { EmptyCat(stringResource(R.string.downloads_empty), stringResource(R.string.downloads_empty_text)) }
        }
        items(tasks, key = { it.id }) { task ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(task.file?.nameWithoutExtension ?: task.url, color = Pelage.Texte, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        when (task.status) {
                            Downloads.Status.EN_ATTENTE -> stringResource(R.string.download_waiting, formatLabel(task.format))
                            Downloads.Status.EN_COURS -> stringResource(R.string.download_progress, formatLabel(task.format), (task.progress * 100).toInt()) + if (task.eta > 0) stringResource(R.string.download_eta, task.eta.toInt()) else ""
                            Downloads.Status.TERMINE -> stringResource(R.string.download_done, formatLabel(task.format))
                            Downloads.Status.ERREUR -> stringResource(R.string.download_error, task.error.orEmpty().take(80))
                        },
                        color = Pelage.Gris,
                        fontSize = 12.sp,
                    )
                    if (task.status == Downloads.Status.EN_COURS) {
                        LinearProgressIndicator(progress = { task.progress }, modifier = Modifier.fillMaxWidth(), color = Pelage.Roux, trackColor = Pelage.Bord)
                    }
                }
                val file = task.file
                if (task.status == Downloads.Status.TERMINE && file != null) {
                    Text(
                        stringResource(R.string.open),
                        Modifier.jellyClickable {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fichiers", file)
                            val open = Intent(Intent.ACTION_VIEW).setDataAndType(uri, context.contentResolver.getType(uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            runCatching { context.startActivity(open) }
                        }.clay(Pelage.Bord).padding(horizontal = 14.dp, vertical = 11.dp),
                        color = Pelage.Texte,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                } else {
                    Box(
                        Modifier.jellyClickable { Downloads.cancel(task.id) }.size(44.dp).clay(Pelage.Bord),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(MkIcons.Close, stringResource(R.string.cancel), tint = Pelage.Texte, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** « Vidéo » / « Video » ; les formats audio gardent leur nom (MP3, FLAC…). */
@Composable
private fun formatLabel(format: Downloads.Format): String =
    if (format == Downloads.Format.VIDEO) stringResource(R.string.format_video) else format.label
