package app.mikeneko.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.mikeneko.data.VideoItem
import app.mikeneko.data.sameTrack
import app.mikeneko.player.NowPlaying
import app.mikeneko.player.PlayerHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// L'état de la musique en verre (pilule ↔ fenêtre) et ce qu'elle montre.

/** Hauteur du dock musique (la pilule du morceau en cours, au-dessus du dock). */
val MUSIC_DOCK_HEIGHT = 64.dp

/** Le dock musique se pose au-dessus du dock : 68 dp de dock + 20 dp de marge + un petit écart. */
val MUSIC_DOCK_BOTTOM = 98.dp

/**
 * Le dock musique et sa fenêtre : la même vitre en verre liquide, en pilule au-dessus du dock, qu'on
 * étire vers le haut jusqu'à une grande fenêtre (et qu'on rabat vers le bas). [expand] : 0 = pilule,
 * 1 = fenêtre ; un peu au-delà pendant les rebonds du ressort.
 */
@Stable
class MusicPlayerState(private val scope: CoroutineScope) {
    val expand = Animatable(0f)

    /** Ouverte (ou en train de s'ouvrir) : le retour la referme, les menus de la musique s'affichent. */
    var open by mutableStateOf(false)
        private set
    var queueOpen by mutableStateOf(false)
    var lyricsOpen by mutableStateOf(false)

    fun expand(velocity: Float = 0f) = settle(true, velocity)

    fun collapse(velocity: Float = 0f) {
        if (!open && expand.value == 0f) return
        settle(false, velocity)
    }

    /** [velocity] : vitesse du doigt (en « fenêtres par seconde ») pour que le ressort la continue. */
    internal fun settle(target: Boolean, velocity: Float = 0f) {
        open = target
        if (!target) {
            queueOpen = false
            lyricsOpen = false
            PlayerHolder.settingsPage.value = null
        }
        scope.launch {
            expand.animateTo(
                if (target) 1f else 0f,
                // Un seul petit dépassement, puis la vitre se pose (2.5.3 : avant, elle rebondissait en trop)
                if (target) spring(dampingRatio = 0.8f, stiffness = 80f) else spring(dampingRatio = 0.86f, stiffness = 110f),
                initialVelocity = velocity,
            )
        }
    }

    internal fun dragTo(value: Float) {
        scope.launch { expand.snapTo(value) }
    }

    internal fun stop() {
        scope.launch { expand.stop() }
    }

    /** Hauteur (px) entre la pilule et la fenêtre : fixée par [MusicDockWindow]. */
    internal var travel = 1f

    /**
     * La fenêtre est tirée vers le bas par son défilement (le doigt est dessus). Tant que c'est vrai,
     * la fenêtre reste posée même sous 30 % : sinon Compose ne lui envoie plus le doigt (ni son
     * lâcher), le geste ne finit jamais et la vitre reste figée à mi-course.
     */
    var pulling by mutableStateOf(false)
        private set

    /**
     * La fenêtre défile (artiste, album sous les commandes). Tout en haut, tirer vers le bas la rabat
     * comme avant : ce que le défilement n'utilise pas fait descendre la fenêtre.
     */
    val scrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Fenêtre déjà en train de descendre : remonter le doigt la rouvre avant de faire défiler
            if (!pulling || available.y >= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
            val before = expand.value
            dragTo((before - available.y / travel).coerceAtMost(1f))
            if (expand.value >= 1f) pulling = false
            return Offset(0f, available.y)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
            pulling = true
            dragTo((expand.value - available.y / travel).coerceIn(0f, 1f))
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!pulling) return Velocity.Zero
            pulling = false
            val down = available.y
            settle(if (down > 900f) false else expand.value > 0.75f, velocity = -down / travel)
            return available
        }
    }

    /**
     * Le doigt a quitté la fenêtre. Normalement le défilement a lancé [scrollConnection.onPreFling],
     * qui repose la vitre ; mais un geste annulé (geste système…) ne lance rien. Après une image, si
     * personne ne l'a reposée, on la pose ici (sans élan).
     */
    internal fun endPull() {
        if (!pulling) return
        scope.launch {
            withFrameNanos { } // laisse d'abord passer le lancer du défilement
            if (!pulling) return@launch
            pulling = false
            settle(expand.value > 0.75f)
        }
    }
}

@Composable
fun rememberMusicPlayerState(): MusicPlayerState {
    val scope = rememberCoroutineScope()
    return remember { MusicPlayerState(scope) }
}

/** Le morceau en cours, pour la musique : rien quand c'est une vidéo qui joue (elle a son mini-lecteur). */
@Composable
internal fun musicNow(): NowPlaying? = PlayerHolder.nowPlaying.collectAsState().value?.takeIf { it.audioOnly }

/** Le morceau qu'une vidéo a mis de côté (montré dans la pilule tant que la vidéo est là). */
@Composable
internal fun parkedTrack(): VideoItem? {
    val parked by PlayerHolder.parkedMusic.collectAsState()
    val queue by PlayerHolder.queue.collectAsState()
    return parked?.let { p -> queue.firstOrNull { sameTrack(it.url, p.url) } }
}
