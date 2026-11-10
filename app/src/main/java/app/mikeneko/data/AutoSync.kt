package app.mikeneko.data

import android.content.Context
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * La synchro en continu (2.19.2) : dès qu'on change quelque chose (un abonnement, une playlist, une
 * vidéo regardée, un sujet…), les appareils liés présents sur le réseau et le coffre GitHub reçoivent
 * la nouveauté peu après, sans toucher « Synchroniser ».
 *
 * - Un changement lance un compte à rebours ([LOCAL_DELAY], [GITHUB_DELAY]) ; ceux qui arrivent
 *   pendant ce temps partent avec (pendant une vidéo, la part regardée change toutes les 10 s : une
 *   synchro par minute au plus, pas une toutes les 10 s).
 * - Pas de ping-pong : après chaque synchro, on retient l'empreinte de ce qu'on a ([markLocal],
 *   [markGitHub]). Ce que la synchro vient d'ajouter change les listes, mais l'empreinte est la même :
 *   rien ne repart.
 */
object AutoSync {
    private const val LOCAL_DELAY = 30_000L
    private const val GITHUB_DELAY = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var localJob: Job? = null
    private var githubJob: Job? = null
    @Volatile private var localPrint: String? = null
    @Volatile private var githubPrint: String? = null
    private var started = false

    /** Au démarrage de l'appli : on écoute tout ce que la synchro emporte. */
    fun start(context: Context) {
        val app = context.applicationContext
        synchronized(this) {
            if (started) return
            started = true
        }
        val watched: List<StateFlow<*>> = listOf(
            SubscriptionStore.get(app).items,
            Playlists.get(app).items,
            Playlists.videos(app).items,
            MusicLibrary.get(app).albums,
            MusicLibrary.get(app).artists,
            Favorites.get(app).items,
            VideoLibrary.get(app).later,
            VideoLibrary.get(app).liked,
            VideoLibrary.get(app).shorts,
            Tastes.get(app).chosen,
            WatchHistory.get(app).entries,
            WatchPositions.progress(app),
        )
        scope.launch {
            merge(*watched.map { it.drop(1) }.toTypedArray()).collect { changed(app) }
        }
    }

    private fun changed(context: Context) {
        synchronized(this) {
            if (localJob?.isActive != true && LocalSync.links.value.isNotEmpty()) {
                localJob = scope.launch {
                    delay(LOCAL_DELAY)
                    if (fingerprint(context) != localPrint) LocalSync.syncPresent(context)
                }
            }
            if (githubJob?.isActive != true && GitHubSync.login.value is GitHubSync.Login.In) {
                githubJob = scope.launch {
                    delay(GITHUB_DELAY)
                    if (fingerprint(context) != githubPrint) GitHubSync.syncNow(context)
                }
            }
        }
    }

    /** Une synchro locale vient de finir : ce qu'on a maintenant est à jour chez l'autre. */
    fun markLocal(context: Context) {
        localPrint = fingerprint(context)
    }

    /** Le coffre GitHub contient maintenant ce qui avait l'empreinte [print]. */
    fun markGitHub(print: String) {
        githubPrint = print
    }

    /** On quitte l'appli : ce qui n'est pas encore parti dans le coffre GitHub part tout de suite. */
    fun leaving(context: Context) {
        val app = context.applicationContext
        if (GitHubSync.login.value !is GitHubSync.Login.In) return
        scope.launch {
            if (fingerprint(app) != githubPrint) {
                synchronized(this@AutoSync) { githubJob?.cancel() }
                GitHubSync.syncNow(app)
            }
        }
    }

    /** L'empreinte de tout ce que la synchro emporte (la sauvegarde complète). */
    fun fingerprint(context: Context): String = digest(
        ByteArrayOutputStream().also { MikenekoBackup.export(context, it, "") }.toByteArray(),
    )

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
