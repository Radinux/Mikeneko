package app.mikeneko.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.glass.LiquidGlass
import app.mikeneko.ui.icons.MkIcons

/** L'avancée de l'étirement (0 : le rond, 1 : la capsule), à partager avec ce qui l'entoure (fondus, ✕). */
@Composable
fun rememberSearchMorph(open: Boolean): State<Float> =
    animateFloatAsState(if (open) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "recherche")

/** Le côté d'un rond en verre et l'écart avec son voisin, comme les [GlassCircleButton] des barres. */
val SEARCH_CIRCLE = 50.dp
val SEARCH_GAP = 10.dp

/**
 * Le rond loupe en verre qui s'étire en capsule de recherche, et s'y replie (2.25.0). Avant, une
 * capsule apparaissait à côté du rond. Il prend toute la largeur qu'on lui donne :
 * - fermé, c'est un rond de 50 dp collé au bord droit, à [closedEnd] près ;
 * - ouvert, la capsule part du bord gauche et s'arrête à [openEnd] du bord droit (la place du ✕, un
 *   rond à part que l'écran dessine lui-même).
 * [field] (le champ) n'existe qu'ouvert ; [glassModifier] ne s'applique qu'à la capsule ouverte.
 */
@Composable
fun SearchMorph(
    backdrop: Backdrop,
    open: Boolean,
    progress: State<Float>,
    label: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    closedEnd: Dp = 0.dp,
    openEnd: Dp = SEARCH_CIRCLE + SEARCH_GAP,
    glassModifier: Modifier = Modifier,
    field: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier.height(SEARCH_CIRCLE)) {
        val full = maxWidth
        LiquidGlass(
            backdrop,
            Modifier
                .layout { measurable, _ ->
                    // Lu ici (mise en page) : l'animation ne recompose pas l'écran à chaque image
                    val p = progress.value
                    val left = lerp(full - closedEnd - SEARCH_CIRCLE, 0.dp, p)
                    val right = lerp(full - closedEnd, full - openEnd, p)
                    val width = (right - left).coerceAtLeast(SEARCH_CIRCLE).roundToPx()
                    val height = SEARCH_CIRCLE.roundToPx()
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout(full.roundToPx(), height) { placeable.place(left.roundToPx(), 0) }
                }
                .then(if (open) glassModifier else Modifier),
            cornerRadius = SEARCH_CIRCLE / 2,
            bezel = 16.dp,
            refraction = 16.dp,
            tint = lerp(Color(0x40100D0B), Color(0x4D100D0B), progress.value),
            onClick = if (open) null else onOpen,
        ) {
            // La loupe reste au bout gauche (là où commence le texte) et s'efface quand le champ arrive
            Icon(
                MkIcons.Search,
                if (open) null else label,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = (SEARCH_CIRCLE - SEARCH_CIRCLE * 0.46f) / 2)
                    .size(SEARCH_CIRCLE * 0.46f)
                    .graphicsLayer { alpha = 1f - (progress.value * 2.5f).coerceIn(0f, 1f) },
            )
            if (open) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = ((progress.value - 0.3f) / 0.5f).coerceIn(0f, 1f) }) { field() }
            }
        }
    }
}

/**
 * Ce qui s'efface quand la capsule s'étire par-dessus (le titre, les autres ronds) : fondu et léger
 * recul. Plus composé du tout une fois la capsule ouverte (plus de taps qui traversent).
 */
@Composable
fun FadeUnderSearch(progress: State<Float>, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    if (progress.value >= 0.999f) return
    Box(
        modifier.graphicsLayer {
            val p = progress.value
            alpha = 1f - p
            scaleX = 1f - 0.15f * p
            scaleY = 1f - 0.15f * p
        },
        content = content,
    )
}

/** Le ✕ (un rond à part) qui arrive quand la capsule s'ouvre. */
@Composable
fun AppearWithSearch(progress: State<Float>, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    if (progress.value <= 0.001f) return
    Box(
        modifier.graphicsLayer {
            val p = progress.value.coerceIn(0f, 1f)
            alpha = p
            scaleX = 0.6f + 0.4f * p
            scaleY = 0.6f + 0.4f * p
        },
        content = content,
    )
}
