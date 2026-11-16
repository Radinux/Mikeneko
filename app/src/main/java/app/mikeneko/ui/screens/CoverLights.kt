package app.mikeneko.ui.screens

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Le son de ce qui joue, pour la lumière derrière la pochette (3.9.0 ; 3.9.2 : la fréquence dominante et
 * la puissance) : [dominant] (0 = les graves, 1 = les aigus, lissée : la lumière glisse), [power] (le
 * volume, 0 à 1), [bass] et les temps forts ([kick]). Nourri par la vague du lecteur musique
 * ([SpectrumWave]), qui analyse déjà le son à chaque image quand la fenêtre est ouverte. Lu pendant le
 * dessin seulement : rien ne se recompose.
 */
object BeatLights {
    var bass by mutableFloatStateOf(0f)
        private set
    /** La force du dernier temps fort, qui s'éteint (la pochette qui « tape », la lumière qui pulse). */
    var kick by mutableFloatStateOf(0f)
        private set
    /** Où est l'énergie du son : 0 = tout en bas (graves), 1 = tout en haut (aigus). */
    var dominant by mutableFloatStateOf(0.1f)
        private set
    /** La puissance du son (0 à 1). */
    var power by mutableFloatStateOf(0f)
        private set
    /** Compté à chaque image analysée : ce qui fait redessiner la lumière. */
    var frame by mutableIntStateOf(0)
        private set

    private var average = 0f
    private var lastBeat = 0L

    /** Une image analysée : [bands] (0 à 1, des graves aux aigus, voir AudioSpectrum), à [now] (ns). */
    fun feed(bands: FloatArray, playing: Boolean, now: Long) {
        if (playing && bands.isNotEmpty()) {
            // Les graves : les 6 premières bandes (~40 à 100 Hz)
            var low = 0f
            for (i in 0 until minOf(6, bands.size)) low += bands[i]
            val level = low / 6f
            bass = if (level > bass) bass + (level - bass) * 0.6f else bass + (level - bass) * 0.15f
            // La fréquence dominante : le centre de gravité du spectre (au-dessus d'un plancher, pour
            // que le souffle ne compte pas), puis lissé : la lumière glisse d'une place à l'autre
            var sum = 0f
            var weighted = 0f
            var total = 0f
            for (i in bands.indices) {
                val e = (bands[i] - 0.25f).coerceAtLeast(0f).let { it * it }
                sum += e
                weighted += e * i
                total += bands[i]
            }
            if (sum > 1e-4f) {
                val target = weighted / sum / (bands.size - 1)
                dominant += (target - dominant) * 0.08f
            }
            val loud = (total / bands.size).coerceIn(0f, 1f)
            power += (loud - power) * if (loud > power) 0.5f else 0.12f
            // Un temps fort : la basse saute nettement au-dessus de sa moyenne récente (≤ 5 par seconde)
            val rise = level - average
            average += (level - average) * 0.06f
            if (rise > RISE && level > 0.35f && now - lastBeat > 200_000_000L) {
                kick = (0.55f + rise * 5f).coerceAtMost(1f)
                lastBeat = now
            } else {
                kick *= 0.8f
            }
        } else {
            bass *= 0.9f
            kick *= 0.8f
            power *= 0.9f
        }
        frame++
    }

    private const val RISE = 0.05f
}

/** Le dessin déborde de la pochette de ce facteur (la lumière sort de derrière elle). */
private const val SPREAD = 2.0f

/**
 * Derrière la pochette (3.9.0 ; 3.9.2, à la demande : « un cercle qui plie la lumière derrière, placé
 * selon la fréquence dominante et la puissance ») : un seul cercle de lumière, comme une lentille qui
 * courbe la lumière (presque transparent au centre, un anneau brillant au bord, un halo autour). Il fait
 * le tour de la pochette selon la fréquence dominante (en bas les graves, sur le côté les médiums, en
 * haut les aigus) et dépasse de derrière son bord ; sa taille et son éclat suivent la puissance, avec
 * une impulsion sur les temps forts. Ajouté (pas posé) sur ce qu'il y a derrière, flouté par Android 12+.
 */
