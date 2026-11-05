package app.mikeneko.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.glass.LiquidGlass
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.GlassTextShadow
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.Rounded
import app.mikeneko.update.AppUpdates

/**
 * Une mise à jour est téléchargée : une pilule en verre en haut de l'écran, « Mise à jour prête /
 * Mikeneko X » et « Installer » (installe tout de suite ; ou ouvre le réglage Android s'il faut encore autoriser
 * les installations). ✕ : plus tard (elle s'installera quand on quitte l'appli). Demandé le 05/11.
 */
@Composable
fun UpdatePill(backdrop: Backdrop, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by AppUpdates.state.collectAsState()
    val ready = state as? AppUpdates.State.Ready
    var dismissed by remember { mutableStateOf<String?>(null) }
    // La dernière version prête, gardée pour que la pilule reste remplie pendant qu'elle s'en va
    var shown by remember { mutableStateOf<AppUpdates.State.Ready?>(null) }
    if (ready != null) shown = ready
    AnimatedVisibility(
        visible = ready != null && dismissed != ready.release.version,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
    ) {
        val update = shown ?: return@AnimatedVisibility
        // Deux lignes (« Mise à jour prête » / « Mikeneko 2.1.8 ») et un bouton court : sur une seule
        // ligne, avec une police système agrandie, le texte était coupé en « Mikeneko 2.… »
        LiquidGlass(backdrop, Modifier.height(60.dp), cornerRadius = 30.dp, bezel = 16.dp, refraction = 16.dp, tint = Color(0x8C100D0B)) {
            Row(Modifier.padding(start = 20.dp, end = 6.dp).align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f, fill = false)) {
                    Text(
                        stringResource(R.string.update_pill),
                        style = TextStyle(fontFamily = Rounded, color = Color(0xFFE3DAD2), fontSize = 12.sp, fontWeight = FontWeight.Bold, shadow = GlassTextShadow),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.update_pill_version, update.release.version),
                        style = TextStyle(fontFamily = Rounded, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, shadow = GlassTextShadow),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.update_now),
                    Modifier
                        .clip(CircleShape)
                        .background(Pelage.Creme)
                        .clickable(role = Role.Button) {
                            if (AppUpdates.canInstall(context)) {
                                AppUpdates.install(context, update.file)
                            } else {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                                }
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    color = Pelage.Encre,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false,
                )
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = stringResource(R.string.update_later)) {
                        dismissed = update.release.version
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(MkIcons.Close, stringResource(R.string.update_later), tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
