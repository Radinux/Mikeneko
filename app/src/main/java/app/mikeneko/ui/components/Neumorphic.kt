package app.mikeneko.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Le neumorphisme (3.9.0, boutons du lecteur musique) : des boutons de la couleur du fond, qui semblent
 * sortir de la surface (une ombre sombre en bas à droite, un reflet clair en haut à gauche, un léger
 * bombé) et s'y enfoncer quand on appuie (le bombé s'inverse, sans ombre). La couleur du fond est donnée
 * par [LocalNeuBase] (le lecteur musique : celle de sa pochette).
 */
val LocalNeuBase = compositionLocalOf { Color(0xFF221D1A) }

/**
 * Dessine la forme en relief ([pressed] = false) ou enfoncée (true), de la couleur [fill]. Les ombres
 * passent par le canevas d'Android (`setShadowLayer`, dessiné par la puce graphique depuis Android 9 ;
 * avant, seul le bombé se voit).
 */
fun Modifier.neumorphic(shape: Shape, fill: Color, pressed: Boolean, elevation: Dp = 6.dp): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val e = elevation.toPx()
    if (!pressed) {
        val path = Path().apply { addOutline(outline) }.asAndroidPath()
        drawIntoCanvas { canvas ->
            val paint = Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                color = fill.toArgb()
            }
            paint.setShadowLayer(e * 1.6f, e, e, Color.Black.copy(alpha = 0.55f).toArgb())
            canvas.nativeCanvas.drawPath(path, paint)
            paint.setShadowLayer(e * 1.6f, -e, -e, Color.White.copy(alpha = 0.11f).toArgb())
            canvas.nativeCanvas.drawPath(path, paint)
        }
    }
    val light = lerp(fill, Color.White, 0.07f)
    val dark = lerp(fill, Color.Black, 0.22f)
    // En relief : clair en haut à gauche ; enfoncé : sombre en haut à gauche (l'ombre est dedans)
    drawOutline(
        outline,
        Brush.linearGradient(
            if (pressed) listOf(dark, light) else listOf(light, dark),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
    )
}

/**
 * Un bouton neumorphique : rond par défaut, de la couleur du fond ([fill] null) ou d'une autre (le
 * bouton lecture, le minuteur en marche). [checked] le garde enfoncé (aléatoire, répéter actifs).
 */
@Composable
fun NeuButton(
    icon: ImageVector,
    label: String,
    size: Dp = 44.dp,
    shape: Shape = CircleShape,
    fill: Color? = null,
    content: Color = Color.White,
    iconSize: Dp = size * 0.46f,
    enabled: Boolean = true,
    checked: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val down = (pressed && enabled) || checked
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                val s = if (pressed && enabled) 0.97f else 1f
                scaleX = s
                scaleY = s
            }
            .neumorphic(shape, fill ?: LocalNeuBase.current, pressed = down || !enabled, elevation = if (size >= 56.dp) 7.dp else 5.dp)
            .clip(shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (enabled) content else content.copy(alpha = 0.35f), modifier = Modifier.size(iconSize))
    }
}
