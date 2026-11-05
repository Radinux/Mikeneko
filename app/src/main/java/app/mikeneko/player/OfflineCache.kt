package app.mikeneko.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import app.mikeneko.data.videoIdOf
import org.json.JSONObject
import java.io.File

/**
 * La sauvegarde hors ligne cachée : tout ce que le lecteur lit de YouTube (musique et vidéos) passe
 * par un cache sur le téléphone, privé à l'appli (rien dans les fichiers ni la galerie).
 *
 * Quatre réserves ([Store]) :
 * - [Store.Music] et [Store.Videos] : ce qui a été lu, dans le dossier cache d'Android (compté en
 *   « cache », Android peut le vider s'il manque de place), 1 Go chacune, les moins récemment lus
 *   partent d'abord. Séparées pour que les vidéos ne chassent pas toute la musique.
 * - [Store.SavedMusic] et [Store.SavedVideos] : les playlists perso téléchargées (bouton ⤓, comme sur
 *   Spotify, voir [PlaylistDownloads]), dans les données de l'appli, sans limite ni effacement
 *   automatique : elles restent tant que la playlist est téléchargée.
 *
 * Les adresses des flux YouTube changent à chaque fois : la clé d'un flux est « id:itag » (la vidéo
 * et le format), stable. Un index par réserve garde, par vidéo, les clés et de quoi afficher le
 * morceau hors ligne.
 */
@OptIn(UnstableApi::class)
object OfflineCache {
    enum class Store(val dir: String, val prefs: String, val maxBytes: Long, val persistent: Boolean, val audioOnly: Boolean) {
        Music("hors_ligne/musique", "hors_ligne_musique", 1L shl 30, false, true),
        Videos("hors_ligne/videos", "hors_ligne_videos", 1L shl 30, false, false),
        SavedMusic("hors_ligne/playlists_musique", "hors_ligne_playlists_musique", 0, true, true),
        SavedVideos("hors_ligne/playlists_videos", "hors_ligne_playlists_videos", 0, true, false),
    }

    /** Ce qu'il faut pour relire une vidéo / un morceau sans réseau. */
    data class Entry(
        val videoId: String,
        val url: String,
        val title: String,
        val channel: String,
        val thumbnail: String?,
        /** La piste principale : le son, ou le flux vidéo+son d'une vidéo « tout-en-un ». */
        val audioKey: String,
        /** La vidéo seule, quand le son est à part. */
        val videoKey: String?,
        val height: Int,
        val store: Store = Store.Music,
    )

    private var database: StandaloneDatabaseProvider? = null
    private val caches = HashMap<Store, SimpleCache>()

    @Synchronized
    private fun database(context: Context) = database ?: StandaloneDatabaseProvider(context.applicationContext).also { database = it }

    @Synchronized
    fun cache(context: Context, store: Store): SimpleCache = caches.getOrPut(store) {
        val app = context.applicationContext
        val evictor: CacheEvictor = if (store.persistent) NoOpCacheEvictor() else LeastRecentlyUsedCacheEvictor(store.maxBytes)
        SimpleCache(File(if (store.persistent) app.filesDir else app.cacheDir, store.dir), evictor, database(context))
    }

    /** La réserve de ce qui est lu (musique ou vidéos). */
    fun cache(context: Context, audioOnly: Boolean) = cache(context, if (audioOnly) Store.Music else Store.Videos)

    fun musicCache(context: Context) = cache(context, Store.Music)

    /** Ouvre les réserves en fond (lire leur index au premier morceau bloquerait l'écran). */
    fun warmUp(context: Context) = Store.entries.forEach { runCatching { cache(context, it) } }

    /** Lit par le cache, et va chercher le reste chez YouTube (qui le met en cache au passage). */
    fun factory(cache: SimpleCache, upstream: DataSource.Factory?): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    fun key(videoId: String, kind: Char, itag: Int) = "$videoId:$kind$itag"

    /** Le flux est entier dans le cache (sa taille est connue dès la première requête). */
    fun complete(cache: SimpleCache, key: String): Boolean = runCatching {
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        length > 0 && cache.isCached(key, 0, length)
    }.getOrDefault(false)

    // ---- Les index : par vidéo, ses clés et de quoi l'afficher ----

    private fun prefs(context: Context, store: Store) =
        context.applicationContext.getSharedPreferences(store.prefs, Context.MODE_PRIVATE)

    /** Retient ce qui vient d'être lancé (il ne sera lisible hors ligne qu'une fois entier). */
    fun remember(context: Context, audioOnly: Boolean, entry: Entry) =
        remember(context, if (audioOnly) Store.Music else Store.Videos, entry)

    fun remember(context: Context, store: Store, entry: Entry) {
        val json = JSONObject()
            .put("url", entry.url).put("title", entry.title).put("channel", entry.channel)
            .put("thumbnail", entry.thumbnail ?: "").put("audio", entry.audioKey)
            .put("video", entry.videoKey ?: "").put("height", entry.height)
            .put("at", System.currentTimeMillis())
        if (!store.persistent) prune(context, store)
        prefs(context, store).edit().putString(entry.videoId, json.toString()).apply()
    }

