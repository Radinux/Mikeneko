package app.mikeneko.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.SimpleCache
import app.mikeneko.data.NewPipeDownloader
import app.mikeneko.data.Playlists
import app.mikeneko.data.VideoItem
import app.mikeneko.data.YouTubeRepository
import app.mikeneko.data.videoIdOf
import app.mikeneko.player.OfflineCache.Store
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Les playlists perso téléchargées (bouton ⤓ de la playlist) : leurs morceaux ou
 * vidéos sont gardés dans l'appli ([OfflineCache], réserves [Store.SavedMusic] / [Store.SavedVideos],
 * jamais effacées d'elles-mêmes) et se lisent sans réseau, sans fichier visible dans le téléphone.
 *
 * Un seul téléchargement à la fois, avec une pause entre deux, pour ménager YouTube. Ce qui a déjà
 * été lu en entier est recopié depuis la sauvegarde de lecture, sans réseau. Un morceau ajouté à une
 * playlist téléchargée se télécharge ; retiré (ou la playlist plus téléchargée), il est effacé.
 */
@OptIn(UnstableApi::class)
class PlaylistDownloads private constructor(private val audioOnly: Boolean) {
    private val store = if (audioOnly) Store.SavedMusic else Store.SavedVideos
    /** La sauvegarde de lecture du même genre, d'où l'on peut recopier. */
    private val played = if (audioOnly) Store.Music else Store.Videos
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var app: Context? = null

    private val _playlists = MutableStateFlow<Set<String>>(emptySet())
    /** Les playlists à garder hors ligne. */
    val playlists: StateFlow<Set<String>> = _playlists.asStateFlow()

    private val _saved = MutableStateFlow<Set<String>>(emptySet())
    /** Les vidéos (id) entières hors ligne. */
    val saved: StateFlow<Set<String>> = _saved.asStateFlow()

    private val _current = MutableStateFlow<String?>(null)
    /** La vidéo (id) en cours de téléchargement. */
    val current: StateFlow<String?> = _current.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    /** Où en est [current], de 0 à 1 (mis à jour par pas de 2 %, pas à chaque octet). */
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private var worker: Job? = null
    @Volatile private var writer: CacheWriter? = null
    @Volatile private var source: DataSource? = null
    /** Dernier octet reçu : sans rien pendant [STALL_MS], le téléchargement est coincé. */
    @Volatile private var lastProgressAt = 0L
    /** Change quand un téléchargement coincé est abandonné : l'ancien travail, s'il se réveille, s'arrête. */
    @Volatile private var generation = 0
    /** Le morceau de [write] en cours : sa part de la progression (vidéo : l'audio puis l'image). */
    @Volatile private var phase = 0f to 1f
    private val retryAt = ConcurrentHashMap<String, Long>()

    /** Un téléchargement est en cours (l'appli ne se vide pas en arrière-plan). */
    val busy: Boolean get() = worker?.isActive == true

    private fun playlistStore(context: Context) = if (audioOnly) Playlists.get(context) else Playlists.videos(context)

    private fun prefs(context: Context) = context.getSharedPreferences(
        if (audioOnly) "playlists_telechargees_musique" else "playlists_telechargees_videos",
        Context.MODE_PRIVATE,
    )

    private fun init(context: Context) {
        val c = context.applicationContext
        app = c
        _playlists.value = prefs(c).getStringSet("ids", emptySet()).orEmpty().toSet()
        scope.launch {
            _saved.value = OfflineCache.ids(c, store)
            // Une playlist modifiée (morceau ajouté, retiré, playlist supprimée) : on se met à jour
            playlistStore(c).items.collect { sync() }
        }
    }

    fun setDownloaded(id: String, on: Boolean) {
        val c = app ?: return
        _playlists.value = if (on) _playlists.value + id else _playlists.value - id
        prefs(c).edit().putStringSet("ids", _playlists.value).apply()
        scope.launch { sync() }
    }

