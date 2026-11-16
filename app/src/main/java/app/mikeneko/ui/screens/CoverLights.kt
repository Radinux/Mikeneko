package app.mikeneko.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Le rythme de ce qui joue, pour les lumières derrière la pochette (3.9.0) : la force de la basse
 * ([bass], lissée) et les temps forts ([kick] : 1 au coup, puis s'éteint). Nourri par la vague du
 * lecteur musique ([SpectrumWave]), qui analyse déjà le son à chaque image quand la fenêtre est ouverte.
 * Les valeurs sont lues pendant le dessin seulement : rien ne se recompose.
 */
object BeatLights {
    var bass by mutableFloatStateOf(0f)
        private set
    var kick by mutableFloatStateOf(0f)
        private set
    /** L'angle des faisceaux (radians) : ils tournent doucement, plus vite avec la basse. */
    var phase by mutableFloatStateOf(0f)
        private set

    private var average = 0f
    private var lastBeat = 0L
    private var lastFrame = 0L

    /** Une image analysée : [bands] (0 à 1, des graves aux aigus, voir AudioSpectrum), à [now] (ns). */
    fun feed(bands: FloatArray, playing: Boolean, now: Long) {
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1e9f).coerceAtMost(0.1f)
        lastFrame = now
        if (!playing) {
            bass *= 0.9f
            kick *= 0.8f
            return
        }
        // Les graves : les 6 premières bandes (~40 à 100 Hz)
        var sum = 0f
        for (i in 0 until minOf(6, bands.size)) sum += bands[i]
        val now0 = sum / 6f
        bass = if (now0 > bass) bass + (now0 - bass) * 0.6f else bass + (now0 - bass) * 0.15f
        // Un temps fort : la basse saute nettement au-dessus de sa moyenne récente (pas plus de 5 par seconde)
        val rise = now0 - average
        average += (now0 - average) * 0.06f
        if (rise > RISE && now0 > 0.35f && now - lastBeat > 200_000_000L) {
            kick = (0.55f + rise * 5f).coerceAtMost(1f)
            lastBeat = now
        } else {
            kick *= 0.8f // ~0,2 s pour s'éteindre à 30 images par seconde
        }
        phase = (phase + dt * (0.12f + bass * 0.5f)) % (2 * PI).toFloat()
    }

    private const val RISE = 0.05f
}

/**
 * Derrière la pochette (3.9.0) : un halo qui respire avec la basse, des faisceaux de lumière qui
 * tournent comme des projecteurs de scène, et une onde qui part de la pochette à chaque temps fort.
 * De la couleur de la pochette, avivée. Dessiné autour de la place de la pochette (il en déborde).
 */
@Composable
fun CoverLights(cover: Color, modifier: Modifier = Modifier) {
    val bright = remember(cover) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(cover.toArgb(), hsv)
        hsv[1] = (hsv[1] * 1.1f).coerceIn(0.35f, 0.9f)
        hsv[2] = 1f
        Color(android.graphics.Color.HSVToColor(hsv))
    }
    val color by animateColorAsState(bright, tween(600), label = "lumieres")
    val beam = remember { Path() }
    Canvas(modifier) {
        val b = BeatLights.bass
        val k = BeatLights.kick
        if (b < 0.01f && k < 0.01f) return@Canvas
        val r = size.minDimension / 2f
        val c = center
        // Le halo
        val halo = r * (1.2f + 0.3f * b + 0.25f * k)
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = (0.15f + 0.45f * b + 0.25f * k).coerceAtMost(0.85f)), Color.Transparent), c, halo),
            radius = halo,
            center = c,
        )
        // Les faisceaux : des triangles fins qui partent du centre, s'effaçant vers l'extérieur
        val reach = r * (1.9f + 0.4f * k)
        val half = (5.5 * PI / 180).toFloat()
        val beamAlpha = (0.05f + 0.25f * b + 0.4f * k).coerceAtMost(0.7f)
        rotate(degrees = BeatLights.phase * 180f / PI.toFloat(), pivot = c) {
            for (i in 0 until BEAMS) {
                val a = (2 * PI * i / BEAMS).toFloat()
                beam.reset()
                beam.moveTo(c.x, c.y)
                beam.lineTo(c.x + reach * cos(a - half), c.y + reach * sin(a - half))
                beam.lineTo(c.x + reach * cos(a + half), c.y + reach * sin(a + half))
                beam.close()
                drawPath(beam, Brush.radialGradient(listOf(color.copy(alpha = beamAlpha), Color.Transparent), c, reach))
            }
        }
        // L'onde d'un temps fort : un anneau qui s'éloigne de la pochette en s'éteignant
        if (k > 0.03f) {
            drawCircle(
                color = Color.White.copy(alpha = 0.45f * k),
                radius = r * (1.02f + (1f - k) * 0.55f),
                center = c,
                style = Stroke(width = 5.dp.toPx() * k),
            )
        }
    }
}

private const val BEAMS = 8

/** La pochette « tape » sur les temps forts : un léger gonflement, lu au dessin (voir [CoverLights]). */
fun beatScale(): Float = 1f + 0.035f * BeatLights.kick
