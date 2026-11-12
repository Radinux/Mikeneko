package app.mikeneko.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.ui.components.Mascot
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.Rounded
import kotlin.math.exp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * L'écran de démarrage (3.0.2) : au lancement de l'appli, Mikeneko et son nom sur le fond, le temps
 * que l'accueil ait ses vidéos, puis il s'efface en grandissant un peu. Cache la page encore vide
 * et le chat qui rebondit. Une seule fois par lancement : revenir dans l'appli déjà ouverte, ou la
 * tourner, ne le remontre pas.
 */
object BootScreen {
    /** L'accueil a de quoi s'afficher (ou on ne l'attend pas : lien ouvert, choix des goûts…). */
    private val ready = MutableStateFlow(false)

    /** Déjà parti pour ce lancement. */
    private var gone by mutableStateOf(false)

    fun ready() {
        ready.value = true
    }

    @Composable
    fun Overlay() {
        if (gone) return
        val leave = remember { Animatable(0f) }
        val name = remember { Animatable(0f) }
        // Le cercle autour du chat : il avance vite puis ralentit (on ne sait pas combien de temps
        // l'accueil va prendre), et se termine d'un coup quand l'accueil est prêt
        val progress = remember { Animatable(0f) }
        LaunchedEffect(Unit) { name.animateTo(1f, tween(NAME_MS)) }
        LaunchedEffect(Unit) {
            val waiting = launch {
                val start = SystemClock.uptimeMillis()
                while (true) {
                    withFrameNanos { }
                    val elapsed = SystemClock.uptimeMillis() - start
                    progress.snapTo(0.9f * (1f - exp(-elapsed / 1_400f)))
                }
            }
            // Jamais plus de MAX_WAIT_MS : sans réseau, l'accueil montre ensuite son propre chargement
            withTimeoutOrNull(MAX_WAIT_MS) { ready.first { it } }
            waiting.cancel()
            progress.animateTo(1f, tween(FILL_MS, easing = FastOutSlowInEasing))
            delay(THUMBNAILS_MS) // les premières miniatures ont le temps d'arriver
            leave.animateTo(1f, tween(LEAVE_MS, easing = FastOutSlowInEasing))
            gone = true
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - leave.value }
                // Le fond de l'écran de démarrage d'Android (values-v31/themes.xml), même en noir pur
                .background(SPLASH_BACKGROUND)
                // Rien ne passe à travers tant qu'il est là
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val grown = Modifier.graphicsLayer {
                val scale = 1f + 0.12f * leave.value
                scaleX = scale
                scaleY = scale
            }
            // Le chat pile à la place de celui d'Android (centré, CAT_SIZE) : on passe de l'un à l'autre
            // sans saut ; le nom apparaît dessous
            Mascot(grown, size = CAT_SIZE)
            // Le cercle de progression : un anneau pâle, rempli dans la couleur d'accent depuis le haut.
            // Il n'est pas sur l'écran d'Android : il apparaît en fondu avec le nom
            Canvas(grown.size(RING_SIZE).graphicsLayer { alpha = name.value }) {
                val stroke = RING_STROKE.toPx()
                val inset = stroke / 2f
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(Pelage.Texte.copy(alpha = 0.12f), 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arc, style = Stroke(stroke))
                drawArc(
                    Pelage.Roux,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.value,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arc,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Text(
                "Mikeneko",
                grown.offset(y = RING_SIZE / 2 + 34.dp).graphicsLayer { alpha = name.value },
                style = TextStyle(fontFamily = Rounded, color = Pelage.Texte, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold),
            )
        }
    }

    /** La taille du chat, la même que dans drawable/splash_icon.xml (110 dp sur 288). */
    private val CAT_SIZE = 110.dp
    private val RING_SIZE = 150.dp
    private val RING_STROKE = 4.dp
    private val SPLASH_BACKGROUND = Color(0xFF141210)
    private const val MAX_WAIT_MS = 4_000L
    private const val THUMBNAILS_MS = 250L

    /** Durée de Compose : à ANIMATION_SPEED = 2, l'effacement dure en vrai la moitié. */
    private const val LEAVE_MS = 700
    private const val NAME_MS = 500
    private const val FILL_MS = 400
}
