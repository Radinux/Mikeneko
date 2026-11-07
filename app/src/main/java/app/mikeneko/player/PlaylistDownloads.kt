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

    private var worker: Job? = null
    @Volatile private var writer: CacheWriter? = null
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
            worker = scope.launch { work(c) }
        }
    }

    private suspend fun work(c: Context) {
        try {
            while (true) {
                val now = System.currentTimeMillis()
                val (id, item) = wanted(c).firstOrNull { (id, _) -> id !in _saved.value && (retryAt[id] ?: 0L) <= now } ?: break
                _current.value = id
                val network = runCatching { download(c, id, item) }
                    .onFailure { retryAt[id] = System.currentTimeMillis() + RETRY_MS }
                    .getOrNull()
                _current.value = null
                if (network != null) {
                    // Retiré entre-temps ? On l'efface, sinon il compte comme hors ligne
                    if (wanted(c).none { it.first == id }) OfflineCache.forget(c, store, id) else _saved.value = _saved.value + id
                }
                delay(if (network == false) 200 else PAUSE_MS)
            }
        } finally {
            _current.value = null
        }
    }

    /** Télécharge une vidéo / un morceau dans la réserve. Renvoie true si le réseau a servi. */
    private suspend fun download(c: Context, id: String, item: VideoItem): Boolean {
        val target = OfflineCache.cache(c, store)
        // Déjà lu en entier : recopié depuis la sauvegarde de lecture
        OfflineCache.entry(c, played, id)?.let { entry ->
            val from = OfflineCache.factory(OfflineCache.cache(c, played), null)
            listOfNotNull(entry.audioKey, entry.videoKey).forEach { write(target, from, "mikeneko-hors-ligne://$it", it) }
            OfflineCache.remember(c, store, entry.copy(store = store))
            return false
        }
        val info = YouTubeRepository.stream(item.url)
        val plan = MediaSources.downloadPlan(info, audioOnly, if (audioOnly) null else VIDEO_HEIGHT)
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(NewPipeDownloader.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        val upstream = ChunkedDataSource.Factory(http)
        write(target, upstream, plan.audioUrl, plan.audioKey)
        if (plan.videoUrl != null && plan.videoKey != null) write(target, upstream, plan.videoUrl, plan.videoKey)
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
        val w = CacheWriter(
            OfflineCache.factory(target, upstream).createDataSource(),
            DataSpec.Builder().setUri(uri).setKey(key).build(),
            null,
            null,
        )
        writer = w
        try {
            w.cache()
        } finally {
            writer = null
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

        val music = PlaylistDownloads(audioOnly = true)
        val videos = PlaylistDownloads(audioOnly = false)

        fun init(context: Context) {
            music.init(context)
            videos.init(context)
        }

        val busy: Boolean get() = music.busy || videos.busy
    }
}
