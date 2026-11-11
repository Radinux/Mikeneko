package app.mikeneko.data

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import app.mikeneko.R
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Écouter au micro et reconnaître le morceau (2.25.0). L'empreinte grossit au fil de l'écoute ; on
 * la soumet à 4, 7, 10 puis 13 s (le service en veut au moins quelques secondes, jusqu'à ~12) et on
 * s'arrête dès qu'il reconnaît. Le son ne quitte jamais le téléphone, seule l'empreinte.
 */
object SongListener {
    private val CHECKPOINTS = intArrayOf(4, 7, 10, 13)

    sealed interface Outcome {
        data class Found(val song: RecognizedSong) : Outcome
        data object NotFound : Outcome
        /** Le service n'a jamais répondu (réseau, ou il a changé). */
        data object Failed : Outcome
    }

    /** [onProgress] : de 0 à 1 au fil de l'écoute. Il faut la permission RECORD_AUDIO. */
    @SuppressLint("MissingPermission")
    suspend fun listen(onProgress: (Float) -> Unit): Outcome = withContext(Dispatchers.IO) {
        val rate = SongSignature.RATE
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate * 2))
        check(record.state == AudioRecord.STATE_INITIALIZED) { "micro indisponible" }
        val total = CHECKPOINTS.last() * rate
        try {
            record.startRecording()
            coroutineScope {
                val signature = SongSignature()
                val buffer = ShortArray(2048)
                var read = 0
                var next = 0
                var answered = false
                // Chaque requête rend un Result : une qui échoue n'arrête pas l'écoute
                val asks = ArrayList<Deferred<Result<RecognizedSong?>>>()
                fun take(result: Result<RecognizedSong?>): RecognizedSong? {
                    if (result.isSuccess) answered = true
                    return result.getOrNull()
                }
                var song: RecognizedSong? = null
                while (read < total && song == null) {
                    ensureActive()
                    val n = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (n <= 0) continue
                    signature.feed(buffer, n) // 2048 : un multiple de 128, rien n'est perdu
                    read += n
                    onProgress(read.toFloat() / total)
                    if (next < CHECKPOINTS.size && read >= CHECKPOINTS[next] * rate) {
                        // L'empreinte est lue ici, sur ce fil ; seule la requête part à côté
                        val uri = signature.toUri()
                        val ms = signature.durationMs
                        asks += async { runCatching { SongRecognizer.recognize(uri, ms) } }
                        next++
                    }
                    song = asks.filter { it.isCompleted }.firstNotNullOfOrNull { take(it.getCompleted()) }
                }
                record.stop()
                // Écoute finie : on attend les réponses encore en route
                if (song == null) for (a in asks) {
                    song = take(a.await())
                    if (song != null) break
                }
                asks.forEach { it.cancel() }
                when {
                    song != null -> Outcome.Found(song)
                    answered -> Outcome.NotFound
                    else -> Outcome.Failed
                }
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }
}

/**
 * La playlist « Found songs » (2.25.0) : chaque morceau reconnu y entre en tête. Créée au premier
 * morceau ; si on la supprime, la suivante la recrée.
 */
object FoundSongs {
    private const val PREFS = "morceaux_trouves"
    private const val KEY = "playlist"

    /** Ajoute [item] ; renvoie l'id de la playlist et si le morceau est nouveau. */
    fun add(context: Context, item: VideoItem): Pair<String, Boolean> {
        val lists = Playlists.get(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = context.getString(R.string.found_songs)
        val id = prefs.getString(KEY, null)?.takeIf { lists.get(it) != null }
            ?: lists.items.value.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
            ?: lists.create(name).id
        prefs.edit().putString(KEY, id).apply()
        return id to lists.add(id, item)
    }

    fun name(context: Context): String = context.getString(R.string.found_songs)
}
