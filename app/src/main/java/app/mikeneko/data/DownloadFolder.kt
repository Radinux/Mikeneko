package app.mikeneko.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * Où vont les téléchargements (3.8.0) : dans `Download/Mikeneko/Music` et `Download/Mikeneko/Videos`,
 * faciles à retrouver avec n'importe quel gestionnaire de fichiers, et gardés si l'appli est désinstallée.
 * Avant, ils restaient cachés dans le dossier privé de l'appli (`Android/data/app.mikeneko/files/…`).
 *
 * yt-dlp écrit toujours dans le dossier privé ([staging]) : le fichier n'arrive dans Download qu'une fois
 * fini ([publish]), sans fichiers `.part` à moitié écrits. Android 11+ : l'appli écrit et relit ses
 * propres fichiers dans Download par leur chemin, sans permission. Android 8–10 : il faut la permission
 * de stockage ; sans elle, les fichiers restent dans le dossier privé, comme avant.
 */
object DownloadFolder {
    private const val TAG = "Mikeneko"

    private fun root() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Mikeneko")

    fun publicMusic() = File(root(), "Music")
    fun publicVideos() = File(root(), "Videos")

    /** Les anciens dossiers (privés), encore lus et vidés vers Download. */
    fun legacyMusic(context: Context) = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Mikeneko")
    fun legacyVideos(context: Context) = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Mikeneko")

    /** Où yt-dlp travaille (privé) avant que le fichier fini parte dans Download. */
    fun staging(context: Context) = File(context.getExternalFilesDir(null), "telechargements").apply { mkdirs() }

    /** Peut-on écrire dans Download ? */
    fun publicUsable(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ||
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** Les dossiers où chercher la musique téléchargée (le nouveau d'abord). */
    fun musicFolders(context: Context): List<File> = listOf(publicMusic(), legacyMusic(context))

    /**
     * Range un fichier fini dans Download/Mikeneko (Music ou Videos) et le signale au système (il apparaît
     * dans les applis de musique, la galerie, les fichiers). Renvoie le fichier à sa nouvelle place, ou à
     * l'ancienne si on n'a pas pu le déplacer.
     */
    fun publish(context: Context, file: File, audio: Boolean): File {
        if (!file.exists()) return file
        val legacy = if (audio) legacyMusic(context) else legacyVideos(context)
        if (publicUsable(context)) {
            moveTo(file, if (audio) publicMusic() else publicVideos())?.let { target ->
                MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
                return target
            }
        }
        // Pas de Download possible : l'ancien dossier privé (toujours lu par l'appli)
        if (file.parentFile == legacy) return file
        return moveTo(file, legacy) ?: file
    }

    /** Signale au système qu'un fichier a été supprimé (il disparaît des applis de musique, de la galerie). */
    fun forget(context: Context, file: File) {
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
    }

    private fun moveTo(file: File, folder: File): File? = runCatching {
        folder.mkdirs()
        val target = freeName(folder, file.name)
        if (!file.renameTo(target)) {
            file.copyTo(target)
            file.delete()
        }
        target
    }.onFailure { Log.e(TAG, "Téléchargement : impossible de ranger ${file.name} dans $folder", it) }.getOrNull()

    /** Une seule fois : les téléchargements d'avant 3.8.0 quittent le dossier privé pour Download. */
    fun migrate(context: Context) {
        val prefs = context.getSharedPreferences("telechargements", Context.MODE_PRIVATE)
        if (prefs.getBoolean("ranges_dans_download", false) || !publicUsable(context)) return
        var failed = false
        for ((old, audio) in listOf(legacyMusic(context) to true, legacyVideos(context) to false)) {
            old.listFiles().orEmpty().filter { it.isFile && it.length() > 0 }.forEach { file ->
                if (publish(context, file, audio).parentFile == old) failed = true // pas pu : on réessaiera
            }
        }
        if (!failed) prefs.edit().putBoolean("ranges_dans_download", true).apply()
    }

    /** « titre.mp3 », ou « titre (2).mp3 » s'il existe déjà. */
    private fun freeName(folder: File, name: String): File {
        var candidate = File(folder, name)
        val base = candidate.nameWithoutExtension
        val ext = candidate.extension.let { if (it.isEmpty()) "" else ".$it" }
        var n = 2
        while (candidate.exists()) candidate = File(folder, "$base ($n)$ext").also { n++ }
        return candidate
    }
}
