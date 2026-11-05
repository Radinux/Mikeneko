package app.mikeneko.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mikeneko.R
import app.mikeneko.data.SponsorBlock
import app.mikeneko.player.NowPlaying
import app.mikeneko.player.PlayerHolder
import app.mikeneko.settings.AppSettings
import app.mikeneko.ui.components.GlassCircleButton
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Les morceaux communs au lecteur de la page et au plein écran : progression, boutons centraux,
// lecture auto, sous-titres, fondu des commandes.

/** Les commandes sur la vidéo apparaissent et s'effacent en douceur (adouci, plus lent à partir). */
internal val ControlsFadeIn = fadeIn(tween(340, easing = FastOutSlowInEasing))
internal val ControlsFadeOut = fadeOut(tween(480, easing = FastOutSlowInEasing))

/** Position / durée du lecteur partagé, rafraîchies 4 fois par seconde. */
@Stable
class PlaybackProgress {
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var buffered by mutableLongStateOf(0L)

    /** Ce qui est déjà chargé, en fraction de la durée. */
    val bufferedFraction: Float
        get() = if (duration > 0) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f

    val fraction: Float
        get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    /** Le temps à afficher : celui visé pendant un glissé, sinon la position réelle. */
    fun shownPosition(scrub: Float?): Long = scrub?.let { (it * duration).toLong() } ?: position

    fun seekTo(fraction: Float) {
        val target = (fraction * duration).toLong()
        PlayerHolder.seekTo(target)
        position = target
    }
}

@Composable
fun rememberPlaybackProgress(): PlaybackProgress {
    // Partir de la vraie position (sinon « 0:00 » s'affiche une image en ouvrant le lecteur)
    val progress = remember {
        PlaybackProgress().apply {
            val (p, d) = PlayerHolder.progress()
            position = p
            duration = d
        }
    }
    LaunchedEffect(progress) {
        while (true) {
            val (position, duration) = PlayerHolder.progress()
            progress.position = position
            progress.duration = duration
            progress.buffered = PlayerHolder.bufferedPosition()
            delay(250)
        }
    }
    return progress
}

/** Les segments sautés automatiquement, en fractions de la durée (pour la barre de progression). */
internal fun sponsorFractions(info: NowPlaying, duration: Long): List<Pair<Float, Float>> =
    if (duration <= 0) emptyList()
    else info.segments
        .filter { it.category in AppSettings.skipCategories.value }
        .map { it.startMs.toFloat() / duration to it.endMs.toFloat() / duration }

/**
 * Le centre des commandes posées sur la vidéo : vidéo précédente, lecture / pause, vidéo suivante.
 * Suivante = la première suggestion ; précédente = la vidéo regardée juste avant.
 * [onInteract] est appelé à chaque appui (pour relancer le minuteur qui masque les commandes).
 */
@Composable
internal fun TransportControls(
    backdrop: Backdrop,
    wantsToPlay: Boolean,
    waiting: Boolean,
    hasNext: Boolean,
    playSize: Dp,
    skipSize: Dp,
    spacing: Dp,
    modifier: Modifier = Modifier,
    onInteract: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history by PlayerHolder.videoHistory.collectAsState()

    fun switchVideo(load: suspend (Context) -> Unit) {
        onInteract()
        scope.launch {
            runCatching { load(context) }
                .onFailure { Toast.makeText(context, context.getString(R.string.error_play_video, it.message.orEmpty()), Toast.LENGTH_SHORT).show() }
        }
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(spacing), verticalAlignment = Alignment.CenterVertically) {
        GlassCircleButton(backdrop, MkIcons.Previous, stringResource(R.string.video_previous), size = skipSize, enabled = history.isNotEmpty()) {
            switchVideo { PlayerHolder.previousVideo(it) }
        }
        PlayPauseButton(backdrop, wantsToPlay, waiting, size = playSize) {
            PlayerHolder.togglePlay()
            onInteract()
        }
        GlassCircleButton(backdrop, MkIcons.Next, stringResource(R.string.video_next), size = skipSize, enabled = hasNext) {
            switchVideo { PlayerHolder.nextVideo(it) }
        }
    }
}

/**
 * Lecture / pause. L'icône suit ce qui est demandé ([wantsToPlay]), même pendant un chargement
 * (changement de vidéo, saut) : le bouton reste là, entouré d'un anneau qui tourne.
 */
@Composable
private fun PlayPauseButton(backdrop: Backdrop, wantsToPlay: Boolean, waiting: Boolean, size: Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.size(size + 14.dp), contentAlignment = Alignment.Center) {
        GlassCircleButton(
            backdrop,
            if (wantsToPlay) MkIcons.Pause else MkIcons.Play,
            stringResource(if (wantsToPlay) R.string.pause else R.string.play),
            size = size,
            onClick = onClick,
        )
        androidx.compose.animation.AnimatedVisibility(waiting, enter = fadeIn(tween(150)), exit = fadeOut(tween(250))) {
            CircularProgressIndicator(
                Modifier.size(size + 14.dp),
                color = Pelage.Roux,
                strokeWidth = 3.dp,
                trackColor = Color.White.copy(alpha = 0.15f),
            )
        }
    }
}

/**
 * L'interrupteur « Lecture automatique » sur la vidéo (comme celui de YouTube) : une pilule avec un
 * rond qui glisse, orange et ▶ quand c'est activé, sombre et ⏸ sinon. [onTouched] : garder les
 * commandes affichées.
 */
@Composable
internal fun AutoplaySwitch(modifier: Modifier = Modifier, onTouched: () -> Unit) {
    val context = LocalContext.current
    val on by PlayerHolder.autoplay.collectAsState()
    val knob by animateDpAsState(if (on) 22.dp else 0.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "lecture auto")
    val track by animateColorAsState(if (on) Pelage.Roux.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.45f), label = "piste")
    Box(
        modifier
            .size(50.dp, 28.dp)
            .clip(CircleShape)
            .background(track)
            .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .clickable(role = Role.Switch, onClickLabel = stringResource(R.string.autoplay)) {
                PlayerHolder.setAutoplay(!on)
                Toast.makeText(context, context.getString(if (!on) R.string.autoplay_on else R.string.autoplay_off), Toast.LENGTH_SHORT).show()
                onTouched()
            }
            .semantics { stateDescription = context.getString(if (on) R.string.autoplay_on else R.string.autoplay_off) }
            .padding(3.dp),
    ) {
        Box(
            Modifier.offset(x = knob).size(22.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (on) MkIcons.Play else MkIcons.Pause, null, tint = Pelage.Encre, modifier = Modifier.size(13.dp))
        }
    }
}

/** Bouton CC : orange quand les sous-titres sont affichés. */
@Composable
internal fun CaptionsButton(backdrop: Backdrop, on: Boolean, onClick: () -> Unit) {
    GlassCircleButton(
        backdrop,
        MkIcons.Captions,
        stringResource(if (on) R.string.captions_hide else R.string.captions_show),
        size = 44.dp,
        tint = if (on) Pelage.Roux.copy(alpha = 0.7f) else Color(0x40100D0B),
        iconTint = if (on) Pelage.Encre else Color.White,
        onClick = onClick,
    )
}
