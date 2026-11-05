package app.mikeneko.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.mikeneko.R
import app.mikeneko.player.PlayerHolder
import app.mikeneko.ui.components.Mascot
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.clay
import app.mikeneko.ui.theme.jellyClickable
import kotlinx.coroutines.delay

/**
 * « T'as vu l'heure ? » : après 1 h d'appli à l'écran, Mikeneko propose d'aller dormir.
 * Le temps n'avance que quand l'appli est visible (PiP compris), pas écran éteint.
 * [suppressed] : le minuteur tourne mais la fenêtre attend (par exemple pendant le PiP système).
 */
@Composable
fun SleepReminder(suppressed: Boolean = false, firstAfterMinutes: Int = 60, snoozeMinutes: Int = 15) {
    var elapsed by rememberSaveable { mutableIntStateOf(0) }
    // Le délai a changé dans les réglages : on repart sur le nouveau
    var nextAt by rememberSaveable(firstAfterMinutes) { mutableIntStateOf(maxOf(firstAfterMinutes, elapsed + 1)) }
    var visible by rememberSaveable { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(60_000L)
                elapsed++
                if (elapsed >= nextAt) visible = true
            }
        }
    }

    fun snooze() {
        visible = false
        nextAt = elapsed + snoozeMinutes
    }

    if (!visible || suppressed) return
    Dialog(onDismissRequest = ::snooze, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Pelage.Nuit).safeDrawingPadding().padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.height(36.dp).clay(Pelage.Creme).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(MkIcons.Moon, null, tint = Color(0xFFC98A1A), modifier = Modifier.size(18.dp))
                Text("${elapsed / 60} h ${"%02d".format(elapsed % 60)}", color = Pelage.Encre, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(40.dp))
            Mascot(size = 150.dp)
            Text(stringResource(R.string.sleep_title), Modifier.padding(top = 28.dp), color = Pelage.Texte, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                stringResource(R.string.sleep_text),
                Modifier.padding(top = 12.dp),
                color = Color(0xFFCFC6BD),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.fillMaxWidth().jellyClickable(strength = 0.6f) {
                    PlayerHolder.pause()
                    snooze()
                }.height(56.dp).clay(Pelage.Roux),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.sleep_goodnight), color = Pelage.Encre, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().jellyClickable(strength = 0.6f, onClick = ::snooze).height(56.dp).clay(Pelage.Bord),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.sleep_snooze, snoozeMinutes), color = Pelage.Texte, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
