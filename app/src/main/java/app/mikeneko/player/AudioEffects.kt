package app.mikeneko.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Les effets audio de la musique (3.3.0) : des préréglages (Slowed + Reverb, 8D, Bass Boost…) calculés
 * ici, sur le son décodé, pour un rendu identique sur tous les téléphones. [speed] : la vitesse ET la
 * hauteur (pas de correction de hauteur : un « slowed » est plus grave, comme un vinyle ralenti),
 * appliquées par le lecteur ([PlayerHolder]). Le reste est la chaîne [EffectChain].
 */
enum class AudioPreset(val id: String, val speed: Float) {
    Normal("normal", 1f),
    Slowed("slowed", 0.85f),
    SpedUp("sped_up", 1.18f),
    Nightcore("nightcore", 1.24f),
    Spatial("spatial", 1f),
    EightD("8d", 1f),
    BassBoost("bass_boost", 1f),
    UltraBass("ultra_bass", 1f),
    XstrmBass("xstrm_bass", 1f),
    Crispy("crispy", 1f),
    VBalance("v_balance", 1f),
    Vocal("vocal", 1f),
    Warm("warm", 1f),
    Wide("wide", 1f),
    Muffled("muffled", 1f),
    OldRadio("old_radio", 1f),
    LoFi("lofi", 0.96f);

    companion object {
        fun of(id: String?): AudioPreset = entries.firstOrNull { it.id == id } ?: Normal
    }
}

/** Ce que le lecteur applique en ce moment (lu par le fil audio). */
object AudioEffects {
    /** Le préréglage choisi pour la musique. */
    @Volatile var preset: AudioPreset = AudioPreset.Normal

    /** Ça joue de la musique (les vidéos gardent leur son tel quel). */
    @Volatile var musicPlaying: Boolean = false

    /** Le préréglage à appliquer au son qui passe : rien pour une vidéo. */
    val active: AudioPreset get() = if (musicPlaying) preset else AudioPreset.Normal
}

/**
 * Le processeur placé dans la sortie audio d'ExoPlayer : il passe le son (16 bits ou flottant, mono ou
 * stéréo) dans la chaîne du préréglage actif, et sort toujours en stéréo. En « Normal », il recopie.
 * La chaîne est refaite quand le préréglage change (ses filtres repartent de zéro).
 */
@OptIn(UnstableApi::class)
class PresetAudioProcessor(private val which: () -> AudioPreset = { AudioEffects.active }) : BaseAudioProcessor() {
    private var chain: EffectChain? = null
    private val frame = FloatArray(2)

