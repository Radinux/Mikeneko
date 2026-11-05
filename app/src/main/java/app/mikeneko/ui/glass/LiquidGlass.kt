package app.mikeneko.ui.glass

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Le « derrière » que le verre réfracte : on enregistre le contenu d'un écran
 * dans un GraphicsLayer, puis chaque morceau de verre le redessine déformé.
 *
 * Règle d'or : un LiquidGlass ne doit jamais être DANS la source qu'il réfracte.
 * Et la source doit être opaque (mettre son fond après backdropSource) : sinon ce qui est
 * réellement derrière le verre transparaît, net, à travers la copie déformée.
 */
class Backdrop internal constructor(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
        internal set
}

@Composable
fun rememberBackdrop(): Backdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { Backdrop(layer) }
}

fun Modifier.backdropSource(backdrop: Backdrop): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

/**
 * Un morceau de liquid glass : réfraction sur les bords bombés (AGSL, Android 13+),
 * léger arc-en-ciel, reflet de bord. Sous Android 12 : verre dépoli. En dessous : verre teinté.
 */
@Composable
fun LiquidGlass(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 34.dp,
    bezel: Dp = 22.dp,
    refraction: Dp = 22.dp,
    tint: Color = Color(0x4D100D0B),
    shadowElevation: Dp = 14.dp,
    /** Effet loupe au centre du verre (1 = aucun, 1.5 = grossi de 50 %). Android 13+ seulement. */
    magnification: Float = 1f,
    /** Verre dépoli : floute ce qui est derrière (avant la réfraction). Pour les panneaux avec du texte. */
    frost: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    var position by remember { mutableStateOf(Offset.Zero) }
    val shape = RoundedCornerShape(cornerRadius)
    val shader = remember { if (Build.VERSION.SDK_INT >= 33) GlassShader() else null }

    // Appui : le verre s'écrase un peu puis rebondit, comme une bulle de gelée
    val interaction = remember { MutableInteractionSource() }
    val squish = remember { Animatable(1f) }
    LaunchedEffect(interaction) {
        interaction.interactions.collectLatest { event ->
            when (event) {
                is PressInteraction.Press -> squish.animateTo(PRESSED_SCALE, spring(stiffness = Spring.StiffnessHigh))
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    // Même un tap très court doit se voir : on finit d'écraser avant de rebondir
                    if (squish.value > PRESSED_SCALE + 0.03f) squish.animateTo(PRESSED_SCALE, tween(70))
                    squish.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMediumLow))
                }
            }
        }
    }

    Box(
        modifier
            .graphicsLayer {
                // Comme une goutte : écrasée sous le doigt (plus large, moins haute), étirée au rebond
                val s = (1f - squish.value) / (1f - PRESSED_SCALE)
                scaleX = 1f + 0.04f * s
                scaleY = 1f - 0.15f * s
            }
            .onGloballyPositioned { position = it.positionInRoot() }
            .shadow(shadowElevation, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick) else Modifier),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    renderEffect = when {
                        shader != null && Build.VERSION.SDK_INT >= 33 -> {
                            val glass = shader.effect(size.width, size.height, cornerRadius.toPx(), bezel.toPx(), refraction.toPx(), magnification)
                            val effect = if (frost > 0.dp) {
                                // D'abord le flou, puis la réfraction des bords par-dessus
                                val blur = android.graphics.RenderEffect.createBlurEffect(frost.toPx(), frost.toPx(), android.graphics.Shader.TileMode.CLAMP)
                                android.graphics.RenderEffect.createChainEffect(glass, blur)
                            } else {
                                glass
                            }
                            effect.asComposeRenderEffect()
                        }
                        Build.VERSION.SDK_INT >= 31 -> maxOf(18f, frost.toPx()).let { BlurEffect(it, it, TileMode.Clamp) }
                        else -> null
                    }
                }
                .drawBehind {
                    val offset = position - backdrop.origin
                    translate(-offset.x, -offset.y) { drawLayer(backdrop.layer) }
                },
        )
        Box(Modifier.matchParentSize().background(tint))
        // Reflet blanc pendant l'appui
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = ((1f - squish.value) / (1f - PRESSED_SCALE)).coerceIn(0f, 1f) }
                .background(Color.White.copy(alpha = 0.22f)),
        )
        Box(
            Modifier
                .matchParentSize()
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.32f)),
                    ),
                    shape = shape,
                ),
        )
        content()
    }
}

/** Taille du verre enfoncé (1 = pas d'écrasement). */
private const val PRESSED_SCALE = 0.86f

@RequiresApi(33)
private class GlassShader {
    private val shader = RuntimeShader(LIQUID_GLASS_AGSL)

    fun effect(width: Float, height: Float, radius: Float, bezel: Float, refraction: Float, zoom: Float): android.graphics.RenderEffect {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("radius", minOf(radius, minOf(width, height) / 2f))
        shader.setFloatUniform("bezel", bezel)
        shader.setFloatUniform("refraction", refraction)
        shader.setFloatUniform("zoom", zoom.coerceAtLeast(0.1f))
        return android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
    }
}

/**
 * Même idée que la maquette : bord bombé façon squircle, l'image près du bord est
 * prise plus vers l'intérieur (comme une lentille), avec un décalage différent
 * par couleur pour la petite frange arc-en-ciel.
 */
private const val LIQUID_GLASS_AGSL = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float bezel;
uniform float refraction;
uniform float zoom;

float roundedBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r);
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 coord) {
    float2 center = size * 0.5;
    float2 p = coord - center;
    float dist = -roundedBox(p, center, radius);
    if (dist <= 0.0) {
        return content.eval(coord);
    }
    float2 grad = float2(
        roundedBox(p + float2(1.0, 0.0), center, radius) - roundedBox(p - float2(1.0, 0.0), center, radius),
        roundedBox(p + float2(0.0, 1.0), center, radius) - roundedBox(p - float2(0.0, 1.0), center, radius)
    );
    float2 normal = normalize(grad + float2(0.0001));
    float t = clamp(dist / bezel, 0.0, 1.0);
    float bend = pow(1.0 - t, 2.5);
    float2 offset = -normal * bend * refraction;
    // Effet loupe : on lit l'image plus près du centre, elle paraît donc plus grande
    float2 base = center + p / zoom;
    half red = content.eval(base + offset * 0.94).r;
    half4 green = content.eval(base + offset);
    half blue = content.eval(base + offset * 1.06).b;
    return half4(red, green.g, blue, green.a);
}
"""
