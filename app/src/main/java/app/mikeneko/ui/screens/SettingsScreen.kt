package app.mikeneko.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.mikeneko.R
import app.mikeneko.data.Comments
import app.mikeneko.data.Feedback
import app.mikeneko.data.ForYou
import app.mikeneko.data.MikenekoBackup
import app.mikeneko.data.SubscriptionImport
import app.mikeneko.data.SubscriptionStore
import app.mikeneko.data.WatchHistory
import app.mikeneko.data.WatchPositions
import app.mikeneko.data.YouTubeRepository
import app.mikeneko.player.OfflineCache
import app.mikeneko.player.PlayerHolder
import app.mikeneko.player.StableVolume
import app.mikeneko.settings.AppLocale
import app.mikeneko.settings.AppSettings
import app.mikeneko.ui.components.ambientSupported
import app.mikeneko.ui.components.sponsorLabel
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.statusBarSpacePadding
import app.mikeneko.update.AppUpdates
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val Secondary = Color(0xFFCFC6BD)
private val QUALITIES = listOf(2160, 1440, 1080, 720, 480, 360, 240, 144)
private val SLEEP_DELAYS = listOf(30, 45, 60, 90, 120)

/** Les réglages de l'appli : langue, lecture, SponsorBlock, historique, bien-être, compte, à propos. */
@Composable
fun SettingsContent(onBack: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.statusBarSpacePadding().padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = stringResource(R.string.back), onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(MkIcons.ChevronLeft, stringResource(R.string.back), tint = Pelage.Texte, modifier = Modifier.size(26.dp))
                }
                Text(
                    stringResource(R.string.settings),
                    Modifier.padding(start = 6.dp).semantics { heading() },
                    color = Pelage.Texte,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
        item { LanguageSection(context) }
        item { PlaybackSection() }
        item { SponsorBlockSection() }
        item { HistorySection(context) }
        item { OfflineSection(context) }
        item { BackupSection(context) }
        item { WellbeingSection() }
        item { AboutSection(context) }
    }
}

// ---------------------------------------------------------------- sections

/** Le fichier de sauvegarde Mikeneko (.xml) : l'exporter, ou en importer un (sans rien effacer). */
@Composable
private fun BackupSection(context: Context) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    val export = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/xml")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching {
                    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
                    context.contentResolver.openOutputStream(uri)?.use { MikenekoBackup.export(context, it, version) } ?: error("")
                }.isSuccess
            }
            toast(context.getString(if (ok) R.string.backup_exported else R.string.backup_export_failed))
        }
    }
    val import = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val content = kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { MikenekoBackup.read(it) } }.getOrNull()
            }
            if (content == null) {
                toast(context.getString(R.string.backup_import_failed))
                return@launch
            }
            val added = MikenekoBackup.import(context, content)
            toast(
                if (added.total == 0) context.getString(R.string.backup_import_nothing)
                else context.getString(R.string.backup_imported, added.channels, added.playlists, added.library, added.videos),
            )
        }
    }
    // Les abonnements d'autres applis (NewPipe .json, FreeTube .db, Google Takeout .csv) : venu de
    // l'onglet Abonnements en 1.18.0, où la grande carte d'import restait toujours affichée
    val importSubs = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
            }
            val list = SubscriptionImport.parse(text)
            SubscriptionStore.get(context).addAll(list)
            toast(if (list.isEmpty()) context.getString(R.string.subs_import_none) else context.resources.getQuantityString(R.plurals.subs_imported_count, list.size, list.size))
        }
    }
    Section(stringResource(R.string.settings_section_backup)) {
        ActionRow(MkIcons.Subscriptions, stringResource(R.string.settings_import_subs), stringResource(R.string.settings_import_subs_text), Pelage.Texte) {
            importSubs.launch(arrayOf("application/json", "text/*", "application/xml", "application/octet-stream"))
        }
        Divider()
        ActionRow(MkIcons.Share, stringResource(R.string.backup_export), stringResource(R.string.backup_export_text), Pelage.Texte) {
            export.launch("mikeneko-${LocalDate.now()}.xml")
        }
        Divider()
        ActionRow(MkIcons.Download, stringResource(R.string.backup_import), stringResource(R.string.backup_import_text), Pelage.Texte) {
            import.launch(arrayOf("text/xml", "application/xml", "text/*", "application/octet-stream"))
        }
    }
}

