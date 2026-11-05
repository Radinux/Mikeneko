package app.mikeneko.ui

import app.mikeneko.ui.components.DOCK_RAIL_SPACE
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.platform.LocalLayoutDirection
import app.mikeneko.ui.screens.LibraryPage
import app.mikeneko.ui.screens.LibraryOverlay
import app.mikeneko.ui.screens.LibraryContent
import app.mikeneko.ui.screens.LibraryState
import app.mikeneko.ui.components.PlaylistDrawer
import app.mikeneko.ui.screens.ChannelPlaylist
import app.mikeneko.ui.screens.ChannelOverlay
import app.mikeneko.ui.screens.ChannelContent
import app.mikeneko.data.ChannelItem
import app.mikeneko.ui.screens.onUserScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.SideEffect
import app.mikeneko.ui.components.RotationMorph
import android.view.Surface
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.graphicsLayer
import app.mikeneko.ui.components.VideoMorphLayer
import app.mikeneko.ui.components.VideoMorph
import app.mikeneko.ui.components.VideoMenuSheet
import app.mikeneko.ui.components.VideoMenu
import app.mikeneko.ui.screens.SettingsContent
import app.mikeneko.settings.AppSettings
import app.mikeneko.R
import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.mikeneko.player.PlayerHolder
import app.mikeneko.ui.components.FloatingDock
import app.mikeneko.ui.components.FloatingMiniPip
import app.mikeneko.ui.components.PlayerSettingsSheet
import app.mikeneko.ui.components.CommentsPanel
import app.mikeneko.data.Comments
import app.mikeneko.data.VideoItem
import app.mikeneko.ui.components.rememberPipPlacement
import app.mikeneko.ui.components.VideoSurface
import app.mikeneko.ui.glass.backdropSource
import app.mikeneko.ui.glass.rememberBackdrop
import app.mikeneko.ui.screens.DownloadsContent
import app.mikeneko.ui.screens.FullscreenPlayer
import app.mikeneko.ui.screens.HomeContent
import app.mikeneko.ui.screens.HomeOverlay
import app.mikeneko.ui.screens.HomeState
import app.mikeneko.ui.screens.PlayerContent
import app.mikeneko.ui.screens.RadioContent
import app.mikeneko.ui.screens.RadioOverlay
import app.mikeneko.ui.screens.RadioState
import app.mikeneko.ui.screens.MusicSheet
import app.mikeneko.ui.screens.MusicSheetHost
import app.mikeneko.ui.screens.RadioPage
import app.mikeneko.ui.screens.MusicDockWindow
import app.mikeneko.ui.screens.MusicPlayerSheets
import app.mikeneko.ui.screens.rememberMusicPlayerState
import app.mikeneko.ui.screens.SleepReminder
import app.mikeneko.ui.screens.SubscriptionsContent
import app.mikeneko.ui.theme.Pelage
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Structure de chaque écran :
 *  1. le contenu qui défile, enregistré comme « backdrop » ;
 *  2. par-dessus, ce qui est en verre (header, dock, mini-lecteur), qui réfracte le contenu.
 */
