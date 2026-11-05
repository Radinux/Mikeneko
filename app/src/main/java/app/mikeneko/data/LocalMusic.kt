package app.mikeneko.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * La musique téléchargée avec yt-dlp (dossier Musique/Mikeneko de l'appli), jouable hors ligne
 * depuis la Musique. Chaque fichier devient un [VideoItem] dont l'adresse est `file://…`.
 */
object LocalMusic {
    private val AUDIO = setOf("mp3", "flac", "opus", "m4a", "ogg", "oga", "aac", "wav", "webm")

    private val _tracks = MutableStateFlow<List<VideoItem>?>(null)
    /** null tant que le dossier n'a pas été lu. Les plus récents d'abord. */
    val tracks: StateFlow<List<VideoItem>?> = _tracks.asStateFlow()

    /** Ce qu'on a déjà lu de chaque fichier (chemin + date de modification). */
    private val known = HashMap<String, VideoItem>()

    fun isLocal(url: String?) = url?.startsWith("file:") == true

    fun folder(context: Context) = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Mikeneko")

    fun fileOf(url: String): File? = Uri.parse(url).path?.let(::File)

    /** Relit le dossier (au premier affichage, et après chaque téléchargement terminé). */
    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        val covers = File(context.cacheDir, "pochettes").apply { mkdirs() }
        val files = folder(context).listFiles().orEmpty()
            .filter { it.isFile && it.extension.lowercase() in AUDIO && it.length() > 0 }
            .sortedByDescending { it.lastModified() }
        val list = files.map { file ->
            val key = file.absolutePath + ":" + file.lastModified()
            synchronized(known) { known[key] } ?: read(file, covers).also { synchronized(known) { known[key] = it } }
        }
        _tracks.value = list
    }

    /** Supprime le fichier du téléphone. */
    suspend fun delete(context: Context, url: String) = withContext(Dispatchers.IO) {
        fileOf(url)?.delete()
        refresh(context)
    }

    private fun read(file: File, covers: File): VideoItem {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            val artist = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST))?.takeIf { it.isNotBlank() }
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            // La pochette intégrée par yt-dlp (--embed-thumbnail), copiée en cache pour Coil et la notification
            val cover = retriever.embeddedPicture?.let { bytes ->
                val out = File(covers, "${file.nameWithoutExtension.hashCode()}-${file.lastModified()}.jpg")
                if (!out.exists()) out.writeBytes(bytes)
                Uri.fromFile(out).toString()
            }
            VideoItem(
                url = Uri.fromFile(file).toString(),
                title = title ?: file.nameWithoutExtension,
                channel = artist?.let(::cleanArtist).orEmpty(),
                thumbnail = cover,
                durationSec = duration?.div(1000) ?: -1,
            )
        } catch (_: Exception) {
            VideoItem(Uri.fromFile(file).toString(), file.nameWithoutExtension, "", null)
        } finally {
            runCatching { retriever.release() }
        }
    }
}
