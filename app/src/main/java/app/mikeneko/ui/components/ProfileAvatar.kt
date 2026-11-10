package app.mikeneko.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import app.mikeneko.data.ProfileFrame
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage
import coil3.compose.AsyncImage
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Ce que prend le cadre en plus de la photo (oreilles, fleurs, étoiles), de chaque côté. */
const val FRAME_MARGIN = 0.22f

/**
 * La photo du profil dans son cadre (2.18.0). [size] : le diamètre de la photo ; le tout prend
 * [size] × (1 + 2 × [FRAME_MARGIN]) pour laisser dépasser le cadre. Sans photo : les initiales du
 * pseudo (ou une patte) sur le roux de nuit de l'accent.
 */
@Composable
fun ProfileAvatar(avatar: String?, pseudo: String, frame: ProfileFrame, size: Dp, modifier: Modifier = Modifier) {
    val outer = size * (1 + 2 * FRAME_MARGIN)
    Box(modifier.size(outer), contentAlignment = Alignment.Center) {
        // Derrière la photo : les oreilles, la lueur
        Canvas(Modifier.fillMaxSize()) { drawFrame(frame, front = false) }
        Box(Modifier.size(size).clip(CircleShape).background(Pelage.RouxNuit), contentAlignment = Alignment.Center) {
            if (avatar != null) {
                AsyncImage(File(avatar), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                val initials = pseudo.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }
                if (initials.isNotEmpty()) {
                    Text(initials, color = Pelage.RouxClair, fontSize = (size.value * 0.36f).sp, fontWeight = FontWeight.ExtraBold)
                } else {
                    Icon(MkIcons.Paw, null, tint = Pelage.RouxClair, modifier = Modifier.size(size * 0.45f))
                }
            }
        }
        // Devant : les anneaux, les fleurs, les étoiles
        Canvas(Modifier.fillMaxSize()) { drawFrame(frame, front = true) }
    }
}

private fun DrawScope.drawFrame(frame: ProfileFrame, front: Boolean) {
    val c = center
    val r = size.minDimension / 2f / (1 + 2 * FRAME_MARGIN) // le rayon de la photo
    when (frame) {
        ProfileFrame.None -> if (front) drawCircle(Pelage.Creme.copy(alpha = 0.8f), r, c, style = Stroke(r * 0.05f))
        ProfileFrame.Calico -> if (front) {
            drawCircle(Pelage.Creme, r, c, style = Stroke(r * 0.09f))
        } else {
            // Les oreilles du logo : grise à gauche, rousse à droite, grandes et arrondies, l'intérieur rose
            calicoEar(c, r, left = true, Pelage.GrisChat)
            calicoEar(c, r, left = false, Pelage.RouxChat)
        }
        ProfileFrame.Sakura -> if (front) {
            drawCircle(Brush.sweepGradient(listOf(Color(0xFFF7B8CC), Color(0xFFFCE4EC), Color(0xFFF48FB1), Color(0xFFF7B8CC)), c), r, c, style = Stroke(r * 0.08f))
            listOf(-140f to 1f, -105f to 0.72f, -60f to 0.85f, 35f to 0.7f, 70f to 1f).forEach { (angle, scale) ->
                flower(point(c, r * 1.02f, angle), r * 0.17f * scale)
            }
        }
        ProfileFrame.Gold -> if (front) {
            val gold = Brush.sweepGradient(listOf(Color(0xFFF2C66D), Color(0xFFFFF1C1), Color(0xFFC8962E), Color(0xFFF7D98B), Color(0xFFF2C66D)), c)
            drawCircle(gold, r * 1.04f, c, style = Stroke(r * 0.1f))
            drawCircle(Color(0xFF8A5A00), r * 1.12f, c, style = Stroke(r * 0.025f))
            listOf(-90f, 0f, 90f, 180f).forEach { angle -> gem(point(c, r * 1.05f, angle), r * 0.12f) }
        }
        ProfileFrame.Halo -> if (front) {
            drawCircle(Brush.sweepGradient(listOf(Pelage.Roux, Pelage.RouxClair, Pelage.Creme, Pelage.RouxClair, Pelage.Roux), c), r * 1.02f, c, style = Stroke(r * 0.08f))
        } else {
            // La lueur : des cercles de plus en plus larges et pâles
            for (i in 1..6) drawCircle(Pelage.Roux.copy(alpha = 0.16f - i * 0.022f), r * (1.04f + i * 0.05f), c)
        }
        ProfileFrame.Night -> if (front) {
            drawCircle(Brush.sweepGradient(listOf(Color(0xFF1B2A55), Color(0xFF3F5AA8), Color(0xFF15213F), Color(0xFF2C4384), Color(0xFF1B2A55)), c), r * 1.02f, c, style = Stroke(r * 0.1f))
            listOf(-125f to 1f, -95f to 0.6f, -50f to 0.8f, 20f to 0.55f, 140f to 0.9f, 165f to 0.5f).forEach { (angle, scale) ->
                star(point(c, r * 1.16f, angle), r * 0.13f * scale)
            }
        }
    }
}

