package app.mikeneko.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * TEMPORAIRE (2.25.0) : un journal de ce qui arrive au lecteur, pour trouver les rares gels très
 * courts (image et son) pendant une vidéo. Écrit dans `Android/data/app.mikeneko/files/lecture.log`
 * (lu par adb : les journaux du téléphone sont cachés). Rien ne part sur Internet. À retirer une fois
 * la cause trouvée.
 */
@OptIn(UnstableApi::class)
object PlaybackLog {
    private const val MAX_BYTES = 256 * 1024
    private val writer = Executors.newSingleThreadExecutor()
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var file: File? = null
    private var audio: android.media.AudioManager? = null

    // Détecteur de gel : la position doit avancer avec l'horloge quand le lecteur dit qu'il joue
    private var lastPosition = -1L
    private var lastWall = 0L
    private var stalledSince = 0L

    fun attach(context: Context, player: ExoPlayer) {
        file = context.getExternalFilesDir(null)?.let { File(it, "lecture.log") }
        audio = context.getSystemService(android.media.AudioManager::class.java)
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onPlaybackStateChanged(event: AnalyticsListener.EventTime, state: Int) {
                val name = when (state) {
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "IDLE"
                }
                log("$name pos=${player.currentPosition} buffered=${player.bufferedPosition} ahead=${player.totalBufferedDuration}ms playWhenReady=${player.playWhenReady}")
            }

            override fun onAudioUnderrun(event: AnalyticsListener.EventTime, bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) =
                log("AUDIO_UNDERRUN bufferMs=$bufferSizeMs sinceLastFeed=${elapsedSinceLastFeedMs}ms pos=${player.currentPosition} ahead=${player.totalBufferedDuration}ms")

            override fun onDroppedVideoFrames(event: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                if (droppedFrames >= 5) log("DROPPED_FRAMES $droppedFrames in ${elapsedMs}ms pos=${player.currentPosition}")
            }

            override fun onLoadError(event: AnalyticsListener.EventTime, info: LoadEventInfo, data: MediaLoadData, error: IOException, wasCanceled: Boolean) =
                log("LOAD_ERROR type=${data.trackType} ${error.javaClass.simpleName}: ${error.message?.take(120)} ahead=${player.totalBufferedDuration}ms")

            override fun onLoadCompleted(event: AnalyticsListener.EventTime, info: LoadEventInfo, data: MediaLoadData) {
                // Une requête lente (au-delà de 2 s) : le réseau ou YouTube qui freine
                if (info.loadDurationMs > 2_000) log("SLOW_LOAD type=${data.trackType} ${info.bytesLoaded / 1024}KB in ${info.loadDurationMs}ms ahead=${player.totalBufferedDuration}ms")
            }

            override fun onPositionDiscontinuity(event: AnalyticsListener.EventTime, old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) =
                log("JUMP reason=$reason ${old.positionMs} -> ${new.positionMs}")

            override fun onVideoInputFormatChanged(event: AnalyticsListener.EventTime, format: Format, evaluation: DecoderReuseEvaluation?) =
                log("VIDEO_FORMAT ${format.width}x${format.height} ${format.sampleMimeType} ${format.bitrate / 1000}kbps")

            override fun onAudioInputFormatChanged(event: AnalyticsListener.EventTime, format: Format, evaluation: DecoderReuseEvaluation?) =
                log("AUDIO_FORMAT ${format.sampleMimeType} ${format.sampleRate}Hz ${format.bitrate / 1000}kbps")

            override fun onIsPlayingChanged(event: AnalyticsListener.EventTime, isPlaying: Boolean) =
                log("PLAYING=$isPlaying pos=${player.currentPosition} playWhenReady=${player.playWhenReady} suppression=${player.playbackSuppressionReason}")

            override fun onPlayerError(event: AnalyticsListener.EventTime, error: androidx.media3.common.PlaybackException) =
                log("ERROR ${error.errorCodeName}: ${error.message?.take(120)}")

            override fun onAudioSinkError(event: AnalyticsListener.EventTime, audioSinkError: Exception) =
                log("AUDIO_SINK_ERROR ${audioSinkError.javaClass.simpleName}: ${audioSinkError.message?.take(120)}")
        })
        log("--- lecteur créé")
    }

    /**
     * Appelé toutes les ~0,4 s pendant la lecture (veilleur de [PlayerHolder]). Si la position n'avance
     * pas alors que le lecteur joue (« prêt » et en lecture), c'est un gel : on le note avec sa durée
     * et la sortie du son, une fois qu'il est fini.
     */
    fun checkStall(player: ExoPlayer) {
        val wall = android.os.SystemClock.elapsedRealtime()
        val position = player.currentPosition
        val playing = player.isPlaying && player.playbackState == Player.STATE_READY
        if (playing && lastPosition >= 0 && wall - lastWall in 1..2_000) {
            val advanced = position - lastPosition
            val elapsed = wall - lastWall
            if (advanced < elapsed / 3) {
                if (stalledSince == 0L) stalledSince = lastWall
            } else if (stalledSince != 0L) {
                log("STALL ${wall - stalledSince}ms around pos=$position ahead=${player.totalBufferedDuration}ms output=${output()}")
                stalledSince = 0L
            }
        } else {
            stalledSince = 0L
        }
        lastPosition = if (playing) position else -1L
        lastWall = wall
    }

    @Suppress("DEPRECATION")
    private fun output(): String {
        val a = audio ?: return "?"
        return when {
            a.isBluetoothA2dpOn -> "bluetooth"
            a.isWiredHeadsetOn -> "wired"
            else -> "speaker"
        }
    }

    /** Une ligne datée (aussi appelé ailleurs : nouvelle vidéo, saut SponsorBlock…). */
    fun log(line: String) {
        val stamped = "${time.format(Date())} $line\n"
        val target = file ?: return
        writer.execute {
            runCatching {
                if (target.length() > MAX_BYTES) {
                    // On garde la seconde moitié : les derniers événements comptent
                    val tail = target.readText().takeLast(MAX_BYTES / 2)
                    target.writeText(tail)
                }
                target.appendText(stamped)
            }
        }
    }
}
