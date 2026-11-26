package app.mikeneko.ui.screens

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.foundation.layout.fillMaxSize
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
    /** Le temps qui passe (s) : l'ondulation du liquide autour de la pochette (3.9.2). */
    var time = 0f
        private set

    private var average = 0f
    private var lastBeat = 0L

    /** Une image analysée : [bands] (0 à 1, des graves aux aigus, voir AudioSpectrum), à [now] (ns). */
    fun feed(bands: FloatArray, playing: Boolean, now: Long) {
        val dt = if (lastFrame == 0L) 1f / 30 else ((now - lastFrame) / 1e9f).coerceIn(0.005f, 0.1f)
        lastFrame = now
        time += dt
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

/**
 * Le dessin déborde de la pochette de ce facteur (le halo sort de derrière elle). 3.10.1 : 2,8 → 1,6, à
 * la demande (le halo allait jusqu'aux boutons du haut et aux bords de l'écran).
 */
private const val SPREAD = 1.6f

/**
 * Le liquide autour de la pochette (3.9.2), en AGSL (Android 13+) : chaque point autour de la pochette
 * va chercher sa couleur dans la pochette elle-même, près de son bord (ses « UV » prolongés vers
 * l'extérieur), avec une ondulation liquide qui s'amplifie avec la basse, et tout s'estompe en
 * s'éloignant (un halo sans contour). Sous la pochette : rien (elle est dessinée par-dessus).
 * 3.10.1, à la demande : plus de dispersion chromatique (rouge, vert et bleu décalés), et un halo
 * borné, éteint bien avant le bord du calque.
 */
private const val LIQUID_SHADER = """
uniform shader content;
uniform float2 center;
uniform float halfSize;
uniform float squareness;
uniform float time;
uniform float bass;
uniform float kick;

half4 main(float2 p) {
    float2 d = p - center;
    float r = length(d);
    float2 dir = d / max(r, 1.0);
    float edge = halfSize / pow(pow(abs(dir.x), squareness) + pow(abs(dir.y), squareness), 1.0 / squareness);
    float outside = r - edge;
    if (outside <= 0.0) return half4(0.0);
    float angle = atan(d.y, d.x);
    // L'ondulation du liquide : quelques vagues qui tournent, plus fortes avec la basse et les temps forts
    float wave = sin(angle * 5.0 + time * 1.3) * 0.55 + sin(angle * 9.0 - time * 1.9) * 0.3 + sin(angle * 2.0 + time * 0.6) * 0.45;
    float bend = halfSize * 0.05 * (0.35 + bass + kick) * wave;
    // On va chercher la couleur dans la pochette, plus profond quand on s'éloigne (la lumière courbée)
    float depth = 3.0 + outside * 0.35 + bend;
    float2 tangent = float2(-dir.y, dir.x);
    float2 base = center + dir * (edge - max(depth, 2.0)) + tangent * bend * 0.6;
    float3 col = content.eval(base).rgb;
    // 3.10.0 : de la lumière, même d'une partie sombre de la pochette : la couleur éclaircie et ravivée
    float luma = dot(col, float3(0.299, 0.587, 0.114));
    col = mix(float3(luma), col, 1.6);
    col = clamp(col, 0.0, 1.0);
    col = 1.0 - (1.0 - col) * (1.0 - col);
    col = max(col, float3(0.18));
    // Le halo : il s'estompe en s'éloignant, un peu plus grand avec la basse et les temps forts.
    // 3.10.1 : plus court, et éteint pour de bon à 45 % d'une demi-pochette du bord, quoi qu'il arrive
    float reach = halfSize * (0.2 + 0.12 * bass + 0.08 * kick);
    float fade = exp(-outside / reach) * (1.2 + 0.6 * bass + 0.4 * kick);
    fade = clamp(fade, 0.0, 1.0) * (1.0 - smoothstep(halfSize * 0.25, halfSize * 0.45, outside));
    return half4(col * fade, fade);
}
"""

/**
 * Autour de la pochette (3.9.0 ; 3.9.2, à la demande : « de la réfraction liquide des UV de la pochette,
 * halo sans contours » ; la dispersion est partie en 3.10.1) : la pochette elle-même déborde de ses bords
 * comme à travers un liquide qui ondule avec la basse, et tout s'estompe en un halo flou, plus grand avec la basse et sur les temps forts. Android 13+ (AGSL) ;
 * avant, une lueur floue de la couleur de la pochette.
 */
@Composable
fun CoverLights(cover: Color, model: Any?, modifier: Modifier = Modifier, corner: Dp = 26.dp) {
    val bright = remember(cover) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(cover.toArgb(), hsv)
        hsv[1] = (hsv[1] * 1.15f).coerceIn(0.4f, 0.95f)
        hsv[2] = 1f
        Color(android.graphics.Color.HSVToColor(hsv))
    }
    val color by animateColorAsState(bright, tween(600), label = "lumiere")
    // Plus grand que la pochette, centré sur elle : le halo a la place de sortir
    val spread = Modifier.layout { measurable, constraints ->
        val w = (constraints.maxWidth * SPREAD).roundToInt()
        val h = (constraints.maxHeight * SPREAD).roundToInt()
        val placeable = measurable.measure(Constraints.fixed(w, h))
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place((constraints.maxWidth - w) / 2, (constraints.maxHeight - h) / 2)
        }
    }
    // Le shader n'est compilé que sur le téléphone : s'il échoue (ou un réglage refusé), la lueur simple
    // prend le relais au lieu d'un plantage
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { android.graphics.RuntimeShader(LIQUID_SHADER) }.getOrNull() else null
    }
    var broken by remember { androidx.compose.runtime.mutableStateOf(false) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && model != null && shader != null && !broken) {
        Box(
            modifier.then(spread).graphicsLayer {
                BeatLights.frame // (refait l'effet à chaque image analysée)
                val half = size.minDimension / SPREAD / 2f
                if (half <= 0f) return@graphicsLayer
                try {
                    shader.setFloatUniform("center", size.width / 2f, size.height / 2f)
                    shader.setFloatUniform("halfSize", half)
                    shader.setFloatUniform("squareness", 6f - 2f * (corner.toPx() / half).coerceIn(0f, 1f))
                    shader.setFloatUniform("time", BeatLights.time)
                    shader.setFloatUniform("bass", BeatLights.bass)
                    shader.setFloatUniform("kick", BeatLights.kick)
                    val liquid = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
                    // Puis un flou : le halo n'a aucun contour
                    val soft = android.graphics.RenderEffect.createBlurEffect(10.dp.toPx(), 10.dp.toPx(), android.graphics.Shader.TileMode.DECAL)
                    renderEffect = android.graphics.RenderEffect.createChainEffect(soft, liquid).asComposeRenderEffect()
                } catch (e: Exception) {
                    broken = true
                }
                blendMode = BlendMode.Plus
            },
        ) {
            // La pochette, à sa place (au centre, à sa taille) : c'est elle que le liquide prolonge
            coil3.compose.AsyncImage(
                model,
                null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.align(androidx.compose.ui.Alignment.Center).fillMaxSize(1f / SPREAD),
            )
        }
    } else {
        // Avant Android 13 : une lueur floue de la couleur de la pochette, qui suit la basse
        Canvas(
            modifier.then(spread).graphicsLayer {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) renderEffect = BlurEffect(14.dp.toPx(), 14.dp.toPx(), TileMode.Decal)
                blendMode = BlendMode.Plus
            },
        ) {
            BeatLights.frame
            val half = size.minDimension / SPREAD / 2f
            val p = (0.35f + 0.5f * BeatLights.bass + 0.3f * BeatLights.kick).coerceAtMost(1f)
            drawCircle(
                Brush.radialGradient(0f to color.copy(alpha = 0.7f * p), 0.7f to color.copy(alpha = 0.35f * p), 1f to Color.Transparent, center = center, radius = half * 1.35f),
                radius = half * 1.35f,
            )
        }
    }
}

/** La pochette « tape » sur les temps forts : un léger gonflement, lu au dessin (voir [CoverLights]). */
fun beatScale(): Float = 1f + 0.035f * BeatLights.kick