private fun point(c: Offset, distance: Float, degrees: Float): Offset {
    val a = degrees * PI.toFloat() / 180f
    return Offset(c.x + cos(a) * distance, c.y + sin(a) * distance)
}

private fun DrawScope.calicoEar(c: Offset, r: Float, left: Boolean, color: Color) {
    val side = if (left) -1f else 1f
    val base = Offset(c.x + side * r * 0.95f, c.y - r * 0.2f)
    val tip = Offset(c.x + side * r * 1.05f, c.y - r * 1.38f)
    val inner = Offset(c.x + side * r * 0.15f, c.y - r * 0.95f)
    // (pas [drawEar] : son contour blanc de 6 dp mangeait toute l'oreille sur une petite photo)
    val ear = roundedTriangle(base, tip, inner, 0.32f)
    drawPath(ear, color)
    drawPath(ear, Pelage.Contour, style = Stroke(r * 0.045f, join = StrokeJoin.Round))
    // L'intérieur de l'oreille, plus petit, rose
    val middle = centroid(base, tip, inner)
    fun toward(p: Offset, k: Float) = Offset(p.x + (middle.x - p.x) * k, p.y + (middle.y - p.y) * k)
    drawPath(roundedTriangle(toward(base, 0.5f), toward(tip, 0.42f), toward(inner, 0.55f), 0.3f), Pelage.Rose)
}

/** Un triangle aux coins arrondis ([round] de 0 à 0,5). */
private fun roundedTriangle(a: Offset, b: Offset, c: Offset, round: Float) = Path().apply {
    val corners = listOf(a, b, c)
    corners.indices.forEach { i ->
        val corner = corners[i]
        val before = corners[(i + 2) % 3]
        val after = corners[(i + 1) % 3]
        val start = corner + (before - corner) * round
        val end = corner + (after - corner) * round
        if (i == 0) moveTo(start.x, start.y) else lineTo(start.x, start.y)
        quadraticTo(corner.x, corner.y, end.x, end.y)
    }
    close()
}

private fun centroid(a: Offset, b: Offset, c: Offset) = Offset((a.x + b.x + c.x) / 3f, (a.y + b.y + c.y) / 3f)

/** Une fleur de cerisier : cinq pétales et un cœur doré. */
private fun DrawScope.flower(at: Offset, radius: Float) {
    for (i in 0 until 5) {
        val petal = point(at, radius * 0.55f, -90f + i * 72f)
        drawCircle(Color(0xFFF8BBD0), radius * 0.48f, petal)
        drawCircle(Color(0xFFF48FB1), radius * 0.48f, petal, style = Stroke(radius * 0.08f))
    }
    drawCircle(Color(0xFFFFE08A), radius * 0.22f, at)
}

/** Une petite pierre en losange, avec un reflet. */
private fun DrawScope.gem(at: Offset, radius: Float) {
    rotate(45f, at) {
        drawRect(Color(0xFFFFF1C1), Offset(at.x - radius / 2, at.y - radius / 2), Size(radius, radius))
        drawRect(Color(0xFF8A5A00), Offset(at.x - radius / 2, at.y - radius / 2), Size(radius, radius), style = Stroke(radius * 0.14f))
    }
    drawCircle(Color.White, radius * 0.14f, Offset(at.x - radius * 0.12f, at.y - radius * 0.12f))
}

/** Une étoile à quatre branches. */
private fun DrawScope.star(at: Offset, radius: Float) {
    val path = Path().apply {
        for (i in 0 until 8) {
            val p = point(at, if (i % 2 == 0) radius else radius * 0.32f, -90f + i * 45f)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    drawPath(path, Color(0xFFFFE08A))
}
