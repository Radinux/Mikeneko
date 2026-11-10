package app.mikeneko.player

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

/** Des sons fabriqués dont on connaît le tempo et la tonalité. */
class TrackDspTest {
    private val fs = 11025f

    /** [bpm] : une grosse caisse à chaque temps ; [chords] : des accords (notes MIDI), un par mesure. */
    private fun song(bpm: Double, chords: List<List<Int>>, seconds: Int = 90): FloatArray {
        val n = (seconds * fs).toInt()
        val x = FloatArray(n)
        val random = Random(1)
        val beat = 60.0 / bpm
        var t = 0.0
        var count = 0
        while (t < seconds) {
            val start = (t * fs).toInt()
            for (i in 0 until (0.08 * fs).toInt()) {
                if (start + i >= n) break
                val env = exp(-i / (0.015 * fs))
                // Le claquement d'attaque de la grosse caisse (5 ms), puis la caisse claire un temps sur deux
                val click = if (i < 0.005 * fs) 0.7 * (random.nextDouble() * 2 - 1) else 0.0
                val hit = sin(2 * PI * 60 * i / fs) + click + (if (count % 2 == 1) 0.6 * (random.nextDouble() * 2 - 1) else 0.0)
                x[start + i] += (0.8 * env * hit).toFloat()
            }
            t += beat
            count++
        }
        val bar = 4 * beat
        for (i in 0 until n) {
            val time = i / fs
            val chord = chords[((time / bar).toInt()) % chords.size]
            var v = 0.0
            for (note in chord) {
                val f = 440.0 * 2.0.pow((note - 69) / 12.0)
                for (h in 1..4) v += sin(2 * PI * f * h * time) / (h * h)
            }
            x[i] += (0.12 * v).toFloat()
        }
        return x
    }

    @Test
    fun tempo128() {
        assertEquals(128, TrackDsp.tempo(song(128.0, listOf(listOf(57, 60, 64))), fs))
    }

    @Test
    fun tempo95() {
        assertEquals(95, TrackDsp.tempo(song(95.0, listOf(listOf(55, 59, 62))), fs))
    }

    @Test
    fun tempo174() {
        assertEquals(174, TrackDsp.tempo(song(174.0, listOf(listOf(57, 60, 64))), fs))
    }

    @Test
    fun tempo140HalfTimeFeel() {
        assertEquals(140, TrackDsp.tempo(song(140.0, listOf(listOf(57, 60, 64))), fs))
    }

    @Test
    fun tempo85() {
        assertEquals(85, TrackDsp.tempo(song(85.0, listOf(listOf(57, 60, 64))), fs))
    }

    @Test
    fun aMinorIs8A() {
        // Am – Dm – E – Am (i – iv – V – i, avec le sol dièse) : la mineur
        val chords = listOf(listOf(57, 60, 64), listOf(50, 53, 57), listOf(52, 56, 59), listOf(45, 57, 60, 64))
        assertEquals("8A", TrackDsp.camelot(song(120.0, chords), fs))
    }

    @Test
    fun gMajorIs9B() {
        // G – D – Em – C (I – V – vi – IV) : sol majeur
        val chords = listOf(listOf(55, 59, 62, 67), listOf(50, 54, 57, 62), listOf(52, 55, 59), listOf(48, 52, 55))
        assertEquals("9B", TrackDsp.camelot(song(100.0, chords), fs))
    }
}
