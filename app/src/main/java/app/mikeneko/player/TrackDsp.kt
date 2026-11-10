package app.mikeneko.player

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Les calculs de [TrackAnalyzer], sans rien d'Android (testés sur l'ordinateur : app/src/test).
 * [x] : le son en mono, [fs] : sa fréquence d'échantillonnage (vers 11 kHz).
 */
internal object TrackDsp {
    // ------------------------------------------------------------------ tempo

    fun tempo(x: FloatArray, fs: Float): Int? {
        val n = 1024
        val hop = 128
        val frames = (x.size - n) / hop
        if (frames < 200) return null
        val window = hann(n)
        val re = FloatArray(n)
        val im = FloatArray(n)
        var previous = FloatArray(n / 2)
        var current = FloatArray(n / 2)
        val onset = FloatArray(frames)
        for (f in 0 until frames) {
            val start = f * hop
            for (i in 0 until n) {
                re[i] = x[start + i] * window[i]
                im[i] = 0f
            }
            fft(re, im)
            var flux = 0f
            for (k in 1 until n / 2) {
                val m = ln(1f + 100f * sqrt(re[k] * re[k] + im[k] * im[k]))
                current[k] = m
                val d = m - previous[k]
                if (d > 0f) flux += d
            }
            onset[f] = flux
            val swap = previous
            previous = current
            current = swap
        }
        // Les attaques au-dessus de leur moyenne locale (~0,3 s)
        val rate = fs / hop // images par seconde
        val half = (rate * 0.15f).roundToInt().coerceAtLeast(1)
        val prefix = DoubleArray(frames + 1)
        for (i in 0 until frames) prefix[i + 1] = prefix[i] + onset[i]
        val o = FloatArray(frames) { t ->
            val a = (t - half).coerceAtLeast(0)
            val b = (t + half + 1).coerceAtMost(frames)
            (onset[t] - ((prefix[b] - prefix[a]) / (b - a)).toFloat()).coerceAtLeast(0f)
        }
        val maxLag = (4 * rate).toInt() + 3 // 4 temps à 60 BPM
        if (maxLag >= frames / 2) return null
        val ac = DoubleArray(maxLag + 1)
        for (lag in 1..maxLag) {
            var s = 0.0
            for (t in 0 until frames - lag) s += o[t] * o[t + lag]
            ac[lag] = s / (frames - lag)
        }
        fun at(lag: Double): Double {
            val i = lag.toInt()
            if (i < 1 || i + 1 > maxLag) return 0.0
            val frac = lag - i
            return ac[i] * (1 - frac) + ac[i + 1] * frac
        }
        /** Comment le tempo [bpm] explique les attaques : ses 4 premiers multiples. */
        fun raw(bpm: Double): Double {
            val period = 60.0 * rate / bpm
            var score = 0.0
            for (k in 1..4) score += at(period * k)
            return score
        }
        var best = 0.0
        var bestBpm = 0.0
        var bpm = 60.0
        while (bpm <= 200.0) {
            val octaves = log2(bpm / 120.0)
            val score = raw(bpm) * exp(-0.5 * octaves * octaves) // un peu de préférence autour de 120
            if (score > best) {
                best = score
                bestBpm = bpm
            }
            bpm += 0.1
        }
        if (best <= 0.0) return null
        var result = bestBpm
        // Le double explique presque aussi bien les attaques (une caisse claire un temps sur deux…) :
        // c'est lui le vrai tempo (174 et non 87). Un tempo juste n'a presque rien à mi-chemin des temps.
        if (result * 2 <= 200.0 && raw(result * 2) >= 0.7 * raw(result)) result *= 2
        if (result < 70.0 && result * 2 <= 200.0) result *= 2 // un tempo lent compté en deux
        return result.roundToInt()
    }

    // ------------------------------------------------------------------ tonalité

