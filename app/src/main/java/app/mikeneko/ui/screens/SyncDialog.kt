package app.mikeneko.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.mikeneko.R
import app.mikeneko.data.LocalSync
import app.mikeneko.data.MikenekoBackup
import app.mikeneko.ui.components.Loading
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.Rounded
import kotlinx.coroutines.launch

/**
 * La synchro entre deux appareils (2.17.0, Réglages › Synchro) : l'un montre un PIN, l'autre le tape
 * (le 6e chiffre lance la synchro). Voir [LocalSync].
 */
@Composable
fun SyncDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf<Boolean?>(null) } // true : montrer le PIN, false : le taper
    val state by LocalSync.state.collectAsState()
    DisposableEffect(Unit) {
        LocalSync.screenOpened(context)
        onDispose { LocalSync.screenClosed(context) }
    }
    fun close() {
        LocalSync.screenClosed(context)
        onDismiss()
    }

    Dialog(onDismissRequest = ::close) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Pelage.Coussin).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.sync_title), Modifier.semantics { heading() }, color = Pelage.Texte, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            when (val s = state) {
                is LocalSync.State.Done -> Result(s) { close() }
                LocalSync.State.Syncing -> {
                    Loading(Modifier.size(72.dp))
                    Text(stringResource(R.string.sync_running), color = Pelage.Gris, fontSize = 14.sp)
                }
                else -> when (mode) {
                    null -> {
                        Choice(MkIcons.Info, stringResource(R.string.sync_show), stringResource(R.string.sync_show_text)) {
                            mode = true
                            LocalSync.showPin(context)
                        }
                        Spacer(Modifier.height(10.dp))
                        Choice(MkIcons.Pencil, stringResource(R.string.sync_enter), stringResource(R.string.sync_enter_text)) { mode = false }
                    }
                    true -> ShowPin(s)
                    false -> EnterPin(s)
                }
            }
            if (state !is LocalSync.State.Done) {
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = ::close, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.sync_close), color = Pelage.Texte, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun Choice(icon: ImageVector, title: String, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Pelage.Bord)
            .clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Pelage.RouxClair, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Pelage.Texte, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text(text, color = Pelage.Gris, fontSize = 13.sp)
        }
    }
}

/** Cet appareil attend : le PIN en grand, à taper sur l'autre. */
@Composable
private fun ShowPin(state: LocalSync.State) {
    val waiting = state as? LocalSync.State.Waiting
    if (waiting == null) {
        Loading(Modifier.size(72.dp))
        return
    }
    Text(
        waiting.pin.chunked(3).joinToString(" "),
        style = TextStyle(fontFamily = Rounded, color = Pelage.RouxClair, fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp),
    )
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.sync_waiting), color = Pelage.Texte, fontSize = 14.sp, textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text(waiting.name + (waiting.address?.let { " · $it" } ?: ""), color = Pelage.Gris, fontSize = 12.sp, textAlign = TextAlign.Center)
}

/** Cet appareil va chercher l'autre : la liste des Mikeneko trouvés, et le PIN. */
@Composable
private fun EnterPin(state: LocalSync.State) {
    val context = LocalContext.current
    // Les appareils qui affichent un PIN en ce moment
    val all by LocalSync.peers.collectAsState()
    val peers = all.filter { it.waitingPin }
    var chosen by remember { mutableStateOf<LocalSync.Peer?>(null) }
    var manual by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    // Un seul appareil trouvé : il est choisi d'office
    val target: Pair<String, Int>? = when {
        manual -> address.trim().split(':').takeIf { it.size == 2 }?.let { (h, p) -> p.toIntOrNull()?.let { h to it } }
        else -> (chosen ?: peers.singleOrNull())?.let { it.host to it.port }
    }
    // Le 6e chiffre lance la synchro
    LaunchedEffect(pin, target) {
        if (pin.length == 6 && target != null) {
            LocalSync.joinWithPin(context, target.first, target.second, pin)
            pin = ""
        }
    }

    if (!manual) {
        if (peers.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Loading(Modifier.size(36.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.sync_searching), color = Pelage.Gris, fontSize = 13.sp)
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                peers.forEach { peer ->
                    val selected = (chosen ?: peers.singleOrNull()) == peer
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (selected) Pelage.RouxNuit else Pelage.Bord)
                            .clickable(role = Role.RadioButton) { chosen = peer }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(MkIcons.Video, null, tint = if (selected) Pelage.RouxClair else Pelage.Texte, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(peer.name, color = if (selected) Pelage.RouxClair else Pelage.Texte, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Text(
            stringResource(R.string.sync_manual),
            Modifier.padding(top = 10.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { manual = true }.padding(6.dp),
            color = Pelage.RouxClair,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    } else {
        Field(address, stringResource(R.string.sync_address_hint), KeyboardType.Uri, autoFocus = true) { address = it }
    }
    Spacer(Modifier.height(12.dp))
    Field(pin, stringResource(R.string.sync_pin_hint), KeyboardType.NumberPassword, autoFocus = !manual, big = true) { typed ->
        pin = typed.filter { it.isDigit() }.take(6)
    }
    (state as? LocalSync.State.Failed)?.let { failed ->
        Text(
            stringResource(if (failed.reason == LocalSync.Reason.WrongPin) R.string.sync_wrong_pin else R.string.sync_network),
            Modifier.padding(top = 8.dp),
            color = Pelage.Rose,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Field(value: String, hint: String, type: KeyboardType, autoFocus: Boolean, big: Boolean = false, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focus.requestFocus() } }
    Box(
        Modifier.fillMaxWidth().height(if (big) 60.dp else 48.dp).clip(RoundedCornerShape(16.dp)).background(Pelage.Nuit)
            .border(1.5.dp, Pelage.Bord, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Pelage.Texte,
                fontSize = if (big) 28.sp else 16.sp,
                fontFamily = Rounded,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = if (big) 6.sp else 0.sp,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(Pelage.RouxClair),
            keyboardOptions = KeyboardOptions(keyboardType = type),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) {
                    if (value.isEmpty()) Text(hint, color = Pelage.Gris, fontSize = if (big) 20.sp else 15.sp, textAlign = TextAlign.Center)
                    inner()
                }
            },
        )
    }
}

@Composable
private fun Result(done: LocalSync.State.Done, onClose: () -> Unit) {
    val added: MikenekoBackup.Added = done.added
    Box(Modifier.size(64.dp).clip(CircleShape).background(Pelage.RouxNuit), contentAlignment = Alignment.Center) {
        Icon(MkIcons.Check, null, tint = Pelage.RouxClair, modifier = Modifier.size(34.dp))
    }
    Spacer(Modifier.height(10.dp))
    Text(stringResource(R.string.sync_done, done.peer), color = Pelage.Texte, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(4.dp))
    Text(
        if (added.total == 0) stringResource(R.string.sync_nothing) else addedSummary(LocalContext.current, added),
        color = Pelage.Gris,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(14.dp))
    Button(onClick = onClose) { Text(stringResource(R.string.sync_close), fontWeight = FontWeight.ExtraBold) }
}

/** Ce qu'une synchro ou un import a ajouté, en une phrase (et l'historique sur une 2e ligne, 2.19.1). */
fun addedSummary(context: android.content.Context, added: MikenekoBackup.Added): String {
    val main = context.getString(R.string.backup_imported, added.channels, added.playlists, added.library, added.videos)
    return if (added.history > 0) main + "\n" + context.getString(R.string.history_imported, added.history) else main
}