@Composable
fun CoverLights(cover: Color, modifier: Modifier = Modifier, corner: Dp = 26.dp) {
    val bright = remember(cover) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(cover.toArgb(), hsv)
        hsv[1] = (hsv[1] * 1.15f).coerceIn(0.4f, 0.95f)
        hsv[2] = 1f
        Color(android.graphics.Color.HSVToColor(hsv))
    }
    val color by animateColorAsState(bright, tween(600), label = "lumiere")
    val blur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // Plus grand que la pochette, centré sur elle : la lumière a la place de sortir
    val spread = Modifier.layout { measurable, constraints ->
        val w = (constraints.maxWidth * SPREAD).roundToInt()
        val h = (constraints.maxHeight * SPREAD).roundToInt()
        val placeable = measurable.measure(Constraints.fixed(w, h))
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place((constraints.maxWidth - w) / 2, (constraints.maxHeight - h) / 2)
        }
    }
    Box(modifier) {
        // Le halo : très flou
        Canvas(
            Modifier.matchParentSize().then(spread).graphicsLayer {
                if (blur) renderEffect = BlurEffect(30.dp.toPx(), 30.dp.toPx(), TileMode.Decal)
                blendMode = BlendMode.Plus
            },
        ) {
            BeatLights.frame // (redessine à chaque image analysée)
            lens(color, corner.toPx(), halo = true)
        }
        // La lentille : l'anneau de lumière courbée, aux bords adoucis
        Canvas(
            Modifier.matchParentSize().then(spread).graphicsLayer {
                if (blur) renderEffect = BlurEffect(4.dp.toPx(), 4.dp.toPx(), TileMode.Decal)
                blendMode = BlendMode.Plus
            },
        ) {
            BeatLights.frame
            lens(color, corner.toPx(), halo = false)
        }
    }
}

/** Dessine le cercle de lumière à sa place (voir [CoverLights]) ; [halo] : la lueur autour, sinon la lentille. */
private fun DrawScope.lens(color: Color, cornerPx: Float, halo: Boolean) {
    val p = BeatLights.power
    val k = BeatLights.kick
    if (p < 0.02f && k < 0.02f) return
    val half = size.minDimension / SPREAD / 2f // la demi-taille de la pochette
    // Où : on fait le tour de la pochette, des graves (en bas) aux aigus (en haut), par la droite
    val angle = (PI / 2 - BeatLights.dominant * PI * 1.6).toFloat()
    val ca = cos(angle)
    val sa = sin(angle)
    // Le bord de la pochette dans cette direction (un carré aux coins arrondis), un peu au-delà
    val squareness = 6f - 2f * (cornerPx / half).coerceIn(0f, 1f)
    val edge = half / (abs(ca).pow(squareness) + abs(sa).pow(squareness)).pow(1f / squareness)
    val at = Offset(center.x + ca * edge * (1.0f + 0.08f * p), center.y + sa * edge * (1.0f + 0.08f * p))
    // Sa taille : la puissance, et une impulsion sur les temps forts
    val r = half * (0.28f + 0.42f * p + 0.12f * k)
    val glow = (0.35f + 0.6f * p + 0.3f * k).coerceAtMost(1f)
    if (halo) {
        drawCircle(
            Brush.radialGradient(
                0f to color.copy(alpha = 0.55f * glow),
                0.6f to color.copy(alpha = 0.35f * glow),
                1f to Color.Transparent,
                center = at,
                radius = r * 1.9f,
            ),
            radius = r * 1.9f,
            center = at,
        )
        return
    }
    // La lentille : presque rien au centre, la lumière courbée se concentre en anneau sur le bord
    drawCircle(
        Brush.radialGradient(
            0f to color.copy(alpha = 0.08f * glow),
            0.62f to color.copy(alpha = 0.3f * glow),
            0.86f to lerp(color, Color.White, 0.6f).copy(alpha = 0.95f * glow),
            0.93f to color.copy(alpha = 0.6f * glow),
            1f to Color.Transparent,
            center = at,
            radius = r,
        ),
        radius = r,
        center = at,
    )
}

/** La pochette « tape » sur les temps forts : un léger gonflement, lu au dessin (voir [CoverLights]). */
fun beatScale(): Float = 1f + 0.035f * BeatLights.kick