    private fun read(context: Context, store: Store, id: String): Entry? {
        val raw = prefs(context, store).getString(id, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            Entry(
                videoId = id,
                url = o.getString("url"),
                title = o.optString("title"),
                channel = o.optString("channel"),
                thumbnail = o.optString("thumbnail").ifEmpty { null },
                audioKey = o.getString("audio"),
                videoKey = o.optString("video").ifEmpty { null },
                height = o.optInt("height"),
                store = store,
            )
        }.getOrNull()
    }

    /** L'entrée de [videoId] dans [store], si elle y est entière. */
    fun entry(context: Context, store: Store, videoId: String): Entry? {
        val entry = read(context, store, videoId) ?: return null
        val cache = cache(context, store)
        val whole = complete(cache, entry.audioKey) && (store.audioOnly || entry.videoKey == null || complete(cache, entry.videoKey))
        return entry.takeIf { whole }
    }

    /**
     * Le morceau / la vidéo de [url], s'il est entier quelque part (playlist téléchargée d'abord).
     * Sans réseau, seulement s'il est téléchargé : la sauvegarde de lecture sert à démarrer plus vite
     * et à moins solliciter YouTube, pas à écouter hors ligne (choix de l'utilisateur, 1.15.5 : ce qui
     * n'est plus téléchargé ne doit plus se lire hors ligne).
     */
    fun playable(context: Context, url: String, audioOnly: Boolean): Entry? {
        val id = videoIdOf(url) ?: return null
        val saved = entry(context, if (audioOnly) Store.SavedMusic else Store.SavedVideos, id)
        if (saved != null || !online(context)) return saved
        return entry(context, if (audioOnly) Store.Music else Store.Videos, id)
    }

    /** Une connexion à Internet est là (dans le doute, on dit oui). */
    private fun online(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = manager.activeNetwork ?: return false
        manager.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(true)

    /** Les vidéos retenues dans [store] (pour les réserves des playlists : celles qui sont entières). */
    fun ids(context: Context, store: Store): Set<String> = prefs(context, store).all.keys.toSet()

    /** Retire une vidéo d'une réserve (ses flux et son entrée). */
    fun forget(context: Context, store: Store, videoId: String) {
        val entry = read(context, store, videoId)
        val cache = cache(context, store)
        listOfNotNull(entry?.audioKey, entry?.videoKey).forEach { runCatching { cache.removeResource(it) } }
        prefs(context, store).edit().remove(videoId).apply()
    }

    /** La source qui relit [entry] depuis le cache seul. */
    fun source(context: Context, entry: Entry, audioOnly: Boolean): MediaSource {
        // Pas de réseau derrière : ce qui manque est une erreur
        val factory = ProgressiveMediaSource.Factory(CacheDataSource.Factory().setCache(cache(context, entry.store)).setUpstreamDataSourceFactory(null))
        val metadata = MediaMetadata.Builder()
            .setTitle(entry.title)
            .setArtist(entry.channel)
            .setArtworkUri(entry.thumbnail?.toUri())
            .build()
        fun item(key: String, main: Boolean) = MediaItem.Builder()
            .setUri("mikeneko-hors-ligne://$key")
            .setCustomCacheKey(key)
            .apply { if (main) setMediaId(entry.url).setMediaMetadata(metadata) }
            .build()
        val videoKey = entry.videoKey
        val audio = factory.createMediaSource(item(entry.audioKey, main = audioOnly || videoKey == null))
        if (audioOnly || videoKey == null) return audio
        return MergingMediaSource(factory.createMediaSource(item(videoKey, main = true)), audio)
    }

    /** Oublie les entrées dont le cache a tout effacé (place libérée pour d'autres). */
    private fun prune(context: Context, store: Store) {
        val prefs = prefs(context, store)
        val cache = cache(context, store)
        val hourAgo = System.currentTimeMillis() - 60 * 60 * 1000L
        val gone = prefs.all.filter { (_, raw) ->
            val o = runCatching { JSONObject(raw as String) }.getOrNull() ?: return@filter true
            // (une entrée récente peut être encore vide : le morceau préparé à l'avance n'a pas commencé)
            o.optLong("at") < hourAgo && runCatching { cache.getCachedBytes(o.getString("audio"), 0, Long.MAX_VALUE) <= 0L }.getOrDefault(true)
        }.keys
        if (gone.isNotEmpty()) prefs.edit().apply { gone.forEach { remove(it) } }.apply()
    }

    /** La place prise par une réserve, en octets. */
    fun size(context: Context, store: Store): Long = runCatching { cache(context, store).cacheSpace }.getOrDefault(0L)

    /** Vide ce qui a été lu (réglages) ; les playlists téléchargées restent. */
    fun clear(context: Context) {
        for (store in listOf(Store.Music, Store.Videos)) {
            val cache = cache(context, store)
            cache.keys.toList().forEach { runCatching { cache.removeResource(it) } }
            prefs(context, store).edit().clear().apply()
        }
    }
}