    /** Ce qui doit être hors ligne : les éléments des playlists téléchargées (sans les directs). */
    private fun wanted(c: Context): List<Pair<String, VideoItem>> =
        playlistStore(c).items.value
            .filter { it.id in _playlists.value }
            .flatMap { it.items }
            .filter { !it.isLive }
            .mapNotNull { item -> videoIdOf(item.url)?.let { it to item } }
            .distinctBy { it.first }

    @Synchronized
    private fun sync() {
        val c = app ?: return
        // Une playlist supprimée n'est plus à télécharger
        val existing = playlistStore(c).items.value.map { it.id }.toSet()
        if (!existing.containsAll(_playlists.value)) {
            _playlists.value = _playlists.value intersect existing
            prefs(c).edit().putStringSet("ids", _playlists.value).apply()
        }
        val wantedIds = wanted(c).map { it.first }.toSet()
        // Plus téléchargé : effacé de la réserve. La sauvegarde de lecture le garde, mais elle ne se
        // lit pas sans réseau (OfflineCache.playable) : il n'est donc plus lisible hors ligne.
        (OfflineCache.ids(c, store) - wantedIds).forEach { OfflineCache.forget(c, store, it) }
        _current.value?.let { if (it !in wantedIds) writer?.cancel() }
        _saved.value = OfflineCache.ids(c, store)
        if (worker?.isActive != true && (wantedIds - _saved.value).isNotEmpty()) {
            val gen = generation
            worker = scope.launch { work(c, gen) }
        }
    }

    private suspend fun work(c: Context, gen: Int) {
        val watchdog = scope.launch { watch(c, gen) }
        try {
            while (gen == generation) {
                val now = System.currentTimeMillis()
                val (id, item) = wanted(c).firstOrNull { (id, _) -> id !in _saved.value && (retryAt[id] ?: 0L) <= now } ?: break
                lastProgressAt = System.currentTimeMillis()
                _progress.value = 0f
                _current.value = id
                val network = runCatching { download(c, id, item) }
                    .onFailure { retryAt[id] = System.currentTimeMillis() + RETRY_MS }
                    .getOrNull()
                if (gen != generation) break // abandonné pendant qu'il était coincé : un autre a pris la suite
                _current.value = null
                if (network != null) {
                    // Retiré entre-temps ? On l'efface, sinon il compte comme hors ligne
                    if (wanted(c).none { it.first == id }) OfflineCache.forget(c, store, id) else _saved.value = _saved.value + id
                }
                delay(if (network == false) 200 else PAUSE_MS)
            }
        } finally {
            watchdog.cancel()
            if (gen == generation) _current.value = null
        }
    }

    /**
     * Surveille le téléchargement : l'appli quittée au milieu (le système la fige), la connexion peut
     * rester pendue sans jamais finir ni échouer, et la marque « en cours » tournait alors pour
     * toujours. Sans un octet pendant [STALL_MS] : on coupe la connexion ; toujours rien
     * [ABANDON_MS] plus tard, on laisse tomber ce travail et on repart (le morceau est réessayé plus tard).
     */
    private suspend fun watch(c: Context, gen: Int) {
        var cutAt = 0L
        while (gen == generation) {
            delay(5_000)
            val id = _current.value ?: continue
            val now = System.currentTimeMillis()
            if (now - lastProgressAt < STALL_MS) {
                cutAt = 0L
                continue
            }
            if (cutAt == 0L) {
                cutAt = now
                writer?.cancel()
                // Fermer depuis un autre fil débloque une lecture pendue ; dans un fil à part, au cas
                // où la fermeture elle-même attendrait
                source?.let { s -> Thread { runCatching { s.close() } }.start() }
            } else if (now - cutAt >= ABANDON_MS) {
                retryAt[id] = now + RETRY_MS
                generation = gen + 1
                _current.value = null
                writer = null
                source = null
                worker = null // l'ancien reste bloqué : sync en lance un nouveau
                scope.launch { sync() }
                return
            }
        }
    }

