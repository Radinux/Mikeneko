package app.mikeneko.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import app.mikeneko.player.VideoFrames
import app.mikeneko.ui.theme.Pelage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Le « morph » entre le mini-lecteur et le grand lecteur, façon dessin animé : une photo de la
 * vidéo plonge en arc d'une place à l'autre, s'étire avec la vitesse, dépasse un peu sa place,
 * revient, puis s'écrase et tremblote comme une goutte en atterrissant. La page apparaît en
 * fondu pendant ce temps ; la vraie vidéo prend le relais à l'arrivée.
 */
object VideoMorph {
    class Morph(val image: ImageBitmap, val from: Rect, val toMini: Boolean) {
        /** Le trajet : 0 au départ, 1 à l'arrivée (un peu plus au rebond). */
        val travel = Animatable(0f)
        /** L'atterrissage : 1 = écrasé, puis tremblote jusqu'à 0. */
        val landing = Animatable(0f)
    }

    /** Où est la vidéo du mini-lecteur / du grand lecteur à l'écran (null = pas affichée). */
    var miniBounds by mutableStateOf<Rect?>(null)
    var playerBounds by mutableStateOf<Rect?>(null)

    var active by mutableStateOf<Morph?>(null)
        private set

    /** Lance l'animation depuis [from] (null = pas d'animation, on passe directement). */
    fun start(from: Rect?, toMini: Boolean) = startWith(VideoFrames.snapshot()?.asImageBitmap(), from, toMini)

    /** Pareil avec une autre image : la miniature d'une carte qui s'envole vers le lecteur. */
    fun startWith(image: ImageBitmap?, from: Rect?, toMini: Boolean = false) {
        active = if (image != null && from != null && from.width > 0f) Morph(image, from, toMini) else null
    }

    /** Opacité de la page pendant l'animation (elle apparaît en fondu). */
    fun pageAlpha(): Float = active?.travel?.value?.coerceIn(0f, 1f) ?: 1f

    /** Le mini-lecteur se cache pendant qu'on rétrécit vers lui (c'est la photo qui arrive). */
    fun hidesMini(): Boolean = active?.toMini == true

    /** Pareil pour la vidéo du grand lecteur pendant qu'on agrandit vers elle. */
    fun hidesPlayer(): Boolean = active?.toMini == false

    internal fun finish(morph: Morph) {
        if (active === morph) active = null
    }
}

private val MINI_CORNER = 18.dp
private const val TILT_DEGREES = 7f

/** À placer tout en haut de l'écran principal, au-dessus de tout le reste. */
@Composable
fun VideoMorphLayer() {
    val morph = VideoMorph.active ?: return

    LaunchedEffect(morph) {
        // On attend que la page d'arrivée ait placé sa vidéo (une ou deux images d'écran)
        withTimeoutOrNull(500) {
            while ((if (morph.toMini) VideoMorph.miniBounds else VideoMorph.playerBounds) == null) withFrameNanos { }
        }
        coroutineScope {
            // L'atterrissage se déclenche dès qu'on touche la place (pendant le petit rebond du trajet)
            launch {
                snapshotFlow { morph.travel.value >= 0.97f }.first { it }
                morph.landing.snapTo(1f)
                morph.landing.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 330f))
            }
            // Un ressort un peu mou : plus lent qu'avant, il dépasse sa place puis revient
            morph.travel.animateTo(1f, spring(dampingRatio = 0.68f, stiffness = 85f))
        }
        // Encore deux images : la vraie vidéo est bien affichée dessous avant de retirer la photo
        withFrameNanos { }
        withFrameNanos { }
        VideoMorph.finish(morph)
    }

    Canvas(Modifier.fillMaxSize()) {
        val target = (if (morph.toMini) VideoMorph.miniBounds else VideoMorph.playerBounds) ?: morph.from
        val t = morph.travel.value
        val tIn = t.coerceIn(0f, 1f)

        // La taille suit le trajet (et le dépasse un peu au rebond)
        val width = morph.from.width + (target.width - morph.from.width) * t
        val height = morph.from.height + (target.height - morph.from.height) * t
        if (width <= 0f || height <= 0f) return@Canvas

        // Le centre suit un arc (courbe de Bézier) : vers le mini-lecteur, la vidéo part de côté puis
        // plonge ; vers le grand lecteur, elle monte d'abord puis se recentre
        val p0 = morph.from.center
        val p2 = target.center
        val control = if (morph.toMini) Offset(p2.x, p0.y) else Offset(p0.x, p2.y)
        val u = 1f - t
        val center = Offset(
            u * u * p0.x + 2 * u * t * control.x + t * t * p2.x,
            u * u * p0.y + 2 * u * t * control.y + t * t * p2.y,
        )
        val rect = Rect(center.x - width / 2, center.y - height / 2, center.x + width / 2, center.y + height / 2)

        // S'étire avec la vitesse, s'écrase à l'atterrissage puis tremblote
        val stretch = (abs(morph.travel.velocity) * 0.06f).coerceAtMost(0.18f)
        val land = morph.landing.value
        val scaleX = (1f - stretch * 0.5f) * (1f + 0.12f * land)
        val scaleY = (1f + stretch) * (1f - 0.12f * land)
        // Et penche un peu pendant le vol
        val tilt = sin(PI.toFloat() * tIn) * TILT_DEGREES * (if (morph.toMini) 1f else -1f)

        val miniSide = if (morph.toMini) tIn else 1f - tIn
        val radius = (if (morph.toMini) lerp(0.dp, MINI_CORNER, tIn) else lerp(MINI_CORNER, 0.dp, tIn)).toPx() +
            12.dp.toPx() * sin(PI.toFloat() * tIn) // plus arrondie en plein vol : une vraie goutte

        withTransform({
            rotate(tilt, center)
            scale(scaleX, scaleY, center)
        }) {
            // L'ombre portée de la carte volante
            drawRoundRect(
                Color.Black.copy(alpha = 0.35f * sin(PI.toFloat() * tIn)),
                topLeft = rect.topLeft + Offset(0f, 10.dp.toPx()),
                size = rect.size,
                cornerRadius = CornerRadius(radius),
            )
            // Côté grand lecteur, la vidéo est entière (bandes noires) ; côté mini-lecteur, recadrée
            val iw = morph.image.width.toFloat()
            val ih = morph.image.height.toFloat()
            val fit = minOf(rect.width / iw, rect.height / ih)
            val crop = maxOf(rect.width / iw, rect.height / ih)
            val imageScale = fit + (crop - fit) * miniSide
            val w = iw * imageScale
            val h = ih * imageScale
            clipPath(Path().apply { addRoundRect(RoundRect(rect, CornerRadius(radius))) }) {
                drawRect(Color.Black, rect.topLeft, rect.size)
                drawImage(
                    morph.image,
                    dstOffset = IntOffset((rect.center.x - w / 2f).roundToInt(), (rect.center.y - h / 2f).roundToInt()),
                    dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                )
            }
            // Le liseré crème du mini-lecteur apparaît avec la forme
            if (miniSide > 0.01f) {
                val stroke = 2.5.dp.toPx()
                drawRoundRect(
                    Pelage.Creme.copy(alpha = miniSide),
                    topLeft = rect.topLeft + Offset(stroke / 2, stroke / 2),
                    size = Size(rect.width - stroke, rect.height - stroke),
                    cornerRadius = CornerRadius(radius),
                    style = Stroke(stroke),
                )
            }
        }
    }
}