@Composable
fun MikenekoRoot(
    inPip: Boolean,
    links: StateFlow<String?>,
    onLinkHandled: () -> Unit,
    /** Renvoie vrai si l'écran va tourner pour ça (bouton plein écran). */
    onFullscreenChanged: (enabled: Boolean, fromRotation: Boolean) -> Boolean,
    /** Sur l'écran d'une vidéo : Android ne doit pas animer la rotation (c'est la vidéo seule qui tourne). */
    onVideoScreen: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var route by rememberSaveable { mutableStateOf(Route.Accueil) }
    var beforePlayer by rememberSaveable { mutableStateOf(Route.Accueil) }
    var dockVisible by remember { mutableStateOf(true) }
    val keyboard = LocalSoftwareKeyboardController.current
    /** La chaîne ouverte depuis l'onglet Abonnements (null = la liste). */
    var subsChannel by remember { mutableStateOf<ChannelItem?>(null) }
    var subsReset by remember { mutableIntStateOf(0) }
    val library = remember { LibraryState() }
    var fullscreen by remember { mutableStateOf(false) }
    var downloadLink by remember { mutableStateOf("") }
    /** Le dock musique et sa fenêtre en verre (ouverte en tirant le dock vers le haut). */
    val music = rememberMusicPlayerState()
    val home = remember { HomeState() }
    val radio = remember { RadioState(context) }
    val pipPlacement = rememberPipPlacement() // le mini-lecteur garde sa place d'un écran à l'autre
    val backdrop = rememberBackdrop()
    val now by PlayerHolder.nowPlaying.collectAsState()
    val link by links.collectAsState()

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun go(target: Route) {
        route = target
        dockVisible = true
    }

    /**
     * Un onglet du dock touché : Accueil ramène au fil « Pour toi » (hors recherche, ou en haut s'il
     * y est déjà), Abonnements à la liste de toutes les chaînes, Musique et Bibliothèque à leur accueil.
     */
    fun selectTab(target: Route) {
        when (target) {
            Route.Accueil -> if (!home.showFeed() && route == Route.Accueil) home.scrollToTop++
            Route.Abonnements -> {
                subsChannel = null
                subsReset++
            }
            Route.Bibliotheque -> library.page = null
            // Musique : retour à son accueil (pages d'artiste / d'album / de playlist et recherche fermées)
            Route.Radio -> {
                radio.pages.clear()
                if (radio.searching) radio.closeSearch()
            }
            else -> Unit
        }
        go(target)
    }

    fun openVideo(url: String) {
        if (route != Route.Lecteur) beforePlayer = route
        route = Route.Lecteur
        scope.launch {
            runCatching { PlayerHolder.play(context, url, audioOnly = false) }
                .onFailure { toast(context.getString(R.string.error_play_video, it.message.orEmpty())) }
        }
    }

    /** Retour à l'écran d'avant : la vidéo rétrécit jusqu'au mini-lecteur. */
    fun minimizePlayer() {
        if (now?.audioOnly == false) VideoMorph.start(VideoMorph.playerBounds, toMini = true)
        route = beforePlayer
    }

    /** Depuis le lecteur : la vidéo passe en mini-lecteur et la page de sa chaîne s'ouvre. */
    fun openChannelFromPlayer(channel: ChannelItem) {
        val back = beforePlayer
        minimizePlayer()
        if (back == Route.Abonnements) {
            subsChannel = channel
        } else {
            home.channel = channel
            route = Route.Accueil
        }
    }

    fun toggleFullscreen() {
        val target = !fullscreen
        val rotates = onFullscreenChanged(target, false)
        // L'écran va tourner (paysage forcé / portrait) : le plein écran s'affiche ou se retire avec
        // la rotation (voir plus bas), pour ne jamais montrer un écran dans le mauvais sens. Au cas
        // où l'écran ne tournerait pas, on bascule quand même au bout d'une seconde. Sinon (téléphone
        // tenu en paysage, 2.0.0) : tout de suite, et on reste en paysage (page lecteur en paysage).
        if (!rotates) {
            // Téléphone en paysage : pas de rotation, donc un zoom entre la page et le plein écran
            if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && route == Route.Lecteur) RotationMorph.startZoom(toFullscreen = target)
            fullscreen = target
            return
        }
        scope.launch {
            delay(1_000)
            if (fullscreen != target) fullscreen = target
        }
    }

    // Tourner le téléphone sur une vidéo = plein écran ; revenir en portrait = on en sort.
    // (Après le bouton, l'orientation est forcée : la config ne change qu'une fois le téléphone tourné.)
    val orientation = LocalConfiguration.current.orientation
    // Paysage : le dock devient une colonne à gauche, le contenu se décale à sa droite
    val landscape = orientation == Configuration.ORIENTATION_LANDSCAPE
    val railShown = landscape && route != Route.Lecteur && route != Route.Reglages
    val layoutDirection = LocalLayoutDirection.current
    val safeSides = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
    val contentInset = if (railShown) {
        Modifier.padding(start = safeSides.calculateStartPadding(layoutDirection) + DOCK_RAIL_SPACE, end = safeSides.calculateEndPadding(layoutDirection))
    } else {
        Modifier
    }
    // Décidé pendant la composition, dans la même image que la rotation (avec la rotation non animée
    // d'Android sur le lecteur, une image du lecteur portrait étiré en paysage se verrait sinon).
    // Seulement quand l'orientation change : le bouton plein écran, lui, bascule avant la rotation.
    val videoScreen = route == Route.Lecteur && now?.audioOnly == false
    val rotationNow = LocalView.current.display?.rotation ?: Surface.ROTATION_0
    val turn = remember { intArrayOf(orientation, rotationNow) }
    var announce by remember { mutableStateOf<Boolean?>(null) }
    if (turn[0] != orientation) {
        val landscapeNow = orientation == Configuration.ORIENTATION_LANDSCAPE
        val wasFullscreen = fullscreen
        if (landscapeNow && !fullscreen && videoScreen) {
            fullscreen = true
            announce = true
        } else if (!landscapeNow && fullscreen) {
            fullscreen = false
            announce = false
        }
        // Seule la vidéo tourne (voir RotationMorph), quand on entre ou sort du plein écran ; entre les
        // pages portrait et paysage du lecteur, c'est la rotation habituelle d'Android
        if (wasFullscreen != fullscreen) RotationMorph.start(turn[1] * 90, rotationNow * 90, toFullscreen = fullscreen)
        turn[0] = orientation
    }
    turn[1] = rotationNow
    SideEffect {
        announce?.let {
            announce = null
            onFullscreenChanged(it, true)
        }
    }
    LaunchedEffect(RotationMorph.token) { RotationMorph.animate() }
    // Pas d'animation d'Android quand la rotation fait entrer ou sortir du plein écran (portrait ↔ plein
    // écran) ; la page lecteur en paysage, elle, tourne normalement vers le portrait
    val videoRotation = fullscreen || (videoScreen && orientation != Configuration.ORIENTATION_LANDSCAPE)
    LaunchedEffect(videoRotation) { onVideoScreen(videoRotation) }

    LaunchedEffect(link) {
        link?.let {
            openVideo(it)
            onLinkHandled()
        }
    }

    // Le menu de réglages appartient à l'écran du lecteur : on le ferme en changeant d'écran
    LaunchedEffect(route) {
        PlayerHolder.settingsPage.value = null
        Comments.open.value = false
        MusicSheet.open.value = null
        VideoMenu.target.value = null
        music.collapse() // changer d'onglet referme la fenêtre de la musique
    }

    LaunchedEffect(Unit) {
        PlayerHolder.skipped.collect { toast(context.getString(R.string.sponsor_skipped)) }
    }

    // Mises à jour depuis GitHub : une vérification par jour au lancement ; une nouvelle version se
    // télécharge en fond et s'installe toute seule quand on quitte l'appli (voir AppUpdates)
    LaunchedEffect(Unit) {
        app.mikeneko.update.AppUpdates.checkIfDue(context)
        app.mikeneko.update.AppUpdates.state.collect { update ->
            if (update is app.mikeneko.update.AppUpdates.State.Ready) {
                val key = if (app.mikeneko.update.AppUpdates.canInstall(context)) R.string.update_ready else R.string.update_ready_allow
                toast(context.getString(key, update.release.version))
            }
        }
    }

    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Avant les « return » ci-dessous : le minuteur survit au plein écran et au PiP
    val sleepReminder by AppSettings.sleepReminder.collectAsState()
    val sleepMinutes by AppSettings.sleepMinutes.collectAsState()
    if (sleepReminder) SleepReminder(suppressed = inPip, firstAfterMinutes = sleepMinutes)

    // Vrai PiP système (on a quitté l'app pendant une vidéo) : juste la vidéo
    if (inPip) {
        Box(Modifier.fillMaxSize().background(Color.Black)) { VideoSurface(Modifier.fillMaxSize()) }
        return
    }

    // Plein écran : la vidéo et ses commandes classiques
    if (fullscreen) {
        BackHandler { toggleFullscreen() }
        FullscreenPlayer(onExit = ::toggleFullscreen)
        return
    }

    BackHandler(enabled = route != Route.Accueil) {
        if (route == Route.Lecteur) minimizePlayer() else route = Route.Accueil
    }
    // Sur l'accueil : d'abord fermer la recherche / revenir à « Pour toi » / remonter, puis quitter
    BackHandler(enabled = route == Route.Accueil && home.canGoBack) { home.goBack() }
    // Sur la Radio : d'abord fermer la page ouverte (album, playlist…) ou la recherche
    BackHandler(enabled = route == Route.Radio && radio.canGoBack) { radio.goBack() }
    // Sur les abonnements : d'abord refermer la page de chaîne ouverte
    BackHandler(enabled = route == Route.Abonnements && subsChannel != null) { subsChannel = null }
    // Dans la Bibliothèque : d'abord revenir à son accueil
    BackHandler(enabled = route == Route.Bibliotheque && library.page != null) { library.page = null }
    // Une playlist ouverte dans une page de chaîne : le retour la referme d'abord
    val channelPlaylist by ChannelPlaylist.open.collectAsState()
    BackHandler(enabled = channelPlaylist != null && route != Route.Lecteur) { ChannelPlaylist.open.value = null }

    // Une vidéo avait mis la musique de côté : au retour dans Musique, elle revient en pause là où
    // elle en était, seulement s'il n'y a plus de vidéo dans le lecteur (sinon la pilule montre le
    // morceau, et ▶ ou la fenêtre le remettent : pas de rechargement à chaque changement d'onglet)
    val parkedMusic by PlayerHolder.parkedMusic.collectAsState()
    val anyPlaying by PlayerHolder.isPlaying.collectAsState()
    val videoLoaded = now?.audioOnly == false
    LaunchedEffect(route, parkedMusic, videoLoaded) {
        if (route == Route.Radio && parkedMusic != null && !videoLoaded) {
            PlayerHolder.launchPlayback { PlayerHolder.restoreParkedMusic(context) }
        }
    }

    // Le mini-lecteur vidéo n'est pas dans Musique (1.19.6) : en y entrant, la vidéo se met en pause
    // et se cache ; en ressortant, elle revient en pause là où elle en était (rechargée seulement si
    // un morceau l'a remplacée, et pas tant qu'une musique joue : on ne la coupe pas)
    val parkedVideo by PlayerHolder.parkedVideo.collectAsState()
    LaunchedEffect(route) {
        if (route == Route.Radio) PlayerHolder.parkVideo()
    }
    LaunchedEffect(route, parkedVideo, anyPlaying) {
        if (route != Route.Radio && route != Route.Lecteur && parkedVideo != null && !(anyPlaying && now?.audioOnly == true)) {
            PlayerHolder.launchPlayback { PlayerHolder.restoreParkedVideo(context) }
        }
    }

    // Changement d'onglet : le nouvel écran arrive en glissant un peu dans le sens du dock, avec un
    // petit rebond, et apparaît en fondu (l'ancien disparaît tout de suite : un seul écran à la fois,
    // ça reste fluide). Pas vers / depuis le lecteur : là, c'est la vidéo qui vole (VideoMorph).
    val lastRoute = remember { arrayOf(route) }
    val fromRoute = lastRoute[0]
    val animateEnter = fromRoute != route && fromRoute != Route.Lecteur && route != Route.Lecteur
    val enter = remember(route) { Animatable(if (animateEnter) 0f else 1f) }
    val enterDirection = remember(route) { if (route.ordinal >= fromRoute.ordinal) 1f else -1f }
    SideEffect { lastRoute[0] = route }
    LaunchedEffect(enter) { enter.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 340f)) }
    val enterLayer = Modifier.graphicsLayer {
        val p = enter.value
        translationX = (1f - p) * enterDirection * 56.dp.toPx()
        val grow = 0.965f + 0.035f * p
        scaleX = grow
        scaleY = grow
        alpha = (p * 1.7f).coerceIn(0f, 1f)
    }

    Box(Modifier.fillMaxSize().background(Pelage.Nuit)) {
        // Le fond est DANS la source : le verre recouvre alors vraiment ce qui est derrière
        // (sinon le contenu net transparaît sous le verre, en double ou à travers le flou)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = VideoMorph.pageAlpha() } // fondu pendant le morph de la vidéo
                .backdropSource(backdrop)
                .background(Pelage.Nuit),
        ) {
          Box(Modifier.fillMaxSize().then(contentInset).then(enterLayer)) {
            when (route) {
                // Faire défiler la page range les suggestions de recherche et le clavier
                Route.Accueil -> Box(Modifier.fillMaxSize().onUserScroll({ home.suggesting }) { home.dismissSuggestions(); keyboard?.hide() }) {
                    HomeContent(home, onOpen = { openVideo(it.url) })
                }
                // Une chaîne ouverte depuis les abonnements (par-dessus la liste)
                Route.Abonnements -> subsChannel?.let { ChannelContent(it, onOpen = { video -> openVideo(video.url) }) }
                    ?: SubscriptionsContent(onOpen = { openVideo(it.url) }, onOpenChannel = { subsChannel = it }, onOpenSettings = { go(Route.Reglages) }, reset = subsReset)
                Route.Radio -> Box(Modifier.fillMaxSize().onUserScroll({ radio.suggesting }) { radio.dismissSuggestions(); keyboard?.hide() }) { RadioContent(radio, onPlayStations = { list, index ->
                    PlayerHolder.launchPlayback(onError = { toast(context.getString(R.string.error_play_radio, it.message.orEmpty())) }) {
                        PlayerHolder.playQueue(context, list, index)
                    }
                    music.expand() // une station touchée : la fenêtre s'ouvre
                }) }
                Route.Bibliotheque -> LibraryContent(library, downloadLink, onOpen = { openVideo(it.url) })
                Route.Reglages -> SettingsContent(onBack = { go(Route.Accueil) }, onOpenSubscriptions = { go(Route.Abonnements) })
                Route.Lecteur -> PlayerContent(
                    onOpenChannel = ::openChannelFromPlayer,
                    onMinimize = ::minimizePlayer,
                    onDownload = { downloadLink = it; library.page = LibraryPage.Downloads; go(Route.Bibliotheque) },
                    onRadio = {
                        // La radio de cette vidéo seule : pas de « suivant » vers d'anciennes stations
                        // (un morceau, pas un direct : son mix enchaîne ensuite)
                        now?.let {
                            val item = VideoItem(it.url, it.title, it.channel, it.thumbnail, isLive = it.live)
                            PlayerHolder.launchPlayback(onError = { e -> toast(context.getString(R.string.error_play_radio, e.message.orEmpty())) }) {
                                PlayerHolder.playQueue(context, listOf(item), 0, mix = !item.isLive)
                            }
                        }
                        go(Route.Radio)
                    },
                    onFullscreen = ::toggleFullscreen,
                    onOpen = { openVideo(it.url) },
                )
            }
          }
        }

        // Le header en verre arrive avec son écran
        Box(Modifier.fillMaxSize().then(contentInset).then(enterLayer)) {
        when (route) {
            Route.Accueil -> HomeOverlay(home, backdrop, onSettings = { go(Route.Reglages) })
            Route.Radio -> RadioOverlay(state = radio, backdrop = backdrop)
            Route.Abonnements -> subsChannel?.let { ChannelOverlay(it, backdrop, onBack = { subsChannel = null }) }
            Route.Bibliotheque -> LibraryOverlay(library, backdrop)
            Route.Lecteur -> {
                PlaylistDrawer(backdrop, onOpen = { openVideo(it.url) }) // playlist et suggestions, sous les commentaires
                CommentsPanel(backdrop)
                PlayerSettingsSheet(backdrop) // au-dessus des commentaires
            }
            else -> Unit
        }
        }

        // Le dock musique : toujours sur la page Musique, et sur les autres onglets quand de la musique
        // joue. On le tire vers le haut (ou on le touche) : il s'étire en fenêtre, sans changer d'onglet
        val showDock = route != Route.Lecteur && route != Route.Reglages
        // Le dock musique ne vit que dans l'onglet Musique (la musique continue ailleurs, sans lui)
        val showMusic = showDock && route == Route.Radio
        LaunchedEffect(showMusic) { if (!showMusic) music.collapse() }
        if (showMusic) {
            MusicDockWindow(
                music,
                backdrop,
                onDownload = { downloadLink = it; library.page = LibraryPage.Downloads; go(Route.Bibliotheque) },
                // Depuis le lecteur : la page de l'artiste / de l'album s'ouvre dans Musique, la fenêtre se rabat
                onOpenArtist = { radio.pages.add(RadioPage.Artist(it)); music.collapse() },
                onOpenAlbum = { radio.pages.add(RadioPage.Album(it)); music.collapse() },
            )
        }

        if (showDock) {
            if (landscape) {
                FloatingDock(
                    backdrop = backdrop,
                    current = route,
                    visible = dockVisible,
                    modifier = Modifier.align(Alignment.CenterStart).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)).padding(start = 12.dp),
                    vertical = true,
                    onSelect = ::selectTab,
                )
            } else {
                FloatingDock(
                    backdrop = backdrop,
                    current = route,
                    visible = dockVisible,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                    onSelect = ::selectTab,
                )
            }
        }

        val pipBottom by animateDpAsState(if (dockVisible && !landscape) 106.dp else 28.dp, label = "pip") // au-dessus du dock (ou de la barre radio)
        if (now?.audioOnly == false && route != Route.Lecteur && route != Route.Radio) { // pas dans Musique
            FloatingMiniPip(
                placement = pipPlacement,
                bottomMargin = pipBottom,
                onExpand = {
                    VideoMorph.start(VideoMorph.miniBounds, toMini = false) // le mini-lecteur grandit
                    beforePlayer = route
                    route = Route.Lecteur
                },
                onClose = { PlayerHolder.stop() },
            )
        }

        // Le morph mini-lecteur ↔ grand lecteur, au-dessus de tout
        VideoMorphLayer()

        // Les panneaux de la musique (file, paroles, minuteur) et le menu ⋮ des morceaux :
        // après le dock, pour passer au-dessus
        if (showMusic) MusicPlayerSheets(music, backdrop)
        if (route == Route.Radio || music.open) {
            MusicSheetHost(backdrop, onPlaylistDeleted = { id -> radio.pages.removeAll { it == RadioPage.MyPlaylist(id) } })
        }

        // Menu ⋮ des vidéos (accueil, chaînes, Bibliothèque, lecteur) et sa barre « Annuler » : après le
        // mini-lecteur, pour passer au-dessus (sinon il peut cacher le bouton « Annuler »)
        if (route != Route.Radio && route != Route.Reglages) {
            VideoMenuSheet(
                backdrop,
                onDownload = { downloadLink = it; library.page = LibraryPage.Downloads; go(Route.Bibliotheque) },
                bottomSpace = if (dockVisible) 100.dp else 20.dp,
            )
        }

    }
}
