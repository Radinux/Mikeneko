package app.mikeneko.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.data.VideoItem
import app.mikeneko.ui.components.VideoThumbnail
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.glass.LiquidGlass
import app.mikeneko.ui.theme.GlassTextShadow
import app.mikeneko.ui.theme.Rounded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Hauteur du panneau « À suivre » du plein écran. */
val SuggestionsHeight = 214.dp

/**
 * Le panneau « À suivre » du plein écran : [progress] va de 0 (caché) à 1 (ouvert)
 * et suit le doigt pendant un glissé.
 */
@Stable
class SuggestionsState {
    val progress = Animatable(0f)

    /** Visible, même en partie (pendant un glissé ou une animation). */
    val shown: Boolean get() = progress.value > 0.01f

    suspend fun open() = progress.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 380f))

    suspend fun close() = progress.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 500f))

    /** Fin d'un glissé : un coup rapide décide, sinon on regarde si le panneau est sorti à plus de 40 %. */
    suspend fun settle(velocityY: Float, fastPx: Float) {
        val open = when {
            velocityY < -fastPx -> true
            velocityY > fastPx -> false
            else -> progress.value > 0.4f
        }
        if (open) open() else close()
    }
}

@Composable
fun rememberSuggestionsState() = remember { SuggestionsState() }

/**
 * Glisser vers le haut n'importe où sur la vidéo sort le panneau, vers le bas le rentre.
 * [heightPx] : la hauteur du panneau, pour que le panneau suive exactement le doigt.
 */
fun Modifier.suggestionsDrag(state: SuggestionsState, enabled: Boolean, heightPx: Float, fastPx: Float, scope: CoroutineScope): Modifier =
    if (!enabled) this
    else pointerInput(state, heightPx) {
        val tracker = VelocityTracker()
        detectVerticalDragGestures(
            onDragStart = { tracker.resetTracking() },
            onDragEnd = { scope.launch { state.settle(tracker.calculateVelocity().y, fastPx) } },
            onDragCancel = { scope.launch { state.settle(0f, fastPx) } },
        ) { change, dy ->
            change.consume()
            tracker.addPosition(change.uptimeMillis, change.position)
            scope.launch { state.progress.snapTo((state.progress.value - dy / heightPx).coerceIn(0f, 1f)) }
        }
    }

/**
 * Le panneau lui-même, en verre dépoli au-dessus de la vidéo, et la zone au-dessus qui le
 * referme d'un tap. À poser en dernier dans la Box du plein écran (par-dessus les commandes).
 */
@Composable
fun SuggestionsSheet(state: SuggestionsState, backdrop: Backdrop, videos: List<VideoItem>, onPick: (VideoItem) -> Unit, onDismiss: () -> Unit) {
    val shown by remember(state) { derivedStateOf { state.shown } } // pas de recomposition à chaque image
    if (!shown) return
    // La même marge des deux côtés : celle de l'encoche de la caméra (d'un seul côté) s'applique aux deux,
    // pour que le panneau reste centré sous la vidéo
    val density = androidx.compose.ui.platform.LocalDensity.current
    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
    val safe = WindowInsets.safeDrawing
    val side = with(density) { maxOf(safe.getLeft(density, direction), safe.getRight(density, direction)).toDp() }
    Box(Modifier.fillMaxSize()) {
        // Au-dessus du panneau : un tap le referme (au lieu de montrer les commandes)
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } })

        Box(
            Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .fillMaxWidth()
                .height(SuggestionsHeight)
                .graphicsLayer {
                    val p = state.progress.value
                    translationY = (1f - p) * (size.height + 24.dp.toPx())
                    alpha = (p * 2f).coerceIn(0f, 1f)
                }
                .padding(horizontal = side + 12.dp),
        ) {
            LiquidGlass(
                backdrop = backdrop,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 28.dp,
                bezel = 22.dp,
                refraction = 22.dp,
                // Verre clair, sans dépoli : la vidéo se voit nette à travers, déformée sur les bords
                tint = Color(0x4D100D0B),
            ) {
                Column(Modifier.fillMaxSize().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.up_next),
                        Modifier.padding(horizontal = 20.dp),
                        style = TextStyle(fontFamily = Rounded, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, shadow = GlassTextShadow),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(videos, key = { it.url }) { video -> SuggestionCard(video) { onPick(video) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(video: VideoItem, onClick: () -> Unit) {
    Column(
        Modifier
            .width(186.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        VideoThumbnail(video, Modifier.fillMaxWidth(), corner = 16.dp)
        Text(
            video.title,
            style = TextStyle(fontFamily = Rounded, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp, shadow = GlassTextShadow),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
