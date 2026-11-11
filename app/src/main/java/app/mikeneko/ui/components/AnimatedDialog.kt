package app.mikeneko.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.mikeneko.ui.theme.Pelage
import kotlinx.coroutines.flow.first

/**
 * Ferme une [AnimatedDialog] : la sortie joue (fondu, léger rétrécissement, le voile s'efface), puis
 * [then] est appelé (par défaut le `onDismissRequest` de la fenêtre, qui la retire de l'écran).
 */
@Stable
class DialogClose internal constructor(private val start: ((() -> Unit)?) -> Unit) {
    operator fun invoke(then: (() -> Unit)? = null) = start(then)
}

/**
 * Une fenêtre par-dessus l'app qui s'ouvre ET se ferme en douceur (2.23.4). Un `Dialog` seul disparaît
 * d'un coup quand on le retire de la composition : ici on garde la fenêtre le temps de la sortie, et on
 * dessine nous-mêmes le voile (celui du système s'éteint sans transition). Fermer passe toujours par
 * [DialogClose] (bouton, retour, tap à côté) ; [onDismissRequest] n'est appelé qu'une fois la sortie finie.
 *
 * [fullScreen] : le contenu prend tout l'écran (et gère lui-même les barres système) ; sinon il est centré,
 * avec une marge, et un tap à côté ferme.
 */
@Composable
fun AnimatedDialog(
    onDismissRequest: () -> Unit,
    fullScreen: Boolean = false,
    margin: androidx.compose.ui.unit.Dp = 24.dp,
    content: @Composable (close: DialogClose) -> Unit,
) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    var after by remember { mutableStateOf<(() -> Unit)?>(null) }
    var gone by remember { mutableStateOf(false) }
    val dismiss by rememberUpdatedState(onDismissRequest)
    val close = remember {
        DialogClose { then ->
            if (state.targetState) {
                after = then ?: { dismiss() }
                state.targetState = false
            }
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { !state.targetState && !state.currentState && state.isIdle }.first { it }
        gone = true
        after?.invoke()
    }
    if (gone) return

    Dialog(
        onDismissRequest = { close() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // Pas de voile ni d'animation du système : les nôtres font l'entrée et la sortie
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(0f)
            window?.setWindowAnimations(0)
        }
        val transition = rememberTransition(state, label = "dialog")
        val scrim by transition.animateFloat({ tween(if (targetState) 320 else 260) }, label = "scrim") { if (it) 0.6f else 0f }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrim))
                .then(if (fullScreen) Modifier else Modifier.pointerInput(Unit) { detectTapGestures { close() } }.safeDrawingPadding()),
            contentAlignment = Alignment.Center,
        ) {
            transition.AnimatedVisibility(
                visible = { it },
                enter = fadeIn(tween(320)) + scaleIn(tween(320), initialScale = if (fullScreen) 0.96f else 0.88f),
                exit = fadeOut(tween(260)) + scaleOut(tween(260), targetScale = if (fullScreen) 0.96f else 0.9f),
            ) {
                Box(
                    if (fullScreen) {
                        Modifier.fillMaxSize()
                    } else {
                        // Un tap sur la fenêtre elle-même ne la ferme pas
                        Modifier.padding(horizontal = margin).widthIn(max = 560.dp).pointerInput(Unit) { detectTapGestures { } }
                    },
                    contentAlignment = Alignment.Center,
                ) { content(close) }
            }
        }
    }
}

/**
 * L'équivalent d'`AlertDialog` (Material 3 : mêmes marges, formes et styles de texte), fermé en douceur
 * par [AnimatedDialog]. Les boutons reçoivent le [DialogClose] à appeler au lieu de retirer la fenêtre.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimatedAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable (close: DialogClose) -> Unit,
    dismissButton: (@Composable (close: DialogClose) -> Unit)? = null,
    containerColor: Color = Pelage.Coussin,
    titleContentColor: Color = Pelage.Texte,
    textContentColor: Color = Pelage.Gris,
) {
    AnimatedDialog(onDismissRequest, margin = 40.dp) { close ->
        Column(
            Modifier.widthIn(min = 280.dp).clip(RoundedCornerShape(28.dp)).background(containerColor).padding(24.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                    Box(Modifier.padding(bottom = 16.dp)) { title() }
                }
            }
            CompositionLocalProvider(LocalContentColor provides textContentColor) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    Box(Modifier.weight(1f, fill = false).padding(bottom = 24.dp)) { text() }
                }
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                dismissButton?.invoke(close)
                confirmButton(close)
            }
        }
    }
}
