package app.mikeneko.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

/**
 * Le spectre en fréquence de ce qui joue (2.5.0), pour la barre de progression de la musique.
 *
 * Le son est lu au passage dans ExoPlayer ([TeeAudioProcessor], voir [SpectrumRenderersFactory]) :
 * pas besoin de la permission micro, contrairement au `Visualizer` d'Android. Une FFT de 1024
 * échantillons à chaque image de l'écran (2.5.2 : avant, au rythme des paquets de son, ~4 par seconde),
 * regroupée en [BANDS] bandes de 40 Hz à 16 kHz (échelle
 * logarithmique, comme l'oreille), adoucie (monte vite, retombe doucement). Rien n'est calculé tant
 * que personne ne regarde ([listening]).
 */
@UnstableApi
object AudioSpectrum : TeeAudioProcessor.AudioBufferSink {
    const val BANDS = 40
    private const val N = 1024
    private const val RING = 1 shl 16 // ~1,5 s à 44,1 kHz
    private const val MIN_HZ = 40.0
    private const val MAX_HZ = 16_000.0

    /** Mis à true par l'écran qui affiche le spectre (sinon, rien n'est gardé sur le fil audio). */
    @Volatile var listening = false

    /** La fréquence d'échantillonnage du son en cours. */
    @Volatile var sampleRate = 44_100
        private set
    /** Combien d'échantillons sont passés en tout (le son arrive par paquets, environ 4 fois par seconde). */
    @Volatile var written = 0L
        private set
    /** Change à chaque saut / changement de morceau : l'écran recale sa tête de lecture. */
    @Volatile var epoch = 0
        private set

    private var channels = 2
    private var encoding = C.ENCODING_PCM_16BIT
    private val ring = FloatArray(RING)

    // Côté écran (fil principal) : la FFT et le lissage
    private val re = FloatArray(N)
    private val im = FloatArray(N)
    private val window = FloatArray(N) { (0.5 - 0.5 * cos(2 * PI * it / (N - 1))).toFloat() } // Hann
    private val smooth = FloatArray(BANDS)

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        sampleRate = sampleRateHz.coerceAtLeast(8_000)
        channels = channelCount.coerceAtLeast(1)
        this.encoding = encoding
        epoch++
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (!listening) return
        val b = buffer.duplicate().order(ByteOrder.nativeOrder())
        var w = written
        when (encoding) {
            C.ENCODING_PCM_16BIT -> while (b.remaining() >= 2 * channels) {
                var sum = 0f
                repeat(channels) { sum += b.short / 32768f }
                ring[(w and (RING - 1).toLong()).toInt()] = sum / channels
                w++
            }
            C.ENCODING_PCM_FLOAT -> while (b.remaining() >= 4 * channels) {
                var sum = 0f
                repeat(channels) { sum += b.float }
                ring[(w and (RING - 1).toLong()).toInt()] = sum / channels
                w++
            }
            else -> return
        }
        written = w
    }

    /**
     * Le spectre des [N] échantillons qui finissent à [end] (appelé à chaque image de l'écran, avec
     * une tête de lecture qui avance en temps réel : voir `SpectrumBars`). Hauteurs de 0 à 1,
     * adoucies d'une image à l'autre (montent vite, retombent doucement).
     */
    fun bandsAt(end: Long, out: FloatArray) {
        val start = end - N
        if (start < 0) return
        for (i in 0 until N) {
            re[i] = ring[((start + i) and (RING - 1).toLong()).toInt()] * window[i]
            im[i] = 0f
        }
        fft(re, im)
        val rate = sampleRate
        val binHz = rate.toDouble() / N
        val top = minOf(MAX_HZ, rate / 2.0)
        for (band in 0 until BANDS) {
            val lo = MIN_HZ * (top / MIN_HZ).pow(band.toDouble() / BANDS)
            val hi = MIN_HZ * (top / MIN_HZ).pow((band + 1).toDouble() / BANDS)
            val from = (lo / binHz).toInt().coerceIn(1, N / 2 - 1)
            val to = (hi / binHz).toInt().coerceIn(from, N / 2 - 1)
            var peak = 0f
            for (k in from..to) peak = maxOf(peak, hypot(re[k], im[k]))
            // En décibels, ramenés de [-60 dB, 0 dB] à [0, 1] ; les aigus un peu relevés (ils sont plus faibles)
            val db = 20 * log10(peak / (N / 4f) + 1e-9f) + 6f * band / BANDS
            val v = ((db + 60f) / 60f).coerceIn(0f, 1f)
            smooth[band] = if (v > smooth[band]) smooth[band] * 0.45f + v * 0.55f else smooth[band] * 0.86f + v * 0.14f
            out[band] = smooth[band]
        }
    }

    /** FFT radix 2 sur place (taille puissance de 2). */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2 * PI / len
            val wr = cos(angle).toFloat()
            val wi = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f
                var ci = 0f
                for (k in 0 until len / 2) {
                    val a = i + k
                    val b = a + len / 2
                    val tr = re[b] * cr - im[b] * ci
                    val ti = re[b] * ci + im[b] * cr
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }
}

/** Les renderers d'ExoPlayer, avec le son qui passe aussi par [AudioSpectrum]. */
@UnstableApi
class SpectrumRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
        DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf(TeeAudioProcessor(AudioSpectrum)))
            .build()
}
