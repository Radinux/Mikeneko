package app.mikeneko.ui.screens

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.PI
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Le rythme de ce qui joue, pour le ferrofluide derrière la pochette (3.9.0, 3.9.1) : la force de la
 * basse ([bass], lissée), les temps forts ([kick]) et les pointes du liquide ([spike] : leur hauteur, de
 * 0 à ~1). Nourri par la vague du lecteur musique ([SpectrumWave]), qui analyse déjà le son à chaque image
 * quand la fenêtre est ouverte. Lu pendant le dessin seulement : rien ne se recompose.
 */
object BeatLights {
    var bass by mutableFloatStateOf(0f)
        private set
    /** La force du dernier temps fort, qui s'éteint (la pochette qui « tape »). */
    var kick by mutableFloatStateOf(0f)
        private set
    /** Compté à chaque image analysée : ce qui fait redessiner le liquide. */
    var frame by mutableIntStateOf(0)
        private set
    /** Le temps qui passe (s) : la rotation lente et le tremblement du liquide. */
    var time = 0f
        private set

    /** La hauteur de chaque pointe (0 au repos, ~1 sur un fort temps fort), et sa vitesse. */
    val spike = FloatArray(SPIKES)
    private val speed = FloatArray(SPIKES)
    /** Chaque pointe réagit un peu différemment (comme un vrai liquide sous un aimant). */
    private val gain = FloatArray(SPIKES) { 0.55f + 0.9f * (((it * 7919 + 13) % 97) / 97f) }

    private var average = 0f
    private var lastBeat = 0L
    private var lastFrame = 0L

    /** Une image analysée : [bands] (0 à 1, des graves aux aigus, voir AudioSpectrum), à [now] (ns). */
    fun feed(bands: FloatArray, playing: Boolean, now: Long) {
        val dt = if (lastFrame == 0L) 1f / 30 else ((now - lastFrame) / 1e9f).coerceIn(0.005f, 0.1f)
        lastFrame = now
        time += dt
        var power = 0f
        if (playing) {
            // Les graves : les 6 premières bandes (~40 à 100 Hz)
            var sum = 0f
            for (i in 0 until minOf(6, bands.size)) sum += bands[i]
            val level = sum / 6f
            bass = if (level > bass) bass + (level - bass) * 0.6f else bass + (level - bass) * 0.15f
            // Un temps fort : la basse saute nettement au-dessus de sa moyenne récente (≤ 5 par seconde)
            val rise = level - average
            average += (level - average) * 0.06f
            if (rise > RISE && level > 0.35f && now - lastBeat > 200_000_000L) {
                power = (0.55f + rise * 5f).coerceAtMost(1f)
                kick = power
                lastBeat = now
            } else {
                kick *= 0.8f
            }
        } else {
            bass *= 0.9f
            kick *= 0.8f
        }
        // Les pointes : des ressorts peu amortis (elles retombent en vibrant), tirés vers une hauteur qui
        // suit la basse, et lancés vers le haut à chaque temps fort
        for (k in 0 until SPIKES) {
            if (power > 0f) speed[k] += power * gain[k] * KICK
            val rest = 0.22f * bass * gain[k]
            val accel = -STIFF * (spike[k] - rest) - DAMP * speed[k]
            speed[k] += accel * dt
            spike[k] = (spike[k] + speed[k] * dt).coerceIn(-0.15f, 1.4f)
        }
        frame++
    }

    const val SPIKES = 18
    private const val RISE = 0.05f
    private const val STIFF = 520f // ~3,6 Hz : une vibration qu'on voit
    private const val DAMP = 15f // peu amorti : elle rebondit quelques fois
    private const val KICK = 9f
}

/** Le dessin déborde de la pochette de ce facteur (les pointes sortent de derrière elle). */
private const val SPREAD = 1.9f

/** Les points du contour du liquide. */
private const val POINTS = 240

