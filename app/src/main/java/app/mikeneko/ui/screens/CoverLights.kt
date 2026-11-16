package app.mikeneko.ui.screens

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
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
 * Le rythme de ce qui joue, pour les lumières derrière la pochette (3.9.0) : la force de la basse
 * ([bass], lissée) et les temps forts, gardés comme des impacts (heure, force). Nourri par la vague du
 * lecteur musique ([SpectrumWave]), qui analyse déjà le son à chaque image quand la fenêtre est ouverte.
 * Lu pendant le dessin seulement : rien ne se recompose.
 */
object BeatLights {
    var bass by mutableFloatStateOf(0f)
        private set
    /** La force du dernier temps fort, qui s'éteint (la pochette qui « tape »). */
    var kick by mutableFloatStateOf(0f)
        private set
    /** L'heure de la dernière image analysée (ns) : l'horloge des impacts, et ce qui fait redessiner. */
    var clock by mutableLongStateOf(0L)
        private set
    /** Compte les impacts (redessine aussi quand l'un arrive). */
    var impacts by mutableIntStateOf(0)
        private set

    /** Les derniers impacts : leur heure (ns) et leur force (0 à 1). */
    val impactAt = LongArray(MAX_IMPACTS)
    val impactPower = FloatArray(MAX_IMPACTS)

    private var average = 0f
    private var lastBeat = 0L

    /** Une image analysée : [bands] (0 à 1, des graves aux aigus, voir AudioSpectrum), à [now] (ns). */
    fun feed(bands: FloatArray, playing: Boolean, now: Long) {
        clock = now
        if (!playing) {
            bass *= 0.9f
            kick *= 0.8f
            return
        }
        // Les graves : les 6 premières bandes (~40 à 100 Hz)
        var sum = 0f
        for (i in 0 until minOf(6, bands.size)) sum += bands[i]
        val level = sum / 6f
        bass = if (level > bass) bass + (level - bass) * 0.6f else bass + (level - bass) * 0.15f
        // Un temps fort : la basse saute nettement au-dessus de sa moyenne récente (pas plus de 5 par seconde)
        val rise = level - average
        average += (level - average) * 0.06f
        if (rise > RISE && level > 0.35f && now - lastBeat > 200_000_000L) {
            val power = (0.55f + rise * 5f).coerceAtMost(1f)
            kick = power
            lastBeat = now
            val slot = impacts % MAX_IMPACTS
            impactAt[slot] = now
            impactPower[slot] = power
            impacts++
        } else {
            kick *= 0.8f
        }
    }

    const val MAX_IMPACTS = 4
    private const val RISE = 0.05f
}

/** Combien de temps un impact pousse et s'éteint. */
private const val IMPACT_NS = 650_000_000L

/** Le dessin déborde de la pochette de ce facteur (la lueur qui sort de derrière elle). */
private const val SPREAD = 1.8f

/**
 * Derrière la pochette (3.9.0, refait en 3.9.1 : « comme des impacts de balles ») : à chaque temps fort,
 * une lueur de la couleur de la pochette jaillit de derrière ses bords, pousse vers l'extérieur et
 * s'éteint vite (fondu fort) ; plusieurs se chevauchent sur une rafale. En fond, une lueur discrète qui
 * suit la basse. Des rectangles arrondis (la forme de la pochette, cachée dessous) floutés par Android
 * (12+) ; avant, une imitation en couches. Plus de faisceaux qui tournent ni d'anneau blanc (3.9.0).
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
    Canvas(
        modifier
            // Plus grand que la pochette, centré sur elle : la lueur a la place de sortir
            .layout { measurable, constraints ->
                val w = (constraints.maxWidth * SPREAD).roundToInt()
                val h = (constraints.maxHeight * SPREAD).roundToInt()
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place((constraints.maxWidth - w) / 2, (constraints.maxHeight - h) / 2)
                }
            }
            .graphicsLayer {
                if (blur) renderEffect = BlurEffect(26.dp.toPx(), 26.dp.toPx(), TileMode.Decal)
            },
    ) {
        val now = BeatLights.clock
        BeatLights.impacts // (redessine à chaque nouvel impact)
        val coverSize = Size(size.width / SPREAD, size.height / SPREAD)
        val radius = corner.toPx()
        // La lueur de fond, discrète, qui suit la basse
        val b = BeatLights.bass
        if (b > 0.02f) glow(color, coverSize, radius, scale = 1.02f + 0.04f * b, alpha = 0.10f + 0.22f * b, soft = !blur)
        // Les impacts : ils poussent vers l'extérieur (vite, puis de moins en moins) en s'éteignant fort
        for (i in 0 until BeatLights.MAX_IMPACTS) {
            val at = BeatLights.impactAt[i]
            if (at == 0L) continue
            val t = (now - at).toFloat() / IMPACT_NS
            if (t < 0f || t >= 1f) continue
            val push = 1f - (1f - t).pow(3)
            val fade = (1f - t).pow(2.5f)
            glow(color, coverSize, radius, scale = 1.0f + 0.34f * push, alpha = 0.95f * BeatLights.impactPower[i] * fade, soft = !blur)
        }
    }
}

/** Un rectangle arrondi de la forme de la pochette, agrandi de [scale] autour du centre. [soft] : sans flou d'Android, en couches. */
private fun DrawScope.glow(color: Color, coverSize: Size, radius: Float, scale: Float, alpha: Float, soft: Boolean) {
    fun rect(grow: Float, a: Float) {
        val w = coverSize.width * scale + grow * 2
        val h = coverSize.height * scale + grow * 2
        drawRoundRect(
            color.copy(alpha = a.coerceIn(0f, 1f)),
            topLeft = Offset(center.x - w / 2, center.y - h / 2),
            size = Size(w, h),
            cornerRadius = CornerRadius(radius * scale + grow),
        )
    }
    if (!soft) {
        rect(0f, alpha)
        return
    }
    // Sans flou : des couches de plus en plus grandes et transparentes
    val step = coverSize.minDimension * 0.035f
    for (k in 0 until 6) rect(step * k, alpha * 0.22f * (1f - k / 6f))
}

/** La pochette « tape » sur les temps forts : un léger gonflement, lu au dessin (voir [CoverLights]). */
fun beatScale(): Float = 1f + 0.035f * BeatLights.kick
