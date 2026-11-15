package app.mikeneko.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import app.mikeneko.data.LocalMusic
import app.mikeneko.data.NewPipeDownloader
import app.mikeneko.data.YouTubeRepository
import app.mikeneko.player.ChunkedDataSource
import app.mikeneko.player.MediaSources
import app.mikeneko.player.NowPlaying
import app.mikeneko.player.OfflineCache
import app.mikeneko.player.PlayerHolder
import app.mikeneko.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Un appareil Cast du réseau : Chromecast, télé, enceinte ([audioOnly] : pas d'écran). */
data class CastDevice(val id: String, val name: String, val model: String, val host: String, val port: Int, val audioOnly: Boolean)

/**
 * Le Chromecast (3.4.0), sans les services Google : comme VLC, Mikeneko trouve les appareils sur le Wi-Fi,
 * leur parle le protocole Cast ([CastChannel]), lance leur lecteur par défaut et lui fait lire ce que le
 * téléphone sert ([CastServer]). Le lecteur du téléphone reste en pause pendant ce temps ; les boutons
 * (lecture, avance, suivant…) passent par [PlayerHolder], qui renvoie ici quand une session est ouverte.
 */
@OptIn(UnstableApi::class)
object Cast {
    private const val SERVICE = "_googlecast._tcp."
    /** Le lecteur multimédia par défaut des appareils Cast. */
    private const val DEFAULT_RECEIVER = "CC1AD845"

    enum class Phase { Connecting, Connected }

    data class Session(val device: CastDevice, val phase: Phase, val error: Boolean = false)

    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    val devices: StateFlow<List<CastDevice>> = _devices.asStateFlow()

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    /** Le volume de l'appareil (0 à 1). */
    val volume = MutableStateFlow(1f)

    /** Une session est ouverte : les commandes vont à l'appareil. */
    val active: Boolean get() = _session.value?.phase == Phase.Connected

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var channel: CastChannel? = null
    private var transportId: String? = null
    private var appSessionId: String? = null
    @Volatile private var mediaSessionId: Int = 0
    /** Les statuts d'un média plus ancien que le dernier LOAD (numéro de session plus petit) sont ignorés. */
    @Volatile private var mediaSessionFloor: Int = 0
    /** Lecture / pause demandée avant que l'appareil ait donné son numéro de session : appliquée dès qu'il arrive. */
    @Volatile private var wanted: Boolean? = null
    private var pollJob: Job? = null
    private var volumeJob: Job? = null
    @Volatile private var volumeSetAt = 0L
    /** Ce qui est chargé sur l'appareil (l'adresse de [NowPlaying]). */
    @Volatile var loadedUrl: String? = null
        private set
    private var loadJob: Job? = null
    private var appContext: Context? = null

    // La position sur l'appareil, d'après son dernier statut (avancée à l'horloge entre deux statuts)
    @Volatile private var position = 0L
    @Volatile private var duration = 0L
    @Volatile private var positionAt = 0L
    @Volatile private var playing = false
    @Volatile private var rate = 1f

    // ------------------------------------------------------------------ la découverte

    private var discovery: NsdManager.DiscoveryListener? = null

    /** Cherche les appareils (le sélecteur est ouvert). */
    fun startDiscovery(context: Context) {
        if (discovery != null) return
        val manager = context.applicationContext.getSystemService(NsdManager::class.java) ?: return
        val pending = ArrayDeque<NsdServiceInfo>()
        var resolving = false
        val found = LinkedHashMap<String, CastDevice>()

        fun next() {
            if (resolving) return
            val info = synchronized(pending) { pending.removeFirstOrNull() } ?: return
            resolving = true
            @Suppress("DEPRECATION")
            manager.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, error: Int) {
                    resolving = false
                    next()
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    resolving = false
                    @Suppress("DEPRECATION")
                    val host = info.host?.hostAddress
                    val attributes = info.attributes
                    fun attr(key: String) = attributes[key]?.let { String(it) }
                    val id = attr("id") ?: info.serviceName
                    if (host != null) {
                        // « ca » : les capacités ; sans le bit 0 (sortie vidéo), c'est une enceinte
                        val audioOnly = attr("ca")?.toIntOrNull()?.let { it and 1 == 0 } ?: false
                        val device = CastDevice(id, attr("fn") ?: info.serviceName, attr("md").orEmpty(), host.substringBefore('%'), info.port, audioOnly)
                        synchronized(found) {
                            // La même appareil annoncé en IPv4 et en IPv6 : on garde l'IPv4
                            val known = found[id]
                            if (known == null || (known.host.contains(':') && !device.host.contains(':'))) found[id] = device
                            _devices.value = found.values.sortedBy { it.name.lowercase() }
                        }
                    }
                    next()
                }
            })
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, error: Int) = Unit
            override fun onStopDiscoveryFailed(type: String, error: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                synchronized(pending) { pending.addLast(info) }
                next()
            }

            override fun onServiceLost(info: NsdServiceInfo) = Unit
        }
        discovery = listener
        runCatching { manager.discoverServices(SERVICE, NsdManager.PROTOCOL_DNS_SD, listener) }.onFailure { discovery = null }
    }

    fun stopDiscovery(context: Context) {
        val listener = discovery ?: return
        discovery = null
        runCatching { context.applicationContext.getSystemService(NsdManager::class.java)?.stopServiceDiscovery(listener) }
    }

    // ------------------------------------------------------------------ la session

    /** Se connecte à [device], y lance le lecteur et y envoie ce qui joue (à l'endroit où on en est). */
    fun connect(context: Context, device: CastDevice) {
        val app = context.applicationContext
        appContext = app
        disconnect(resumeLocally = false)
        _session.value = Session(device, Phase.Connecting)
        scope.launch {
            try {
                val c = CastChannel(device.host, device.port, ::onMessage) { onClosed(device) }
                channel = c
                c.connect()
                c.send(CastChannel.RECEIVER, CastMessage.NS_CONNECTION, JSONObject().put("type", "CONNECT"))
                val status = c.request(CastChannel.RECEIVER, CastMessage.NS_RECEIVER, JSONObject().put("type", "LAUNCH").put("appId", DEFAULT_RECEIVER), 20_000)
                val app0 = findApp(status) ?: error("lancement refusé")
                transportId = app0.optString("transportId")
                appSessionId = app0.optString("sessionId")
                c.send(transportId!!, CastMessage.NS_CONNECTION, JSONObject().put("type", "CONNECT"))
                readVolume(status)
                _session.value = Session(device, Phase.Connected)
                PlayerHolder.castStarted()
                PlayerHolder.nowPlaying.value?.let { load(app, it, PlayerHolder.progress().first) }
                poll(c)
            } catch (e: Exception) {
                channel?.close()
                channel = null
                _session.value = Session(device, Phase.Connecting, error = true)
                delay(2_500)
                if (_session.value?.error == true) _session.value = null
            }
        }
    }

    /** Ferme la session ; [resumeLocally] : le téléphone reprend à l'endroit où en était l'appareil (en pause). */
    fun disconnect(resumeLocally: Boolean = true) {
        val c = channel ?: run {
            if (_session.value?.phase == Phase.Connected) _session.value = null
            return
        }
        val at = progress().first
        val wasActive = active
        loadJob?.cancel()
        appSessionId?.let { id -> c.send(CastChannel.RECEIVER, CastMessage.NS_RECEIVER, JSONObject().put("type", "STOP").put("sessionId", id).put("requestId", c.nextId())) }
        channel = null
        c.close()
        cleanUp()
        if (wasActive) PlayerHolder.castStopped(if (resumeLocally) at else null)
    }

    /** La session s'est fermée sans qu'on le demande (lecteur fermé sur l'appareil, Wi-Fi coupé…) : on le dit. */
    private fun onClosed(device: CastDevice) {
        if (channel == null) return
        val at = progress().first
        val c = channel
        channel = null
        c?.close()
        cleanUp()
        PlayerHolder.castStopped(at)
        PlayerHolder.castLost(device.name)
    }

    /**
     * Toutes les 3 s, on redemande où en est l'appareil (3.6.0) : sa position, lecture ou pause, son numéro
     * de session. L'appli reste ainsi d'accord avec l'appareil, même si un statut s'est perdu ou si
     * quelqu'un a mis pause avec la télécommande de la télé.
     */
    private fun poll(c: CastChannel) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (channel === c) {
                delay(3_000)
                val transport = transportId ?: continue
                c.send(transport, CastMessage.NS_MEDIA, JSONObject().put("type", "GET_STATUS").put("requestId", c.nextId()))
            }
        }
    }

    private fun cleanUp() {
        pollJob?.cancel()
        pollJob = null
        volumeJob?.cancel()
        volumeJob = null
        transportId = null
        appSessionId = null
        mediaSessionId = 0
        mediaSessionFloor = 0
        wanted = null
        loadedUrl = null
        playing = false
        _session.value = null
        CastServer.stop()
    }

    // ------------------------------------------------------------------ ce qui joue

    /** Envoie [now] à l'appareil, à [startMs]. Appelé à la connexion et à chaque nouveau morceau / vidéo. */
    fun load(context: Context, now: NowPlaying, startMs: Long = 0L) {
        val c = channel ?: return
        val transport = transportId ?: return
        loadJob?.cancel()
        loadedUrl = now.url
        // Le média d'avant ne compte plus : ses statuts (« interrompu »…) sont ignorés, et lecture/pause
        // attendent le numéro de session du nouveau
        mediaSessionFloor = mediaSessionId + 1
        mediaSessionId = 0
        wanted = null
        position = startMs
        positionAt = SystemClock.elapsedRealtime()
        loadJob = scope.launch {
            try {
                val device = _session.value?.device ?: return@launch
                val audioOnly = now.audioOnly || device.audioOnly
                val media = withContext(Dispatchers.IO) { mediaFor(context, now, audioOnly) } ?: return@launch
                val metadata = JSONObject()
                    .put("metadataType", if (now.audioOnly) 3 else 0)
                    .put("title", now.title)
                    .put(if (now.audioOnly) "artist" else "subtitle", now.channel)
                now.thumbnail?.let { metadata.put("images", JSONArray().put(JSONObject().put("url", it))) }
                media.put("metadata", metadata)
                val load = JSONObject()
                    .put("type", "LOAD")
                    .put("media", media)
                    .put("autoplay", true)
                    .put("currentTime", startMs / 1000.0)
                c.request(transport, CastMessage.NS_MEDIA, load, 30_000)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                PlayerHolder.castFailed()
            }
        }
    }

    /** La description du média pour LOAD : l'adresse (servie par le téléphone) et son type. */
    private suspend fun mediaFor(context: Context, now: NowPlaying, audioOnly: Boolean): JSONObject? {
        fun media(url: String, type: String, live: Boolean = false) =
            JSONObject().put("contentId", url).put("contentType", type).put("streamType", if (live) "LIVE" else "BUFFERED")

        // Un fichier du téléphone
        if (LocalMusic.isLocal(now.url)) {
            val file = LocalMusic.fileOf(now.url) ?: return null
            val mime = when (file.extension.lowercase()) {
                "mp3" -> "audio/mpeg"; "flac" -> "audio/flac"; "wav" -> "audio/wav"; "ogg", "oga", "opus" -> "audio/ogg"
                "webm" -> "audio/webm"; else -> "audio/mp4"
            }
            val resource = CastServer.Resource(mime, DefaultDataSource.Factory(context), now.url.toUri())
            val url = CastServer.publish(context, resource, file.extension.ifEmpty { "audio" }) ?: return null
            CastServer.retain(listOf(url))
            return media(url, mime)
        }
        // Un morceau téléchargé (son seul) : servi depuis la réserve, sans réseau
        if (audioOnly) {
            OfflineCache.playable(context, now.url, audioOnly = true)?.let { entry ->
                val itag = entry.audioKey.substringAfter(':').drop(1).substringBefore(':').toIntOrNull() ?: 0
                val mime = if (itag in WEBM_AUDIO_ITAGS) "audio/webm" else "audio/mp4"
                val factory = CacheDataSource.Factory().setCache(OfflineCache.cache(context, entry.store)).setUpstreamDataSourceFactory(null)
                val resource = CastServer.Resource(mime, factory, "mikeneko-hors-ligne://${entry.audioKey}".toUri(), entry.audioKey)
                val url = CastServer.publish(context, resource, if (mime == "audio/webm") "webm" else "m4a") ?: return null
                CastServer.retain(listOf(url))
                return media(url, mime)
            }
        }
        val info = PlayerHolder.castInfo(now) ?: YouTubeRepository.stream(now.url)
        // Un direct : son flux HLS, lu par l'appareil lui-même
        if (MediaSources.isLive(info)) {
            val hls = info.hlsUrl?.takeIf { it.isNotBlank() } ?: return null
            return media(hls, "application/x-mpegURL", live = true)
        }
        val plan = MediaSources.castPlan(info, audioOnly, AppSettings.musicQuality(context))
        val http = DefaultHttpDataSource.Factory().setUserAgent(NewPipeDownloader.USER_AGENT).setAllowCrossProtocolRedirects(true)
        fun serve(stream: MediaSources.CastStream, extension: String): String? {
            val cache = OfflineCache.cache(context, audioOnly)
            val resource = CastServer.Resource(stream.mime, OfflineCache.factory(cache, ChunkedDataSource.Factory(http)), stream.url.toUri(), stream.key)
            stream.stream.itagItem?.contentLength?.takeIf { it > 0 }?.let { resource.length = it }
            return CastServer.publish(context, resource, extension)
        }
        return when (plan) {
            is MediaSources.CastPlan.Single -> {
                val ext = if (plan.stream.mime.endsWith("webm")) "webm" else if (plan.stream.mime.startsWith("video")) "mp4" else "m4a"
                val url = serve(plan.stream, ext) ?: return null
                CastServer.retain(listOf(url))
                media(url, plan.stream.mime)
            }
            is MediaSources.CastPlan.Dash -> {
                val video = serve(plan.video, "mp4") ?: return null
                val audio = serve(plan.audio, "m4a") ?: return null
                val manifest = CastServer.publish(context, CastServer.Resource("application/dash+xml", text = MediaSources.dashManifest(plan, video, audio)), "mpd") ?: return null
                CastServer.retain(listOf(video, audio, manifest))
                media(manifest, "application/dash+xml")
            }
        }
    }

    // ------------------------------------------------------------------ les commandes

    private fun media(type: String, extra: JSONObject.() -> Unit = {}) {
        val c = channel ?: return
        val transport = transportId ?: return
        if (mediaSessionId == 0) return
        val payload = JSONObject().put("type", type).put("mediaSessionId", mediaSessionId).put("requestId", c.nextId())
        payload.extra()
        c.send(transport, CastMessage.NS_MEDIA, payload)
    }

    fun play() = command(true)

    fun pause() = command(false)

    private fun command(play: Boolean) {
        anchor()
        playing = play
        PlayerHolder.castState(play)
        if (mediaSessionId == 0) wanted = play // le média charge encore : appliqué à son premier statut
        else media(if (play) "PLAY" else "PAUSE")
    }

    fun togglePlay() = if (playing) pause() else play()

    fun seek(positionMs: Long) {
        position = positionMs.coerceAtLeast(0)
        positionAt = SystemClock.elapsedRealtime()
        media("SEEK") { put("currentTime", positionMs / 1000.0) }
    }

    /** Le volume de l'appareil : [delta] en plus ou en moins (touches de volume du téléphone). */
    fun adjustVolume(delta: Float) = setVolume(volume.value + delta)

    /**
     * Le volume de l'appareil (0 à 1). 3.6.0 : envoyé au plus toutes les 150 ms (le curseur en envoyait
     * un à chaque pixel), et les volumes renvoyés par l'appareil pendant le réglage sont ignorés (ils
     * faisaient sauter le curseur en arrière).
     */
    fun setVolume(level: Float) {
        if (channel == null) return
        volume.value = level.coerceIn(0f, 1f)
        volumeSetAt = SystemClock.elapsedRealtime()
        if (volumeJob?.isActive == true) return
        volumeJob = scope.launch {
            var sent = -1f
            while (true) {
                val target = volume.value
                if (target == sent) break
                val c = channel ?: break
                c.send(CastChannel.RECEIVER, CastMessage.NS_RECEIVER, JSONObject().put("type", "SET_VOLUME").put("volume", JSONObject().put("level", target.toDouble())).put("requestId", c.nextId()))
                sent = target
                delay(150)
            }
        }
    }

    /** Position et durée sur l'appareil (ms). */
    fun progress(): Pair<Long, Long> {
        val now = if (playing) position + ((SystemClock.elapsedRealtime() - positionAt) * rate).toLong() else position
        return (if (duration > 0) now.coerceAtMost(duration) else now) to duration
    }

    private fun anchor() {
        position = progress().first
        positionAt = SystemClock.elapsedRealtime()
    }

    // ------------------------------------------------------------------ les messages de l'appareil

    private fun onMessage(message: CastMessage.Message, json: JSONObject) {
        when (message.namespace) {
            CastMessage.NS_MEDIA -> if (json.optString("type") == "MEDIA_STATUS") onMediaStatus(json)
            CastMessage.NS_RECEIVER -> if (json.optString("type") == "RECEIVER_STATUS") {
                readVolume(json)
                // Le lecteur a été fermé sur l'appareil (télécommande, quelqu'un d'autre caste) : fin de session.
                // 3.6.0 : seulement si le statut donne bien la liste des applis et que la nôtre n'y est plus ;
                // un statut de volume sans cette liste coupait la session alors que l'appareil jouait encore
                if (_session.value?.phase == Phase.Connected && appClosed(json)) scope.launch { onClosed(_session.value?.device ?: return@launch) }
            }
            CastMessage.NS_CONNECTION -> if (json.optString("type") == "CLOSE" && message.source == transportId) {
                scope.launch { onClosed(_session.value?.device ?: return@launch) }
            }
        }
    }

    private fun onMediaStatus(json: JSONObject) {
        val status = json.optJSONArray("status")?.optJSONObject(0) ?: return
        val id = status.optInt("mediaSessionId", 0)
        if (id == 0 || id < mediaSessionFloor) return // le média d'avant
        mediaSessionId = id
        status.optJSONObject("media")?.optDouble("duration", -1.0)?.takeIf { it > 0 }?.let { duration = (it * 1000).toLong() }
        if (status.has("currentTime")) {
            position = (status.optDouble("currentTime", 0.0) * 1000).toLong()
            positionAt = SystemClock.elapsedRealtime()
        }
        rate = status.optDouble("playbackRate", 1.0).toFloat()
        val state = status.optString("playerState")
        // Lecture / pause demandée pendant le chargement : maintenant que l'appareil a un numéro de session
        wanted?.let { want ->
            if (state == "PLAYING" || state == "PAUSED") {
                wanted = null
                if (want != (state == "PLAYING")) {
                    media(if (want) "PLAY" else "PAUSE")
                    return
                }
            }
        }
        when (state) {
            "PLAYING" -> { playing = true; PlayerHolder.castState(true) }
            "PAUSED" -> { playing = false; PlayerHolder.castState(false) }
            "BUFFERING" -> Unit
            "IDLE" -> {
                playing = false
                PlayerHolder.castState(false)
                when (status.optString("idleReason")) {
                    "FINISHED" -> { mediaSessionId = 0; PlayerHolder.castFinished() }
                    "ERROR" -> PlayerHolder.castFailed()
                }
            }
        }
    }

    private fun findApp(status: JSONObject): JSONObject? {
        val apps = status.optJSONObject("status")?.optJSONArray("applications") ?: return null
        for (i in 0 until apps.length()) {
            val app = apps.optJSONObject(i) ?: continue
            if (app.optString("appId") == DEFAULT_RECEIVER) return app
        }
        return null
    }

    /** Le statut liste les applis lancées sur l'appareil, et notre lecteur n'y est plus. */
    private fun appClosed(status: JSONObject): Boolean {
        val apps = status.optJSONObject("status")?.optJSONArray("applications") ?: return false
        val ours = appSessionId
        for (i in 0 until apps.length()) {
            val app = apps.optJSONObject(i) ?: continue
            if (if (ours.isNullOrEmpty()) app.optString("appId") == DEFAULT_RECEIVER else app.optString("sessionId") == ours) return false
        }
        return true
    }

    private fun readVolume(status: JSONObject) {
        if (SystemClock.elapsedRealtime() - volumeSetAt < 1_000) return // on est en train de le régler
        status.optJSONObject("status")?.optJSONObject("volume")?.optDouble("level", -1.0)?.takeIf { it >= 0 }?.let { volume.value = it.toFloat() }
    }

    /** Les itags de son WebM (Opus) de YouTube. */
    private val WEBM_AUDIO_ITAGS = setOf(249, 250, 251, 338, 600, 774)
}
