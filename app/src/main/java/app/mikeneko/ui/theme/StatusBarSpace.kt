package app.mikeneko.ui.theme

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * La place de la barre d'état du téléphone, qu'elle soit affichée ou masquée (réglage) : la mise
 * en page ne bouge pas, et rien ne passe sous l'objectif de la caméra quand elle est cachée.
 */
@OptIn(ExperimentalLayoutApi::class)
val WindowInsets.Companion.statusBarSpace: WindowInsets
    @Composable get() = WindowInsets.statusBarsIgnoringVisibility

/** Comme `statusBarsPadding()`, mais garde la place même quand la barre d'état est masquée. */
fun Modifier.statusBarSpacePadding(): Modifier = composed { windowInsetsPadding(WindowInsets.statusBarSpace) }
