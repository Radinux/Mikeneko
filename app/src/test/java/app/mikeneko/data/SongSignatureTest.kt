package app.mikeneko.data

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Test

class SongSignatureTest {
    /** Le même signal que le script de référence Python : deux sinus qui glissent + un bruit LCG. */
    private fun synthetic(seconds: Int): ShortArray {
        var seed = 12345L
        return ShortArray(SongSignature.RATE * seconds) { i ->
            seed = (seed * 1103515245 + 12345) and 0x7fffffff
            val t = i.toDouble() / SongSignature.RATE
            val f1 = 440.0 + 200.0 * (i / 8000 % 7)
            val f2 = 1800.0 + 300.0 * (i / 12000 % 5)
            val v = 6000 * Math.sin(2 * Math.PI * f1 * t) + 3000 * Math.sin(2 * Math.PI * f2 * t) + (seed % 2000 - 1000)
            v.toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun md5(bytes: ByteArray) = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Empreinte identique, octet pour octet, à celle de ShazamIO 0.4 (bande 3,5–5,5 kHz réparée) sur ce signal. */
    @Test
    fun matchesReference() {
        val sig = SongSignature().apply { feed(synthetic(8)) }
        val bytes = sig.toBinary()
        System.getenv("MIKENEKO_SIG_OUT")?.let { File(it, "synthetic.bin").writeBytes(bytes) }
        assertEquals(EXPECTED_MD5, md5(bytes))
    }

    /** Outil : MIKENEKO_SIG_PCM=fichier s16le 16 kHz mono → l'URI de son empreinte dans MIKENEKO_SIG_OUT. */
    @Test
    fun dumpFromPcm() {
        val pcm = System.getenv("MIKENEKO_SIG_PCM") ?: return
        val raw = File(pcm).readBytes()
        val samples = ShortArray(raw.size / 2) { ((raw[2 * it + 1].toInt() shl 8) or (raw[2 * it].toInt() and 0xFF)).toShort() }
        val sig = SongSignature().apply { feed(samples) }
        File(System.getenv("MIKENEKO_SIG_OUT"), "clip.uri").writeText(sig.toUri())
    }

    companion object {
        const val EXPECTED_MD5 = "559e08c0b084a370c254268097f6c530"
    }
}
