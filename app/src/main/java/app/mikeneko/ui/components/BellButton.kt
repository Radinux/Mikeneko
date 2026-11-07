package app.mikeneko.ui.components

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mikeneko.R
import app.mikeneko.data.SubscriptionStore
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.update.Notifier

/**
 * La cloche à côté de « S'abonné » (2.10.0) : allumée, une notification à chaque nouvelle vidéo de la
 * chaîne (vérifiée en fond toutes les ~2 h, voir Notifier). Éteinte par défaut. En l'allumant, on
 * demande la permission des notifications si Android ne l'a pas encore.
 */
@Composable
fun BellButton(channelId: String, channelName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { SubscriptionStore.get(context) }
    val bells by store.bells.collectAsState()
    val on = channelId in bells
    fun turnOn() {
        store.toggleBell(channelId)
        Toast.makeText(context, context.getString(R.string.bell_enabled, channelName), Toast.LENGTH_SHORT).show()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) turnOn() else Toast.makeText(context, context.getString(R.string.bell_denied), Toast.LENGTH_LONG).show()
    }
    FilledIconToggleButton(
        checked = on,
        onCheckedChange = { wanted ->
            when {
                !wanted -> {
                    store.toggleBell(channelId)
                    Toast.makeText(context, context.getString(R.string.bell_disabled, channelName), Toast.LENGTH_SHORT).show()
                }
                Notifier.allowed(context) -> turnOn()
                Build.VERSION.SDK_INT >= 33 -> permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else -> Toast.makeText(context, context.getString(R.string.bell_denied), Toast.LENGTH_LONG).show()
            }
        },
        modifier = modifier,
    ) {
        Icon(if (on) MkIcons.BellFilled else MkIcons.Bell, stringResource(if (on) R.string.bell_off else R.string.bell_on), modifier = Modifier.size(20.dp))
    }
}