/**
 * Derrière la pochette (3.9.0, refait en 3.9.1) : un ferrofluide qui réagit à un aimant en vibration.
 * Au repos, une masse de la forme de la pochette, cachée juste derrière elle ; des pointes s'en dressent
 * tout autour, un peu avec la basse, et jaillissent à chaque temps fort avant de retomber en vibrant.
 * De la couleur de la pochette, avivée : un halo flou dessous, le liquide aux bords adoucis dessus
 * (flou d'Android 12+ ; avant, sans flou). Il tourne lentement et tremble un peu.
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
    val color by animateColorAsState(bright, tween(600), label = "lumieres")
    val blur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val path = remember { Path() }
    // Plus grand que la pochette, centré sur elle : les pointes ont la place de sortir
    val spread = Modifier.layout { measurable, constraints ->
        val w = (constraints.maxWidth * SPREAD).roundToInt()
        val h = (constraints.maxHeight * SPREAD).roundToInt()
        val placeable = measurable.measure(Constraints.fixed(w, h))
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place((constraints.maxWidth - w) / 2, (constraints.maxHeight - h) / 2)
        }
    }
    // 3.9.1 : de la lumière, pas un liquide peint : un dégradé presque blanc contre la pochette, de sa
    // couleur ensuite, transparent au bout des pointes, ajouté (pas posé) sur ce qu'il y a derrière
    fun DrawScope.light(alpha: Float): Brush {
        val half = size.minDimension / SPREAD / 2f
        val tips = half * 1.6f
        return Brush.radialGradient(
            0f to androidx.compose.ui.graphics.lerp(color, Color.White, 0.55f).copy(alpha = alpha),
            (half * 0.98f / tips) to androidx.compose.ui.graphics.lerp(color, Color.White, 0.25f).copy(alpha = alpha),
            (half * 1.12f / tips) to color.copy(alpha = alpha * 0.85f),
            1f to color.copy(alpha = 0f),
            center = center,
            radius = tips,
        )
    }
    Box(modifier) {
        // Le halo : la même lumière, très floue
        Canvas(
            Modifier.matchParentSize().then(spread).graphicsLayer {
                if (blur) renderEffect = BlurEffect(34.dp.toPx(), 34.dp.toPx(), TileMode.Decal)
                blendMode = BlendMode.Plus
            },
        ) {
            BeatLights.frame // (redessine à chaque image analysée)
            if (fluid(path, corner.toPx())) drawPath(path, light(0.8f))
        }
        // Le cœur de la lumière, aux bords adoucis
        Canvas(
            Modifier.matchParentSize().then(spread).graphicsLayer {
                if (blur) renderEffect = BlurEffect(9.dp.toPx(), 9.dp.toPx(), TileMode.Decal)
                blendMode = BlendMode.Plus
            },
        ) {
            BeatLights.frame
            if (fluid(path, corner.toPx())) drawPath(path, light(0.75f))
        }
    }
}

/**
 * Le contour du liquide dans [path] : un carré arrondi (la pochette, un peu plus grand) d'où sortent les
 * pointes. Renvoie false s'il n'y a rien à dessiner (silence).
 */
private fun DrawScope.fluid(path: Path, cornerPx: Float): Boolean {
    val b = BeatLights.bass
    var tallest = 0f
    for (h in BeatLights.spike) tallest = maxOf(tallest, h)
    if (b < 0.02f && tallest < 0.02f) return false
    val half = size.minDimension / SPREAD / 2f // la demi-taille de la pochette
    val c = center
    val t = BeatLights.time
    val spin = t * 0.08f // une rotation très lente
    val n = BeatLights.SPIKES
    // La forme de la pochette : un « superellipse » (carré aux coins arrondis), à peine plus grand
    val squareness = 6f - 2f * (cornerPx / half).coerceIn(0f, 1f)
    val base = half * (1.0f + 0.03f * b)
    val reach = half * 0.55f // la hauteur d'une pointe pleinement dressée
    path.reset()
    for (p in 0..POINTS) {
        val a = (2 * PI * p / POINTS).toFloat()
        val ca = cos(a)
        val sa = sin(a)
        val r0 = base / (abs(ca).pow(squareness) + abs(sa).pow(squareness)).pow(1f / squareness)
        // La pointe la plus proche, et à quelle distance de son axe on est
        val pos = (a - spin) / (2 * PI).toFloat() * n
        val k = ((pos.roundToInt() % n) + n) % n
        val off = pos - pos.roundToInt() // -0,5…0,5 : 0 sur l'axe de la pointe
        val shape = cos(off * PI.toFloat()).let { if (it <= 0f) 0f else it.pow(9f) } // fine et pointue
        val tremble = 1f + 0.06f * sin(t * 23f + k * 1.7f) // le tremblement de l'aimant
        val r = r0 + reach * BeatLights.spike[k].coerceAtLeast(0f) * shape * tremble
        val x = c.x + ca * r
        val y = c.y + sa * r
        if (p == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return true
}

/** La pochette « tape » sur les temps forts : un léger gonflement, lu au dessin (voir [CoverLights]). */
fun beatScale(): Float = 1f + 0.035f * BeatLights.kick
