package app.mikeneko.ui.components

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import app.mikeneko.R
import app.mikeneko.ui.icons.MkIcons

/** Où part le son (3.2.0) : le nom à afficher et son icône. */
class AudioOutput(val label: String, val icon: ImageVector)

/**
 * La sortie du son en ce moment, tenue à jour quand un appareil se branche ou part (casque Bluetooth
 * qui se connecte…). [refreshKey] : une autre raison de relire (le morceau change). Null si inconnue.
 */
@Composable
fun rememberAudioOutput(refreshKey: Any?): AudioOutput? {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(AudioManager::class.java) }
    var output by remember { mutableStateOf(audioOutput(context, manager)) }
    DisposableEffect(manager, refreshKey) {
        output = audioOutput(context, manager)
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                output = audioOutput(context, manager)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                output = audioOutput(context, manager)
            }
        }
        manager?.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { manager?.unregisterAudioDeviceCallback(callback) }
    }
    return output
}

private fun audioOutput(context: Context, manager: AudioManager?): AudioOutput? {
    manager ?: return null
    val device = runCatching { routedDevice(manager) }.getOrNull() ?: return null
    val name = device.productName?.toString()?.trim().orEmpty()
    return when (device.type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_HEARING_AID,
        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_BROADCAST,
        -> AudioOutput(name.ifEmpty { context.getString(R.string.output_bluetooth) }, MkIcons.Bluetooth)
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET ->
            AudioOutput(context.getString(R.string.output_wired), MkIcons.Headphones)
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY ->
            AudioOutput(name.ifEmpty { context.getString(R.string.output_usb) }, MkIcons.Headphones)
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE ->
            AudioOutput(context.getString(R.string.output_speaker), MkIcons.Speaker)
        else -> AudioOutput(name.ifEmpty { context.getString(R.string.output_other) }, MkIcons.Speaker)
    }
}

/**
 * L'appareil où part la musique. Android 13+ le dit directement (pour un son « média ») ; avant, on
 * prend le plus probable parmi les sorties branchées : Bluetooth, puis casque, puis haut-parleur.
 */
private fun routedDevice(manager: AudioManager): AudioDeviceInfo? {
    val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    if (Build.VERSION.SDK_INT >= 33) {
        val media = android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).build()
        val routed = manager.getAudioDevicesForAttributes(media).firstOrNull()
        if (routed != null) {
            outputs.firstOrNull { it.type == routed.type && it.address == routed.address }?.let { return it }
            outputs.firstOrNull { it.type == routed.type }?.let { return it }
        }
    }
    val order = listOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
    )
    return order.firstNotNullOfOrNull { type -> outputs.firstOrNull { it.type == type } }
}