    // 3.6.0 : au changement de préréglage en pleine lecture, l'ancien son (son effet, ou le son d'origine)
    // se fond dans le nouveau en quelques millisecondes, au lieu d'un saut qui pouvait claquer
    private var shown: AudioPreset? = null // le préréglage du dernier tampon (null : rien n'a encore joué)
    private var fadeFrom: EffectChain? = null // la chaîne qui s'efface (null : le son d'origine)
    private var fadeLeft = 0
    private var fadeTotal = 1
    private val old = FloatArray(2)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val encoding = inputAudioFormat.encoding
        if ((encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) || inputAudioFormat.channelCount !in 1..2) {
            return AudioFormat.NOT_SET // autre format (multicanal…) : on n'y touche pas
        }
        return AudioFormat(inputAudioFormat.sampleRate, 2, encoding)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val format = inputAudioFormat
        val float = format.encoding == C.ENCODING_PCM_FLOAT
        val bytes = if (float) 4 else 2
        val channels = format.channelCount
        val frames = inputBuffer.remaining() / (bytes * channels)
        val out = replaceOutputBuffer(frames * 2 * bytes)
        val preset = which()
        var current = chain
        val sameRate = current == null || current.sampleRate == format.sampleRate
        if (shown != null && shown != preset && sameRate) {
            fadeFrom = current
            fadeTotal = (format.sampleRate * FADE_SECONDS).toInt().coerceAtLeast(1)
            fadeLeft = fadeTotal
        }
        shown = preset
        if (preset != AudioPreset.Normal && (current == null || current.preset != preset || current.sampleRate != format.sampleRate)) {
            current = EffectChain(preset, format.sampleRate)
            chain = current
        } else if (preset == AudioPreset.Normal) {
            chain = null
            current = null
        }
        inputBuffer.order(ByteOrder.nativeOrder())
        out.order(ByteOrder.nativeOrder())
        repeat(frames) {
            val l = if (float) inputBuffer.float else inputBuffer.short / 32768f
            val r = if (channels == 2) (if (float) inputBuffer.float else inputBuffer.short / 32768f) else l
            frame[0] = l
            frame[1] = r
            current?.process(frame)
            if (fadeLeft > 0) {
                old[0] = l
                old[1] = r
                fadeFrom?.process(old)
                val t = fadeLeft.toFloat() / fadeTotal // la part de l'ancien son : de 1 à 0
                frame[0] += (old[0] - frame[0]) * t
                frame[1] += (old[1] - frame[1]) * t
                if (--fadeLeft == 0) fadeFrom = null
            }
            if (float) {
                out.putFloat(frame[0])
                out.putFloat(frame[1])
            } else {
                out.putShort(toShort(frame[0]))
                out.putShort(toShort(frame[1]))
            }
        }
        inputBuffer.position(inputBuffer.limit()) // (une trame incomplète ne peut pas arriver, mais rien ne doit rester)
        out.flip()
    }

    override fun onFlush() {
        chain?.reset() // un saut : les traînes (réverbération, délais) repartent à vide
        endFade()
    }

    override fun onReset() {
        chain = null
        shown = null
        endFade()
    }

    private fun endFade() {
        fadeFrom = null
        fadeLeft = 0
    }

    private fun toShort(x: Float): Short = (x.coerceIn(-1f, 1f) * 32767f).toInt().toShort()

    private companion object {
        const val FADE_SECONDS = 0.008
    }
}

/** Un filtre du second ordre (formules RBJ), en forme transposée II. */
class Biquad private constructor(private val b0: Float, private val b1: Float, private val b2: Float, private val a1: Float, private val a2: Float) {
    private var z1 = 0f
    private var z2 = 0f

