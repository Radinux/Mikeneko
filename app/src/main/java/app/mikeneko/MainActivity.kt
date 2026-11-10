package app.mikeneko

import app.mikeneko.update.Notifier
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.util.Rational
import android.view.OrientationEventListener
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.mikeneko.data.PlaylistImport
import app.mikeneko.data.Downloads
import app.mikeneko.data.youtubeLinkIn
import app.mikeneko.player.PlaybackService
import app.mikeneko.player.PlayerHolder
import app.mikeneko.player.PlaylistDownloads
import app.mikeneko.settings.AppLocale
import app.mikeneko.settings.AppSettings
import app.mikeneko.ui.MikenekoRoot
import app.mikeneko.ui.components.VideoMenu
import app.mikeneko.ui.fixedSpeedRecomposer
import app.mikeneko.ui.screens.MusicSheet
import app.mikeneko.ui.theme.MikenekoTheme
import app.mikeneko.update.AppUpdates
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val inPip = MutableStateFlow(false)
    private val links = MutableStateFlow<String?>(null)
    private var controller: ListenableFuture<MediaController>? = null

    /** La langue choisie dans les réglages (sinon celle du téléphone). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        AppLocale.overrideConfiguration(newBase)?.let { applyOverrideConfiguration(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            links.value = linkFrom(intent)
            if (intent?.getBooleanExtra(Notifier.EXTRA_UPDATE, false) == true) AppUpdates.check(this) // notification de mise à jour
            // Nouvelle ouverture : aucun menu resté ouvert de la fois d'avant
            MusicSheet.open.value = null
            VideoMenu.target.value = null
        }

        if (Build.VERSION.SDK_INT >= 31) {
            // PiP automatique quand on quitte l'app pendant une vidéo. Android lit ce réglage
            // AVANT onPause : il faut donc le tenir à jour dès que la lecture change.
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    combine(PlayerHolder.isPlaying, PlayerHolder.nowPlaying, AppSettings.autoPip) { _, _, auto -> auto && PlayerHolder.shouldEnterPip() }
                        .distinctUntilChanged()
                        .collect { setPictureInPictureParams(pipParams(autoEnter = true)) }
                }
            }
        }

        // Barre d'état masquée ou non (réglage), réappliqué dès qu'on le change
        lifecycleScope.launch { AppSettings.hideStatusBar.collect { applySystemBars() } }

        // Pas de mise en veille pendant une vidéo (la radio, elle, laisse l'écran s'éteindre)
        lifecycleScope.launch {
            combine(PlayerHolder.isPlaying, PlayerHolder.nowPlaying) { playing, now -> playing && now?.audioOnly == false }
                .distinctUntilChanged()
                .collect { keepOn ->
                    if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
        }

        // Animations à la même vitesse sur tous les téléphones (voir AnimationSpeed.kt)
        setContent(parent = fixedSpeedRecomposer()) {
            val pip by inPip.collectAsState()
            MikenekoTheme {
                MikenekoRoot(
                    inPip = pip,
                    links = links,
                    onLinkHandled = { links.value = null },
                    onFullscreenChanged = ::applyFullscreen,
                    onVideoScreen = ::setVideoRotation,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        linkFrom(intent)?.let { links.value = it }
        if (intent.getBooleanExtra(Notifier.EXTRA_UPDATE, false)) AppUpdates.check(this)
    }

    // ---- Inactive 15 min en arrière-plan : l'appli se vide ----

    /** Depuis quand l'appli, en arrière-plan, ne fait plus rien (ni lecture, ni téléchargement). 0 = au premier plan. */
    private var idleSince = 0L
    private var idleWatch: Job? = null

    private fun busy() = PlayerHolder.isPlaying.value ||
        Downloads.tasks.value.any { it.status == Downloads.Status.EN_COURS || it.status == Downloads.Status.EN_ATTENTE } ||
        PlaylistDownloads.busy || PlaylistImport.busy

    /** En arrière-plan : toutes les minutes, on regarde si l'appli travaille encore ; 15 min sans rien → on vide. */
    private fun watchIdle() {
        idleSince = System.currentTimeMillis()
        idleWatch?.cancel()
        idleWatch = lifecycleScope.launch {
            while (true) {
                delay(60_000)
                val now = System.currentTimeMillis()
                if (busy()) {
                    idleSince = now
                } else if (now - idleSince >= IDLE_CLEAR_MS) {
                    // Comme retirer l'appli des applis récentes : tout s'arrête, la mémoire est libérée
                    PlayerHolder.reset()
                    finishAndRemoveTask()
                    Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 500)
                    break
                }
            }
        }
    }

    /**
     * Retour au premier plan. Si le téléphone a gelé l'appli (le minuteur n'a pas pu tourner) et qu'elle
     * dormait depuis plus de 15 min sans rien faire, on la vide maintenant : elle repart de zéro.
     */
    private fun stopWatchingIdle(): Boolean {
        idleWatch?.cancel()
        idleWatch = null
        val stale = idleSince > 0 && System.currentTimeMillis() - idleSince >= IDLE_CLEAR_MS && !busy()
        idleSince = 0L
        if (stale) {
            PlayerHolder.reset()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }
        return stale
    }

    override fun onStart() {
        super.onStart()
        if (stopWatchingIdle()) return
        // Un téléchargement de playlist figé pendant que l'appli était quittée repart
        PlaylistDownloads.resume()
        // Une nouvelle version sur GitHub ? (au lancement et au retour dans l'appli, toutes les 4 h au plus)
        AppUpdates.checkIfDue(this)
        // Se connecter au service : c'est ce qui affiche les contrôles dans la notification
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controller = MediaController.Builder(this, token).buildAsync()
    }

    override fun onStop() {
        // onStop = plus rien de visible (le PiP ne déclenche pas onStop : la vidéo y continue,
        // mais fermer la fenêtre PiP, si). Pas pendant une simple rotation.
        if (!isChangingConfigurations) {
            PlayerHolder.pauseVideoInBackground()
            watchIdle()
            // Une mise à jour téléchargée s'installe maintenant (elle ferme l'appli), sauf si une musique
            // joue en fond (les vidéos viennent d'être mises en pause juste au-dessus)
            val musicPlaying = PlayerHolder.nowPlaying.value?.audioOnly == true && PlayerHolder.isPlaying.value
            AppUpdates.installIfReady(this, playing = musicPlaying)
        }
        controller?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onStop()
    }

    private companion object {
        const val IDLE_CLEAR_MS = 15 * 60_000L
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31 && AppSettings.autoPip.value && PlayerHolder.shouldEnterPip()) {
            runCatching { enterPictureInPictureMode(pipParams(autoEnter = false)) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
    }

    private fun pipParams(autoEnter: Boolean): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setAutoEnterEnabled(autoEnter && AppSettings.autoPip.value && PlayerHolder.shouldEnterPip())
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    /**
     * Plein écran. [fromRotation] : déclenché parce qu'on a tourné le téléphone (rien à forcer).
     * Sinon (bouton), on force le paysage / le portrait, puis on rend la main à la rotation
     * automatique dès que le téléphone est vraiment tenu dans ce sens (comme YouTube).
     */
    private fun applyFullscreen(enabled: Boolean, fromRotation: Boolean): Boolean {
        // Renvoie vrai si l'écran va tourner (le plein écran s'affichera ou se retirera avec la rotation)
        var rotates = false
        if (!fromRotation) {
            val landscapeNow = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            when {
                enabled -> {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    releaseWhen = Hold.LANDSCAPE
                    rotates = !landscapeNow
                }
                // Le téléphone est tenu en paysage (d'après le capteur, même si la rotation automatique
                // d'Android est coupée) : on reste en paysage, sur la page lecteur, jusqu'à ce qu'on le
                // redresse (2.9.0 ; avant, il repassait souvent en portrait)
                physical == Hold.LANDSCAPE -> {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    releaseWhen = Hold.UNTIL_PORTRAIT
                    rotates = !landscapeNow
                }
                // Plein écran forcé alors que le téléphone est resté droit : retour au portrait
                releaseWhen == Hold.LANDSCAPE || !landscapeNow || physical == Hold.PORTRAIT -> {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    releaseWhen = Hold.PORTRAIT
                    rotates = landscapeNow
                }
                // Le téléphone est vraiment tenu en paysage : on y reste (page lecteur en paysage, 2.0.0)
                else -> releaseOrientation()
            }
        }
        fullscreen = enabled
        applySystemBars()
        return rotates
    }

    private var fullscreen = false

    /**
     * Sur l'écran d'une vidéo, Android ne fait pas tourner toute l'interface : la nouvelle orientation
     * s'affiche d'un coup et c'est la vidéo seule qui pivote (RotationMorph). Ailleurs, la rotation
     * habituelle d'Android.
     */
    private fun setVideoRotation(videoScreen: Boolean) {
        val wanted = if (videoScreen) WindowManager.LayoutParams.ROTATION_ANIMATION_JUMPCUT else WindowManager.LayoutParams.ROTATION_ANIMATION_ROTATE
        val attributes = window.attributes
        if (attributes.rotationAnimation == wanted) return
        attributes.rotationAnimation = wanted
        window.attributes = attributes
    }

    /**
     * Barres du téléphone : tout est masqué en plein écran ; sinon, la barre d'état (heure,
     * batterie, notifications) l'est aussi si le réglage est activé. Un glissé depuis le bord
     * les fait réapparaître un instant. La barre de navigation (gestes) reste hors plein écran.
     */
    private fun applySystemBars() {
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        when {
            fullscreen -> bars.hide(WindowInsetsCompat.Type.systemBars())
            AppSettings.hideStatusBar.value -> {
                bars.show(WindowInsetsCompat.Type.navigationBars())
                bars.hide(WindowInsetsCompat.Type.statusBars())
            }
            else -> bars.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // De retour (notification, autre appli…) : Android a pu réafficher la barre d'état
        if (hasFocus) applySystemBars()
    }

    // ---- Rotation : rendre la main au capteur une fois le téléphone tourné ----

    /**
     * PORTRAIT / LANDSCAPE : forcé dans ce sens, rendu au capteur quand le téléphone y est vraiment.
     * UNTIL_PORTRAIT : paysage gardé (sortie du plein écran en tenant le téléphone en paysage), rendu
     * quand on le redresse.
     */
    private enum class Hold { PORTRAIT, LANDSCAPE, UNTIL_PORTRAIT }
    private var releaseWhen: Hold? = null

    /** Comment le téléphone est vraiment tenu (capteur) ; null : inconnu ou à plat. */
    private var physical: Hold? = null

    private val orientationListener by lazy {
        object : OrientationEventListener(this) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees < 0) return
                val portrait = degrees <= 25 || degrees >= 335
                val landscape = degrees in 65..115 || degrees in 245..295
                if (portrait) physical = Hold.PORTRAIT else if (landscape) physical = Hold.LANDSCAPE
                when (releaseWhen) {
                    // Si la rotation automatique d'Android est coupée, on garde le paysage forcé
                    Hold.LANDSCAPE -> if (landscape && autoRotateEnabled()) releaseOrientation()
                    Hold.PORTRAIT, Hold.UNTIL_PORTRAIT -> if (portrait) releaseOrientation()
                    null -> Unit
                }
            }
        }
    }

    private fun releaseOrientation() {
        releaseWhen = null
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun autoRotateEnabled(): Boolean =
        runCatching { Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 1 }.getOrDefault(false)

    override fun onResume() {
        super.onResume()
        if (orientationListener.canDetectOrientation()) orientationListener.enable()
    }

    override fun onPause() {
        orientationListener.disable()
        super.onPause()
    }

    private fun linkFrom(intent: Intent?): String? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.dataString
        Intent.ACTION_SEND -> youtubeLinkIn(intent.getStringExtra(Intent.EXTRA_TEXT))
        else -> null
    }
}