    /** L'appli revient au premier plan : un téléchargement figé pendant qu'elle était quittée repart. */
    private fun resume() {
        if (_current.value != null) lastProgressAt = minOf(lastProgressAt, System.currentTimeMillis() - STALL_MS + 10_000)
        scope.launch { sync() }
    }

    /** Télécharge une vidéo / un morceau dans la réserve. Renvoie true si le réseau a servi. */
    private suspend fun download(c: Context, id: String, item: VideoItem): Boolean {
        val target = OfflineCache.cache(c, store)
        // Déjà lu en entier : recopié depuis la sauvegarde de lecture
        OfflineCache.entry(c, played, id)?.let { entry ->
            val from = OfflineCache.factory(OfflineCache.cache(c, played), null)
            val keys = listOfNotNull(entry.audioKey, entry.videoKey)
            keys.forEachIndexed { i, key ->
                phase = i.toFloat() / keys.size to 1f / keys.size
                write(target, from, "mikeneko-hors-ligne://$key", key)
            }
            OfflineCache.remember(c, store, entry.copy(store = store))
            return false
        }
        val info = YouTubeRepository.stream(item.url)
        val plan = MediaSources.downloadPlan(info, audioOnly, if (audioOnly) null else VIDEO_HEIGHT)
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(NewPipeDownloader.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
        val upstream = ChunkedDataSource.Factory(http)
        val withVideo = plan.videoUrl != null && plan.videoKey != null
        phase = 0f to (if (withVideo) 0.2f else 1f)
        write(target, upstream, plan.audioUrl, plan.audioKey)
        if (withVideo) {
            phase = 0.2f to 0.8f
            write(target, upstream, plan.videoUrl!!, plan.videoKey!!)
        }
        OfflineCache.remember(
            c,
            store,
            OfflineCache.Entry(
                videoId = info.id,
                url = info.url,
                title = info.name,
                channel = info.uploaderName.orEmpty(),
                thumbnail = info.thumbnails.maxByOrNull { it.height }?.url,
                audioKey = plan.audioKey,
                videoKey = plan.videoKey,
                height = plan.height,
                store = store,
            ),
        )
        return true
    }

    private fun write(target: SimpleCache, upstream: DataSource.Factory, uri: String, key: String) {
        if (OfflineCache.complete(target, key)) return
        val (start, share) = phase
        var shown = -1
        val ds = OfflineCache.factory(target, upstream).createDataSource()
        val w = CacheWriter(ds, DataSpec.Builder().setUri(uri).setKey(key).build(), null) { length, cached, _ ->
            lastProgressAt = System.currentTimeMillis()
            if (length > 0) {
                val step = (cached * 50 / length).toInt() // pas de 2 %
                if (step != shown) {
                    shown = step
                    _progress.value = start + share * step / 50f
                }
            }
        }
        writer = w
        source = ds
        try {
            w.cache()
        } finally {
            writer = null
            source = null
        }
        check(OfflineCache.complete(target, key)) { "incomplet : $key" }
    }

    companion object {
        /** Entre deux téléchargements : YouTube n'aime pas les rafales. */
        private const val PAUSE_MS = 2_500L
        /** Un échec (pas de réseau…) : on réessaie plus tard. */
        private const val RETRY_MS = 10 * 60_000L
        /** Les vidéos téléchargées : 720p au plus (la place compte, sur un téléphone). */
        private const val VIDEO_HEIGHT = 720
        /** Plus un octet depuis 45 s : la connexion est pendue. */
        private const val STALL_MS = 45_000L
        /** Toujours rien 20 s après l'avoir coupée : on abandonne ce travail. */
        private const val ABANDON_MS = 20_000L

        val music = PlaylistDownloads(audioOnly = true)
        val videos = PlaylistDownloads(audioOnly = false)

        fun init(context: Context) {
            music.init(context)
            videos.init(context)
        }

        val busy: Boolean get() = music.busy || videos.busy

        /** Au retour au premier plan (voir [resume]). */
        fun resume() {
            music.resume()
            videos.resume()
        }
    }
}
