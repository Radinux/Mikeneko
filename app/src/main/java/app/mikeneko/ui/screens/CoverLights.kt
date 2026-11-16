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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
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

    /** Le spectre lissé (des graves aux aigus, 0 à 1) : le contour et les barres autour de la pochette (3.9.2). */
    val levels = FloatArray(LEVELS)
    /** Les particules qui partent de la pochette : leur direction (radians) et leur distance (en demi-pochettes). */
    val particleAngle = FloatArray(PARTICLES) { (it * 2.39996f) % (2 * PI.toFloat()) } // (l'angle d'or : bien réparties)
    val particleDistance = FloatArray(PARTICLES) { 1f + (it % 9) * 0.1f }
    private var seed = 7
    private var lastFrame = 0L

    private var average = 0f
    private var lastBeat = 0L

    /** Une image analysée : [bands] (0 à 1, des graves aux aigus, voir AudioSpectrum), à [now] (ns). */
    fun feed(bands: FloatArray, playing: Boolean, now: Long) {
        val dt = if (lastFrame == 0L) 1f / 30 else ((now - lastFrame) / 1e9f).coerceIn(0.005f, 0.1f)
        lastFrame = now
        // Le spectre, ramené à [LEVELS] valeurs et lissé (monte vite, retombe doucement)
        for (i in 0 until LEVELS) {
            val v = if (playing && bands.isNotEmpty()) {
                val x = i.toFloat() / (LEVELS - 1) * (bands.size - 1)
                val a = x.toInt().coerceAtMost(bands.size - 1)
                val b = (a + 1).coerceAtMost(bands.size - 1)
                // Au-dessus d'un plancher, pour que le silence et le souffle restent à plat
                ((bands[a] + (bands[b] - bands[a]) * (x - a) - 0.3f) / 0.7f).coerceIn(0f, 1f)
            } else 0f
            levels[i] += (v - levels[i]) * if (v > levels[i]) 0.7f else 0.18f
        }
        // Les particules s'éloignent, plus vite avec la basse ; trop loin, elles repartent du bord
        for (i in 0 until PARTICLES) {
            particleDistance[i] += dt * (0.08f + 0.9f * bass + 0.8f * kick) * (0.6f + (i % 5) * 0.12f)
            if (particleDistance[i] > 1.9f) {
                seed = seed * 1103515245 + 12345
                particleAngle[i] = ((seed ushr 8) and 0xFFFF) / 65536f * 2 * PI.toFloat()
                particleDistance[i] = 1.02f
            }
        }
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
    const val LEVELS = 48
    const val PARTICLES = 40
}

/** Le dessin déborde de la pochette de ce facteur (la lumière sort de derrière elle). */
private const val SPREAD = 2.0f

/** Les points du contour, les barres autour de la pochette. */
private const val POINTS = 180
private const val BARS = 72

/**
 * Autour de la pochette (3.9.0 ; 3.9.2, l'ambiance des visualiseurs de musique en vidéo) : le spectre
 * fait le tour de la pochette, en suivant sa forme (graves en bas, aigus en haut, en miroir gauche /
 * droite). Une forme de lumière qui se déforme avec lui (floue, ajoutée à ce qu'il y a derrière), des
 * barres fines et nettes par-dessus, des particules qui s'envolent plus vite avec la basse ; le tout
 * pulse sur les temps forts, et la pochette « tape ». De la couleur de la pochette, avivée.
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
    val path = remember { Path() }
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
        // La forme de lumière qui se déforme : floue, ajoutée
        Canvas(
            Modifier.matchParentSize().then(spread).graphicsLayer {
                if (blur) renderEffect = BlurEffect(20.dp.toPx(), 20.dp.toPx(), TileMode.Decal)
                blendMode = BlendMode.Plus
            },
        ) {
            BeatLights.frame // (redessine à chaque image analysée)
            if (!contour(path, corner.toPx())) return@Canvas
            val half = size.minDimension / SPREAD / 2f
            drawPath(
                path,
                Brush.radialGradient(
                    0f to color.copy(alpha = 0.9f),
                    (half * 1.0f / (half * 1.5f)) to lerp(color, Color.White, 0.3f).copy(alpha = 0.85f),
                    1f to color.copy(alpha = 0f),
                    center = center,
                    radius = half * 1.5f,
                ),
            )
        }
        // Les barres, les particules et le fil du contour : nets
        Canvas(Modifier.matchParentSize().then(spread).graphicsLayer { blendMode = BlendMode.Plus }) {
            BeatLights.frame
            sharp(color, corner.toPx())
            if (contour(path, corner.toPx())) {
                drawPath(path, lerp(color, Color.White, 0.6f).copy(alpha = 0.7f), style = Stroke(width = 1.5.dp.toPx()))
            }
        }
    }
}

/** La place du bord de la pochette dans la direction (cos, sin) : un carré aux coins arrondis. */
private fun edge(half: Float, cornerPx: Float, ca: Float, sa: Float): Float {
    val squareness = 6f - 2f * (cornerPx / half).coerceIn(0f, 1f)
    return half / (abs(ca).pow(squareness) + abs(sa).pow(squareness)).pow(1f / squareness)
}

