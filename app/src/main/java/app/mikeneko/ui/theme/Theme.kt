package app.mikeneko.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.mikeneko.R

/** La palette « pelage » de la maquette. */
object Pelage {
    val Nuit = Color(0xFF141210)
    val Coussin = Color(0xFF1F1B18)
    val Bord = Color(0xFF2A2420)
    val Roux = Color(0xFFE07A2E)
    val RouxClair = Color(0xFFF5A564)
    val RouxFonce = Color(0xFFC45A1C)
    val RouxNuit = Color(0xFF3A2418)
    val Creme = Color(0xFFFCFAF7)
    val Encre = Color(0xFF1C1917)
    val Texte = Color(0xFFF5F0EB)
    val Gris = Color(0xFFB5ABA2)
    val Dore = Color(0xFFF2C66D)
    val DoreFonce = Color(0xFF8A5A00)
    val Rose = Color(0xFFF4A7B0)
    val GrisChat = Color(0xFF55504D)
    val RouxChat = Color(0xFFF0954A)
    val Contour = Color(0xFF3D3A3A)
}

val Rounded = FontFamily(
    Font(R.font.mplus_rounded_regular, FontWeight.Normal),
    Font(R.font.mplus_rounded_medium, FontWeight.Medium),
    Font(R.font.mplus_rounded_bold, FontWeight.Bold),
    Font(R.font.mplus_rounded_extrabold, FontWeight.ExtraBold),
)

/** Ombre portée pour le texte posé sur du verre. */
val GlassTextShadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 6f)

private fun TextStyle.rounded() = copy(fontFamily = Rounded)

private val base = Typography()

private val MikenekoTypography = Typography(
    displayLarge = base.displayLarge.rounded(),
    displayMedium = base.displayMedium.rounded(),
    displaySmall = base.displaySmall.rounded(),
    headlineLarge = base.headlineLarge.rounded(),
    headlineMedium = base.headlineMedium.rounded(),
    headlineSmall = base.headlineSmall.rounded(),
    titleLarge = base.titleLarge.rounded(),
    titleMedium = base.titleMedium.rounded(),
    titleSmall = base.titleSmall.rounded(),
    bodyLarge = base.bodyLarge.rounded(),
    bodyMedium = base.bodyMedium.rounded(),
    bodySmall = base.bodySmall.rounded(),
    labelLarge = base.labelLarge.rounded(),
    labelMedium = base.labelMedium.rounded(),
    labelSmall = base.labelSmall.rounded(),
)

@Composable
fun MikenekoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Pelage.Roux,
            onPrimary = Pelage.Encre,
            secondary = Pelage.Creme,
            onSecondary = Pelage.Encre,
            background = Pelage.Nuit,
            onBackground = Pelage.Texte,
            surface = Pelage.Coussin,
            onSurface = Pelage.Texte,
            surfaceVariant = Pelage.Bord,
            onSurfaceVariant = Pelage.Gris,
        ),
        typography = MikenekoTypography,
        content = content,
    )
}
