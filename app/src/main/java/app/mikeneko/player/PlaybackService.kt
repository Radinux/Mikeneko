package app.mikeneko.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.common.MediaItem
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import app.mikeneko.data.PlaylistImport
import app.mikeneko.MainActivity
import app.mikeneko.data.Downloads

/**
 * Garde la musique en vie écran éteint (mode radio) et affiche les contrôles
 * dans la notification et sur l'écran de verrouillage.
 */
class PlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // 3.10.0 : une bibliothèque qu'on parcourt (les playlists, leurs morceaux), pour l'appli d'une montre
        // ou une appli compagnon : voir [MusicLibrary] et TECHNICAL-NOTES « The media API »
        session = MediaLibrarySession.Builder(this, SkipPlayer(PlayerHolder.get(this)), LibraryCallback(this))
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

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

    /**
     * 3.10.0 : la playlist du morceau, pour les montres et les applis compagnon (voir TECHNICAL-NOTES,
     * « The media API ») : dans les extras (`app.mikeneko.PLAYLIST`, `app.mikeneko.PLAYLIST_ID`), et dans
     * l'album quand il est vide (c'est ce que les montres affichent sous le titre).
     */
    override fun getMediaMetadata(): MediaMetadata {
        val base = super.getMediaMetadata()
        val (id, name) = PlayerHolder.currentPlaylist() ?: return base
        val extras = android.os.Bundle(base.extras ?: android.os.Bundle()).apply {
            putString(EXTRA_PLAYLIST, name)
            putString(EXTRA_PLAYLIST_ID, id)
        }
        return base.buildUpon()
            .setExtras(extras)
            .apply { if (base.albumTitle.isNullOrBlank()) setAlbumTitle(name) }
            .build()
    }

    /** Le nom de la playlist comme titre de la file (le champ standard d'Android, `queueTitle`). */
    override fun getPlaylistMetadata(): MediaMetadata =
        PlayerHolder.currentPlaylist()?.let { (_, name) -> MediaMetadata.Builder().setTitle(name).build() } ?: super.getPlaylistMetadata()

    // Un morceau ou une playlist choisis depuis la bibliothèque (une montre…) : c'est Mikeneko qui lance
    // la lecture (sa file, ses sources), pas ExoPlayer avec des éléments sans adresse
    private fun library(items: List<MediaItem>): Boolean {
        val id = items.firstOrNull()?.mediaId ?: return false
        if (!MediaLibraryTree.isLibraryId(id)) return false
        PlayerHolder.playFromLibrary(id)
        return true
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>) { if (!library(mediaItems)) super.setMediaItems(mediaItems) }
    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) { if (!library(mediaItems)) super.setMediaItems(mediaItems, resetPosition) }
    override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {
        if (!library(mediaItems.drop(startIndex.coerceAtLeast(0)).ifEmpty { mediaItems })) super.setMediaItems(mediaItems, startIndex, startPositionMs)
    }
    override fun setMediaItem(mediaItem: MediaItem) { if (!library(listOf(mediaItem))) super.setMediaItem(mediaItem) }
    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) { if (!library(listOf(mediaItem))) super.setMediaItem(mediaItem, resetPosition) }
    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) { if (!library(listOf(mediaItem))) super.setMediaItem(mediaItem, startPositionMs) }
    override fun addMediaItems(mediaItems: MutableList<MediaItem>) { if (!library(mediaItems)) super.addMediaItems(mediaItems) }
    override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) { if (!library(mediaItems)) super.addMediaItems(index, mediaItems) }

    override fun hasNextMediaItem() = true
    override fun hasPreviousMediaItem() = true
    override fun seekToNext() = PlayerHolder.skipFromSystem(forward = true)
    override fun seekToNextMediaItem() = PlayerHolder.skipFromSystem(forward = true)
    override fun seekToPrevious() = PlayerHolder.skipFromSystem(forward = false)
    override fun seekToPreviousMediaItem() = PlayerHolder.skipFromSystem(forward = false)
}

/** Les extras de la session média (3.10.0) : le nom et l'identifiant de la playlist du morceau. */
const val EXTRA_PLAYLIST = "app.mikeneko.PLAYLIST"
const val EXTRA_PLAYLIST_ID = "app.mikeneko.PLAYLIST_ID"

/**
 * La bibliothèque de la session (3.10.0) : la racine liste les playlists (Favoris d'abord, puis les
 * playlists perso), chaque playlist ses morceaux. Choisir un morceau lance sa playlist à partir de lui ;
 * une playlist, depuis le début (voir [SkipPlayer] et [PlayerHolder.playFromLibrary]).
 */
@OptIn(UnstableApi::class)
private class LibraryCallback(private val context: android.content.Context) : MediaLibrarySession.Callback {
    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(LibraryResult.ofItem(MediaLibraryTree.root(context), params))

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val all = MediaLibraryTree.children(context, parentId)
            ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
        val from = (page * pageSize).coerceIn(0, all.size)
        val to = (from + pageSize).coerceIn(from, all.size)
        return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params))
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val item = MediaLibraryTree.item(context, mediaId)
            ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
        return Futures.immediateFuture(LibraryResult.ofItem(item, null))
    }

    // Les éléments choisis n'ont pas d'adresse : on les garde tels quels, SkipPlayer s'en occupe
    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(mediaItems)
}
