package app.mikeneko.update

import androidx.compose.material3.Button
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.ui.components.Mascot
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.glass.LiquidGlass
import app.mikeneko.ui.theme.GlassTextShadow
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.Rounded
import app.mikeneko.ui.theme.clay
import app.mikeneko.ui.theme.jellyClickable

/**
 * La page « Quoi de neuf » (2.7.0) : au premier lancement après une mise à jour (pas après une
 * première installation), une fois par version moyenne (pas pour une simple correction, 2.10.1). Le texte est dans l'APK (`whats_new_body`, traduit),
 * donc elle marche sans réseau ; il est à réécrire à chaque version qui apporte quelque chose.
 */
object WhatsNew {
    private fun version(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    /** A-t-on déjà noté une version vue ? (non : installation neuve ou données effacées, voir [Tutorial]) */
    fun everSeen(context: Context): Boolean =
        context.getSharedPreferences("nouveautes", Context.MODE_PRIVATE).getString("vue", null) != null

    /** À montrer ? Une première installation compte comme « déjà vue ». */
    fun pending(context: Context): Boolean {
        // Le tuto passe d'abord (premier lancement, données effacées) : il remplace « Quoi de neuf »
        Tutorial.check(context)
        if (Tutorial.open.value) return false
        val prefs = context.getSharedPreferences("nouveautes", Context.MODE_PRIVATE)
        val current = version(context)
        val seen = prefs.getString("vue", null)
        if (seen == current) return false
        // Une version de correction (2.10.0 → 2.10.1) : même page qu'avant, on ne la remontre pas
        if (seen != null && seen.split('.').take(2) == current.split('.').take(2)) {
            markSeen(context)
            return false
        }
        if (seen == null) {
            val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            if (info == null || info.firstInstallTime == info.lastUpdateTime) {
                markSeen(context)
                return false
            }
        }
        return true
    }

    fun markSeen(context: Context) {
        context.getSharedPreferences("nouveautes", Context.MODE_PRIVATE).edit().putString("vue", version(context)).apply()
    }
}

/** La page elle-même : un panneau en verre au milieu, sur un voile ; « Super ! » la ferme pour de bon. */
@Composable
fun WhatsNewSheet(backdrop: Backdrop) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(WhatsNew.pending(context)) }
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    fun close() {
        WhatsNew.markSeen(context)
        open = false
    }
    if (open) BackHandler { close() }
    AnimatedVisibility(open, enter = fadeIn(tween(260)), exit = fadeOut(tween(220))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }, // rien ne passe dessous
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(open, enter = scaleIn(tween(320), initialScale = 0.85f), exit = scaleOut(tween(200), targetScale = 0.9f)) {
                LiquidGlass(
                    backdrop,
                    Modifier.safeDrawingPadding().padding(20.dp).widthIn(max = 460.dp).fillMaxWidth(),
                    cornerRadius = 32.dp,
                    bezel = 26.dp,
                    refraction = 24.dp,
                    tint = Color(0xC71A1613),
                    frost = 28.dp,
                ) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Mascot(size = 64.dp)
                        Text(
                            stringResource(R.string.whats_new_title, version),
                            Modifier.padding(top = 10.dp, bottom = 14.dp),
                            style = TextStyle(fontFamily = Rounded, color = Pelage.Texte, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, shadow = GlassTextShadow, textAlign = TextAlign.Center),
                        )
                        Box(Modifier.heightIn(max = 380.dp)) {
                            Text(
                                stringResource(R.string.whats_new_body),
                                Modifier.verticalScroll(rememberScrollState()),
                                style = TextStyle(fontFamily = Rounded, color = Color(0xFFEDE6DF), fontSize = 15.sp, lineHeight = 21.sp, shadow = GlassTextShadow),
                            )
                        }
                        Button(onClick = { close() }, modifier = Modifier.padding(top = 18.dp)) {
                            Text(stringResource(R.string.whats_new_ok), fontFamily = Rounded, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}
