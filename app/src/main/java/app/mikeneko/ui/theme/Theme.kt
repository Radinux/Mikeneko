package app.mikeneko.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.mikeneko.R

/**
 * Une couleur d'accent au choix (2.9.0, Réglages › Apparence) : la couleur des boutons, capsules,
 * barres et liens. Calico (le roux du logo) par défaut ; pas de violet. [night] : le fond très
 * sombre teinté (pochettes absentes, pages sans image).
 */
data class Accent(val id: String, @StringRes val label: Int, val base: Color, val light: Color, val dark: Color, val night: Color) {
    companion object {
        val Calico = Accent("roux", R.string.accent_calico, Color(0xFFE07A2E), Color(0xFFF5A564), Color(0xFFC45A1C), Color(0xFF3A2418))
        val Sakura = Accent("sakura", R.string.accent_sakura, Color(0xFFE8789A), Color(0xFFF6A9C0), Color(0xFFC4547A), Color(0xFF3A1A26))
        val Ocean = Accent("ocean", R.string.accent_ocean, Color(0xFF3D8FE0), Color(0xFF8CC0F5), Color(0xFF1F6BB8), Color(0xFF14263A))
        val Matcha = Accent("matcha", R.string.accent_matcha, Color(0xFF6DB359), Color(0xFFA5D892), Color(0xFF4A8C38), Color(0xFF1C2E18))
        val Cherry = Accent("cerise", R.string.accent_cherry, Color(0xFFE0484B), Color(0xFFF58C8E), Color(0xFFB8282B), Color(0xFF3A1617))
        val Mint = Accent("menthe", R.string.accent_mint, Color(0xFF2FB5A0), Color(0xFF7DDACB), Color(0xFF1B8C7B), Color(0xFF12302B))
        val Amber = Accent("ambre", R.string.accent_amber, Color(0xFFE0A82E), Color(0xFFF5CF7A), Color(0xFFB8861A), Color(0xFF3A2C12))
        // 2.16.0 : trois de plus (le ciel, le yuzu, le thé torréfié)
        val Sora = Accent("sora", R.string.accent_sora, Color(0xFF38B6E8), Color(0xFF8AD6F5), Color(0xFF1C8DBB), Color(0xFF12293A))
        val Yuzu = Accent("yuzu", R.string.accent_yuzu, Color(0xFFB4CC34), Color(0xFFD5E67E), Color(0xFF8BA21C), Color(0xFF262E12))
        val Hojicha = Accent("hojicha", R.string.accent_hojicha, Color(0xFFC08A5B), Color(0xFFE0B890), Color(0xFF9A6638), Color(0xFF33241A))

        /** Les couleurs toutes faites, dans l'ordre des pastilles. */
        val presets = listOf(Calico, Sakura, Ocean, Matcha, Cherry, Mint, Amber, Sora, Yuzu, Hojicha)

        const val CUSTOM = "perso"

        /** La couleur choisie au cercle (2.16.0) : ses nuances plus claire, plus foncée et de nuit s'en déduisent. */
        fun custom(color: Color): Accent {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(color.toArgb(), hsv)
            val (h, s, v) = Triple(hsv[0], hsv[1], hsv[2])
            return Accent(
                CUSTOM,
                R.string.accent_custom,
                base = color,
                light = Color.hsv(h, s * 0.55f, minOf(1f, v + 0.12f)),
                dark = Color.hsv(h, minOf(1f, s * 1.05f), v * 0.78f),
                night = Color.hsv(h, minOf(s, 0.6f) * 0.75f, 0.22f),
            )
        }

        fun of(id: String?, custom: Color? = null): Accent =
            if (id == CUSTOM && custom != null) custom(custom) else presets.firstOrNull { it.id == id } ?: Calico
    }
}

/**
 * Les choix d'apparence en cours, lus par [Pelage] : des états Compose, donc tout ce qui les lit
 * se redessine quand on change de couleur ou de fond (rempli par AppSettings au démarrage).
 */
object Appearance {
    var accent by mutableStateOf(Accent.Calico)
    /** La couleur perso en cours (2.16.0) : celle de l'accent quand c'est une couleur perso. */
    var custom by mutableStateOf<Color?>(null)
    /** Toutes les couleurs perso, dans l'ordre de création (2.26.1 : avant, il n'y en avait qu'une). */
    var customs by mutableStateOf<List<Color>>(emptyList())
    /** Fond noir pur, pour les écrans OLED. */
    var amoled by mutableStateOf(false)
}

/** La palette « pelage » de la maquette ; l'accent et les fonds suivent [Appearance] (2.9.0). */
object Pelage {
    val Nuit: Color get() = if (Appearance.amoled) Color.Black else Color(0xFF141210)
    val Coussin: Color get() = if (Appearance.amoled) Color(0xFF0E0E0E) else Color(0xFF1F1B18)
    val Bord: Color get() = if (Appearance.amoled) Color(0xFF1E1E1E) else Color(0xFF2A2420)
    val Roux: Color get() = Appearance.accent.base
    val RouxClair: Color get() = Appearance.accent.light
    val RouxFonce: Color get() = Appearance.accent.dark
    val RouxNuit: Color get() = Appearance.accent.night
    val Creme = Color(0xFFFCFAF7)
    val Encre = Color(0xFF1C1917)
    val Texte = Color(0xFFF5F0EB)
    val Gris = Color(0xFFB5ABA2)
    val Dore = Color(0xFFF2C66D)
    val DoreFonce = Color(0xFF8A5A00)
    val Rose = Color(0xFFF4A7B0)
    // Les couleurs du chat (logo, mini-lecteur) restent calico, quel que soit l'accent
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
        // Material 3 (2.9.0) : les boutons et capsules sont des composants Material, colorés par l'accent
        colorScheme = darkColorScheme(
            primary = Pelage.Roux,
            onPrimary = Pelage.Encre,
            primaryContainer = Pelage.RouxFonce,
            onPrimaryContainer = Pelage.Texte,
            secondary = Pelage.RouxClair,
            onSecondary = Pelage.Encre,
            // Boutons tonals et capsules non choisies : clairs (2.10.0, demandé), une crème légèrement
            // teintée de l'accent, texte foncé ; les choisis sont pleins de l'accent
            secondaryContainer = lerp(Pelage.Creme, Pelage.RouxClair, 0.3f),
            onSecondaryContainer = Pelage.Encre,
            tertiary = Pelage.Creme,
            onTertiary = Pelage.Encre,
            background = Pelage.Nuit,
            onBackground = Pelage.Texte,
            surface = Pelage.Coussin,
            onSurface = Pelage.Texte,
            surfaceVariant = Pelage.Bord,
            onSurfaceVariant = Pelage.Gris,
            surfaceContainerLowest = Pelage.Nuit,
            surfaceContainerLow = Pelage.Coussin,
            surfaceContainer = Pelage.Coussin,
            surfaceContainerHigh = Pelage.Bord,
            surfaceContainerHighest = Pelage.Bord,
            outline = Pelage.Gris.copy(alpha = 0.6f),
            outlineVariant = Pelage.Bord,
        ),
        typography = MikenekoTypography,
    ) {
        // Tout ce qui se touche réagit sous le doigt (2.22.0), voir PressIndication
        CompositionLocalProvider(LocalIndication provides PressIndication, content = content)
    }
}
