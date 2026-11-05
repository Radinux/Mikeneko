package app.mikeneko.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl

/**
 * Comme d'habitude (~50 s d'avance), sauf pour un morceau de musique : il est chargé en entier tout
 * de suite (quelques Mo). Il passe ainsi tout entier dans la sauvegarde hors ligne ([OfflineCache]),
 * même si on le quitte avant la fin, et une coupure de réseau ne l'interrompt plus.
 * Pas pour les longues vidéos audio (au-delà de [MAX_WHOLE_US]) ni les vidéos.
 */
@OptIn(UnstableApi::class)
class MusicLoadControl : DefaultLoadControl() {
    private val period = Timeline.Period()

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        if (super.shouldContinueLoading(parameters)) return true
        val now = PlayerHolder.nowPlaying.value
        if (now == null || !now.audioOnly || now.live || parameters.timeline.isEmpty) return false
        val duration = runCatching { parameters.timeline.getPeriodByUid(parameters.mediaPeriodId.periodUid, period).durationUs }
            .getOrDefault(C.TIME_UNSET)
        if (duration == C.TIME_UNSET || duration > MAX_WHOLE_US) return false
        val remaining = duration - parameters.playbackPositionUs
        val bytes = getAllocator(parameters.playerId).totalBytesAllocated
        return parameters.bufferedDurationUs < remaining + 1_000_000 && bytes < MAX_BYTES
    }

    private companion object {
        const val MAX_WHOLE_US = 20L * 60 * 1_000_000 // 20 min
        const val MAX_BYTES = 40 * 1024 * 1024
    }
}
