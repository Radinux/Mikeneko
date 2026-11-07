package app.mikeneko.ui.glass

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Le shader de la vague en verre marche-t-il sur ce téléphone ? (AGSL : Android 13+). Il est compilé
 * une fois pour essayer : s'il échoue, la vague est dessinée sans verre au lieu de faire planter.
 */
val liquidWaveSupported: Boolean by lazy {
    Build.VERSION.SDK_INT >= 33 && runCatching { RuntimeShader(LIQUID_WAVE_AGSL) }.isSuccess
}

/**
 * Une vague en liquid glass (2.6.4) : sous sa crête, le fond de [backdrop] est réfracté comme sous
 * les boutons en verre (bord bombé, petite frange arc-en-ciel), teinté de [color], avec un reflet
 * sur la crête, et le verre s'efface vers le bas.
 *
 * [levels] : une hauteur entre 0 et 1 par point de la vague ([WAVE_POINTS] points, de gauche à
 * droite), modifiée sur place ; [frame] est relu à chaque image pour redessiner sans recomposer.
 * La forme est la même courbe que `Path.quadraticTo` par les milieux (B-spline quadratique).
 */
@Composable
fun LiquidWave(
    backdrop: Backdrop,
    levels: FloatArray,
    frame: () -> Int,
    color: () -> Color,
    modifier: Modifier = Modifier,
    floor: Dp = 3.dp,
    bezel: Dp = 18.dp,
    refraction: Dp = 16.dp,
) {
    require(levels.size == WAVE_POINTS)
    var position by remember { mutableStateOf(Offset.Zero) }
    val shader = remember { if (Build.VERSION.SDK_INT >= 33 && liquidWaveSupported) WaveShader() else null }
    val crest = remember { FloatArray(WAVE_POINTS) }
    Box(
        modifier
            .onGloballyPositioned { position = it.positionInRoot() }
            .graphicsLayer {
                frame()
                if (shader != null && Build.VERSION.SDK_INT >= 33) {
                    val min = floor.toPx()
                    val h = size.height
                    for (i in 0 until WAVE_POINTS) crest[i] = h - (min + levels[i] * (h - min)).coerceAtMost(h)
                    renderEffect = shader.effect(size.width, h, crest, bezel.toPx(), refraction.toPx(), color()).asComposeRenderEffect()
                }
            }
            .drawBehind {
                val offset = position - backdrop.origin
                translate(-offset.x, -offset.y) { drawLayer(backdrop.layer) }
            },
    )
}

const val WAVE_POINTS = 40

@RequiresApi(33)
private class WaveShader {
    private val shader = RuntimeShader(LIQUID_WAVE_AGSL)

    fun effect(width: Float, height: Float, crest: FloatArray, bezel: Float, refraction: Float, color: Color): android.graphics.RenderEffect {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("crest", crest)
        shader.setFloatUniform("bezel", bezel)
        shader.setFloatUniform("refraction", refraction)
        shader.setFloatUniform("tint", color.red, color.green, color.blue, 1f)
        return android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
    }
}

/**
 * La crête est une B-spline quadratique : entre les milieux de deux points, la courbe passe près du
 * point du milieu (exactement ce que dessine `quadraticTo` par les milieux), et des segments droits
 * aux deux bouts. Sous la crête : réfraction vers l'intérieur près du bord, teinte qui s'efface vers
 * le bas, reflet clair sur le bord. Au-dessus : transparent.
 */
private const val LIQUID_WAVE_AGSL = """
uniform shader content;
uniform float2 size;
uniform float crest[40];
uniform float bezel;
uniform float refraction;
uniform float4 tint;

float at(int k) {
    float v = 0.0;
    for (int j = 0; j < 40; j++) {
        if (j == k) { v = crest[j]; }
    }
    return v;
}

float crestY(float x) {
    float stepX = size.x / 39.0;
    float u = clamp(x / stepX, 0.0, 39.0);
    if (u <= 0.5) { return mix(crest[0], crest[1], u); }
    if (u >= 38.5) { return mix(crest[38], crest[39], u - 38.0); }
    int i = int(floor(u + 0.5));
    float t = u - (float(i) - 0.5);
    float a = at(i - 1);
    float b = at(i);
    float c = at(i + 1);
    return 0.5 * (1.0 - t) * (1.0 - t) * a + (-t * t + t + 0.5) * b + 0.5 * t * t * c;
}

half4 main(float2 coord) {
    float cy = crestY(coord.x);
    float slope = (crestY(coord.x + 1.5) - crestY(coord.x - 1.5)) / 3.0;
    float d = (coord.y - cy) / sqrt(1.0 + slope * slope);
    if (d <= -1.0) { return half4(0.0); }
    float edge = clamp(d + 1.0, 0.0, 1.0); // bord adouci sur un pixel
    float2 outward = normalize(float2(slope, -1.0));
    float bend = pow(1.0 - clamp(d / bezel, 0.0, 1.0), 2.5);
    float2 offset = -outward * bend * refraction;
    float red = float(content.eval(coord + offset * 0.94).r);
    float4 green = float4(content.eval(coord + offset));
    float blue = float(content.eval(coord + offset * 1.06).b);
    float3 glass = float3(red, green.g, blue);
    // De la crête vers le bas de la vague : la teinte puis le verre s'effacent
    float depth = clamp((coord.y - cy) / max(size.y - cy, 1.0), 0.0, 1.0);
    float3 col = mix(glass, tint.rgb, 0.5 * (1.0 - depth));
    // Reflet sur la crête, clair et un peu teinté
    float rim = 1.0 - smoothstep(0.0, 3.0, d);
    col = mix(col, mix(float3(1.0), tint.rgb, 0.35), rim * 0.85);
    float alpha = edge * (1.0 - smoothstep(0.15, 0.95, depth));
    return half4(half3(col * alpha), half(alpha));
}
"""
