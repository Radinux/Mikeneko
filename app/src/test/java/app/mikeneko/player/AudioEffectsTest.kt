package app.mikeneko.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les effets audio (3.3.0) : pas de bruit numérique, pas de saturation, et chacun change bien le son. */
class AudioEffectsTest {
    /** Un signal fort et riche : basses (55 Hz), médiums, aigus, et un côté différent de l'autre. */
    private fun signal(i: Int, fs: Int, right: Boolean): Float {
        val t = i.toDouble() / fs
        val bass = 0.45 * sin(2 * PI * 55 * t)
        val mid = 0.25 * sin(2 * PI * (440 + if (right) 3 else 0) * t)
        val high = 0.15 * sin(2 * PI * 9_000 * t + if (right) 1.0 else 0.0)
        return (bass + mid + high).toFloat()
    }

    @Test fun everyPresetStaysFiniteAndBelowFullScale() {
        for (fs in intArrayOf(44_100, 48_000)) {
            for (preset in AudioPreset.entries) {
                val chain = EffectChain(preset, fs)
                val frame = FloatArray(2)
                var peak = 0f
                var changed = 0.0
                for (i in 0 until fs * 3) {
                    val l = signal(i, fs, false)
                    val r = signal(i, fs, true)
                    frame[0] = l
                    frame[1] = r
                    chain.process(frame)
                    assertTrue("$preset @ $fs : NaN/infini", frame[0].isFinite() && frame[1].isFinite())
                    peak = maxOf(peak, abs(frame[0]), abs(frame[1]))
                    changed += abs(frame[0] - l) + abs(frame[1] - r)
                }
                assertTrue("$preset @ $fs : crête $peak ≥ 0 dBFS", peak < 1f)
                if (preset == AudioPreset.Normal) {
                    assertEquals("Normal doit laisser le son intact", 0.0, changed, 1e-6)
                } else {
                    assertTrue("$preset @ $fs ne change rien au son", changed / (fs * 3) > 1e-3)
                }
            }
        }
    }

    @Test fun silenceStaysQuietExceptTheRadioHiss() {
        for (preset in AudioPreset.entries) {
            val chain = EffectChain(preset, 48_000)
            val frame = FloatArray(2)
            var peak = 0f
            repeat(48_000) {
                frame[0] = 0f
                frame[1] = 0f
                chain.process(frame)
                peak = maxOf(peak, abs(frame[0]), abs(frame[1]))
            }
            // Le souffle de la vieille radio est à −38 dB (≈ 0,0126) ; le reste ne doit rien inventer
            val allowed = if (preset == AudioPreset.OldRadio || preset == AudioPreset.LoFi) 0.05f else 1e-6f
            assertTrue("$preset : bruit $peak sur du silence", peak <= allowed)
        }
    }

    @Test fun processorTurnsMono16BitIntoStereo() {
        val processor = PresetAudioProcessor { AudioPreset.BassBoost }
        val out = processor.configure(AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_16BIT))
        assertEquals(2, out.channelCount)
        processor.flush()
        val frames = 4_800
        val input = ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.nativeOrder())
        repeat(frames) { input.putShort((sin(2 * PI * 100 * it / 48_000.0) * 12_000).toInt().toShort()) }
        input.flip()
        processor.queueInput(input)
        val output = processor.output
        assertEquals(frames * 2 * 2, output.remaining())
        assertEquals(0, input.remaining())
    }

    @Test fun multichannelIsLeftAlone() {
        val processor = PresetAudioProcessor { AudioPreset.Slowed }
        val out = processor.configure(AudioProcessor.AudioFormat(48_000, 6, C.ENCODING_PCM_16BIT))
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, out)
        assertTrue(!processor.isActive)
    }
}
