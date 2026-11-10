package app.mikeneko.player

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import app.mikeneko.data.Chapter
import app.mikeneko.data.Feedback
import app.mikeneko.data.LocalMusic
import app.mikeneko.data.Music
import app.mikeneko.data.MusicHistory
import app.mikeneko.data.ReturnDislike
import app.mikeneko.data.Segment
import app.mikeneko.data.SponsorBlock
import app.mikeneko.data.SubscriptionStore
import app.mikeneko.data.TrackTags
import app.mikeneko.data.albumNameFrom
import app.mikeneko.data.VideoItem
import app.mikeneko.data.WatchHistory
import app.mikeneko.data.WatchPositions
import app.mikeneko.data.YouTubeRepository
import app.mikeneko.data.channelIdFrom
import app.mikeneko.data.cleanArtist
import app.mikeneko.data.sameTrack
import app.mikeneko.data.toVideo
import app.mikeneko.data.videoIdOf
import app.mikeneko.settings.AppSettings
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** Un seul ExoPlayer pour toute l'app : vidéo, mini-lecteur PiP et mode radio. */
object PlayerHolder {
    private var player: ExoPlayer? = null
    private var appContext: Context? = null
    private val scope = MainScope()

    /**
     * La vidéo dont le lecteur contient vraiment le flux (pour la reprise, [WatchPositions]). Remise
     * à null dès qu'on change de flux ([setCurrent]) : sinon, pendant le changement, la position de
     * la nouvelle vidéo serait gardée sous le nom de l'ancienne.
     */
    private var positionOwner: String? = null

    /** La vidéo en cours est un short (voir [play]) : en boucle, sans reprise. */
    private var playingShort = false

    /** Retient où on en est dans la vidéo en cours (reprise, même après fermeture de l'appli). */
    private fun saveVideoPosition() {
        val owner = positionOwner ?: return
        val now = nowPlaying.value ?: return
        if (now.audioOnly || now.live || now.videoId != owner) return
        val p = player ?: return
        val c = appContext ?: return
        if (p.mediaItemCount == 0) return
        val duration = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        WatchPositions.save(c, owner, p.currentPosition, duration)
        WatchHistory.get(c).recordProgress(owner, p.currentPosition, duration) // le temps regardé nourrit le « Pour toi »
    }

    /** La vidéo [now] est maintenant celle du lecteur : sa position sera gardée. */
    private fun ownPosition(now: NowPlaying?) {
        positionOwner = now?.takeIf { !it.audioOnly && !it.live && !playingShort }?.videoId?.takeIf { it.isNotBlank() }
    }
    private var watcher: Job? = null

    /** Numéro de la dernière demande de lecture : une demande plus ancienne qui finit après abandonne. */
    private var ticket = 0

    /** La vidéo en cours telle que NewPipe l'a lue (pour reconstruire la source : qualité, sous-titres). */
    private var info: StreamInfo? = null
    private var prefs: SharedPreferences? = null
    private var sleepJob: Job? = null
    /** Langue des sous-titres choisie : réutilisée pour les vidéos suivantes (comme YouTube). */
    private var captionLanguage: String? = null

    // ---- Réglages du lecteur (menu ⚙) ----
    val settingsPage = MutableStateFlow<SettingsPage?>(null)

    /** La file de la radio (stations, morceaux, fichiers téléchargés) et la position dedans. */
    val queue = MutableStateFlow<List<VideoItem>>(emptyList())
    val queueIndex = MutableStateFlow(-1)
    val repeat = MutableStateFlow(Repeat.Off)
    val shuffle = MutableStateFlow(false)
    /** L'ordre d'avant le mélange, pour le retrouver quand on coupe l'aléatoire. */
    private var unshuffled: List<VideoItem>? = null
    /** Mix auto : la file se remplit toute seule de titres proches quand on arrive au bout. */
    val autoMix = MutableStateFlow(false)
    /** Le mix en cours de YouTube (morceau de départ + page suivante), pour continuer la même suite. */
    private var mixSeed: String? = null
    private var mixNext: Page? = null
    private var mixJob: Job? = null
    private const val MAX_VIDEO_HISTORY = 50
    /** Les vidéos regardées avant celle en cours (la plus récente à la fin), pour « Vidéo précédente ». */
    val videoHistory = MutableStateFlow<List<VideoItem>>(emptyList())
    /** Qualités proposées pour la vidéo en cours (ex. 1080, 720, 480…). */
    val qualities = MutableStateFlow<List<Int>>(emptyList())
    /** Qualité voulue : null = Auto. Gardée d'une vidéo à l'autre. */
    val quality = MutableStateFlow<Int?>(null)
    /** Qualité réellement jouée (pour afficher « Auto (720p) »). */
    val playingQuality = MutableStateFlow(0)
    val captionTracks = MutableStateFlow<List<CaptionTrack>>(emptyList())
    val caption = MutableStateFlow<CaptionTrack?>(null)
    val speed = MutableStateFlow(1f)
    val loop = MutableStateFlow(false)
    val ambient = MutableStateFlow(true)
    val stableVolume = MutableStateFlow(false)
    val sleepTimer = MutableStateFlow<SleepTimer>(SleepTimer.Off)

    val nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val isPlaying = MutableStateFlow(false)
    /**
     * Lecture demandée (pas en pause, pas finie), même pendant un chargement. C'est elle qui choisit
     * l'icône lecture / pause : [isPlaying] passe à false à chaque chargement, ce qui ferait croire à une pause.
     */
    val playRequested = MutableStateFlow(false)
    val loading = MutableStateFlow(false)
    /** ExoPlayer attend des données (démarrage, saut, réseau lent). */
    val buffering = MutableStateFlow(false)
    val autoSkip = MutableStateFlow(true)

    /** Émis à chaque coup de patte sur un sponsor. */
    val skipped = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Au démarrage de l'app : relit les réglages gardés (mode ambiant, volume stable). */
    fun init(context: Context) {
        val p = context.getSharedPreferences("lecteur", Context.MODE_PRIVATE)
        prefs = p
        ambient.value = p.getBoolean("ambiant", true)
        stableVolume.value = p.getBoolean("volume_stable", false) && StableVolume.supported
        quality.value = p.getInt("qualite", 0).takeIf { it > 0 } // 0 = Auto
        autoSkip.value = p.getBoolean("sponsorblock_auto", true)
        autoplay.value = p.getBoolean("lecture_auto", true)
        repeat.value = runCatching { Repeat.valueOf(p.getString("repeter", null) ?: "Off") }.getOrDefault(Repeat.Off)
        shuffle.value = p.getBoolean("aleatoire", false)
        // La position de la vidéo, gardée de temps en temps (si l'appli est tuée sans prévenir)
        scope.launch {
            while (true) {
                delay(10_000)
                if (isPlaying.value) saveVideoPosition()
            }
        }
        // Le morceau suivant, préparé à l'avance : revu à chaque changement de file, de morceau, de mode
        scope.launch {
            combine(
                queue,
                queueIndex,
                repeat,
                sleepTimer,
                nowPlaying.map { now -> now?.let { Triple(it.url, it.audioOnly, it.live) } }.distinctUntilChanged(),
            ) { _, _, _, _, _ -> Unit }.collect { syncPreload() }
        }
        // « Récemment écoutés » : chaque morceau ou direct qui démarre en audio
        val history = MusicHistory.get(context)
        scope.launch {
            nowPlaying.map { it?.takeIf { now -> now.audioOnly } }.distinctUntilChanged { a, b -> a?.url == b?.url }.collect { now ->
                if (now == null) return@collect
                val item = queue.value.firstOrNull { sameTrack(it.url, now.url) }
                    ?: VideoItem(now.url, now.title, cleanArtist(now.channel), now.thumbnail, isLive = now.live)
                history.add(item)
            }
        }
        // Musique ↔ vidéo : le volume stable est coupé pour la musique, remis pour les vidéos
        scope.launch {
            nowPlaying.map { it?.audioOnly == true }.distinctUntilChanged().collect { applyStableVolume() }
        }
    }