    /** Profils de KeyFinder (I. Sha'ath), de la tonique à la septième. */
    private val MAJOR = doubleArrayOf(7.23900, 3.50351, 3.58429, 2.84467, 5.81627, 4.55836, 2.44715, 6.99643, 3.39176, 4.55660, 4.07402, 4.45940)
    private val MINOR = doubleArrayOf(7.00377, 3.14456, 4.36870, 5.40263, 3.67479, 4.08929, 3.91115, 6.20211, 3.63236, 2.87152, 5.35975, 3.83452)

    /** La case Camelot de chaque tonique (0 = do, 1 = do♯…), en majeur (B) et en mineur (A). */
    private val CAMELOT_MAJOR = intArrayOf(8, 3, 10, 5, 12, 7, 2, 9, 4, 11, 6, 1)
    private val CAMELOT_MINOR = intArrayOf(5, 12, 7, 2, 9, 4, 11, 6, 1, 8, 3, 10)

    fun camelot(x: FloatArray, fs: Float): String? {
        val n = 8192
        val hop = 4096
        val frames = (x.size - n) / hop
        if (frames < 4) return null
        // Chaque case de fréquence → sa note, et son poids (plein au milieu de la note, nul entre deux)
        val note = IntArray(n / 2) { -1 }
        val weight = FloatArray(n / 2)
        for (k in 1 until n / 2) {
            val f = k * fs / n
            if (f < 55f || f > 1760f) continue
            val midi = 69.0 + 12.0 * log2(f / 440.0)
            val nearest = midi.roundToInt()
            note[k] = ((nearest % 12) + 12) % 12
            weight[k] = (1.0 - 2.0 * abs(midi - nearest)).toFloat()
        }
        val window = hann(n)
        val re = FloatArray(n)
        val im = FloatArray(n)
        val total = DoubleArray(12)
        val chroma = DoubleArray(12)
        for (f in 0 until frames) {
            val start = f * hop
            for (i in 0 until n) {
                re[i] = x[start + i] * window[i]
                im[i] = 0f
            }
            fft(re, im)
            chroma.fill(0.0)
            for (k in 1 until n / 2) {
                val pc = note[k]
                if (pc < 0) continue
                chroma[pc] += weight[k] * sqrt(re[k] * re[k] + im[k] * im[k])
            }
            val max = chroma.max()
            if (max > 1e-6) for (i in 0 until 12) total[i] += chroma[i] / max // chaque passage compte pareil
        }
        if (total.max() <= 0.0) return null
        var best = -2.0
        var code: String? = null
        for (tonic in 0 until 12) {
            for (minor in listOf(false, true)) {
                val profile = if (minor) MINOR else MAJOR
                val r = correlation(total) { pc -> profile[(pc - tonic + 12) % 12] }
                if (r > best) {
                    best = r
                    code = if (minor) "${CAMELOT_MINOR[tonic]}A" else "${CAMELOT_MAJOR[tonic]}B"
                }
            }
        }
        return code
    }

    private fun correlation(a: DoubleArray, b: (Int) -> Double): Double {
        val ma = a.average()
        val mb = (0 until 12).sumOf { b(it) } / 12
        var num = 0.0
        var da = 0.0
        var db = 0.0
        for (i in 0 until 12) {
            val x = a[i] - ma
            val y = b(i) - mb
            num += x * y
            da += x * x
            db += y * y
        }
        return if (da <= 0 || db <= 0) -1.0 else num / sqrt(da * db)
    }

    // ------------------------------------------------------------------ outils

    private fun hann(n: Int) = FloatArray(n) { (0.5 - 0.5 * cos(2 * PI * it / (n - 1))).toFloat() }

    /** FFT en place (taille en puissance de 2). */
    internal fun fft(re: FloatArray, im: FloatArray) {
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
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
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
                    val xr = re[b] * cr - im[b] * ci
                    val xi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - xr
                    im[b] = im[a] - xi
                    re[a] += xr
                    im[a] += xi
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
