package app.mikeneko.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.ui.theme.Pelage
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** La luminosité des couleurs du cercle : assez claires pour que le texte foncé des boutons reste lisible. */
private const val VALUE = 0.92f
/** Le centre du cercle n'est pas tout à fait blanc : une couleur trop pâle ne se verrait plus sur les boutons. */
private const val MIN_SATURATION = 0.12f

/**
 * Le cercle chromatique (2.16.0, Réglages › Apparence) : la teinte autour du cercle, la saturation vers
 * le centre. Un rond suit le doigt ; l'aperçu et le code de la couleur sont dessous.
 */
@Composable
fun ColorWheelDialog(initial: Color, onDismiss: () -> Unit, onPick: (Color) -> Unit) {
    val start = remember(initial) { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1].coerceIn(MIN_SATURATION, 1f)) }
    val color = Color.hsv(hue, saturation, VALUE)

    AnimatedDialog(onDismissRequest = onDismiss) { close ->
        Column(
            Modifier.clip(RoundedCornerShape(28.dp)).background(Pelage.Coussin).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.accent_picker_title), Modifier.semantics { heading() }, color = Pelage.Texte, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(stringResource(R.string.accent_picker_text), Modifier.padding(top = 4.dp), color = Pelage.Gris, fontSize = 13.sp)
            Box(
                Modifier
                    .padding(vertical = 20.dp)
                    .size(250.dp)
                    .pointerInput(Unit) {
                        fun pick(at: Offset) {
                            val radius = size.width / 2f
                            val dx = at.x - radius
                            val dy = at.y - radius
                            hue = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0).toFloat()
                            saturation = (hypot(dx, dy) / radius).coerceIn(MIN_SATURATION, 1f)
                        }
                        detectTapGestures { pick(it) }
                    }
                    .pointerInput(Unit) {
                        fun pick(at: Offset) {
                            val radius = size.width / 2f
                            val dx = at.x - radius
                            val dy = at.y - radius
                            hue = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0).toFloat()
                            saturation = (hypot(dx, dy) / radius).coerceIn(MIN_SATURATION, 1f)
                        }
                        detectDragGestures(onDragStart = { pick(it) }) { change, _ ->
                            change.consume()
                            pick(change.position)
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2f
                    // Les teintes dans le sens des aiguilles d'une montre (comme l'angle du doigt, l'axe y vers le bas)
                    val hues = (0..12).map { Color.hsv((it * 30f) % 360f, 1f, VALUE) }
                    drawCircle(Brush.sweepGradient(hues, center), radius)
                    drawCircle(Brush.radialGradient(listOf(Color.hsv(0f, 0f, VALUE), Color.hsv(0f, 0f, VALUE).copy(alpha = 0f)), center, radius), radius)
                    val angle = Math.toRadians(hue.toDouble())
                    val thumb = center + Offset((cos(angle) * saturation * radius).toFloat(), (sin(angle) * saturation * radius).toFloat())
                    drawCircle(color, 15.dp.toPx(), thumb)
                    drawCircle(Color.White, 15.dp.toPx(), thumb, style = Stroke(3.dp.toPx()))
                    drawCircle(Color.Black.copy(alpha = 0.35f), 16.5.dp.toPx(), thumb, style = Stroke(1.dp.toPx()))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(color).border(2.dp, Pelage.Texte.copy(alpha = 0.5f), CircleShape))
                Spacer(Modifier.width(12.dp))
                Text("#%06X".format(color.toArgb() and 0xFFFFFF), color = Pelage.Texte, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { close() }) { Text(stringResource(R.string.accent_picker_cancel), color = Pelage.Texte, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(8.dp))
                // L'aperçu sur le bouton lui-même : c'est la couleur des boutons choisis
                Button(
                    onClick = { close { onPick(color) } },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = color, contentColor = Pelage.Encre),
                ) { Text(stringResource(R.string.accent_picker_ok), fontWeight = FontWeight.ExtraBold) }
            }
        }
    }
}