    /**
     * Le volume stable ne s'applique qu'aux vidéos : la musique est déjà mixée à un volume régulier,
     * la compresser l'aplatit. Le réglage ([stableVolume]) reste gardé pour la prochaine vidéo.
     */
    private fun applyStableVolume(session: Int = player?.audioSessionId ?: 0): Boolean =
        StableVolume.apply(stableVolume.value && nowPlaying.value?.audioOnly != true, session)

    @OptIn(UnstableApi::class)
    fun get(context: Context): ExoPlayer = player ?: ExoPlayer.Builder(context.applicationContext, SpectrumRenderersFactory(context.applicationContext))
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .setLoadControl(MusicLoadControl()) // un morceau se charge en entier (sauvegarde hors ligne)
        .build()
        .also { created ->
            player = created
            appContext = context.applicationContext
            MediaSources.appContext = context.applicationContext
            created.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    VideoFrames.firstFrames.value++
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying.value = playing
                    if (!playing) saveVideoPosition() // pause, appli quittée…
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (playWhenReady) VideoFrames.forgetPausedFrame()
                    updatePlayRequested(created)
                }

                override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                    if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                        VideoFrames.forgetPausedFrame()
                        VideoFrames.seeks.value++
                    }
                }

                override fun onPlaybackStateChanged(state: Int) {
                    buffering.value = state == Player.STATE_BUFFERING
                    updatePlayRequested(created)
                    // (un lecteur vide n'a rien « fini » : ce n'est pas la fin d'un morceau)
                    if (state == Player.STATE_ENDED && created.mediaItemCount > 0) {
                        saveVideoPosition() // finie : on repartira du début
                        if (sleepTimer.value == SleepTimer.EndOfVideo) sleepTimer.value = SleepTimer.Off else onTrackEnded()
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    // Le morceau préparé à l'avance prend le relais (fin du précédent, ou « suivant ») ;
                    // ou on revient sur le morceau gardé (« précédent », tap sur l'ancien morceau)
                    val jump = jumping
                    jumping = null
                    if (preloaded != null && (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK && jump == Jump.Forward))) {
                        adoptPreloaded(created)
                        return
                    }
                    if (kept != null && reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK && jump == Jump.Back) {
                        adoptKept(created)
                        return
                    }
                    // En boucle, la vidéo ne « finit » jamais : le minuteur « fin de la vidéo » coupe au retour au début
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT && sleepTimer.value == SleepTimer.EndOfVideo) {
                        created.pause()
                        sleepTimer.value = SleepTimer.Off
                    }
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    // En direct, la qualité change toute seule (HLS) : on affiche celle reçue
                    if (nowPlaying.value?.live == true && videoSize.height > 0) {
                        playingQuality.value = MediaSources.qualityTier(videoSize.width, videoSize.height)
                    }
                }

                override fun onTracksChanged(tracks: Tracks) {
                    if (nowPlaying.value?.live == true) qualities.value = liveQualities(tracks)
                }

                override fun onPlayerError(error: PlaybackException) {
                    // Une seule relance automatique par lecture ; ensuite, le bouton lecture relance
                    if (retriedTicket != ticket) retry(automatic = true)
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    applyStableVolume(audioSessionId)
                }
            })
            created.setPlaybackSpeed(speed.value)
            applyStableVolume(created.audioSessionId)
        }

    /**
     * Nouvelle file (une grille de stations, un album, une playlist…). En aléatoire, le morceau choisi
     * passe en premier et le reste est mélangé. [mix] : la file se prolongera toute seule (Mix auto).
     */
    fun setQueue(list: List<VideoItem>, first: Int, mix: Boolean = false) {
        // Pas deux fois le même élément : la file s'en sert comme clé
        val chosen = list.getOrNull(first)
        val items = list.distinctBy { it.url }
        val start = if (chosen == null) first else items.indexOfFirst { it.url == chosen.url }
        mixJob?.cancel()
        autoMix.value = mix
        mixSeed = null
        mixNext = null
        if (shuffle.value && items.size > 1 && start in items.indices) {
            unshuffled = items
            queue.value = listOf(items[start]) + (items.toMutableList().apply { removeAt(start) }).shuffled()
            queueIndex.value = 0
        } else {
            unshuffled = null
            queue.value = items
            queueIndex.value = start
        }
    }

    /**
     * Lance une lecture qui survit à l'écran qui l'a demandée (changer de page ne l'annule pas).
     * [onError] est appelé sur le thread principal si elle échoue.
     */
    fun launchPlayback(onError: (Throwable) -> Unit = {}, block: suspend () -> Unit): Job = scope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            onError(e)
        }
    }

    /** Lance la file [items] à partir de [start] (en audio seul, pour la Musique). */
    suspend fun playQueue(context: Context, items: List<VideoItem>, start: Int, mix: Boolean = false) {
        if (items.isEmpty()) return
        setQueue(items, start.coerceIn(items.indices), mix)
        val target = queueIndex.value
        play(context, queue.value[target].url, audioOnly = true)
        prefetchMix()
    }

    /** Lance un morceau suivi de son mix : une file sans fin de titres proches. */
    suspend fun startMix(context: Context, item: VideoItem) {
        setQueue(listOf(item), 0, mix = true)
        play(context, item.url, audioOnly = true)
        prefetchMix()
    }

    /** Coupe ou relance le mix auto sur la file en cours. */
    fun setAutoMix(on: Boolean) {
        autoMix.value = on
        if (!on) {
            mixJob?.cancel()
        } else {
            mixSeed = null
            mixNext = null
            prefetchMix()
        }
    }

    // ---- Aléatoire / répéter ----

    fun setShuffle(on: Boolean) {
        shuffle.value = on
        prefs?.edit()?.putBoolean("aleatoire", on)?.apply()
        val items = queue.value
        val index = queueIndex.value
        if (on) {
            if (items.size < 2) return
            unshuffled = items
            val current = items.getOrNull(index)
            val rest = items.filterIndexed { i, _ -> i != index }.shuffled()
            queue.value = listOfNotNull(current) + rest
            queueIndex.value = if (current != null) 0 else -1
        } else {
            val original = unshuffled ?: return
            unshuffled = null
            // Ce qui a été ajouté pendant le mélange (mix, « à la suite »…) reste, à la fin
            val added = items.filter { item -> original.none { it.url == item.url } }
            val restored = original.filter { item -> items.any { it.url == item.url } } + added
            val current = items.getOrNull(index)
            queue.value = restored
            queueIndex.value = restored.indexOfFirst { it.url == current?.url }
        }
    }

    fun setRepeat(mode: Repeat) {
        repeat.value = mode
        prefs?.edit()?.putString("repeter", mode.name)?.apply()
        val now = nowPlaying.value
        if (now != null && now.audioOnly) player?.repeatMode = repeatModeFor(now.live)
    }

    /** Off → Tout → Un → Off. */
    fun cycleRepeat() = setRepeat(Repeat.entries[(repeat.value.ordinal + 1) % Repeat.entries.size])

    private fun repeatModeFor(live: Boolean) =
        if (!live && repeat.value == Repeat.One) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF

    // ---- Modifier la file ----

    /** Déplace un élément de la file (glisser-déposer), la lecture continue. */
    fun moveInQueue(from: Int, to: Int) {
        val items = queue.value.toMutableList()
        if (from !in items.indices || to !in items.indices || from == to) return
        val current = queueIndex.value
        items.add(to, items.removeAt(from))
        queue.value = items
        queueIndex.value = when {
            current == from -> to
            from < current && to >= current -> current - 1
            from > current && to <= current -> current + 1
            else -> current
        }
    }

    /** Retire un élément de la file (pas celui en cours). */
    fun removeFromQueue(index: Int) {
        val items = queue.value
        if (index !in items.indices || index == queueIndex.value) return
        val removed = items[index]
        queue.value = items.toMutableList().apply { removeAt(index) }
        unshuffled = unshuffled?.filterNot { it.url == removed.url }
        if (index < queueIndex.value) queueIndex.value--
    }

    /** « Lire ensuite » : juste après le morceau en cours. */
    fun playNext(item: VideoItem) {
        val current = queue.value.getOrNull(queueIndex.value)
        if (current != null && sameTrack(current.url, item.url)) return
        val items = queue.value.filterNot { sameTrack(it.url, item.url) }.toMutableList()
        val index = items.indexOfFirst { it.url == current?.url } // -1 : rien en cours, il passe en premier
        items.add(index + 1, item)
        queue.value = items
        queueIndex.value = index
        unshuffled = unshuffled?.let { list -> list.filterNot { sameTrack(it.url, item.url) } + item }
    }

    /** « Ajouter à la file » : à la fin. */
    fun addToQueue(item: VideoItem) {
        if (queue.value.any { it.url == item.url }) return
        queue.value = queue.value + item
        unshuffled = unshuffled?.let { it + item }
    }

    /** À appeler depuis le thread principal (c'est le cas de tous les scopes Compose). */
    suspend fun play(
        context: Context,
        url: String,
        audioOnly: Boolean,
        rememberPrevious: Boolean = true,
        startAtMs: Long = 0L,
        autoPlay: Boolean = true,
        short: Boolean = false,
    ) {
        // Un short : il tourne en boucle, repart toujours du début et ne prend pas de place dans la reprise
        playingShort = short && !audioOnly
        // Le morceau déjà en cours (musique), touché à nouveau : il repart du début, sans rien
        // recharger (pas de nouvelle requête à YouTube). En erreur, on recharge vraiment.
        val current = nowPlaying.value
        val loaded = player
        if (audioOnly && current != null && current.audioOnly && sameTrack(current.url, url) && startAtMs == 0L && autoPlay &&
            loaded != null && loaded.playerError == null && loaded.mediaItemCount > 0 && !loading.value
        ) {
            if (!current.live) loaded.seekTo(loaded.currentMediaItemIndex, 0L)
            loaded.play()
            return
        }
        // Une vidéo déjà commencée (parmi les 10 dernières) reprend où on l'avait laissée
        @Suppress("NAME_SHADOWING")
        val startAtMs = if (startAtMs == 0L && !audioOnly && !playingShort) WatchPositions.get(context, videoIdOf(url)) ?: 0L else startAtMs
        // Une autre vidéo demandée : celle mise de côté par la musique ne reviendra plus
        if (!audioOnly) parkedVideo.value = null
        val mine = ++ticket
        // Une autre lecture est demandée : le morceau préparé à l'avance ne doit pas prendre le relais
        dropPreload()
        // Une vidéo démarre pendant une musique : la musique est mise de côté (morceau et position),
        // elle reviendra en pause au retour dans Musique. Une nouvelle musique, elle, remplace tout.
        val leaving = nowPlaying.value
        if (!audioOnly && leaving != null && leaving.audioOnly) {
            parkedMusic.value = ParkedMusic(leaving.url, player?.currentPosition ?: 0L, leaving.live)
        } else if (audioOnly) {
            parkedMusic.value = null
        }
        if (LocalMusic.isLocal(url)) {
            playLocal(context, url, startAtMs, autoPlay)
            return
        }
        // Un morceau entier dans la sauvegarde hors ligne : il part tout de suite, sans flux à demander à YouTube
        // Pareil pour une vidéo d'une playlist téléchargée
        val ready = if (audioOnly) {
            OfflineCache.playable(context, url, audioOnly = true)
        } else {
            videoIdOf(url)?.let { OfflineCache.entry(context, OfflineCache.Store.SavedVideos, it) }
        }
        if (ready != null) {
            playOffline(context, ready, audioOnly, startAtMs, autoPlay, mine)
            return
        }
        loading.value = true
        try {
            val info = try {
                // Un short préparé d'avance (2.20.0) : sa fiche est déjà là, ou presque
                (if (playingShort) ShortsPreload.info(url) else null) ?: YouTubeRepository.stream(url)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Pas de réseau, ou YouTube refuse : la vidéo, si elle est entière hors ligne
                val entry = if (!audioOnly && mine == ticket) OfflineCache.playable(context, url, audioOnly = false) else null
                if (entry == null) throw e
                playOffline(context, entry, audioOnly = false, startAtMs, autoPlay, mine)
                return
            }
            if (mine != ticket) return // une autre vidéo a été demandée entre-temps
            // La vidéo qu'on quitte rejoint l'historique de « Vidéo précédente »
            val leaving = nowPlaying.value
            if (rememberPrevious && !audioOnly && leaving != null && !leaving.audioOnly && leaving.url != info.url) {
                val item = VideoItem(leaving.url, leaving.title, leaving.channel, leaving.thumbnail, isLive = leaving.live)
                videoHistory.value = (videoHistory.value + item).takeLast(MAX_VIDEO_HISTORY)
            }
            val p = get(context)
            val live = MediaSources.isLive(info)
            val leavingInfo = this.info // pour garder le morceau qu'on quitte avec ses infos
            this.info = info
            if (playingShort) ShortsPreload.remember(info)
            val tracks = if (audioOnly || live) emptyList() else MediaSources.captions(info)
            captionTracks.value = tracks
            caption.value = captionLanguage?.let { pickCaption(tracks, listOf(it)) }
            qualities.value = if (audioOnly || live) emptyList() else MediaSources.qualities(info)
            loop.value = playingShort
            p.repeatMode = if (playingShort) Player.REPEAT_MODE_ONE else if (audioOnly) repeatModeFor(live) else Player.REPEAT_MODE_OFF

            val built = MediaSources.build(info, audioOnly, quality.value, caption.value, cache = if (live) null else OfflineCache.cache(context, audioOnly))
            playingQuality.value = built.height
            setCurrent(p, audioOnly && !live, startAtMs.takeIf { !live } ?: 0L, leavingInfo, source = built.source)
            applyVideoSizeLimit(p, live)
            p.setPlaybackSpeed(if (live) 1f else speed.value)
            p.prepare()
            if (autoPlay) p.play() else p.pause()
            nowPlaying.value = nowPlayingFrom(context, info, audioOnly, live)
            ownPosition(nowPlaying.value)
            // Une chaîne suivie sans image (importée) : la vidéo nous donne son avatar, sans requête de plus
            if (!audioOnly) nowPlaying.value?.let { now ->
                now.channelUrl?.let(::channelIdFrom)?.let { id -> SubscriptionStore.get(context).setAvatar(id, now.channelAvatar) }
            }
            if (!live) rememberOffline(context, info, audioOnly, built)
            // Un morceau : son album est dans sa fiche, déjà lue (pour le tri par album, 2.15.0)
            if (audioOnly && !live && TrackTags.get(context, info.url)?.albumChecked != true) {
                TrackTags.setAlbum(context, info.url, albumNameFrom(info.description?.content.orEmpty()))
            }
            SubscriptionStore.get(context).markWatched(info.url)
            // Le « Pour toi » apprend des vidéos (pas de la radio, qui tourne en fond)
            if (!audioOnly && !live) WatchHistory.get(context).add(info)

            loadExtras(info, mine)
        } finally {
            if (mine == ticket) loading.value = false
        }
    }

    private fun nowPlayingFrom(context: Context, info: StreamInfo, audioOnly: Boolean, live: Boolean) = NowPlaying(
        url = info.url,
        videoId = info.id,
        title = info.name,
        channel = info.uploaderName.orEmpty(),
        thumbnail = info.thumbnails.maxByOrNull { it.height }?.url,
        audioOnly = audioOnly,
        live = live,
        views = info.viewCount,
        likes = info.likeCount,
        chapters = info.streamSegments.map { Chapter(it.title, it.startTimeSeconds * 1000L) },
        description = info.description?.content.orEmpty(),
        descriptionHtml = info.description?.type == Description.Type.HTML,
        uploadedAt = info.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli(),
        related = Feedback.get(context).let { feedback ->
            info.relatedItems.filterIsInstance<StreamInfoItem>()
                .map { it.toVideo() }
                .filter { !it.isLive && it.url != info.url && feedback.allows(it) }
        },
        channelUrl = info.uploaderUrl?.takeIf { it.isNotBlank() },
        channelAvatar = info.uploaderAvatars.maxByOrNull { it.height }?.url,
        channelSubscribers = info.uploaderSubscriberCount,
        // Les plus nettes jusqu'à 320 px de large (au-delà, des planches lourdes pour une vignette)
        previewFrames = info.previewFrames.filter { it.frameWidth in 1..320 }.maxByOrNull { it.frameWidth }
            ?: info.previewFrames.minByOrNull { it.frameWidth },
        isShort = !audioOnly && !live && isShort(info),
    )

    /**
     * Un short ? YouTube le dit (`isShortFormContent`), mais pas toujours selon le client utilisé :
     * sinon, une vidéo en hauteur de 3 min au plus en est un aussi.
     */
    private fun isShort(info: StreamInfo): Boolean {
        if (runCatching { info.isShortFormContent }.getOrDefault(false)) return true
        if (info.duration !in 1..180) return false
        val stream = (info.videoStreams + info.videoOnlyStreams).maxByOrNull { it.height } ?: return false
        return stream.width in 1 until stream.height
    }

    /**
     * La vidéo en cours s'avère être un short (reconnu une fois chargée) : elle passe en boucle et
     * ne compte plus dans la reprise des vidéos, comme si elle avait été ouverte avec `short = true`.
     */
    fun becomeShort() {
        if (playingShort || nowPlaying.value?.isShort != true) return
        playingShort = true
        positionOwner = null
        setLoop(true)
    }

    /** SponsorBlock et dislikes de la vidéo en cours (ignorés si une autre lecture a commencé entre-temps). */
    private suspend fun loadExtras(info: StreamInfo, mine: Int) {
        val (segments, votes) = coroutineScope {
            val segments = async { SponsorBlock.segments(info.id) }
            val votes = async { ReturnDislike.votes(info.id) }
            segments.await() to votes.await()
        }
        if (mine != ticket) return
        nowPlaying.value = nowPlaying.value?.copy(segments = segments, dislikes = votes?.dislikes)
        startWatcher()
    }

    // ---- Le morceau suivant, chargé à l'avance ----
    //
    // Dès qu'un morceau de la file joue, le suivant est résolu (adresse du son) et ajouté derrière lui
    // dans ExoPlayer : la transition est instantanée, sans les ~2 s de chargement ni l'arrêt du lecteur
    // (pendant lequel le téléphone pouvait endormir l'appli). « Suivant » y saute aussi tout de suite.

    private class Preloaded(val index: Int, val url: String, val info: StreamInfo?, val offline: OfflineCache.Entry? = null)

    private var preloaded: Preloaded? = null
    private var preloadJob: Job? = null

    private fun canPreload(): Boolean {
        val now = nowPlaying.value ?: return false
        return player != null && now.audioOnly && !now.live && repeat.value != Repeat.One &&
            sleepTimer.value != SleepTimer.EndOfVideo && sameTrack(queue.value.getOrNull(queueIndex.value)?.url, now.url)
    }

    /** L'élément de la file qui suit le morceau en cours (la file repart au début en « répéter tout »). */
    private fun expectedNextIndex(): Int? {
        val index = queueIndex.value
        val size = queue.value.size
        return when {
            index + 1 < size -> index + 1
            repeat.value == Repeat.All && size > 1 -> 0
            else -> null // en mix, la suite arrivera (prefetchMix) et relancera la préparation
        }
    }

    /** Le morceau préparé n'est plus le bon (file modifiée, mode changé…) : on le retire et on prépare le bon. */
    private fun syncPreload() {
        val pre = preloaded
        val wanted = expectedNextIndex()
        val ok = pre != null && canPreload() && pre.index == wanted && sameTrack(queue.value.getOrNull(pre.index)?.url, pre.url)
        if (pre != null && !ok) dropPreload()
        if (preloaded == null && canPreload() && wanted != null && preloadJob?.isActive != true) {
            val context = appContext ?: return
            preloadJob = scope.launch { runCatching { preloadNext(context) } }
        }
    }

    private fun dropPreload() {
        preloadJob?.cancel()
        preloadJob = null
        if (preloaded != null) {
            player?.let { p ->
                val after = p.currentMediaItemIndex + 1
                if (p.mediaItemCount > after) p.removeMediaItems(after, p.mediaItemCount)
            }
        }
        preloaded = null
    }

    // ---- Le morceau précédent, gardé ----
    //
    // Le dernier morceau joué reste chargé juste avant le morceau en cours : « précédent » (ou un tap
    // dessus dans la file) y revient tout de suite. Un seul : le lecteur garde au plus
    // [précédent] [en cours] [suivant préparé].

    private class Kept(val url: String, val info: StreamInfo?, val nowPlaying: NowPlaying)

    private enum class Jump { Forward, Back }

    private var kept: Kept? = null
    /** Le saut demandé (suivant / précédent) que la prochaine transition d'ExoPlayer doit adopter. */
    private var jumping: Jump? = null

    /**
     * Pose le nouveau morceau. [keepPrevious] (musique) : le morceau qu'on quitte reste chargé juste
     * avant ; sinon (vidéo, direct) le lecteur ne contient plus que le nouveau.
     */
    private fun setCurrent(
        p: ExoPlayer,
        keepPrevious: Boolean,
        startAtMs: Long,
        leavingInfo: StreamInfo?,
        source: MediaSource? = null,
        item: MediaItem? = null,
    ) {
        // La vidéo qu'on quitte : sa position est gardée avant que le lecteur change de flux
        saveVideoPosition()
        positionOwner = null
        val leaving = nowPlaying.value
        val canKeep = keepPrevious && leaving != null && leaving.audioOnly && !leaving.live && p.mediaItemCount > 0
        if (!canKeep) {
            // Remplacer d'un seul coup : vider puis remplir ferait passer le lecteur par « terminé »,
            // et la fin du morceau d'avant lancerait le suivant de la file (par-dessus une vidéo)
            kept = null
            if (source != null) p.setMediaSource(source, startAtMs) else if (item != null) p.setMediaItem(item, startAtMs)
            return
        }
        fun add() {
            if (source != null) p.addMediaSource(source) else if (item != null) p.addMediaItem(item)
        }
        val cur = p.currentMediaItemIndex
        if (p.mediaItemCount > cur + 1) p.removeMediaItems(cur + 1, p.mediaItemCount)
        if (cur > 0) p.removeMediaItems(0, cur)
        kept = Kept(leaving.url, leavingInfo, leaving)
        add()
        p.seekTo(1, startAtMs)
    }

    /** On est revenu sur le morceau gardé : il redevient le morceau en cours, celui qu'on quitte devient le suivant. */
    private fun adoptKept(p: ExoPlayer) {
        val back = kept ?: return
        kept = null
        ++ticket
        val leaving = nowPlaying.value
        val leavingInfo = info
        // [gardé = en cours] [celui qu'on quitte] [ancien suivant préparé] → on garde les deux premiers
        val cur = p.currentMediaItemIndex
        if (p.mediaItemCount > cur + 2) p.removeMediaItems(cur + 2, p.mediaItemCount)
        if (cur > 0) p.removeMediaItems(0, cur)
        VideoFrames.forgetPausedFrame()
        val index = queue.value.indexOfFirst { sameTrack(it.url, back.url) }
        if (index >= 0) queueIndex.value = index
        info = back.info
        nowPlaying.value = back.nowPlaying
        if (back.info == null) watcher?.cancel() else startWatcher()
        // Celui qu'on quitte est déjà chargé juste après : c'est le suivant tout trouvé
        preloaded = leaving?.let { now ->
            val at = queue.value.indexOfFirst { sameTrack(it.url, now.url) }
            if (p.mediaItemCount > p.currentMediaItemIndex + 1 && at >= 0) Preloaded(at, now.url, leavingInfo) else null
        }
        syncPreload()
    }

    private suspend fun preloadNext(context: Context) {
        val mine = ticket
        val index = queueIndex.value
        val target = expectedNextIndex() ?: return
        val item = queue.value.getOrNull(target) ?: return
        val local = LocalMusic.isLocal(item.url)
        val offline = if (local) null else OfflineCache.playable(context, item.url, audioOnly = true)
        val info = if (local || offline != null) null else YouTubeRepository.stream(item.url)
        if (info != null && MediaSources.isLive(info)) return // un direct ne se prépare pas
        // Entre-temps, rien n'a bougé ?
        val p = player ?: return
        if (mine != ticket || queueIndex.value != index || expectedNextIndex() != target || !canPreload()) return
        if (!sameTrack(queue.value.getOrNull(target)?.url, item.url) || p.mediaItemCount != p.currentMediaItemIndex + 1) return
        when {
            offline != null -> p.addMediaSource(OfflineCache.source(context, offline, audioOnly = true))
            info == null -> p.addMediaItem(localMediaItem(item.url))
            else -> {
                val built = MediaSources.build(info, audioOnly = true, cache = OfflineCache.musicCache(context))
                p.addMediaSource(built.source)
                rememberOffline(context, info, audioOnly = true, built)
            }
        }
        preloaded = Preloaded(target, item.url, info, offline)
    }

    /** Le morceau préparé joue maintenant : il devient le morceau en cours, et on prépare le suivant. */
    private fun adoptPreloaded(p: ExoPlayer) {
        val next = preloaded ?: return
        preloaded = null
        val context = appContext ?: return
        val mine = ++ticket
        // [ancien gardé] [celui qu'on quitte] [en cours] → on ne garde que celui qu'on quitte avant
        val cur = p.currentMediaItemIndex
        if (cur > 1) p.removeMediaItems(0, cur - 1)
        val leaving = nowPlaying.value
        kept = leaving?.takeIf { it.audioOnly && !it.live && p.currentMediaItemIndex > 0 }?.let { Kept(it.url, info, it) }
        VideoFrames.forgetPausedFrame()
        queueIndex.value = next.index
        val info = next.info
        if (info == null) {
            this.info = null
            watcher?.cancel()
            val offline = next.offline ?: if (LocalMusic.isLocal(next.url)) null else OfflineCache.playable(context, next.url, audioOnly = true)
            if (offline != null) {
                nowPlaying.value = offlineNowPlaying(offline, audioOnly = true)
                scope.launch { runCatching { fillDetails(context, offline.url, mine) } }
            } else {
                nowPlaying.value = localNowPlaying(next.url)
            }
        } else {
            this.info = info
            nowPlaying.value = nowPlayingFrom(context, info, audioOnly = true, live = false)
            SubscriptionStore.get(context).markWatched(info.url)
            scope.launch { runCatching { loadExtras(info, mine) } }
        }
        prefetchMix()
        syncPreload()
    }

    // ---- La sauvegarde hors ligne (voir OfflineCache) ----

    /** Retient le morceau / la vidéo qui vient d'être lancé : une fois entier dans le cache, il se relira hors ligne. */
    private fun rememberOffline(context: Context, info: StreamInfo, audioOnly: Boolean, built: MediaSources.Built) {
        val key = built.audioKey ?: return
        runCatching {
            OfflineCache.remember(
                context,
                audioOnly,
                OfflineCache.Entry(
                    videoId = info.id,
                    url = info.url,
                    title = info.name,
                    channel = info.uploaderName.orEmpty(),
                    thumbnail = info.thumbnails.maxByOrNull { it.height }?.url,
                    audioKey = key,
                    videoKey = built.videoKey,
                    height = built.height,
                ),
            )
        }
    }

    /** Relit depuis la sauvegarde hors ligne, sans réseau. */
    private fun playOffline(context: Context, entry: OfflineCache.Entry, audioOnly: Boolean, startAtMs: Long, autoPlay: Boolean, mine: Int) {
        loading.value = false
        val leavingInfo = info
        val p = get(context)
        info = null
        captionTracks.value = emptyList()
        caption.value = null
        qualities.value = emptyList()
        loop.value = playingShort
        p.repeatMode = if (playingShort) Player.REPEAT_MODE_ONE else if (audioOnly) repeatModeFor(live = false) else Player.REPEAT_MODE_OFF
        playingQuality.value = entry.height
        setCurrent(p, keepPrevious = audioOnly, startAtMs, leavingInfo, source = OfflineCache.source(context, entry, audioOnly))
        applyVideoSizeLimit(p, live = false)
        p.setPlaybackSpeed(speed.value)
        p.prepare()
        if (autoPlay) p.play() else p.pause()
        watcher?.cancel()
        nowPlaying.value = offlineNowPlaying(entry, audioOnly)
        ownPosition(nowPlaying.value)
        // En ligne, les détails (artiste, album, suggestions, SponsorBlock…) arrivent ensuite, sans toucher à la lecture
        scope.launch { runCatching { fillDetails(context, entry.url, mine, audioOnly) } }
    }

    private fun offlineNowPlaying(entry: OfflineCache.Entry, audioOnly: Boolean) = NowPlaying(
        url = entry.url,
        videoId = entry.videoId,
        title = entry.title,
        channel = entry.channel,
        thumbnail = entry.thumbnail,
        audioOnly = audioOnly,
        live = false,
        views = -1,
        likes = -1,
    )

    /** Les infos complètes d'un morceau lu hors ligne, si le réseau répond (la lecture n'est pas touchée). */
    private suspend fun fillDetails(context: Context, url: String, mine: Int, audioOnly: Boolean = true) {
        val info = YouTubeRepository.stream(url)
        if (mine != ticket || !sameTrack(nowPlaying.value?.url, url)) return
        this.info = info
        nowPlaying.value = nowPlayingFrom(context, info, audioOnly, live = false)
        loadExtras(info, mine)
    }

    /** Un fichier téléchargé : lu directement, sans YouTube (ni SponsorBlock, ni historique). */
    private fun playLocal(context: Context, url: String, startAtMs: Long = 0L, autoPlay: Boolean = true) {
        loading.value = false
        val leavingInfo = info
        val p = get(context)
        info = null
        captionTracks.value = emptyList()
        caption.value = null
        qualities.value = emptyList()
        loop.value = false
        p.repeatMode = repeatModeFor(live = false)
        setCurrent(p, keepPrevious = true, startAtMs, leavingInfo, item = localMediaItem(url))
        applyVideoSizeLimit(p, live = false)
        p.setPlaybackSpeed(speed.value)
        p.prepare()
        if (autoPlay) p.play() else p.pause()
        watcher?.cancel()
        nowPlaying.value = localNowPlaying(url)
    }

    private fun localTrack(url: String) = queue.value.firstOrNull { it.url == url } ?: LocalMusic.tracks.value?.firstOrNull { it.url == url }

    private fun localTitle(url: String) = localTrack(url)?.title ?: LocalMusic.fileOf(url)?.nameWithoutExtension.orEmpty()

    private fun localMediaItem(url: String): MediaItem {
        val item = localTrack(url)
        return MediaItem.Builder()
            .setUri(url)
            .setMediaId(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(localTitle(url))
                    .setArtist(item?.channel)
                    .setArtworkUri(item?.thumbnail?.toUri())
                    .build(),
            )
            .build()
    }

    private fun localNowPlaying(url: String): NowPlaying {
        val item = localTrack(url)
        return NowPlaying(
            url = url,
            videoId = "",
            title = localTitle(url),
            channel = item?.channel.orEmpty(),
            thumbnail = item?.thumbnail,
            audioOnly = true,
            live = false,
            views = -1,
            likes = -1,
        )
    }

    /** Il y a une autre station avant / après (la file boucle). */
    fun canSkip(): Boolean = queue.value.size > 1 || autoMix.value

    /** Suivant (bouton) : au bout de la file, le mix se prolonge, sinon on repart au début. */
    suspend fun next(context: Context) {
        if (autoMix.value && queueIndex.value >= queue.value.size - 1) extendMix()
        playAt(context, queueIndex.value + 1)
    }

    /** Précédent : après 3 s d'un morceau, on revient à son début (comme tous les lecteurs de musique). */
    suspend fun previous(context: Context) {
        val p = player
        val now = nowPlaying.value
        if (p != null && now != null && !now.live && p.currentPosition > 3_000) {
            p.seekTo(0)
            return
        }
        playAt(context, queueIndex.value - 1)
    }

    /** Un morceau de la file vient de finir : le suivant, ou fin de la file (sauf répéter tout / mix). */
    private fun onTrackEnded() {
        val now = nowPlaying.value ?: return
        if (!now.audioOnly) {
            onVideoEnded(now)
            return
        }
        if (now.live) return
        val items = queue.value
        val index = queueIndex.value
        if (!sameTrack(items.getOrNull(index)?.url, now.url)) return
        val context = appContext ?: return
        val mine = ticket
        scope.launch {
            // Entre-temps, autre chose a été lancé (une vidéo…) : ce n'est plus à la file de continuer
            if (mine != ticket || nowPlaying.value?.url != now.url) return@launch
            runCatching {
                when {
                    index + 1 < queue.value.size -> playAt(context, index + 1)
                    autoMix.value -> {
                        extendMix()
                        if (index + 1 < queue.value.size) playAt(context, index + 1)
                    }
                    repeat.value == Repeat.All && items.isNotEmpty() -> playAt(context, 0)
                }
            }
        }
    }

    /** Commandes venues du système (notification, écouteurs, montre) : file en radio, suggestions en vidéo. */
    fun skipFromSystem(forward: Boolean) {
        val context = appContext ?: return
        val now = nowPlaying.value ?: return
        scope.launch {
            runCatching {
                when {
                    now.audioOnly && (queue.value.isNotEmpty()) -> if (forward) next(context) else previous(context)
                    forward -> nextVideo(context)
                    player?.let { it.currentPosition > 3_000 } == true -> player?.seekTo(0)
                    else -> previousVideo(context)
                }
            }
        }
    }

    /** Proche de la fin de la file en mix auto : on charge la suite en avance (sans attendre la fin). */
    private fun prefetchMix() {
        if (!autoMix.value || queueIndex.value < queue.value.size - 3) return
        if (mixJob?.isActive == true) return
        mixJob = scope.launch { runCatching { extendMix() } }
    }

    /**
     * Prolonge la file : la suite du mix de YouTube, ou un nouveau mix à partir du morceau en cours,
     * et en dernier recours les suggestions déjà reçues avec le morceau (sans nouvelle requête).
     */
    private suspend fun extendMix() {
        mixJob?.takeIf { it.isActive && it != kotlinx.coroutines.currentCoroutineContext()[Job] }?.let {
            it.join()
            if (queueIndex.value < queue.value.size - 1) return
        }
        val items = queue.value
        // On continue le mix commencé, sinon on en part un nouveau du dernier morceau de la file
        val seed = mixSeed ?: items.lastOrNull()?.url?.let(::videoIdOf)
        val page = seed?.let { id -> runCatching { Music.mix(id, mixNext.takeIf { mixSeed == id }) }.getOrNull() }
        if (page != null && page.next != null) {
            mixSeed = seed
            mixNext = page.next
        } else {
            mixSeed = null
            mixNext = null
        }
        val fresh = (page?.tracks.orEmpty().ifEmpty { nowPlaying.value?.related.orEmpty() })
            .filter { !it.isLive && it.durationSec <= 15 * 60 } // pas de directs ni de compilations de 2 h
            .filter { track -> queue.value.none { sameTrack(it.url, track.url) } }
        if (fresh.isEmpty()) {
            // Le mix est épuisé : le prochain repartira du morceau en cours
            mixSeed = null
            mixNext = null
            return
        }
        queue.value = queue.value + fresh
        unshuffled = unshuffled?.let { it + fresh }
    }

    /** Vidéo suivante : la première suggestion de YouTube (comme « À suivre »). */
    suspend fun nextVideo(context: Context) {
        val now = nowPlaying.value ?: return
        // Ouverte depuis une playlist : la suivante de la playlist, sinon la première suggestion
        val list = videoPlaylist.value
        val index = list.indexOfFirst { sameTrack(it.url, now.url) }
        val next = list.getOrNull(index + 1)?.takeIf { index >= 0 } ?: now.related.firstOrNull() ?: return
        play(context, next.url, audioOnly = false)
    }

    /** Une musique mise de côté par une vidéo : son adresse, où elle en était, si c'est un direct. */
    data class ParkedMusic(val url: String, val positionMs: Long, val live: Boolean)

    val parkedMusic = MutableStateFlow<ParkedMusic?>(null)

    /** Une vidéo mise de côté en allant dans Musique : son adresse et où elle en était. */
    data class ParkedVideo(val url: String, val positionMs: Long)

    val parkedVideo = MutableStateFlow<ParkedVideo?>(null)

    /**
     * On entre dans Musique pendant une vidéo : elle se met en pause (son mini-lecteur se cache) et
     * on retient où elle en était, au cas où un morceau la remplacerait dans le lecteur.
     */
    fun parkVideo() {
        val now = nowPlaying.value ?: return
        if (now.audioOnly) return
        pause()
        parkedVideo.value = ParkedVideo(now.url, if (now.live) 0L else player?.currentPosition ?: 0L)
    }

    /**
     * On ressort de Musique : la vidéo est encore dans le lecteur (rien à faire, son mini-lecteur
     * revient) ; sinon (un morceau l'a remplacée) elle revient en pause, là où elle en était.
     */
    suspend fun restoreParkedVideo(context: Context) {
        val parked = parkedVideo.value ?: return
        parkedVideo.value = null
        val now = nowPlaying.value
        if (now != null && !now.audioOnly && sameTrack(now.url, parked.url)) return
        play(context, parked.url, audioOnly = false, startAtMs = parked.positionMs, autoPlay = false)
    }

    /** Remet la musique mise de côté, en pause (ou qui joue : [autoPlay]), là où elle en était (la file n'a pas bougé). */
    suspend fun restoreParkedMusic(context: Context, autoPlay: Boolean = false) {
        val parked = parkedMusic.value ?: return
        val index = queue.value.indexOfFirst { sameTrack(it.url, parked.url) }
        if (index >= 0) queueIndex.value = index
        play(context, parked.url, audioOnly = true, startAtMs = if (parked.live) 0L else parked.positionMs, autoPlay = autoPlay)
    }

    /** La playlist d'où vient la vidéo en cours (vide sinon) : « suivante » en suit l'ordre. */
    val videoPlaylist = MutableStateFlow<List<VideoItem>>(emptyList())
    /** Son nom (pour le tiroir de la playlist dans le lecteur). */
    val videoPlaylistTitle = MutableStateFlow<String?>(null)

    /**
     * Une vidéo vient de finir : la suivante de sa playlist, sinon (lecture automatique activée) la
     * première suggestion, comme « À suivre ». Pas pour un direct.
     */
    private fun onVideoEnded(now: NowPlaying) {
        if (now.live) return
        val list = videoPlaylist.value
        val index = list.indexOfFirst { sameTrack(it.url, now.url) }
        val next = list.getOrNull(index + 1)?.takeIf { index >= 0 }
            ?: now.related.firstOrNull()?.takeIf { autoplay.value && index < 0 }
            ?: return
        val context = appContext ?: return
        val mine = ticket
        scope.launch {
            if (mine != ticket || nowPlaying.value?.url != now.url) return@launch
            runCatching { play(context, next.url, audioOnly = false) }
        }
    }

    /** Lecture automatique : à la fin d'une vidéo (hors playlist), la première suggestion démarre. */
    val autoplay = MutableStateFlow(true)

    fun setAutoplay(on: Boolean) {
        autoplay.value = on
        prefs?.edit()?.putBoolean("lecture_auto", on)?.apply()
    }

    /** Vidéo précédente : celle regardée juste avant (sans la remettre dans l'historique). */
    suspend fun previousVideo(context: Context) {
        val before = videoHistory.value
        val previous = before.lastOrNull() ?: return
        videoHistory.value = before.dropLast(1)
        try {
            play(context, previous.url, audioOnly = false, rememberPrevious = false)
        } catch (e: Throwable) {
            videoHistory.value = before // pas jouée : elle reste disponible
            throw e
        }
    }

    /** Joue l'élément [position] de la file (en boucle aux deux bouts). */
    suspend fun playAt(context: Context, position: Int) {
        val items = queue.value
        if (items.isEmpty()) return
        val target = ((position % items.size) + items.size) % items.size
        val p = player
        val now = nowPlaying.value
        if (p != null && now != null && now.audioOnly) {
            // Déjà chargé juste après (suivant préparé) ou juste avant (morceau gardé) : on y saute
            val pre = preloaded
            if (pre != null && pre.index == target && sameTrack(items[target].url, pre.url) && p.mediaItemCount > p.currentMediaItemIndex + 1) {
                jumping = Jump.Forward
                p.seekToNextMediaItem()
                p.play()
                return
            }
            val back = kept
            if (back != null && sameTrack(items[target].url, back.url) && p.currentMediaItemIndex > 0) {
                jumping = Jump.Back
                p.seekTo(p.currentMediaItemIndex - 1, 0L)
                p.play()
                return
            }
        }
        queueIndex.value = target
        play(context, items[target].url, nowPlaying.value?.audioOnly ?: true)
        prefetchMix()
    }

    /** La lecture (numéro [ticket]) déjà relancée une fois après une erreur. */
    private var retriedTicket = -1

    /**
     * La lecture a échoué (« Source error » : flux expiré, réseau coupé, fichier gardé qui ne
     * correspond plus…) : le lecteur s'est arrêté, image noire, et « lecture » n'y faisait plus
     * rien. On redemande des flux tout neufs à YouTube (sans ce qui en a été gardé) et on repart
     * au même endroit.
     */
    private fun retry(automatic: Boolean) {
        val p = player ?: return
        val context = appContext ?: return
        val now = nowPlaying.value ?: return
        if (LocalMusic.isLocal(now.url)) {
            p.prepare()
            p.play()
            return
        }
        // La position d'un lecteur en erreur n'est pas fiable : elle ne doit pas écraser la reprise
        positionOwner = null
        val resumeVideo = if (now.audioOnly || now.live || playingShort) null else WatchPositions.get(context, now.videoId)
        val position = if (now.live) 0L else p.currentPosition.takeIf { it > 0L } ?: resumeVideo ?: 0L
        val autoPlay = !automatic || p.playWhenReady
        val short = playingShort
        // Joué depuis la sauvegarde de lecture (info gardée) : on l'oublie, elle a pu causer l'erreur
        if (info != null) runCatching { OfflineCache.forgetPlayed(context, now.audioOnly, now.videoId) }
        launchPlayback {
            retriedTicket = ticket + 1 // le numéro que [play] va prendre
            play(context, now.url, now.audioOnly, rememberPrevious = false, startAtMs = position, autoPlay = autoPlay, short = short)
        }
    }

    /** Pause si la lecture est demandée (même pendant un chargement), sinon lecture ; vidéo finie : on la relance. */
    fun togglePlay() {
        val p = player ?: return
        when {
            // En erreur, le lecteur est arrêté : « lecture » recharge la vidéo
            p.playbackState == Player.STATE_IDLE && p.playerError != null -> retry(automatic = false)
            p.playbackState == Player.STATE_ENDED -> { p.seekTo(0); p.play() }
            p.playWhenReady -> p.pause()
            else -> p.play()
        }
    }

    private fun updatePlayRequested(p: Player) {
        playRequested.value = p.playWhenReady && p.playbackState != Player.STATE_ENDED && p.playbackState != Player.STATE_IDLE
    }

    fun pause() {
        player?.pause()
    }

    /**
     * L'appli n'est plus visible (quittée, fenêtre PiP fermée, autre appli ou page par-dessus,
     * écran éteint) : une vidéo se met en pause. La radio (audio seul), elle, continue.
     */
    fun pauseVideoInBackground() {
        if (nowPlaying.value?.audioOnly == false) player?.pause()
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    /** Avancer (positif) ou reculer (négatif), sans sortir de la vidéo. */
    fun seekBy(deltaMs: Long) {
        val p = player ?: return
        val end = p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        p.seekTo((p.currentPosition + deltaMs).coerceIn(0L, end))
    }

    /** Position et durée en ms (durée 0 si inconnue, par exemple en direct). */
    fun progress(): Pair<Long, Long> =
        player?.let { it.currentPosition to it.duration.coerceAtLeast(0) } ?: (0L to 0L)

    /** Jusqu'où la vidéo / le morceau en cours est déjà chargé (ms). */
    fun bufferedPosition(): Long = player?.bufferedPosition ?: 0L

    fun stop() {
        playingShort = false
        parkedVideo.value = null
        saveVideoPosition()
        positionOwner = null
        ticket++ // une vidéo encore en chargement ne doit pas démarrer après la fermeture
        dropPreload()
        kept = null
        VideoFrames.forgetPausedFrame()
        loading.value = false
        info = null
        qualities.value = emptyList()
        captionTracks.value = emptyList()
        caption.value = null
        settingsPage.value = null
        watcher?.cancel()
        // D'abord : le son se coupe et le mini-lecteur disparaît. L'arrêt complet (qui remplace
        // l'image par du noir) attend que plus rien ne soit affiché, sinon on voit un flash noir.
        player?.pause()
        nowPlaying.value = null
        val mine = ticket
        scope.launch {
            delay(150)
            if (mine == ticket) {
                player?.stop()
                player?.clearMediaItems()
            }
        }
    }

    /**
     * L'appli retirée des applis récentes : tout s'arrête (son, file, mix, minuteur) et la
     * prochaine ouverture repart de zéro, comme après un vrai redémarrage.
     */
    fun reset() {
        stop()
        player?.stop()
        player?.clearMediaItems()
        mixJob?.cancel()
        mixJob = null
        mixSeed = null
        mixNext = null
        autoMix.value = false
        queue.value = emptyList()
        queueIndex.value = -1
        unshuffled = null
        videoHistory.value = emptyList()
        videoPlaylist.value = emptyList()
        videoPlaylistTitle.value = null
        parkedMusic.value = null
        setSleepTimer(null)
    }

    /** La taille de la vidéo en cours (pour [VideoFrames], qui recadre ses photos). */
    internal fun videoSize(): VideoSize? = player?.videoSize

    // ---- Réglages ----

    /** Qualité voulue (null = Auto), gardée pour les vidéos suivantes et entre les lancements. */
    fun setQuality(height: Int?) {
        quality.value = height
        prefs?.edit()?.putInt("qualite", height ?: 0)?.apply()
        val p = player ?: return
        if (nowPlaying.value?.live == true) applyVideoSizeLimit(p, live = true) else rebuild()
    }

    fun setCaption(track: CaptionTrack?) {
        caption.value = track
        captionLanguage = track?.language
        rebuild()
    }

    /**
     * Bouton CC : coupe les sous-titres, ou les active dans la langue la plus probable
     * (celle choisie avant, puis celle du téléphone, puis français, puis anglais).
     * Renvoie false s'il n'y a aucun sous-titre pour cette vidéo.
     */
    fun toggleCaptions(): Boolean {
        if (caption.value != null) {
            setCaption(null)
            return true
        }
        val tracks = captionTracks.value
        if (tracks.isEmpty()) return false
        val wanted = listOfNotNull(captionLanguage, Locale.getDefault().language, "fr", "en")
        setCaption(pickCaption(tracks, wanted) ?: tracks.first())
        return true
    }

    /** Un passage qu'on vient d'envoyer à SponsorBlock : pris en compte tout de suite. */
    fun addSegment(segment: Segment) {
        nowPlaying.value = nowPlaying.value?.let { it.copy(segments = it.segments + segment) }
    }

    fun setAutoSkip(on: Boolean) {
        autoSkip.value = on
        prefs?.edit()?.putBoolean("sponsorblock_auto", on)?.apply()
    }

    fun setSpeed(value: Float) {
        speed.value = value
        if (nowPlaying.value?.live != true) player?.setPlaybackSpeed(value)
    }

    /** Le doigt maintenu sur la vidéo ou le short (2.21.0) : ×2 tant qu'il reste posé. */
    val boosted = MutableStateFlow(false)

    /** [on] : passe en ×2 ; relâché : la vitesse choisie revient. Pas pendant un direct. */
    fun holdSpeed(on: Boolean) {
        val now = nowPlaying.value
        val can = on && now != null && !now.live && !now.audioOnly
        if (boosted.value == can) return
        boosted.value = can
        player?.setPlaybackSpeed(if (can) HOLD_SPEED else if (now?.live == true) 1f else speed.value)
    }

    const val HOLD_SPEED = 2f

    fun setLoop(on: Boolean) {
        loop.value = on
        player?.repeatMode = if (on) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun setAmbient(on: Boolean) {
        ambient.value = on
        prefs?.edit()?.putBoolean("ambiant", on)?.apply()
    }

    /** Renvoie false si l'appareil refuse l'effet audio. */
    fun setStableVolume(on: Boolean): Boolean {
        stableVolume.value = on
        val ok = applyStableVolume()
        stableVolume.value = on && ok
        prefs?.edit()?.putBoolean("volume_stable", stableVolume.value)?.apply()
        return ok
    }

    /** null = arrêter le minuteur. */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) {
            sleepTimer.value = SleepTimer.Off
            return
        }
        sleepTimer.value = SleepTimer.At(SystemClock.elapsedRealtime() + minutes * 60_000L, minutes)
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            player?.pause()
            sleepTimer.value = SleepTimer.Off
        }
    }

    fun setSleepAtEndOfVideo() {
        sleepJob?.cancel()
        sleepTimer.value = SleepTimer.EndOfVideo
    }

    /** Reconstruit la source de la vidéo en cours (autre qualité / sous-titres) en restant au même endroit. */
    private fun rebuild() {
        val p = player ?: return
        val current = info ?: return
        val now = nowPlaying.value ?: return
        if (now.live) return
        val cache = appContext?.let { OfflineCache.cache(it, now.audioOnly) }
        val built = runCatching { MediaSources.build(current, now.audioOnly, quality.value, caption.value, cache) }.getOrNull() ?: return
        val position = p.currentPosition
        val wasPlaying = p.playWhenReady
        playingQuality.value = built.height
        p.setMediaSource(built.source, position)
        p.prepare()
        p.playWhenReady = wasPlaying
    }

    /** En direct (HLS), la qualité se choisit en limitant la taille des pistes vidéo. */
    private fun applyVideoSizeLimit(p: ExoPlayer, live: Boolean) {
        val limit = quality.value
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().apply {
            if (live && limit != null) setMaxVideoSize(Int.MAX_VALUE, limit) else clearVideoSizeConstraints()
        }.build()
    }

    private fun liveQualities(tracks: Tracks): List<Int> =
        tracks.groups
            .filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { group -> (0 until group.length).map { group.getTrackFormat(it) } }
            .mapNotNull { f -> MediaSources.qualityTier(f.width, f.height).takeIf { it > 0 } }
            .distinct()
            .sortedDescending()

    private fun pickCaption(tracks: List<CaptionTrack>, languages: List<String>): CaptionTrack? =
        languages.firstNotNullOfOrNull { lang ->
            tracks.firstOrNull { it.language == lang && !it.auto } ?: tracks.firstOrNull { it.language == lang }
        }

    fun shouldEnterPip(): Boolean = isPlaying.value && nowPlaying.value?.audioOnly == false

    private fun startWatcher() {
        watcher?.cancel()
        watcher = scope.launch {
            while (isActive) {
                val p = player
                val segments = nowPlaying.value?.segments.orEmpty()
                if (p != null && autoSkip.value && p.isPlaying && segments.isNotEmpty()) {
                    val position = p.currentPosition
                    segments.firstOrNull { it.category in AppSettings.skipCategories.value && position >= it.startMs && position < it.endMs - 500 }
                        ?.let { segment ->
                            p.seekTo(segment.endMs)
                            skipped.tryEmit(segment.category)
                        }
                }
                delay(400)
            }
        }
    }
}
