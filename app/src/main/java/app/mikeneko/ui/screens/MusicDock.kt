package app.mikeneko.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import app.mikeneko.player.PlayerHolder
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.GlassTextShadow
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.Rounded
import coil3.compose.AsyncImage
import kotlin.math.abs
import kotlinx.coroutines.launch

// La pilule de la musique : pochette, titre, lecture / suivant.

/**
 * Le contenu de la pilule : pochette, titre, lecture / suivant. Glisser à gauche / à droite change
 * de morceau (le titre suit le doigt).
 */
@Composable
internal fun MusicDockRow() {
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
    /** Décalage horizontal de la pochette + titre pendant un glissé. */
    val swipe = remember { Animatable(0f) }

    // Pendant le chargement du morceau suivant, on montre déjà son nom et sa pochette
    val pending = if (loading) queue.getOrNull(queueIndex)?.takeIf { it.url != now?.url } else null
    val shownTitle = pending?.title ?: now?.title ?: parked?.title
    val shownThumbnail = pending?.thumbnail ?: now?.thumbnail ?: parked?.thumbnail
    val shownSubtitle = pending?.let { stringResource(R.string.radio_loading_suffix, it.channel) }
        ?: now?.let { if (it.live) stringResource(R.string.radio_live_suffix, it.channel) else it.channel }
        ?: parked?.channel
    val nextLabel = stringResource(R.string.radio_next)
    val previousLabel = stringResource(R.string.radio_previous)

    /** Glissé validé : le titre sort d'un côté, le morceau change, le nouveau entre de l'autre. */
    fun skipBySwipe(forward: Boolean, width: Float) {
        scope.launch {
            val out = if (width > 0f) width else 400f
            swipe.animateTo(if (forward) -out else out, tween(140))
            launch {
                runCatching { if (forward) PlayerHolder.next(context) else PlayerHolder.previous(context) }
                    .onFailure { Toast.makeText(context, context.getString(R.string.radio_station_failed), Toast.LENGTH_SHORT).show() }
            }
            swipe.snapTo(if (forward) out * 0.6f else -out * 0.6f)
            swipe.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
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
                            distance <= -threshold -> skipBySwipe(forward = true, width = size.width.toFloat())
                            distance >= threshold -> skipBySwipe(forward = false, width = size.width.toFloat())
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
                        CustomAccessibilityAction(nextLabel) { skipBySwipe(true, 0f); true },
                        CustomAccessibilityAction(previousLabel) { skipBySwipe(false, 0f); true },
                    )
                }
            }
            .padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // La pochette et le titre suivent le doigt (les boutons restent en place)
        Row(
            Modifier
                .weight(1f)
                .clipToBounds()
                .graphicsLayer {
                    translationX = swipe.value
                    alpha = 1f - (abs(swipe.value) / (size.width * 0.7f)).coerceIn(0f, 0.85f)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Pelage.RouxNuit)) {
                AsyncImage(shownThumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    shownTitle ?: stringResource(R.string.radio_pick),
                    style = TextStyle(fontFamily = Rounded, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, shadow = GlassTextShadow),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    shownSubtitle ?: stringResource(R.string.radio_tap),
                    style = TextStyle(fontFamily = Rounded, color = Color(0xFFEDE6DF), fontSize = 12.sp, shadow = GlassTextShadow),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
