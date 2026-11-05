package app.mikeneko.player

import android.media.audiofx.DynamicsProcessing
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * « Volume stable » : compresse les écarts de volume (les chuchotements remontent,
 * les cris redescendent) puis limite les pics. Effet audio d'Android 9+, appliqué
 * uniquement à la session audio du lecteur de Mikeneko.
 */
object StableVolume {
    val supported: Boolean get() = Build.VERSION.SDK_INT >= 28

    private var effect: Any? = null // DynamicsProcessing, typé Any pour rester chargeable sous Android 8
    private var session = 0

    /** Active / désactive l'effet sur la session audio donnée. Renvoie false si l'appareil refuse. */
    fun apply(enabled: Boolean, audioSessionId: Int): Boolean {
        if (!supported) return !enabled
        if (!enabled || audioSessionId <= 0) {
            release()
            return true
        }
        if (effect != null && session == audioSessionId) return true
        release()
        return runCatching { create(audioSessionId) }.onSuccess {
            effect = it
            session = audioSessionId
        }.isSuccess
    }

    fun release() {
        if (Build.VERSION.SDK_INT >= 28) (effect as? DynamicsProcessing)?.release()
        effect = null
        session = 0
    }

    @RequiresApi(28)
    private fun create(audioSessionId: Int): DynamicsProcessing {
        val channels = 2
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            channels,
            false, 0, // pas d'égaliseur avant
            true, 1, // un compresseur, une seule bande (toutes les fréquences)
            false, 0, // pas d'égaliseur après
            true, // limiteur
        ).build()
        return DynamicsProcessing(0, audioSessionId, config).apply {
            setMbcBandAllChannelsTo(
                0,
                DynamicsProcessing.MbcBand(
                    true,
                    20_000f, // fréquence de coupure : toute la bande audible
                    5f, // attaque (ms)
                    250f, // relâchement (ms)
                    3.5f, // ratio de compression
                    -26f, // seuil (dB)
                    8f, // genou (dB)
                    -70f, // porte de bruit : on ne remonte pas le souffle
                    1f, // pas d'expandeur
                    0f, // gain avant
                    9f, // gain après : on remonte l'ensemble une fois compressé
                ),
            )
            setLimiterAllChannelsTo(
                DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1.5f, 0f),
            )
            enabled = true
        }
    }
}
