package app.mikeneko.update

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.ui.components.Mascot
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.Rounded
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Le tuto (2.26.0) : quelques pages au premier lancement, et après avoir effacé les données de
 * l'appli (pour elle, c'est pareil). Pas après une simple mise à jour : là, c'est « Quoi de neuf ».
 * Toujours passable ; Réglages › À propos › « Revoir le tuto » le rouvre.
 */
object Tutorial {
    private const val PREFS = "tutoriel"
    private const val SEEN = "vu"

    /** Ouvert maintenant ? (rempli par [check] au démarrage, ou par [replay]). */
    val open = MutableStateFlow(false)
    private var checked = false

    /**
     * À appeler une fois au démarrage, AVANT « Quoi de neuf » (qui note la version vue) : premier
     * lancement = installation neuve, ou données effacées (« Quoi de neuf » n'a jamais rien noté).
     */
    fun check(context: Context) {
        if (checked) return
        checked = true
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(SEEN, false)) return
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val freshInstall = info == null || info.firstInstallTime == info.lastUpdateTime
        val dataCleared = !WhatsNew.everSeen(context)
        if (freshInstall || dataCleared) {
            open.value = true
            // Le tuto présente déjà tout : pas de « Quoi de neuf » par-dessus
            WhatsNew.markSeen(context)
        } else {
            markSeen(context) // déjà utilisateur : il connaît l'appli
        }
    }

    fun replay() {
        open.value = true
    }

    fun close(context: Context) {
        markSeen(context)
        open.value = false
    }

    private fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(SEEN, true).apply()
    }
}

private class TutorialPage(val icon: ImageVector?, val title: Int, val lines: List<Int>)

private val PAGES = listOf(
    TutorialPage(null, R.string.tuto_welcome_title, listOf(R.string.tuto_welcome_1, R.string.tuto_welcome_2)),
    TutorialPage(MkIcons.Download, R.string.tuto_import_title, listOf(R.string.tuto_import_1, R.string.tuto_import_2, R.string.tuto_import_3)),
    TutorialPage(MkIcons.Video, R.string.tuto_player_title, listOf(R.string.tuto_player_1, R.string.tuto_player_2, R.string.tuto_player_3, R.string.tuto_player_4, R.string.tuto_player_5)),
    TutorialPage(MkIcons.Music, R.string.tuto_music_title, listOf(R.string.tuto_music_1, R.string.tuto_music_2, R.string.tuto_music_3, R.string.tuto_music_4)),
    TutorialPage(MkIcons.Globe, R.string.tuto_devices_title, listOf(R.string.tuto_devices_1, R.string.tuto_devices_2, R.string.tuto_devices_3)),
    TutorialPage(MkIcons.Paw, R.string.tuto_ready_title, listOf(R.string.tuto_ready_1)),
)
private const val IMPORT_PAGE = 1

/**
 * Les pages, en plein écran au-dessus de tout. *Passer* en haut à droite sur chaque page (sauf la
 * dernière, qui a son grand bouton) ; retour = passer aussi. [onImport] : ouvre les imports (Réglages).
 */
@Composable
fun TutorialSheet(onImport: () -> Unit) {
    val context = LocalContext.current
    remember { Tutorial.check(context) }
    val open by Tutorial.open.collectAsState()
    if (open) BackHandler { Tutorial.close(context) }
    AnimatedVisibility(open, enter = fadeIn(tween(300)), exit = fadeOut(tween(260))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Pelage.Nuit)
                .background(Brush.radialGradient(listOf(Pelage.Roux.copy(alpha = 0.22f), Color.Transparent), radius = 1400f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }, // rien ne passe dessous
        ) {
            val pager = rememberPagerState { PAGES.size }
            val scope = rememberCoroutineScope()
            val last = pager.currentPage == PAGES.lastIndex
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                // En haut : les points, et « Passer » toujours à portée
                Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dots(PAGES.size, pager.currentPage)
                    Spacer(Modifier.weight(1f))
                    AnimatedVisibility(!last, enter = fadeIn(), exit = fadeOut()) {
                        TextButton(onClick = { Tutorial.close(context) }) {
                            Text(stringResource(R.string.tuto_skip), color = Pelage.Texte, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { index ->
                    Page(PAGES[index], showImport = index == IMPORT_PAGE) {
                        Tutorial.close(context)
                        onImport()
                    }
                }
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = {
                            if (last) Tutorial.close(context) else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        },
                        modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).height(56.dp),
                    ) {
                        Text(stringResource(if (last) R.string.tuto_go else R.string.tuto_next), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Page(page: TutorialPage, showImport: Boolean, onImport: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (page.icon == null) {
            Mascot(size = 132.dp)
        } else {
            Box(Modifier.size(120.dp).clip(CircleShape).background(Pelage.Roux), contentAlignment = Alignment.Center) {
                Icon(page.icon, null, tint = Pelage.Encre, modifier = Modifier.size(58.dp))
            }
        }
        Text(
            stringResource(page.title),
            Modifier.padding(top = 28.dp, bottom = 18.dp).semantics { heading() },
            style = TextStyle(fontFamily = Rounded, color = Pelage.Texte, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center),
        )
        Column(Modifier.widthIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            page.lines.forEach { line ->
                Row(verticalAlignment = Alignment.Top) {
                    if (page.lines.size > 1) {
                        Box(Modifier.padding(top = 8.dp).size(7.dp).clip(CircleShape).background(Pelage.RouxClair))
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        stringResource(line),
                        color = Color(0xFFE6DED6),
                        fontSize = 16.sp,
                        lineHeight = 23.sp,
                        textAlign = if (page.lines.size > 1) TextAlign.Start else TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (showImport) {
            FilledTonalButton(onClick = onImport, modifier = Modifier.padding(top = 22.dp).heightIn(min = 48.dp)) {
                Icon(MkIcons.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tuto_import_now), fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/** Les points de page : le point de la page en cours s'allonge en pilule. */
@Composable
private fun Dots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 22.dp else 8.dp, tween(240), label = "point")
            val color by animateColorAsState(if (i == current) Pelage.RouxClair else Pelage.Texte.copy(alpha = 0.3f), tween(240), label = "point")
            Box(Modifier.height(8.dp).width(width).clip(CircleShape).background(color))
        }
    }
}
