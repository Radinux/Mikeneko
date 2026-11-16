package app.mikeneko.player

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.mikeneko.R
import app.mikeneko.data.Favorites
import app.mikeneko.data.Playlists
import app.mikeneko.data.VideoItem

/**
 * L'arbre de la bibliothèque de la session média (3.10.0) : ce qu'une montre ou une appli compagnon
 * parcourt. Identifiants : `root` ; `pl:<id>` une playlist (`pl:favoris` les Favoris) ; `pl:<id>/<n>` son
 * morceau n° n (dans l'ordre de la playlist).
 */
object MediaLibraryTree {
    private const val ROOT = "root"
    private const val PLAYLIST = "pl:"

    fun isLibraryId(id: String) = id.startsWith(PLAYLIST)

    fun root(context: Context): MediaItem = MediaItem.Builder()
        .setMediaId(ROOT)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(context.getString(R.string.music_library)).setIsBrowsable(true).setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS).build(),
        )
        .build()

    /** Les playlists : (identifiant, nom, morceaux), Favoris d'abord (sans les directs). */
    fun playlists(context: Context): List<Triple<String, String, List<VideoItem>>> =
        listOf(Triple(Favorites.PLAYLIST_ID, context.getString(R.string.favorites_title), Favorites.get(context).items.value.filter { !it.isLive })) +
            Playlists.get(context).items.value.map { Triple(it.id, it.name, it.items.filter { item -> !item.isLive }) }

    fun children(context: Context, parentId: String): List<MediaItem>? {
        if (parentId == ROOT) return playlists(context).map { (id, name, items) -> playlistItem(id, name, items) }
        val (id, name, items) = playlist(context, parentId) ?: return null
        return items.mapIndexed { n, track -> trackItem(id, name, n, track) }
    }

    fun item(context: Context, mediaId: String): MediaItem? {
        if (mediaId == ROOT) return root(context)
        val (id, name, items) = playlist(context, mediaId) ?: return null
        val n = index(mediaId) ?: return playlistItem(id, name, items)
        return items.getOrNull(n)?.let { trackItem(id, name, n, it) }
    }

    /** Ce qu'un identifiant demande de jouer : les morceaux de la playlist, et par lequel commencer. */
    fun toPlay(context: Context, mediaId: String): Pair<List<VideoItem>, Int>? {
        val (_, _, items) = playlist(context, mediaId) ?: return null
        if (items.isEmpty()) return null
        return items to (index(mediaId) ?: 0).coerceIn(items.indices)
    }

    private fun playlist(context: Context, mediaId: String): Triple<String, String, List<VideoItem>>? {
        if (!isLibraryId(mediaId)) return null
        val id = mediaId.removePrefix(PLAYLIST).substringBefore('/')
        return playlists(context).firstOrNull { it.first == id }
    }

    private fun index(mediaId: String): Int? = mediaId.substringAfter('/', "").toIntOrNull()

    private fun playlistItem(id: String, name: String, items: List<VideoItem>): MediaItem = MediaItem.Builder()
        .setMediaId(PLAYLIST + id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(name)
                .setTotalTrackCount(items.size)
                .setArtworkUri(items.firstOrNull()?.thumbnail?.toUri())
                .setIsBrowsable(true)
                .setIsPlayable(items.isNotEmpty())
                .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                .build(),
        )
        .build()

    private fun trackItem(id: String, playlistName: String, n: Int, track: VideoItem): MediaItem = MediaItem.Builder()
        .setMediaId("$PLAYLIST$id/$n")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.channel)
                .setAlbumTitle(playlistName)
                .setArtworkUri(track.thumbnail?.toUri())
                .setTrackNumber(n + 1)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build(),
        )
        .build()
}