/** Le niveau du spectre dans la direction [angle] (0 = vers la droite) : graves en bas, aigus en haut, en miroir. */
private fun levelAt(angle: Float): Float {
    // 0 en bas, 1 en haut, pareil à gauche et à droite
    val fromBottom = abs(((angle - PI.toFloat() / 2 + 3 * PI.toFloat()) % (2 * PI.toFloat())) - PI.toFloat()) / PI.toFloat()
    val s = fromBottom // (0 en bas : les graves)
    val x = s * (BeatLights.LEVELS - 1)
    val a = x.toInt().coerceIn(0, BeatLights.LEVELS - 1)
    val b = (a + 1).coerceAtMost(BeatLights.LEVELS - 1)
    val v = BeatLights.levels[a] + (BeatLights.levels[b] - BeatLights.levels[a]) * (x - a)
    return v.pow(1.4f) // les pointes ressortent, le bas reste calme
}

/** Le contour du spectre autour de la pochette dans [path] ; false s'il n'y a rien à dessiner (silence). */
private fun DrawScope.contour(path: Path, cornerPx: Float): Boolean {
    if (BeatLights.power < 0.02f && BeatLights.kick < 0.02f) return false
    val half = size.minDimension / SPREAD / 2f
    val reach = half * 0.24f * (1f + 0.35f * BeatLights.kick)
    path.reset()
    for (p in 0..POINTS) {
        val a = (2 * PI * p / POINTS).toFloat()
        val ca = cos(a)
        val sa = sin(a)
        val r = edge(half, cornerPx, ca, sa) * 1.02f + reach * levelAt(a)
        val x = center.x + ca * r
        val y = center.y + sa * r
        if (p == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return true
}

/** Les barres fines autour de la pochette et les particules. */
private fun DrawScope.sharp(color: Color, cornerPx: Float) {
    if (BeatLights.power < 0.02f && BeatLights.kick < 0.02f) return
    val half = size.minDimension / SPREAD / 2f
    val reach = half * 0.3f * (1f + 0.35f * BeatLights.kick)
    val line = lerp(color, Color.White, 0.75f)
    val width = 2.5.dp.toPx()
    for (i in 0 until BARS) {
        val a = (2 * PI * i / BARS).toFloat()
        val ca = cos(a)
        val sa = sin(a)
        val v = levelAt(a)
        if (v < 0.03f) continue
        val inner = edge(half, cornerPx, ca, sa) * 1.06f
        val outer = inner + reach * v
        drawLine(line.copy(alpha = 0.35f + 0.6f * v), Offset(center.x + ca * inner, center.y + sa * inner), Offset(center.x + ca * outer, center.y + sa * outer), width, cap = StrokeCap.Round)
    }
    // Les particules : de petits points qui s'éteignent en s'éloignant
    val dot = 2.dp.toPx()
    for (i in 0 until BeatLights.PARTICLES) {
        val a = BeatLights.particleAngle[i]
        val d = BeatLights.particleDistance[i]
        val ca = cos(a)
        val sa = sin(a)
        val r = edge(half, cornerPx, ca, sa) * d
        val fade = (1f - (d - 1f) / 0.9f).coerceIn(0f, 1f)
        drawCircle(line.copy(alpha = 0.8f * fade * (0.3f + 0.7f * BeatLights.power)), radius = dot * (0.6f + 0.6f * fade), center = Offset(center.x + ca * r, center.y + sa * r))
    }
}

/** La pochette « tape » sur les temps forts : un léger gonflement, lu au dessin (voir [CoverLights]). */
fun beatScale(): Float = 1f + 0.035f * BeatLights.kick
