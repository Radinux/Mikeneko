package app.mikeneko.ui.screens

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.data.VideoItem
import app.mikeneko.data.sameTrack
import app.mikeneko.player.PlayerHolder
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.GlassTextShadow
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.Rounded
import coil3.compose.AsyncImage
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// La pilule de la musique : pochette, titre, lecture / suivant.

/**
 * Le contenu de la pilule : pochette, titre, lecture / suivant. Glisser à gauche / à droite change
 * de morceau (le titre suit le doigt).
 */
@Composable
internal fun MusicDockRow(hideCover: () -> Boolean = { false }) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Une vidéo qui joue n'est pas de la musique : on montre le morceau qu'elle a mis de côté (s'il y en a)
    val now = musicNow()
    val parked = if (now == null) parkedTrack() else null
    val playing = PlayerHolder.isPlaying.collectAsState().value && now != null
    val queue by PlayerHolder.queue.collectAsState()
    val queueIndex by PlayerHolder.queueIndex.collectAsState()
    val loading = PlayerHolder.loading.collectAsState().value && now != null
    val haptic = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    val canSwipe = now != null && queue.size > 1
    /** Décalage horizontal du titre pendant un glissé (la pochette, elle, reste en place). */
    val swipe = remember { Animatable(0f) }
    /** La largeur de la zone du titre : le titre voisin arrive juste à côté. */
    var textWidth by remember { mutableFloatStateOf(0f) }
    /** Le morceau sur lequel on vient de glisser, montré tout de suite (le temps que la file suive). */
    var landed by remember { mutableStateOf<VideoItem?>(null) }

    // Pendant le chargement du morceau suivant, on montre déjà son nom et sa pochette
    val pending = if (loading) queue.getOrNull(queueIndex)?.takeIf { it.url != now?.url } else null
    LaunchedEffect(now?.url, pending?.url, landed) {
        val target = landed ?: return@LaunchedEffect
        if (sameTrack(now?.url, target.url) || sameTrack(pending?.url, target.url)) landed = null
        else { delay(2_000); landed = null } // au cas où la file ne suivrait pas
    }
    val shownTitle = landed?.title ?: pending?.title ?: now?.title ?: parked?.title
    val shownThumbnail = landed?.thumbnail ?: pending?.thumbnail ?: now?.thumbnail ?: parked?.thumbnail
    val shownSubtitle = landed?.let { stringResource(R.string.radio_loading_suffix, it.channel) }
        ?: pending?.let { stringResource(R.string.radio_loading_suffix, it.channel) }
        ?: now?.let { if (it.live) stringResource(R.string.radio_live_suffix, it.channel) else it.channel }
        ?: parked?.channel
    // Les voisins dans la file (elle boucle) : le titre suivant arrive dès qu'on commence à glisser.
    // Vers la droite après 3 s de lecture, « précédent » remet le morceau au début : c'est lui qu'on montre.
    val index = (landed ?: pending)?.let { t -> queue.indexOfFirst { sameTrack(it.url, t.url) } }?.takeIf { it >= 0 } ?: queueIndex
    val nextItem = if (canSwipe) queue.getOrNull((index + 1) % queue.size) else null
    val previousItem = when {
        !canSwipe -> null
        landed == null && pending == null && PlayerHolder.progress().first > 3_000 -> now?.let { VideoItem(it.url, it.title, it.channel, it.thumbnail) }
        else -> queue.getOrNull((index - 1 + queue.size) % queue.size)
    }
    val nextLabel = stringResource(R.string.radio_next)
    val previousLabel = stringResource(R.string.radio_previous)

    /** Glissé validé : le titre voisin finit d'arriver à sa place, puis le morceau change. */
    fun skipBySwipe(forward: Boolean) {
        val target = if (forward) nextItem else previousItem
        scope.launch {
            val out = if (textWidth > 0f) textWidth + SWIPE_GAP else 400f
            swipe.animateTo(if (forward) -out else out, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow))
            landed = target
            swipe.snapTo(0f) // le voisin devient le titre affiché, exactement au même endroit
            runCatching { if (forward) PlayerHolder.next(context) else PlayerHolder.previous(context) }
                .onFailure {
                    landed = null
                    Toast.makeText(context, context.getString(R.string.radio_station_failed), Toast.LENGTH_SHORT).show()
                }
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .pointerInput(canSwipe) {
                if (!canSwipe) return@pointerInput
                var crossed = false
                detectHorizontalDragGestures(
                    onDragStart = {
                        crossed = false
                        scope.launch { swipe.stop() }
                    },
                    onDragEnd = {
                        val distance = swipe.value
                        when {
                            distance <= -threshold -> skipBySwipe(forward = true)
                            distance >= threshold -> skipBySwipe(forward = false)
                            else -> scope.launch { swipe.animateTo(0f, spring(dampingRatio = 0.65f)) }
                        }
                    },
                    onDragCancel = { scope.launch { swipe.animateTo(0f, spring(dampingRatio = 0.65f)) } },
                ) { change, dx ->
                    change.consume()
                    val next = swipe.value + dx * 0.9f
                    // Petit retour sous le doigt au moment où le glissé « compte »
                    val past = abs(next) >= threshold
                    if (past && !crossed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    crossed = past
                    scope.launch { swipe.snapTo(next) }
                }
            }
            .semantics {
                if (canSwipe) {
                    customActions = listOf(
                        CustomAccessibilityAction(nextLabel) { skipBySwipe(true); true },
                        CustomAccessibilityAction(previousLabel) { skipBySwipe(false); true },
                    )
                }
            }
            .padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // La pochette reste en place (elle change en fondu) ; seul le titre suit le doigt, et le titre du
        // morceau voisin arrive à côté dès le début du glissé
        // (cachée pendant qu'elle « vole » vers la grande pochette de la fenêtre, voir MusicDockWindow)
        Box(Modifier.size(48.dp).graphicsLayer { alpha = if (hideCover()) 0f else 1f }.clip(RoundedCornerShape(14.dp)).background(Pelage.RouxNuit)) {
            Crossfade(shownThumbnail, animationSpec = tween(220), label = "pochette") { thumb ->
                AsyncImage(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .clipToBounds()
                .onSizeChanged { textWidth = it.width.toFloat() },
        ) {
            val offset = swipe.value
            val travel = (textWidth + SWIPE_GAP).coerceAtLeast(1f)
            TrackText(
                shownTitle ?: stringResource(R.string.radio_pick),
                shownSubtitle ?: stringResource(R.string.radio_tap),
                Modifier.graphicsLayer {
                    translationX = offset
                    alpha = 1f - (abs(offset) / travel).coerceIn(0f, 0.9f)
                },
            )
            val neighbour = when {
                offset < 0f -> nextItem
                offset > 0f -> previousItem
                else -> null
            }
            if (neighbour != null) {
                TrackText(
                    neighbour.title,
                    neighbour.channel,
                    Modifier.graphicsLayer {
                        translationX = if (offset < 0f) offset + travel else offset - travel
                        alpha = (abs(offset) / travel).coerceIn(0.15f, 1f)
                    },
                )
            }
        }
        when {
            now != null -> IconTap(if (playing) MkIcons.Pause else MkIcons.Play, stringResource(if (playing) R.string.pause else R.string.play)) { PlayerHolder.togglePlay() }
            // Pendant une vidéo : ▶ reprend le morceau mis de côté (la vidéo s'arrête), pas la vidéo
            parked != null -> IconTap(MkIcons.Play, stringResource(R.string.play)) {
                PlayerHolder.launchPlayback { PlayerHolder.restoreParkedMusic(context, autoPlay = true) }
            }
        }
        if (now != null && queue.size > 1) IconTap(MkIcons.Next, nextLabel) { scope.launch { runCatching { PlayerHolder.next(context) } } }
    }
}

@Composable
private fun IconTap(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

/** L'écart entre le titre affiché et le titre voisin qui arrive pendant un glissé. */
private const val SWIPE_GAP = 40f

/** Le titre et la ligne du dessous d'un morceau, dans la pilule. */
@Composable
private fun TrackText(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(
            title,
            style = TextStyle(fontFamily = Rounded, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, shadow = GlassTextShadow),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            style = TextStyle(fontFamily = Rounded, color = Color(0xFFEDE6DF), fontSize = 12.sp, shadow = GlassTextShadow),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
