package app.mikeneko.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.mikeneko.R
import app.mikeneko.data.GitHubSync
import app.mikeneko.ui.components.Loading
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.Rounded

/** Le cadre commun des fenêtres GitHub (comme celle de la synchro par PIN). */
@Composable
private fun GitHubDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Pelage.Coussin).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, Modifier.semantics { heading() }, color = Pelage.Texte, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

private fun copy(context: android.content.Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
}

/**
 * La connexion par appareil (2.19.0) : le code en grand (déjà copié), un bouton qui ouvre la page de
 * GitHub où le coller. La fenêtre se ferme toute seule une fois connecté.
 */
@Composable
fun GitHubSignInDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val login by GitHubSync.login.collectAsState()
    LaunchedEffect(login) { if (login is GitHubSync.Login.In) onDismiss() }
    fun close() {
        if (login is GitHubSync.Login.Code) GitHubSync.cancelSignIn()
        onDismiss()
    }
    GitHubDialog(stringResource(R.string.github_sign_in), ::close) {
        when (val state = login) {
            is GitHubSync.Login.Code -> {
                LaunchedEffect(state.userCode) { copy(context, "GitHub", state.userCode) }
                Text(
                    state.userCode,
                    style = TextStyle(fontFamily = Rounded, color = Pelage.RouxClair, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp),
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.github_code_text), color = Pelage.Texte, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Button(onClick = { runCatching { uri.openUri(state.uri) } }) {
                    Text(stringResource(R.string.github_open_page), fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(10.dp))
                Box(contentAlignment = Alignment.Center) { Loading(Modifier.size(40.dp)) }
                Text(stringResource(R.string.github_waiting), color = Pelage.Gris, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            else -> {
                Loading(Modifier.size(64.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = ::close, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.sync_close), color = Pelage.Texte, fontWeight = FontWeight.Bold)
        }
    }
}

/** La clé de synchro de cet appareil, à recopier sur un autre (ou qu'un appareil lié par PIN reçoit tout seul). */
@Composable
fun SyncKeyDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val key = remember { GitHubSync.syncKey(context) }
    GitHubDialog(stringResource(R.string.github_key), onDismiss) {
        if (key == null) {
            Text(stringResource(R.string.github_key_none), color = Pelage.Gris, fontSize = 14.sp, textAlign = TextAlign.Center)
        } else {
            Text(
                key,
                style = TextStyle(fontFamily = Rounded, color = Pelage.RouxClair, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, textAlign = TextAlign.Center),
            )
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.github_key_text), color = Pelage.Texte, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                copy(context, "Mikeneko", key)
                Toast.makeText(context, context.getString(R.string.github_key_copied), Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.github_key_copy), fontWeight = FontWeight.ExtraBold) }
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.sync_close), color = Pelage.Texte, fontWeight = FontWeight.Bold)
        }
    }
}

/** Le coffre existe déjà, chiffré par un autre appareil : on tape sa clé de synchro. */
@Composable
fun EnterSyncKeyDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val status by GitHubSync.status.collectAsState()
    var typed by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    // Une fois la synchro passée avec cette clé, on ferme
    LaunchedEffect(status) { if (sent && status is GitHubSync.Status.Done) onDismiss() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    GitHubDialog(stringResource(R.string.github_key_enter), onDismiss) {
        Text(stringResource(R.string.github_key_enter_text), color = Pelage.Texte, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp)).background(Pelage.Nuit)
                .border(1.5.dp, Pelage.Bord, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicTextField(
                value = typed,
                onValueChange = { typed = it.take(30) },
                singleLine = true,
                textStyle = TextStyle(color = Pelage.Texte, fontSize = 17.sp, fontFamily = Rounded, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(Pelage.RouxClair),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (typed.isEmpty()) Text("XXXXX-XXXXX-XXXXX-XXXXX", color = Pelage.Gris, fontSize = 15.sp)
                        inner()
                    }
                },
            )
        }
        val wrong = (status as? GitHubSync.Status.NeedKey)?.wrong == true && sent
        if (status is GitHubSync.Status.Running) {
            Loading(Modifier.padding(top = 8.dp).size(40.dp))
        } else if (wrong) {
            Text(stringResource(R.string.github_key_wrong), Modifier.padding(top = 8.dp), color = Pelage.Rose, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            sent = true
            GitHubSync.enterKey(context, typed)
        }, enabled = GitHubSync.normalizeKey(typed) != null) { Text(stringResource(R.string.github_key_use), fontWeight = FontWeight.ExtraBold) }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.sync_close), color = Pelage.Texte, fontWeight = FontWeight.Bold)
        }
    }
}
