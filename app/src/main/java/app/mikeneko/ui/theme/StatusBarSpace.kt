package app.mikeneko.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalConfiguration
import app.mikeneko.settings.AppSettings

/**
 * La place de la barre d'état du téléphone, qu'elle soit affichée ou masquée (réglage) : la mise
 * en page ne bouge pas, et rien ne passe sous l'objectif de la caméra quand elle est cachée.
 *
 * 3.6.1 : en paysage avec la barre masquée, plus de place gardée : la caméra est alors sur le côté
 * (gérée par les marges latérales), et cette bande vide en haut descendait toutes les pages pour rien.
 * Ça suit le réglage, pas la barre qui réapparaît un instant d'un glissé : rien ne saute.
 */
@OptIn(ExperimentalLayoutApi::class)
val WindowInsets.Companion.statusBarSpace: WindowInsets
    @Composable get() {
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val hidden by AppSettings.hideStatusBar.collectAsState()
        return if (landscape && hidden) NONE else WindowInsets.statusBarsIgnoringVisibility
    }

private val NONE = WindowInsets(0, 0, 0, 0)

/** Comme `statusBarsPadding()`, mais garde la place même quand la barre d'état est masquée (sauf en paysage, voir plus haut). */
fun Modifier.statusBarSpacePadding(): Modifier = composed { windowInsetsPadding(WindowInsets.statusBarSpace) }