/** La sauvegarde hors ligne : sa place, et de quoi la vider (rien n'y est listé, l'appli s'en sert seule). */
@Composable
private fun OfflineSection(context: Context) {
    var size by remember { mutableStateOf<Triple<Long, Long, Long>?>(null) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        size = kotlinx.coroutines.withContext(Dispatchers.IO) {
            val cache = OfflineCache
            Triple(
                cache.size(context, OfflineCache.Store.Music),
                cache.size(context, OfflineCache.Store.Videos),
                cache.size(context, OfflineCache.Store.SavedMusic) + cache.size(context, OfflineCache.Store.SavedVideos),
            )
        }
    }
    Section(stringResource(R.string.settings_section_offline)) {
        val (music, videos, saved) = size ?: Triple(0L, 0L, 0L)
        ActionRow(
            icon = MkIcons.Trash,
            title = stringResource(R.string.settings_offline_clear),
            subtitle = stringResource(R.string.settings_offline_text) + "\n" + stringResource(
                R.string.settings_offline_size,
                Formatter.formatShortFileSize(context, music),
                Formatter.formatShortFileSize(context, videos),
            ) + "\n" + stringResource(R.string.settings_offline_saved, Formatter.formatShortFileSize(context, saved)),
            color = Color(0xFFFF8A80),
        ) { confirm = true }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Pelage.Coussin,
            titleContentColor = Pelage.Texte,
            textContentColor = Secondary,
            title = { Text(stringResource(R.string.settings_offline_confirm_title), fontWeight = FontWeight.ExtraBold) },
            text = { Text(stringResource(R.string.settings_offline_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    Thread { runCatching { OfflineCache.clear(context) } }.start()
                    size = size?.copy(first = 0L, second = 0L)
                }) { Text(stringResource(R.string.settings_offline_confirm), color = Color(0xFFFF8A80), fontWeight = FontWeight.ExtraBold) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel), color = Pelage.Texte) }
            },
        )
    }
}

@Composable
private fun LanguageSection(context: Context) {
    val language by AppSettings.language.collectAsState()
    val region by AppSettings.region.collectAsState()
    val systemLabel = stringResource(R.string.settings_language_system)
    Section(stringResource(R.string.settings_section_language)) {
        ChoiceRow(
            icon = MkIcons.Globe,
            title = stringResource(R.string.settings_language),
            options = listOf(AppSettings.LANGUAGE_SYSTEM to systemLabel) + AppLocale.LANGUAGES.map { it.key to it.value },
            selected = language,
        ) { value ->
            if (value == language) return@ChoiceRow
            AppSettings.setLanguage(value)
            refreshContent()
            context.findActivity()?.recreate() // l'interface se redessine dans la nouvelle langue
        }
        Divider()
        val autoLabel = stringResource(R.string.settings_region_auto, AppLocale.regionName(AppLocale.region().takeIf { region == AppSettings.REGION_AUTO } ?: systemRegion()))
        ChoiceRow(
            icon = MkIcons.MapPin,
            title = stringResource(R.string.settings_region),
            subtitle = stringResource(R.string.settings_region_text),
            options = listOf(AppSettings.REGION_AUTO to autoLabel) +
                AppLocale.REGIONS.map { it to AppLocale.regionName(it) }.sortedBy { it.second },
            selected = region,
        ) { value ->
            if (value == region) return@ChoiceRow
            AppSettings.setRegion(value)
            refreshContent()
        }
    }
}