    fun process(x: Float): Float {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    fun reset() {
        z1 = 0f
        z2 = 0f
    }

    fun copy() = Biquad(b0, b1, b2, a1, a2)

    companion object {
        private fun make(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
            Biquad((b0 / a0).toFloat(), (b1 / a0).toFloat(), (b2 / a0).toFloat(), (a1 / a0).toFloat(), (a2 / a0).toFloat())

        private fun w(fs: Int, f: Double) = 2 * PI * f.coerceAtMost(fs * 0.45) / fs

        fun lowPass(fs: Int, f: Double, q: Double = 0.7071): Biquad {
            val w = w(fs, f); val a = sin(w) / (2 * q); val c = cos(w)
            return make((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + a, -2 * c, 1 - a)
        }

        fun highPass(fs: Int, f: Double, q: Double = 0.7071): Biquad {
            val w = w(fs, f); val a = sin(w) / (2 * q); val c = cos(w)
            return make((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + a, -2 * c, 1 - a)
        }

        fun peak(fs: Int, f: Double, gainDb: Double, q: Double): Biquad {
            val w = w(fs, f); val a = sin(w) / (2 * q); val c = cos(w); val g = 10.0.pow(gainDb / 40)
            return make(1 + a * g, -2 * c, 1 - a * g, 1 + a / g, -2 * c, 1 - a / g)
        }

        fun lowShelf(fs: Int, f: Double, gainDb: Double): Biquad {
            val w = w(fs, f); val c = cos(w); val g = 10.0.pow(gainDb / 40); val a = sin(w) / 2 * sqrt(2.0)
            val s = 2 * sqrt(g) * a
            return make(
                g * ((g + 1) - (g - 1) * c + s), 2 * g * ((g - 1) - (g + 1) * c), g * ((g + 1) - (g - 1) * c - s),
                (g + 1) + (g - 1) * c + s, -2 * ((g - 1) + (g + 1) * c), (g + 1) + (g - 1) * c - s,
            )
        }

        fun highShelf(fs: Int, f: Double, gainDb: Double): Biquad {
            val w = w(fs, f); val c = cos(w); val g = 10.0.pow(gainDb / 40); val a = sin(w) / 2 * sqrt(2.0)
            val s = 2 * sqrt(g) * a
            return make(
                g * ((g + 1) + (g - 1) * c + s), -2 * g * ((g - 1) + (g + 1) * c), g * ((g + 1) + (g - 1) * c - s),
                (g + 1) - (g - 1) * c + s, 2 * ((g - 1) - (g + 1) * c), (g + 1) - (g - 1) * c - s,
            )
        }

        /** Une pente de 24 dB/octave : deux filtres de Butterworth à la suite. */
        fun lowPass24(fs: Int, f: Double) = listOf(lowPass(fs, f, 0.5412), lowPass(fs, f, 1.3066))
        fun highPass24(fs: Int, f: Double) = listOf(highPass(fs, f, 0.5412), highPass(fs, f, 1.3066))
    }
}

/** Une ligne à retard (en échantillons), lue avec interpolation linéaire. */
private class Delay(size: Int) {
    private val buffer = FloatArray(size.coerceAtLeast(2))
    private var index = 0

    fun push(x: Float) {
        buffer[index] = x
        index = (index + 1) % buffer.size
    }

    /** L'échantillon d'il y a [delay] échantillons (≥ 1). */
    fun read(delay: Float): Float {
        val d = delay.coerceIn(1f, (buffer.size - 1).toFloat())
        val i = d.toInt()
        val frac = d - i
        val a = buffer[(index - i + buffer.size * 2) % buffer.size]
        val b = buffer[(index - i - 1 + buffer.size * 2) % buffer.size]
        return a + (b - a) * frac
    }

    fun reset() = buffer.fill(0f)
}

/** Réverbération façon Freeverb (8 filtres en peigne + 4 passe-tout par côté), réglée par sa durée. */
private class Reverb(fs: Int, decaySeconds: Double, private val wet: Float, highPassHz: Double) {
    private val scale = fs / 44100.0
    private val combsL = COMBS.map { Comb((it * scale).toInt(), decaySeconds, fs) }
    private val combsR = COMBS.map { Comb(((it + SPREAD) * scale).toInt(), decaySeconds, fs) }
    private val allL = ALLPASS.map { AllPass((it * scale).toInt()) }
    private val allR = ALLPASS.map { AllPass(((it + SPREAD) * scale).toInt()) }
    private val inputFilter = Biquad.highPass(fs, highPassHz)

    fun process(frame: FloatArray) {
        val input = inputFilter.process((frame[0] + frame[1]) * 0.015f)
        var l = 0f
        var r = 0f
        for (c in combsL) l += c.process(input)
        for (c in combsR) r += c.process(input)
        for (a in allL) l = a.process(l)
        for (a in allR) r = a.process(r)
        // Le son d'origine baisse un peu de la part de la réverbération, qui revient à peu près au même niveau
        frame[0] = frame[0] * (1 - wet * 0.5f) + l * 3f * wet
        frame[1] = frame[1] * (1 - wet * 0.5f) + r * 3f * wet
    }

    fun reset() {
        inputFilter.reset()
        combsL.forEach { it.reset() }; combsR.forEach { it.reset() }
        allL.forEach { it.reset() }; allR.forEach { it.reset() }
    }

    private class Comb(size: Int, decay: Double, fs: Int) {
        private val buffer = FloatArray(size.coerceAtLeast(1))
        private var index = 0
        private var store = 0f
        // Le retour qui donne un temps de réverbération (−60 dB) de [decay] secondes
        private val feedback = 10.0.pow(-3.0 * size / (fs * decay)).toFloat().coerceAtMost(0.98f)

        fun process(x: Float): Float {
            val out = buffer[index]
            store = out * (1 - DAMP) + store * DAMP
            buffer[index] = x + store * feedback
            index = (index + 1) % buffer.size
            return out
        }

        fun reset() {
            buffer.fill(0f)
            store = 0f
        }
    }

    private class AllPass(size: Int) {
        private val buffer = FloatArray(size.coerceAtLeast(1))
        private var index = 0

        fun process(x: Float): Float {
            val delayed = buffer[index]
            buffer[index] = x + delayed * 0.5f
            index = (index + 1) % buffer.size
            return delayed - x
        }

        fun reset() = buffer.fill(0f)
    }

    companion object {
        private val COMBS = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        private val ALLPASS = intArrayOf(556, 441, 341, 225)
        private const val SPREAD = 23
        private const val DAMP = 0.3f
    }
}

/**
 * Limiteur à anticipation (3.6.0) : il voit les crêtes 5 ms avant qu'elles sortent, et baisse le volume
 * en douceur juste avant, puis le remonte en ~40 ms. Rien ne dépasse −0,3 dBFS et, contrairement à une
 * courbe de saturation, la forme du son n'est pas écrasée. Les deux côtés baissent ensemble (l'image
 * stéréo ne bouge pas). Le son sort avec 5 ms de retard.
 *
 * Le gain voulu de chaque trame passe par un minimum glissant sur la fenêtre d'anticipation (file
 * monotone), une remontée lente, puis une moyenne glissante de même longueur : la moyenne ne contient
 * que des valeurs ≤ au gain voulu de la trame qui sort, donc aucune crête ne passe.
 */
private class Limiter(fs: Int) {
    private val size = (fs * 0.005).toInt().coerceAtLeast(8)
    private val delayL = FloatArray(size)
    private val delayR = FloatArray(size)
    private var pos = 0

    // Le minimum glissant : une file de (gain, numéro de trame) aux gains croissants
    private val queueGain = FloatArray(size)
    private val queueAt = LongArray(size)
    private var head = 0
    private var count = 0
    private var at = 0L

    private val release = (1 - kotlin.math.exp(-1.0 / (0.040 * fs))).toFloat()
    private var envelope = 1f
    private val window = FloatArray(size) { 1f }
    private var windowPos = 0
    private var sum = size.toDouble()

    fun process(frame: FloatArray) {
        val peak = maxOf(abs(frame[0]), abs(frame[1]))
        val wanted = if (peak > CEILING) CEILING / peak else 1f
        if (count > 0 && queueAt[head] <= at - size) { // sortie de la fenêtre
            head = (head + 1) % size
            count--
        }
        while (count > 0 && queueGain[(head + count - 1) % size] >= wanted) count--
        val tail = (head + count) % size
        queueGain[tail] = wanted
        queueAt[tail] = at
        count++
        at++
        val lowest = queueGain[head]
        envelope = minOf(lowest, envelope + (1f - envelope) * release)
        sum += envelope - window[windowPos]
        window[windowPos] = envelope
        windowPos = (windowPos + 1) % size
        val gain = (sum / size).toFloat().coerceIn(0f, 1f)

        delayL[pos] = frame[0]
        delayR[pos] = frame[1]
        pos = (pos + 1) % size // la plus ancienne trame : celle d'il y a (size − 1) trames
        // (le bornage ne sert qu'à couvrir les arrondis du calcul flottant)
        frame[0] = (delayL[pos] * gain).coerceIn(-CEILING, CEILING)
        frame[1] = (delayR[pos] * gain).coerceIn(-CEILING, CEILING)
    }

    fun reset() {
        delayL.fill(0f); delayR.fill(0f)
        window.fill(1f)
        sum = size.toDouble()
        envelope = 1f
        head = 0; count = 0
    }

    companion object {
        private const val CEILING = 0.966f // −0,3 dBFS
    }
}

/**
 * Les harmoniques de basses d'Ultra Bass (3.6.0) : les très basses (< 80 Hz) passent dans une
 * saturation douce (harmoniques impaires) et un carré (harmoniques paires) ; on n'en garde que les
 * harmoniques (100 à 400 Hz), ajoutées au son. Une petite enceinte ou des écouteurs qui ne descendent
 * pas à 50 Hz font quand même entendre la note, et la basse d'origine reste intacte.
 */
private class BassHarmonics(
    fs: Int,
    below: Double = 80.0,
    from: Double = 100.0,
    to: Double = 400.0,
    private val drive: Float = 2.2f,
    private val odd: Float = 2.5f,
    private val even: Float = 0.9f,
) {
    private val lowL = Biquad.lowPass24(fs, below)
    private val lowR = Biquad.lowPass24(fs, below)
    private val bandL = listOf(Biquad.highPass(fs, from), Biquad.highPass(fs, from), Biquad.lowPass(fs, to))
    private val bandR = bandL.map { it.copy() }

    fun process(frame: FloatArray) {
        frame[0] += side(frame[0], lowL, bandL)
        frame[1] += side(frame[1], lowR, bandR)
    }

    private fun side(x: Float, low: List<Biquad>, band: List<Biquad>): Float {
        var sub = x
        for (i in low.indices) sub = low[i].process(sub)
        var h = (tanh(sub * drive) / drive - sub) * odd + sub * sub * even
        for (i in band.indices) h = band[i].process(h)
        return h
    }

    fun reset() {
        (lowL + lowR + bandL + bandR).forEach { it.reset() }
    }
}

/**
 * Le « coup » des basses de Xstrm Bass (3.6.0) : la bande sous [below] Hz passe dans une saturation
 * qui la gonfle de [gain] quand elle est faible et l'écrase vers [cap] quand elle est forte. Les basses
 * calmes remontent, les fortes tapent toujours au même niveau, dense et saturé : elles « crachent ».
 */
private class BassSlam(fs: Int, below: Double, private val gain: Float, private val cap: Float) {
    private val lowL = Biquad.lowPass(fs, below)
    private val lowR = Biquad.lowPass(fs, below)

    fun process(frame: FloatArray) {
        val l = lowL.process(frame[0])
        val r = lowR.process(frame[1])
        frame[0] += cap * tanh(l * gain / cap) - l
        frame[1] += cap * tanh(r * gain / cap) - r
    }

    fun reset() {
        lowL.reset(); lowR.reset()
    }
}

/**
 * La chaîne d'un préréglage, pour un taux d'échantillonnage. [process] modifie une trame stéréo
 * (gauche, droite, de −1 à 1) en place. Toujours un limiteur à la fin ([Limiter]) : un fort renfort de
 * basses ne sature jamais.
 */
class EffectChain(val preset: AudioPreset, val sampleRate: Int) {
    private val fs = sampleRate
    private val eqL = ArrayList<Biquad>()
    private val eqR = ArrayList<Biquad>()
    private var preGain = 1f
    private var width = 0f // renfort du canal « côté » (Mid/Side)
    private var monoBelow: Pair<Biquad, Biquad>? = null
    private var reverb: Reverb? = null

    // Spatial : délai croisé (Haas) filtré, et premières réflexions
    private var haas: Haas? = null
    private var early: Early? = null

    // 8D : panoramique tournant (basses au centre)
    private var autoPan: AutoPan? = null

    // Harmoniques de basses (Ultra Bass) / saturation à lampes (vieille radio)
    private var bassHarmonics: BassHarmonics? = null
    private var bassSlam: BassSlam? = null
    private val limiter = if (preset == AudioPreset.Normal) null else Limiter(fs) // « Normal » : le son tel quel
    private var tubeDrive = 0f
    private var mono = false
    private var radio: Radio? = null

    init {
        fun eq(vararg filters: Biquad) = filters.forEach { eqL += it; eqR += it.copy() }
        fun db(v: Double) = 10.0.pow(v / 20).toFloat()
        when (preset) {
            AudioPreset.Normal -> Unit
            AudioPreset.Slowed -> {
                eq(Biquad.lowPass(fs, 10_000.0, 0.6), Biquad.lowShelf(fs, 120.0, 2.0))
                preGain = db(-2.0)
                reverb = Reverb(fs, 2.6, 0.20f, 180.0) // 3.4.0 : 0,25 → 0,20 (un peu moins de réverbération)
            }
            AudioPreset.SpedUp, AudioPreset.Nightcore -> eq(Biquad.highShelf(fs, 10_000.0, 2.0))
            AudioPreset.Spatial -> {
                monoBelow = Biquad.lowPass(fs, 150.0) to Biquad.lowPass(fs, 150.0)
                width = 0.3f
                haas = Haas(fs)
                early = Early(fs)
            }
            AudioPreset.EightD -> {
                autoPan = AutoPan(fs)
                reverb = Reverb(fs, 1.5, 0.12f, 150.0)
            }
            AudioPreset.BassBoost -> {
                eq(Biquad.peak(fs, 60.0, 5.0, 0.9), Biquad.peak(fs, 150.0, 3.0, 1.0), Biquad.peak(fs, 320.0, -2.5, 0.9))
                preGain = db(-4.0)
            }
            AudioPreset.UltraBass -> {
                eq(Biquad.lowShelf(fs, 60.0, 9.0), Biquad.peak(fs, 100.0, 5.0, 1.0), Biquad.peak(fs, 300.0, -3.5, 1.0))
                preGain = db(-6.0)
                bassHarmonics = BassHarmonics(fs)
            }
            AudioPreset.XstrmBass -> {
                // Pour les petites enceintes à radiateurs passifs, qui répondent le plus fort vers 68 Hz. Le
                // pic vise cette fréquence ; tout ce qui est sous ~40 Hz est coupé (24 dB/oct) : l'enceinte
                // ne le rend pas, il ne ferait que pousser la membrane et déclencher son propre limiteur, qui
                // écraserait le reste. Basses en mono (sous 120 Hz) pour que les deux côtés poussent les
                // radiateurs ensemble, et un creux vers 300 Hz pour garder la clarté.
                // 3.6.0 : bien plus fort (les basses doivent « cracher ») : pic de 7 → 10 dB, la bande des
                // basses gonflée et saturée ([BassSlam]), et des harmoniques (136, 204 Hz…) qui font
                // grogner la note même là où l'enceinte s'essouffle.
                // 3.7.0 : poussé sur toute la zone 50–68 Hz (deux pics qui font un plateau d'environ +12 dB),
                // la coupure du bas descendue à 33 Hz pour laisser passer les 50 Hz en entier
                Biquad.highPass24(fs, 33.0).forEach { eq(it) }
                eq(Biquad.peak(fs, 50.0, 8.0, 1.4), Biquad.peak(fs, 68.0, 8.0, 1.4), Biquad.peak(fs, 300.0, -3.0, 0.9))
                monoBelow = Biquad.lowPass(fs, 120.0) to Biquad.lowPass(fs, 120.0)
                bassSlam = BassSlam(fs, 120.0, gain = 1.6f, cap = 0.6f)
                bassHarmonics = BassHarmonics(fs, below = 100.0, from = 110.0, to = 450.0, drive = 3f, odd = 3.5f, even = 1.2f)
                preGain = db(-6.0)
            }
            AudioPreset.Crispy -> {
                eq(
                    Biquad.highPass(fs, 30.0), Biquad.peak(fs, 285.0, -2.5, 1.0), Biquad.peak(fs, 3_200.0, 2.0, 0.9),
                    Biquad.highShelf(fs, 10_000.0, 4.0),
                )
                width = 0.1f
                preGain = db(-3.0)
            }
            AudioPreset.VBalance -> {
                eq(Biquad.lowShelf(fs, 80.0, 4.5), Biquad.peak(fs, 120.0, 3.0, 1.0), Biquad.peak(fs, 870.0, -3.5, 0.8), Biquad.highShelf(fs, 8_000.0, 4.0))
                width = 0.12f
                preGain = db(-4.5)
            }
            AudioPreset.Vocal -> {
                eq(Biquad.highPass(fs, 120.0), Biquad.peak(fs, 3_000.0, 3.5, 0.8))
                preGain = db(-2.0)
            }
            AudioPreset.Warm -> eq(Biquad.highShelf(fs, 12_000.0, -3.0), Biquad.peak(fs, 180.0, 1.5, 0.9))
            AudioPreset.Wide -> width = 0.2f
            AudioPreset.Muffled -> {
                Biquad.lowPass24(fs, 520.0).forEach { eq(it) }
                reverb = Reverb(fs, 1.2, 0.12f, 100.0)
            }
            AudioPreset.OldRadio, AudioPreset.LoFi -> {
                (Biquad.highPass24(fs, 400.0) + Biquad.lowPass24(fs, 3_500.0)).forEach { eq(it) }
                eq(Biquad.peak(fs, 1_500.0, 5.0, 2.0))
                mono = true
                tubeDrive = 1.6f
                radio = Radio(fs)
            }
        }
    }

    fun process(frame: FloatArray) {
        var l = frame[0] * preGain
        var r = frame[1] * preGain
        if (mono) {
            val m = (l + r) * 0.5f
            l = m
            r = m
        }
        for (i in eqL.indices) {
            l = eqL[i].process(l)
            r = eqR[i].process(r)
        }
        monoBelow?.let { (fl, fr) ->
            val lowL = fl.process(l)
            val lowR = fr.process(r)
            val lowMono = (lowL + lowR) * 0.5f
            l += lowMono - lowL
            r += lowMono - lowR
        }
        if (width != 0f) {
            val m = (l + r) * 0.5f
            val s = (l - r) * 0.5f * (1 + width)
            l = m + s
            r = m - s
        }
        frame[0] = l
        frame[1] = r
        bassSlam?.process(frame)
        bassHarmonics?.process(frame)
        haas?.process(frame)
        early?.process(frame)
        autoPan?.process(frame)
        if (tubeDrive > 0f) {
            val norm = tanh(tubeDrive)
            frame[0] = tanh(frame[0] * tubeDrive) / norm
            frame[1] = tanh(frame[1] * tubeDrive) / norm
        }
        radio?.process(frame)
        reverb?.process(frame)
        limiter?.process(frame)
    }

    fun reset() {
        eqL.forEach { it.reset() }; eqR.forEach { it.reset() }
        monoBelow?.let { it.first.reset(); it.second.reset() }
        bassSlam?.reset()
        bassHarmonics?.reset()
        limiter?.reset()
        reverb?.reset()
        haas?.reset()
        early?.reset()
        autoPan?.reset()
        radio?.reset()
    }

    /** Délai croisé de Haas (15 ms) : chaque côté reçoit l'autre en retard, adouci vers 6 kHz. */
    private class Haas(fs: Int) {
        private val delay = fs * 0.015f
        private val fromL = Delay((fs * 0.02).toInt())
        private val fromR = Delay((fs * 0.02).toInt())
        private val notchL = Biquad.peak(fs, 6_000.0, -3.0, 1.0)
        private val notchR = Biquad.peak(fs, 6_000.0, -3.0, 1.0)

        fun process(frame: FloatArray) {
            fromL.push(frame[0])
            fromR.push(frame[1])
            val toL = notchL.process(fromR.read(delay))
            val toR = notchR.process(fromL.read(delay))
            frame[0] = frame[0] * 0.85f + toL * 0.3f
            frame[1] = frame[1] * 0.85f + toR * 0.3f
        }

        fun reset() {
            fromL.reset(); fromR.reset(); notchL.reset(); notchR.reset()
        }
    }

    /** Premières réflexions : quelques échos très proches (7 à 41 ms), de plus en plus faibles, alternés. */
    private class Early(fs: Int) {
        private val line = Delay((fs * 0.05).toInt())
        private val taps = floatArrayOf(0.007f, 0.013f, 0.019f, 0.027f, 0.034f, 0.041f).map { it * fs }
        private val gains = floatArrayOf(0.18f, 0.14f, 0.11f, 0.08f, 0.06f, 0.045f)

        fun process(frame: FloatArray) {
            line.push((frame[0] + frame[1]) * 0.5f)
            var l = 0f
            var r = 0f
            for (i in taps.indices) {
                val v = line.read(taps[i]) * gains[i]
                if (i % 2 == 0) l += v else r += v
            }
            frame[0] += l
            frame[1] += r
        }

        fun reset() = line.reset()
    }

    /**
     * 8D : le son fait le tour de la tête (un cycle en 7 s) ; les basses restent au centre. 3.6.0 : en
     * plus du volume, l'oreille opposée reçoit le son un peu plus tard (jusqu'à 0,65 ms, l'écart réel
     * entre deux oreilles) : c'est surtout ce retard qui fait situer un son sur le côté.
     */
    private class AutoPan(private val fs: Int) {
        private val lowL = Biquad.lowPass(fs, 150.0)
        private val lowR = Biquad.lowPass(fs, 150.0)
        private val lineL = Delay((fs * 0.002).toInt())
        private val lineR = Delay((fs * 0.002).toInt())
        private val earGap = fs * 0.00065f
        private var phase = 0.0
        private val step = 2 * PI / (7.0 * fs)

        fun process(frame: FloatArray) {
            val lL = lowL.process(frame[0])
            val lR = lowR.process(frame[1])
            val low = (lL + lR) * 0.5f
            val highL = frame[0] - lL
            val highR = frame[1] - lR
            // Panoramique à puissance constante : au centre, chaque côté garde son niveau
            val p = sin(phase)
            phase += step
            if (phase > 2 * PI) phase -= 2 * PI
            val angle = (p + 1) * PI / 4
            val gL = (cos(angle) * sqrt(2.0)).toFloat()
            val gR = (sin(angle) * sqrt(2.0)).toFloat()
            lineL.push(highL)
            lineR.push(highR)
            val side = p.toFloat() // +1 : tout à droite (la gauche est en retard), −1 : tout à gauche
            frame[0] = low + lineL.read(1f + earGap * maxOf(0f, side)) * gL
            frame[1] = low + lineR.read(1f + earGap * maxOf(0f, -side)) * gR
        }

        fun reset() {
            lowL.reset(); lowR.reset(); lineL.reset(); lineR.reset()
        }
    }

    /** Vieille radio : un léger souffle (bruit rose à −38 dB) et un petit « wow » (±0,2 % de hauteur). */
    private class Radio(private val fs: Int) {
        private val line = Delay((fs * 0.004).toInt() + 4)
        private var phase = 0.0
        private val rate = 0.5 // Hz
        private val depth = (0.002 * fs / (2 * PI * rate)).toFloat() // l'écart de hauteur voulu
        private val base = depth + 2f
        private var seed = 12345
        private var b0 = 0f
        private var b1 = 0f
        private var b2 = 0f

        fun process(frame: FloatArray) {
            line.push(frame[0])
            phase += 2 * PI * rate / fs
            if (phase > 2 * PI) phase -= 2 * PI
            val wowed = line.read(base + depth * sin(phase).toFloat())
            // Bruit rose (filtre de Paul Kellet) sur un bruit blanc pseudo-aléatoire
            seed = seed * 1103515245 + 12345
            val white = ((seed ushr 8) and 0xFFFF) / 32768f - 1f
            b0 = 0.99765f * b0 + white * 0.0990460f
            b1 = 0.96300f * b1 + white * 0.2965164f
            b2 = 0.57000f * b2 + white * 1.0526913f
            val pink = (b0 + b1 + b2 + white * 0.1848f) * 0.11f
            val v = wowed + pink * NOISE
            frame[0] = v
            frame[1] = v
        }

        fun reset() {
            line.reset()
            b0 = 0f; b1 = 0f; b2 = 0f
        }

        companion object {
            private val NOISE = 10.0.pow(-38.0 / 20).toFloat()
        }
    }
}
