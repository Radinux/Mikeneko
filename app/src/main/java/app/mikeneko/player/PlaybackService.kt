package app.mikeneko.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.mikeneko.data.PlaylistImport
import app.mikeneko.MainActivity
import app.mikeneko.data.Downloads

/**
 * Garde la musique en vie écran éteint (mode radio) et affiche les contrôles
 * dans la notification et sur l'écran de verrouillage.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, SkipPlayer(PlayerHolder.get(this)))
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * L'appli retirée des applis récentes : la musique s'arrête aussi, et l'appli quitte vraiment
     * (sa mémoire est libérée, la prochaine ouverture repart de zéro). Sauf si un téléchargement
     * tourne encore : lui doit pouvoir finir.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        PlayerHolder.reset()
        session?.release()
        session = null
        stopSelf()
        val downloading = Downloads.tasks.value.any { it.status == Downloads.Status.EN_COURS || it.status == Downloads.Status.EN_ATTENTE } ||
            PlaylistDownloads.busy || PlaylistImport.busy
        if (!downloading) {
            // Le temps que la notification disparaisse, puis on quitte
            Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 300)
        }
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}

/**
 * ExoPlayer ne joue qu'un élément à la fois (la file est dans [PlayerHolder]) : on annonce quand
 * même « suivant / précédent » au système, et on les renvoie vers la file de la radio (ou les
 * suggestions pour une vidéo). Les boutons apparaissent ainsi dans la notification, sur l'écran
 * de verrouillage et marchent avec les écouteurs.
 */
@OptIn(UnstableApi::class)
private class SkipPlayer(player: Player) : ForwardingPlayer(player) {
    private val skips = intArrayOf(
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
    )

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon().addAll(*skips).build()

    override fun isCommandAvailable(command: Int): Boolean = command in skips || super.isCommandAvailable(command)

    override fun hasNextMediaItem() = true
    override fun hasPreviousMediaItem() = true
    override fun seekToNext() = PlayerHolder.skipFromSystem(forward = true)
    override fun seekToNextMediaItem() = PlayerHolder.skipFromSystem(forward = true)
    override fun seekToPrevious() = PlayerHolder.skipFromSystem(forward = false)
    override fun seekToPreviousMediaItem() = PlayerHolder.skipFromSystem(forward = false)
}