@Composable
private fun PlaybackSection() {
    val quality by PlayerHolder.quality.collectAsState()
    val autoPip by AppSettings.autoPip.collectAsState()
    val hideStatusBar by AppSettings.hideStatusBar.collectAsState()
    val ambient by PlayerHolder.ambient.collectAsState()
    val stable by PlayerHolder.stableVolume.collectAsState()
    val autoplay by PlayerHolder.autoplay.collectAsState()
    val context = LocalContext.current
    Section(stringResource(R.string.settings_section_playback)) {
        ChoiceRow(
            icon = MkIcons.Quality,
            title = stringResource(R.string.settings_default_quality),
            options = listOf<Pair<Int?, String>>(null to stringResource(R.string.quality_auto)) + QUALITIES.map { it to "${it}p" },
            selected = quality,
        ) { PlayerHolder.setQuality(it) }
        Divider()
        SwitchRow(MkIcons.Next, stringResource(R.string.autoplay), stringResource(R.string.autoplay_text), autoplay) { PlayerHolder.setAutoplay(it) }
        Divider()
        SwitchRow(MkIcons.Bubble, stringResource(R.string.settings_auto_pip), stringResource(R.string.settings_auto_pip_text), autoPip) { AppSettings.setAutoPip(it) }
        Divider()
        SwitchRow(MkIcons.Fullscreen, stringResource(R.string.settings_hide_status_bar), stringResource(R.string.settings_hide_status_bar_text), hideStatusBar) {
            AppSettings.setHideStatusBar(it)
        }
        if (ambientSupported) {
            Divider()
            SwitchRow(MkIcons.Ambient, stringResource(R.string.ambient_mode), stringResource(R.string.ambient_mode_text), ambient) { PlayerHolder.setAmbient(it) }
        }
        if (StableVolume.supported) {
            Divider()
            SwitchRow(MkIcons.Waves, stringResource(R.string.stable_volume), stringResource(R.string.stable_volume_text), stable) { on ->
                if (!PlayerHolder.setStableVolume(on)) {
                    Toast.makeText(context, context.getString(R.string.stable_volume_refused), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
private fun SponsorBlockSection() {
    val enabled by PlayerHolder.autoSkip.collectAsState()
    val skip by AppSettings.skipCategories.collectAsState()
    Section(stringResource(R.string.settings_section_sponsorblock)) {
        SwitchRow(MkIcons.Paw, stringResource(R.string.settings_sponsorblock), stringResource(R.string.settings_sponsorblock_text), enabled) {
            PlayerHolder.setAutoSkip(it)
        }
        AnimatedVisibility(enabled, enter = expandVertically(), exit = shrinkVertically()) {
            Column(Modifier.padding(bottom = 6.dp)) {
                AppSettings.SPONSOR_CATEGORIES.forEach { category ->
                    CheckRow(sponsorLabel(category), category in skip) { AppSettings.setSkipCategory(category, it) }
                }
            }
        }
    }
}

@Composable
private fun HistorySection(context: Context) {
    val paused by AppSettings.historyPaused.collectAsState()
    val history by remember { WatchHistory.get(context) }.entries.collectAsState()
    val feedback = remember { Feedback.get(context) }
    val hidden by feedback.hidden.collectAsState()
    val blocked by feedback.blocked.collectAsState()
    var blockedOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Section(stringResource(R.string.settings_section_foryou)) {
        SwitchRow(MkIcons.Clock, stringResource(R.string.settings_history_pause), stringResource(R.string.settings_history_pause_text), paused) {
            AppSettings.setHistoryPaused(it)
        }
        Divider()
        ActionRow(
            icon = MkIcons.Trash,
            title = stringResource(R.string.settings_history_clear),
            subtitle = stringResource(R.string.settings_history_clear_text) + "\n" + stringResource(R.string.settings_history_count, history.size),
            color = Color(0xFFFF8A80),
        ) { confirm = true }
        Divider()
        // Chaînes bloquées avec « Ne pas recommander »
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clickable(enabled = blocked.isNotEmpty(), role = Role.Button) { blockedOpen = !blockedOpen }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MkIcons.Ban, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(stringResource(R.string.settings_blocked_channels), color = Pelage.Texte, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (blocked.isEmpty()) stringResource(R.string.settings_blocked_none) else blocked.joinToString(", ") { it.name },
                    color = if (blocked.isEmpty()) Secondary else Pelage.RouxClair,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (blocked.isNotEmpty()) Icon(if (blockedOpen) MkIcons.ChevronDown else MkIcons.ChevronRight, null, tint = Secondary, modifier = Modifier.size(18.dp))
        }
        AnimatedVisibility(blockedOpen && blocked.isNotEmpty(), enter = expandVertically(), exit = shrinkVertically()) {
            Column(Modifier.padding(bottom = 6.dp)) {
                blocked.forEach { channel ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 52.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(channel.name, Modifier.weight(1f), color = Pelage.Texte, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(R.string.unblock),
                            Modifier.clip(CircleShape).clickable(role = Role.Button) { feedback.unblock(channel.key) }.padding(horizontal = 12.dp, vertical = 10.dp),
                            color = Pelage.RouxClair,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }
        if (hidden.isNotEmpty()) {
            Divider()
            ActionRow(
                icon = MkIcons.EyeOff,
                title = stringResource(R.string.settings_hidden_videos),
                subtitle = stringResource(R.string.settings_hidden_count, hidden.size),
                color = Color.White,
            ) {
                feedback.clearHidden()
                Toast.makeText(context, context.getString(R.string.settings_hidden_cleared), Toast.LENGTH_SHORT).show()
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Pelage.Coussin,
            titleContentColor = Pelage.Texte,
            textContentColor = Secondary,
            title = { Text(stringResource(R.string.settings_history_confirm_title), fontWeight = FontWeight.ExtraBold) },
            text = { Text(stringResource(R.string.settings_history_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    WatchHistory.get(context).clear()
                    WatchPositions.clear(context)
                    SubscriptionStore.get(context).clearWatched()
                    ForYou.clearCache()
                    confirm = false
                    Toast.makeText(context, context.getString(R.string.settings_history_cleared), Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.settings_history_confirm), color = Color(0xFFFF8A80), fontWeight = FontWeight.ExtraBold) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel), color = Pelage.Texte) }
            },
        )
    }
}

@Composable
private fun WellbeingSection() {
    val enabled by AppSettings.sleepReminder.collectAsState()
    val minutes by AppSettings.sleepMinutes.collectAsState()
    Section(stringResource(R.string.settings_section_wellbeing)) {
        SwitchRow(MkIcons.Moon, stringResource(R.string.settings_sleep_reminder), stringResource(R.string.settings_sleep_reminder_text), enabled) {
            AppSettings.setSleepReminder(it)
        }
        AnimatedVisibility(enabled, enter = expandVertically(), exit = shrinkVertically()) {
            Column {
                Divider()
                ChoiceRow(
                    icon = MkIcons.Clock,
                    title = stringResource(R.string.settings_sleep_after),
                    options = SLEEP_DELAYS.map { it to durationLabel(it) },
                    selected = minutes,
                ) { AppSettings.setSleepMinutes(it) }
            }
        }
    }
}

@Composable
private fun AboutSection(context: Context) {
    val uri = LocalUriHandler.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    Section(stringResource(R.string.settings_section_about)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(MkIcons.Info, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Column(Modifier.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Mikeneko · " + stringResource(R.string.settings_version, version), color = Pelage.Texte, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.settings_about_text), color = Secondary, fontSize = 13.sp)
                UpdateRow(context)
                Text(stringResource(R.string.settings_credits), Modifier.padding(top = 8.dp), color = Pelage.Texte, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                CREDITS.forEach { (name, url) ->
                    Text(
                        name,
                        Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button) { runCatching { uri.openUri(url) } }.padding(vertical = 6.dp),
                        color = Pelage.RouxClair,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * Les mises à jour depuis GitHub (voir AppUpdates) : l'état et un bouton (vérifier, autoriser
 * l'installation, installer maintenant).
 */
@Composable
private fun UpdateRow(context: Context) {
    val updates = AppUpdates
    val state by updates.state.collectAsState()
    val uri = LocalUriHandler.current
    // Revenir des réglages Android (« installer des applis inconnues ») : on relit le droit
    var allowed by remember { mutableStateOf(updates.canInstall(context)) }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = updates.canInstall(context)
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    fun allowInstalls() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
            )
        }
    }

    val row: Triple<String, String?, (() -> Unit)?> = when (val s = state) {
        is AppUpdates.State.Checking -> Triple(stringResource(R.string.update_checking), null, null)
        is AppUpdates.State.Downloading ->
            Triple(stringResource(R.string.update_downloading, s.release.version, (s.progress * 100).toInt()), null, null)
        is AppUpdates.State.Ready ->
            if (allowed) {
                Triple(stringResource(R.string.update_ready_label, s.release.version), stringResource(R.string.update_install), { updates.install(context, s.file) })
            } else {
                Triple(stringResource(R.string.update_allow_label, s.release.version), stringResource(R.string.update_allow), ::allowInstalls)
            }
        is AppUpdates.State.Installing -> Triple(stringResource(R.string.update_installing), null, null)
        is AppUpdates.State.UpToDate -> Triple(stringResource(R.string.update_up_to_date), stringResource(R.string.update_check), { updates.check(context) })
        is AppUpdates.State.Failed -> Triple(stringResource(R.string.update_failed, s.message), stringResource(R.string.update_check), { updates.check(context) })
        AppUpdates.State.Idle -> Triple(stringResource(R.string.update_idle), stringResource(R.string.update_check), { updates.check(context) })
    }
    val (label, action, onAction) = row
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.update_title), color = Pelage.Texte, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Secondary, fontSize = 13.sp)
        // Pas encore le droit d'installer : on le dit tout de suite (la mise à jour se fera sans appui ensuite)
        if (!allowed && state !is AppUpdates.State.Ready) {
            Text(
                stringResource(R.string.update_allow_hint),
                Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button) { allowInstalls() }.padding(vertical = 4.dp),
                color = Pelage.RouxClair,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        // Les boutons passent à la ligne s'ils ne tiennent pas côte à côte (le texte ne se coupe jamais)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (action != null && onAction != null) {
                Text(
                    action,
                    Modifier.clip(CircleShape).background(Pelage.Creme).clickable(role = Role.Button) { onAction() }.padding(horizontal = 14.dp, vertical = 9.dp),
                    color = Pelage.Encre,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            val page = (state as? AppUpdates.State.Ready)?.release?.page
            if (page != null) {
                Text(
                    stringResource(R.string.update_notes),
                    Modifier.clip(CircleShape).border(1.5.dp, Pelage.RouxClair, CircleShape).clickable(role = Role.Button) { runCatching { uri.openUri(page) } }.padding(horizontal = 14.dp, vertical = 9.dp),
                    color = Pelage.RouxClair,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

private val CREDITS = listOf(
    "NewPipe Extractor" to "https://github.com/TeamNewPipe/NewPipeExtractor",
    "SponsorBlock" to "https://sponsor.ajay.app",
    "Return YouTube Dislike" to "https://returnyoutubedislike.com",
    "yt-dlp" to "https://github.com/yt-dlp/yt-dlp",
    "M PLUS Rounded 1c" to "https://fonts.google.com/specimen/M+PLUS+Rounded+1c",
    "Lucide" to "https://lucide.dev",
)

// ---------------------------------------------------------------- outils

/** Langue ou région changée : contenus, « Pour toi » et commentaires sont à recharger. */
private fun refreshContent() {
    AppLocale.refresh()
    YouTubeRepository.clearCache()
    ForYou.clearCache()
    Comments.reset()
}

private fun systemRegion(): String =
    Resources.getSystem().configuration.locales[0].country.ifEmpty { "FR" }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun durationLabel(minutes: Int): String = when {
    minutes < 60 -> stringResource(R.string.duration_minutes, minutes)
    minutes % 60 == 0 -> stringResource(R.string.duration_hours, minutes / 60)
    else -> stringResource(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
}

// ---------------------------------------------------------------- briques

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = 14.dp)) {
        Text(
            title.uppercase(),
            Modifier.padding(start = 6.dp, bottom = 8.dp).semantics { heading() },
            color = Pelage.Gris,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Pelage.Coussin)
                .border(1.5.dp, Pelage.Bord, RoundedCornerShape(22.dp)),
            content = content,
        )
    }
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(start = 52.dp), thickness = 1.dp, color = Pelage.Bord)

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, color = Pelage.Texte, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, color = Secondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Pelage.Roux,
                checkedThumbColor = Pelage.Creme,
                checkedBorderColor = Pelage.Roux,
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                uncheckedThumbColor = Color.White.copy(alpha = 0.75f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.3f),
            ),
        )
    }
}

@Composable
private fun CheckRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Checkbox) { onChange(!checked) }
            .padding(start = 48.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), color = if (checked) Pelage.Texte else Secondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = Pelage.Roux, checkmarkColor = Pelage.Encre, uncheckedColor = Secondary),
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String?, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, color = Secondary, fontSize = 12.sp)
        }
    }
}

/** Une ligne « titre — valeur » qui se déplie en liste de choix. */
@Composable
private fun <T> ChoiceRow(
    icon: ImageVector,
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    subtitle: String? = null,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second.orEmpty()
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clickable(role = Role.DropdownList) { open = !open }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            // Comme dans les réglages d'Android : la valeur choisie sous le titre, puis l'explication
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, color = Pelage.Texte, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(current, color = Pelage.RouxClair, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (subtitle != null) Text(subtitle, color = Secondary, fontSize = 12.sp)
            }
            Icon(if (open) MkIcons.ChevronDown else MkIcons.ChevronRight, null, tint = Secondary, modifier = Modifier.padding(start = 4.dp).size(18.dp))
        }
        AnimatedVisibility(open, enter = expandVertically(), exit = shrinkVertically()) {
            Column(Modifier.padding(bottom = 6.dp)) {
                options.forEach { (value, label) ->
                    val isSelected = value == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                            .clickable(role = Role.RadioButton) {
                                open = false
                                onSelect(value)
                            }
                            .padding(start = 52.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label,
                            Modifier.weight(1f),
                            color = if (isSelected) Pelage.RouxClair else Pelage.Texte,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        )
                        if (isSelected) Icon(MkIcons.Check, stringResource(R.string.selected), tint = Pelage.RouxClair, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
