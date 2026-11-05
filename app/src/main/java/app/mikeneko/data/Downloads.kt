package app.mikeneko.data

import android.content.Context
import android.os.Environment
import app.mikeneko.R
import app.mikeneko.player.MediaSources
import app.mikeneko.player.PlayerHolder
import app.mikeneko.settings.AppLocale
import app.mikeneko.settings.AppSettings
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Gestionnaire de téléchargements basé sur yt-dlp (youtubedl-android). */
object Downloads {
    enum class Format(val label: String, val audio: Boolean) {
        VIDEO("Vidéo", false), MP3("MP3", true), FLAC("FLAC", true), OPUS("Opus", true), M4A("M4A", true)
    }

    enum class Status { EN_ATTENTE, EN_COURS, TERMINE, ERREUR }

    data class Task(
        val id: String,
        val url: String,
        val format: Format,
        val progress: Float = 0f,
        val eta: Long = -1,
        val status: Status = Status.EN_ATTENTE,
        val file: File? = null,
        val error: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    /** Terminé quand yt-dlp + ffmpeg sont installés ; en erreur si l'installation a échoué. */
    private val ready = CompletableDeferred<Unit>()

    private val started = AtomicBoolean(false)

    /**
     * Installe yt-dlp + ffmpeg (~125 Mo une fois décompressés) la première fois qu'on en a besoin :
     * en ouvrant l'onglet Téléchargés ou en lançant un téléchargement, pas au démarrage de l'appli.
     * yt-dlp cherche ensuite sa mise à jour au plus une fois par jour.
     */
    fun prepare(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        scope.launch {
            runCatching {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
            }.onSuccess {
                ready.complete(Unit)
                val prefs = app.getSharedPreferences("telechargements", Context.MODE_PRIVATE)
                val now = System.currentTimeMillis()
                if (now - prefs.getLong("maj_ytdlp", 0L) > UPDATE_EVERY_MS) {
                    // Une mise à jour ratée (pas de réseau…) n'empêche pas de télécharger
                    runCatching { YoutubeDL.getInstance().updateYoutubeDL(app) }
                        .onSuccess { prefs.edit().putLong("maj_ytdlp", now).apply() }
                }
            }.onFailure { e ->
                ready.completeExceptionally(IllegalStateException(AppLocale.string(R.string.ytdlp_install_failed, e.message.orEmpty()), e))
            }
        }
    }

    private const val UPDATE_EVERY_MS = 24 * 60 * 60_000L

    fun enqueue(context: Context, url: String, format: Format, cutSponsors: Boolean) {
        val task = Task(UUID.randomUUID().toString(), url, format)
        _tasks.update { listOf(task) + it }
        scope.launch {
            val folder = File(
                context.getExternalFilesDir(if (format.audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES),
                "Mikeneko",
            ).apply { mkdirs() }
            // yt-dlp écrit ici le chemin final du fichier (après conversion et déplacement)
            val report = File(context.cacheDir, "telechargement-${task.id}.txt").apply { delete() }
            val request = YoutubeDLRequest(url).apply {
                addOption("-o", "${folder.absolutePath}/%(title)s.%(ext)s")
                addCommands(listOf("--print-to-file", "after_move:filepath", report.absolutePath))
                addOption("--embed-metadata")
                addOption("--embed-thumbnail")
                addOption("--embed-chapters")
                addOption("--no-playlist")
                when (format) {
                    Format.VIDEO -> {
                        // Audio d'origine (pas un doublage IA) : yt-dlp le note « original » quand il y a plusieurs pistes
                        // Même plafond que la qualité choisie dans les réglages (Auto = résolution de l'écran)
                        val max = PlayerHolder.quality.value ?: MediaSources.autoMaxHeight
                        addOption("-f", "bv*[height<=$max]+ba[format_note*=original]/bv*[height<=$max]+ba/b")
                        // À résolution égale : pas de HDR, puis AV1 > VP9 > H.264 selon ce que le téléphone
                        // décode en matériel (fichier plus léger, aussi lisible par la galerie)
                        addOption("-S", "res:$max,hdr:sdr,vcodec:${MediaSources.preferredDownloadCodec(max)}")
                        addOption("--merge-output-format", "mp4")
                    }
                    else -> {
                        addOption("-f", "ba[format_note*=original]/ba/b")
                        addOption("-x")
                        addOption("--audio-format", format.name.lowercase())
                        addOption("--audio-quality", "0")
                    }
                }
                val skip = AppSettings.skipCategories.value
                if (cutSponsors && skip.isNotEmpty()) addOption("--sponsorblock-remove", skip.joinToString(","))
            }
            prepare(context)
            runCatching {
                ready.await()
                change(task.id) { it.copy(status = Status.EN_COURS) }
                YoutubeDL.getInstance().execute(request, processId = task.id, callback = { progress, eta, _ ->
                    change(task.id) { it.copy(progress = progress / 100f, eta = eta) }
                })
            }.onSuccess {
                // Une ligne par fichier écrit (une seule pour une vidéo, plusieurs pour une playlist)
                val file = runCatching { report.readLines() }.getOrDefault(emptyList())
                    .lastOrNull { it.isNotBlank() }
                    ?.let(::File)
                    ?.takeIf { it.exists() }
                change(task.id) { it.copy(status = Status.TERMINE, progress = 1f, file = file) }
                // Un morceau audio : il apparaît tout de suite dans « Téléchargés » de la Musique
                if (format.audio) runCatching { LocalMusic.refresh(context) }
            }.onFailure { e ->
                change(task.id) { it.copy(status = Status.ERREUR, error = e.message) }
            }
            report.delete()
        }
    }

    fun cancel(id: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(id) }
        _tasks.update { list -> list.filterNot { it.id == id } }
    }

    private fun change(id: String, block: (Task) -> Task) {
        _tasks.update { list -> list.map { if (it.id == id) block(it) else it } }
    }
}
