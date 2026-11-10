package app.mikeneko.player

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.SimpleCache
import app.mikeneko.data.LocalMusic
import app.mikeneko.data.TrackTags
import app.mikeneko.data.trackKey
import app.mikeneko.data.videoIdOf
import java.io.File
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Le tempo (BPM) et la tonalité (roue Camelot) des morceaux, calculés dans l'appli à partir du son
 * (2.15.0) : aucun service extérieur, aucune requête à YouTube. Seuls les morceaux entiers sur le
 * téléphone sont analysés (téléchargés, ou gardés dans la sauvegarde de lecture après avoir été
 * écoutés) ; les autres le seront après leur première écoute.
 *
 * - Tempo : l'énergie des attaques (flux spectral), puis l'autocorrélation ; chaque tempo de 60 à
 *   200 BPM est noté sur ses 4 premiers multiples (précision au dixième de BPM), avec une préférence
 *   douce autour de 120 (évite de trouver la moitié ou le double).
 * - Tonalité : le chromagramme (l'énergie de chacune des 12 notes, de 55 Hz à 1,76 kHz), comparé aux
 *   24 gammes majeures et mineures (profils de KeyFinder), puis écrit sur la roue Camelot.
 *
 * Une minute et demie du milieu du morceau suffit (l'intro est sautée). Un fil à basse priorité, un
 * morceau à la fois.
 */
@OptIn(UnstableApi::class)
object TrackAnalyzer {
    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "analyse-morceaux").apply { priority = Thread.MIN_PRIORITY } }
        .asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val queue = LinkedHashSet<String>()
    private var job: Job? = null
    private var watching = false

    /** Analyse ceux de [urls] qui ne le sont pas encore (et qui sont entiers sur le téléphone). */
    fun request(context: Context, urls: List<String>) {
        val app = context.applicationContext
        val known = TrackTags.load(app).value
        val wanted = urls.filter { known[trackKey(it)]?.analyzed != true }
        if (wanted.isEmpty()) return
        synchronized(queue) { queue.addAll(wanted) }
        if (job?.isActive == true) return
        job = scope.launch {
            while (true) {
                val url = synchronized(queue) { queue.firstOrNull()?.also { queue.remove(it) } } ?: break
                if (TrackTags.get(app, url)?.analyzed == true) continue
                val result = runCatching { analyze(app, url) }.getOrNull() ?: continue // pas sur le téléphone : plus tard
                TrackTags.setAnalysis(app, url, result.first, result.second)
                delay(300)
            }
        }
    }

    /**
     * Un morceau écouté passe tout entier dans la sauvegarde de lecture (MusicLoadControl) : on
     * l'analyse un peu après son début, sans attendre qu'on ouvre la playlist.
     */
    fun watch(context: Context) {
        if (watching) return
        watching = true
        val app = context.applicationContext
        scope.launch {
            PlayerHolder.nowPlaying.collectLatest { now ->
                if (now == null || !now.audioOnly || now.live) return@collectLatest
                delay(40_000)
                request(app, listOf(now.url))
                delay(80_000) // un long morceau, ou un réseau lent
                request(app, listOf(now.url))
            }
        }
    }

    /** (BPM, Camelot) du morceau, ou null s'il n'est pas entier sur le téléphone. */
    private fun analyze(context: Context, url: String): Pair<Int?, String?>? {
        val local = if (LocalMusic.isLocal(url)) LocalMusic.fileOf(url)?.takeIf { it.isFile } else null
        val temp = if (local == null) copyFromCache(context, url) ?: return null else null
        try {
            val pcm = decode(local ?: temp!!) ?: return null to null
            return TrackDsp.tempo(pcm.samples, pcm.rate) to TrackDsp.camelot(pcm.samples, pcm.rate)
        } finally {
            temp?.delete()
        }
    }

    /** Le son du morceau recopié de la sauvegarde (playlist téléchargée d'abord) dans un fichier temporaire. */
    private fun copyFromCache(context: Context, url: String): File? {
        val id = videoIdOf(url) ?: return null
        val entry = OfflineCache.entry(context, OfflineCache.Store.SavedMusic, id)
            ?: OfflineCache.entry(context, OfflineCache.Store.Music, id)
            ?: return null
        val cache: SimpleCache = OfflineCache.cache(context, entry.store)
        val file = File(context.cacheDir, "analyse-${id.hashCode()}.audio")
        val source = OfflineCache.factory(cache, null).createDataSource()
        return try {
            source.open(DataSpec.Builder().setUri("mikeneko-hors-ligne://${entry.audioKey}").setKey(entry.audioKey).build())
            file.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = source.read(buffer, 0, buffer.size)
                    if (n == C.RESULT_END_OF_INPUT) break
                    out.write(buffer, 0, n)
                }
            }
            file
        } catch (e: Exception) {
            file.delete()
            null
        } finally {
            runCatching { source.close() }
        }
    }

    // ------------------------------------------------------------------ décodage

    private class Pcm(val samples: FloatArray, val rate: Float)

    /** Le son en mono, ramené vers 11 kHz, [SECONDS] secondes au plus prises au milieu du morceau. */
    private fun decode(file: File): Pcm? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val duration = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            // L'intro sautée (un cinquième du morceau, 45 s au plus) quand il est assez long
            if (duration > (SECONDS + 40) * 1_000_000L) extractor.seekTo(minOf(duration / 5, 45_000_000L), MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false
            var factor = 0
            var out = FloatArray(0)
            var count = 0
            var sum = 0f
            var summed = 0
            var inputDone = false
            val info = MediaCodec.BufferInfo()
            while (true) {
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = decoder.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val index = decoder.dequeueOutputBuffer(info, 10_000)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = decoder.outputFormat
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (index >= 0) {
                    if (factor == 0) {
                        factor = (rate / TARGET_RATE).coerceAtLeast(1)
                        out = FloatArray(SECONDS * rate / factor + 1)
                    }
                    val buffer = decoder.getOutputBuffer(index)!!.order(ByteOrder.nativeOrder())
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    val frames = if (floatPcm) info.size / 4 / channels else info.size / 2 / channels
                    val floats = if (floatPcm) buffer.asFloatBuffer() else null
                    val shorts = if (floatPcm) null else buffer.asShortBuffer()
                    for (i in 0 until frames) {
                        var mono = 0f
                        for (c in 0 until channels) {
                            mono += floats?.get(i * channels + c) ?: (shorts!!.get(i * channels + c) / 32768f)
                        }
                        sum += mono / channels
                        if (++summed == factor) {
                            if (count < out.size) out[count++] = sum / factor
                            sum = 0f
                            summed = 0
                        }
                    }
                    decoder.releaseOutputBuffer(index, false)
                    if (count >= out.size || info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            if (factor == 0 || count < TARGET_RATE * 8) return null // moins de 8 s : rien de fiable
            return Pcm(out.copyOf(count), rate.toFloat() / factor)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    /** Ce qu'on analyse : 90 s, vers 11 kHz. */
    private const val SECONDS = 90
    private const val TARGET_RATE = 11025
}
