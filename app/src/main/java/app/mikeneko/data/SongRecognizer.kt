package app.mikeneko.data

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.TimeZone
import java.util.UUID
import java.util.zip.CRC32
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * L'empreinte d'un extrait audio, au format attendu par le service de reconnaissance (2.25.0).
 * Portage en Kotlin de l'algorithme de ShazamIO 0.4 (MIT, lui-même tiré de SongRec, GPL-3.0) :
 * des FFT de 2048 échantillons toutes les 128, on garde les pics locaux (en fréquence et en temps)
 * de quatre bandes, codés en binaire puis en base64.
 *
 * Entrée : PCM 16 bits mono à 16 kHz. Un objet par extrait (il garde l'état de ses tampons).
 */
class SongSignature {
    private val ring = DoubleArray(WINDOW)
    private var ringPos = 0

    private val fftOutputs = Array(HISTORY) { DoubleArray(BINS) }
    private var fftPos = 0

    private val spread = Array(HISTORY) { DoubleArray(BINS) }
    private var spreadPos = 0
    private var spreadWritten = 0

    private val peaks = Array(4) { ArrayList<Peak>() }
    private var samples = 0

    private class Peak(val fftPass: Int, val magnitude: Int, val bin: Int)

    // Tampons de travail (une FFT toutes les 128 échantillons : pas d'allocation à chaque fois)
    private val re = DoubleArray(WINDOW)
    private val im = DoubleArray(WINDOW)

    /** Ajoute des échantillons (le reste qui ne fait pas un bloc de 128 est ignoré). */
    fun feed(pcm: ShortArray, count: Int = pcm.size) {
        var i = 0
        while (i + CHUNK <= count) {
            for (k in 0 until CHUNK) ring[(ringPos + k) % WINDOW] = pcm[i + k].toDouble()
            ringPos = (ringPos + CHUNK) % WINDOW
            samples += CHUNK
            doFft()
            doPeakSpreading()
            if (spreadWritten >= 46) doPeakRecognition()
            i += CHUNK
        }
    }

    val durationMs: Int get() = (samples.toLong() * 1000 / RATE).toInt()

    private fun doFft() {
        // Du plus ancien au plus récent, fenêtré par Hann
        for (k in 0 until WINDOW) {
            re[k] = ring[(ringPos + k) % WINDOW] * HANNING[k]
            im[k] = 0.0
        }
        fft(re, im)
        val out = fftOutputs[fftPos]
        for (k in 0 until BINS) out[k] = max((re[k] * re[k] + im[k] * im[k]) / (1 shl 17), 1e-10)
        fftPos = (fftPos + 1) % HISTORY
    }

    private fun doPeakSpreading() {
        val last = fftOutputs[(fftPos - 1 + HISTORY) % HISTORY]
        val now = spread[spreadPos]
        for (k in 0 until BINS) now[k] = if (k < BINS - 3) max(last[k], max(last[k + 1], last[k + 2])) else last[k]
        val i1 = spread[(spreadPos - 1 + HISTORY) % HISTORY]
        val i2 = spread[(spreadPos - 3 + HISTORY) % HISTORY]
        val i3 = spread[(spreadPos - 6 + HISTORY) % HISTORY]
        for (k in 0 until BINS) {
            val a = max(now[k], i1[k])
            val b = max(a, i2[k])
            i1[k] = a
            i2[k] = b
            i3[k] = max(b, i3[k])
        }
        spreadPos = (spreadPos + 1) % HISTORY
        spreadWritten++
    }

    private fun doPeakRecognition() {
        val fft46 = fftOutputs[(fftPos - 46 + HISTORY) % HISTORY]
        val spread49 = spread[(spreadPos - 49 + HISTORY) % HISTORY]
        for (bin in 10 until 1015) {
            val v = fft46[bin]
            if (v < 1.0 / 64 || v < spread49[bin - 1]) continue
            var maxNeighbor = 0.0
            for (o in FREQ_NEIGHBORS) maxNeighbor = max(spread49[bin + o], maxNeighbor)
            if (v <= maxNeighbor) continue
            var maxOther = maxNeighbor
            for (o in TIME_NEIGHBORS) maxOther = max(spread[((spreadPos + o) % HISTORY + HISTORY) % HISTORY][bin - 1], maxOther)
            if (v <= maxOther) continue

            val magnitude = magnitude(v)
            val before = magnitude(fft46[bin - 1])
            val after = magnitude(fft46[bin + 1])
            val variation1 = magnitude * 2 - before - after
            if (variation1 <= 0) continue
            val variation2 = (after - before) * 32 / variation1
            val corrected = bin * 64 + variation2
            val hz = corrected * (RATE / 2.0 / 1024 / 64)
            val band = when {
                hz > 250 && hz < 520 -> 0
                hz > 520 && hz < 1450 -> 1
                hz > 1450 && hz < 3500 -> 2
                hz > 3500 && hz <= 5500 -> 3
                else -> continue
            }
            peaks[band] += Peak(spreadWritten - 46, magnitude.toInt(), corrected.toInt())
        }
    }

    private fun magnitude(v: Double) = ln(max(1.0 / 64, v)) * 1477.3 + 6144

    /** L'empreinte en « data URI », à envoyer au service. */
    fun toUri(): String = URI_PREFIX + Base64.getEncoder().encodeToString(toBinary())

    fun toBinary(): ByteArray {
        val contents = ByteArrayOutputStream()
        for ((band, list) in peaks.withIndex()) {
            if (list.isEmpty()) continue
            val buf = ByteArrayOutputStream()
            var pass = 0
            for (p in list) {
                if (p.fftPass - pass >= 255) {
                    buf.write(0xFF)
                    buf.u32(p.fftPass)
                    pass = p.fftPass
                }
                buf.write(p.fftPass - pass)
                buf.u16(p.magnitude)
                buf.u16(p.bin)
                pass = p.fftPass
            }
            val bytes = buf.toByteArray()
            contents.u32(0x60030040 + band)
            contents.u32(bytes.size)
            contents.write(bytes)
            repeat((4 - bytes.size % 4) % 4) { contents.write(0) }
        }
        val body = contents.toByteArray()
        val out = ByteArrayOutputStream()
        out.u32(0xCAFE2580.toInt())
        out.u32(0) // CRC-32, réécrit plus bas
        out.u32(body.size + 8)
        out.u32(0x94119C00.toInt())
        repeat(3) { out.u32(0) }
        out.u32(3 shl 27) // 16 kHz
        repeat(2) { out.u32(0) }
        out.u32((samples + RATE * 0.24).toInt())
        out.u32((15 shl 19) + 0x40000)
        out.u32(0x40000000)
        out.u32(body.size + 8)
        out.write(body)
        val bytes = out.toByteArray()
        val crc = CRC32().apply { update(bytes, 8, bytes.size - 8) }.value.toInt()
        for (k in 0 until 4) bytes[4 + k] = (crc ushr (8 * k)).toByte()
        return bytes
    }

    private fun ByteArrayOutputStream.u32(v: Int) {
        for (k in 0 until 4) write((v ushr (8 * k)) and 0xFF)
    }

    private fun ByteArrayOutputStream.u16(v: Int) {
        write(v and 0xFF)
        write((v ushr 8) and 0xFF)
    }

    companion object {
        const val RATE = 16000
        private const val WINDOW = 2048
        private const val BINS = WINDOW / 2 + 1
        private const val CHUNK = 128
        private const val HISTORY = 256
        private const val URI_PREFIX = "data:audio/vnd.shazam.sig;base64,"
        private val FREQ_NEIGHBORS = intArrayOf(-10, -7, -4, -3, 1, 2, 5, 8)
        private val TIME_NEIGHBORS = intArrayOf(-53, -45, 165, 172, 179, 186, 193, 200, 214, 221, 228, 235, 242, 249)

        /** Hann sur 2050 points sans ses deux zéros des bords (comme numpy.hanning(2050)[1:-1]). */
        private val HANNING = DoubleArray(WINDOW) { 0.5 - 0.5 * cos(2 * PI * (it + 1) / (WINDOW + 1)) }

        private val BIT_REVERSED = IntArray(WINDOW).also { rev ->
            val bits = Integer.numberOfTrailingZeros(WINDOW)
            for (i in 0 until WINDOW) rev[i] = Integer.reverse(i) ushr (32 - bits)
        }
        private val COS = DoubleArray(WINDOW / 2) { cos(2 * PI * it / WINDOW) }
        private val SIN = DoubleArray(WINDOW / 2) { -sin(2 * PI * it / WINDOW) }

        /** FFT radix 2 en place (taille [WINDOW]). */
        private fun fft(re: DoubleArray, im: DoubleArray) {
            val n = WINDOW
            for (i in 0 until n) {
                val j = BIT_REVERSED[i]
                if (j > i) {
                    var t = re[i]; re[i] = re[j]; re[j] = t
                    t = im[i]; im[i] = im[j]; im[j] = t
                }
            }
            var size = 2
            while (size <= n) {
                val half = size / 2
                val step = n / size
                var start = 0
                while (start < n) {
                    for (k in 0 until half) {
                        val wr = COS[k * step]
                        val wi = SIN[k * step]
                        val a = start + k
                        val b = a + half
                        val tr = re[b] * wr - im[b] * wi
                        val ti = re[b] * wi + im[b] * wr
                        re[b] = re[a] - tr
                        im[b] = im[a] - ti
                        re[a] += tr
                        im[a] += ti
                    }
                    start += size
                }
                size *= 2
            }
        }
    }
}

/** Un morceau reconnu : le titre et l'artiste donnés par le service, et sa pochette. */
data class RecognizedSong(val title: String, val artist: String, val cover: String?)

/**
 * La reconnaissance de musique (2.25.0) : une empreinte de l'extrait part au service de
 * reconnaissance (non officiel, la même requête que ShazamIO), qui renvoie le morceau s'il le connaît.
 * Seule l'empreinte est envoyée, jamais le son lui-même.
 */
object SongRecognizer {
    private const val ENDPOINT = "https://amp.shazam.com/discovery/v5/en-US/GB/iphone/-/tag/%s/%s" +
        "?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true&hubv5minorversion=v5.1&hidelb=true&video=v3"

    /**
     * [uri] et [durationMs] viennent de [SongSignature.toUri] / [SongSignature.durationMs], pris sur le
     * fil qui l'alimente. null : pas reconnu. Lève une exception si le réseau ou le service répond mal.
     */
    suspend fun recognize(uri: String, durationMs: Int): RecognizedSong? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("timezone", TimeZone.getDefault().id)
            .put("signature", JSONObject().put("uri", uri).put("samplems", durationMs))
            .put("timestamp", System.currentTimeMillis())
            .put("context", JSONObject())
            .put("geolocation", JSONObject())
        val request = Request.Builder()
            .url(ENDPOINT.format(UUID.randomUUID().toString().uppercase(), UUID.randomUUID().toString().uppercase()))
            .header("X-Shazam-Platform", "IPHONE")
            .header("X-Shazam-AppVersion", "14.1.0")
            .header("Accept", "*/*")
            .header("Accept-Language", "en-US")
            .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 12; Pixel 6 Build/SQ3A.220705.003)")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        NewPipeDownloader.client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val track = JSONObject(response.body?.string().orEmpty()).optJSONObject("track") ?: return@use null
            val title = track.optString("title").trim()
            if (title.isEmpty()) return@use null
            val images = track.optJSONObject("images")
            RecognizedSong(
                title = title,
                artist = track.optString("subtitle").trim(),
                cover = images?.optString("coverarthq")?.ifBlank { null } ?: images?.optString("coverart")?.ifBlank { null },
            )
        }
    }
}
