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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Le spectre en fréquence de ce qui joue (2.5.0), pour la barre de progression de la musique.
 *
 * Le son est lu au passage dans ExoPlayer ([TeeAudioProcessor], voir [SpectrumRenderersFactory]) :
 * pas besoin de la permission micro, contrairement au `Visualizer` d'Android. Une FFT de 1024
 * échantillons, 30 fois par seconde, regroupée en [BANDS] bandes de 40 Hz à 16 kHz (échelle
 * logarithmique, comme l'oreille), adoucie (monte vite, retombe doucement). Rien n'est calculé tant
 * que personne ne regarde ([listening]).
 */
@UnstableApi
object AudioSpectrum : TeeAudioProcessor.AudioBufferSink {
    const val BANDS = 40
    private const val N = 1024
    private const val MIN_HZ = 40.0
    private const val MAX_HZ = 16_000.0

    /** Mis à true par l'écran qui affiche le spectre (sinon, aucun calcul sur le fil audio). */
    @Volatile var listening = false

    private val _bands = MutableStateFlow(FloatArray(BANDS))
    /** La hauteur de chaque bande, de 0 à 1 (graves à gauche). */
    val bands: StateFlow<FloatArray> = _bands.asStateFlow()

    private var sampleRate = 44_100
    private var channels = 2
    private var encoding = C.ENCODING_PCM_16BIT

    private val ring = FloatArray(N)
    private var pos = 0
    private var filled = 0
    private var sinceLast = 0
    private val re = FloatArray(N)
    private val im = FloatArray(N)
    private val window = FloatArray(N) { (0.5 - 0.5 * cos(2 * PI * it / (N - 1))).toFloat() } // Hann
    private val smooth = FloatArray(BANDS)

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        sampleRate = sampleRateHz.coerceAtLeast(8_000)
        channels = channelCount.coerceAtLeast(1)
        this.encoding = encoding
        filled = 0
        pos = 0
        sinceLast = 0
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (!listening) return
        val b = buffer.duplicate().order(ByteOrder.nativeOrder())
        val every = sampleRate / 30
        when (encoding) {
            C.ENCODING_PCM_16BIT -> while (b.remaining() >= 2 * channels) {
                var sum = 0f
                repeat(channels) { sum += b.short / 32768f }
                push(sum / channels, every)
            }
            C.ENCODING_PCM_FLOAT -> while (b.remaining() >= 4 * channels) {
                var sum = 0f
                repeat(channels) { sum += b.float }
                push(sum / channels, every)
            }
            else -> return
        }
    }

    private fun push(sample: Float, every: Int) {
        ring[pos] = sample
        pos = (pos + 1) % N
        if (filled < N) filled++
        if (++sinceLast >= every && filled == N) {
            sinceLast = 0
            analyze()
        }
    }

    private fun analyze() {
        for (i in 0 until N) {
            re[i] = ring[(pos + i) % N] * window[i]
            im[i] = 0f
        }
        fft(re, im)
        val binHz = sampleRate.toDouble() / N
        val top = minOf(MAX_HZ, sampleRate / 2.0)
        val out = FloatArray(BANDS)
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
            smooth[band] = if (v > smooth[band]) smooth[band] * 0.35f + v * 0.65f else smooth[band] * 0.8f + v * 0.2f
            out[band] = smooth[band]
        }
        _bands.value = out
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
