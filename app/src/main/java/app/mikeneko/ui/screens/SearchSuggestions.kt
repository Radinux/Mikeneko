package app.mikeneko.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.data.SearchHistory
import app.mikeneko.data.YouTubeRepository
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.glass.LiquidGlass
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.Rounded
import kotlin.math.abs
import kotlinx.coroutines.delay

private const val MAX_ROWS = 10
private const val HISTORY_WHILE_TYPING = 4

/**
 * [SearchSuggestions] qui apparaît en se dépliant depuis la barre de recherche, avec un petit rebond
 * de gelée, et se replie en fondu. (Durées divisées par ANIMATION_SPEED à l'écran.)
 */
@Composable
fun AnimatedSearchSuggestions(
    visible: Boolean,
    query: String,
    backdrop: Backdrop,
    onPick: (String) -> Unit,
    onFill: (String) -> Unit,
    modifier: Modifier = Modifier,
    music: Boolean = false,
) {
    val top = TransformOrigin(0.5f, 0f)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(420)) +
            expandVertically(spring(dampingRatio = 0.8f, stiffness = 70f), expandFrom = Alignment.Top) +
            scaleIn(spring(dampingRatio = 0.5f, stiffness = 60f), initialScale = 0.86f, transformOrigin = top),
        // La sortie démarre en douceur : d'abord le fondu et un léger recul, puis le panneau se replie
        exit = fadeOut(tween(520, easing = LinearOutSlowInEasing)) +
            scaleOut(tween(560, easing = EaseInOutCubic), targetScale = 0.9f, transformOrigin = top) +
            shrinkVertically(tween(600, delayMillis = 120, easing = EaseInOutCubic), shrinkTowards = Alignment.Top),
    ) {
        // Pendant la sortie, le contenu ne bouge plus (sinon il change sous le fondu : recherche lancée…)
        val shown = remember { arrayOf(query) }
        if (visible) shown[0] = query
        SearchSuggestions(shown[0], backdrop, onPick, onFill, music = music)
    }
}

/**
 * Sous la barre de recherche, en verre : l'historique quand le champ est vide ; pendant la frappe,
 * les recherches passées qui correspondent puis les suggestions de YouTube.
 * Un tap lance la recherche ([onPick]), la flèche ↖ complète le champ sans chercher ([onFill]).
 * Glisser une recherche passée vers la gauche la supprime de l'historique.
 */
@Composable
fun SearchSuggestions(
    query: String,
    backdrop: Backdrop,
    onPick: (String) -> Unit,
    onFill: (String) -> Unit,
    modifier: Modifier = Modifier,
    music: Boolean = false,
) {
    val context = LocalContext.current
    val history = remember(music) { if (music) SearchHistory.music(context) else SearchHistory.get(context) }
    val past by history.items.collectAsState()
    val typed = query.trim()

    // Suggestions de YouTube, un court instant après la dernière lettre (les anciennes restent en attendant)
    val remote by produceState(emptyList<String>(), typed) {
        if (typed.isEmpty()) {
            value = emptyList()
            return@produceState
        }
        delay(200)
        value = runCatching { YouTubeRepository.suggestions(typed) }.getOrDefault(value)
    }

    val fromHistory = if (typed.isEmpty()) {
        past.take(MAX_ROWS)
    } else {
        past.filter { it.contains(typed, ignoreCase = true) && !it.equals(typed, ignoreCase = true) }.take(HISTORY_WHILE_TYPING)
    }
    val fromYouTube = remote
        .filterNot { r -> r.equals(typed, ignoreCase = true) || fromHistory.any { it.equals(r, ignoreCase = true) } }
        .take(MAX_ROWS - fromHistory.size)
    if (fromHistory.isEmpty() && fromYouTube.isEmpty()) return

    LiquidGlass(
        backdrop = backdrop,
        modifier = modifier.fillMaxWidth().heightIn(max = 432.dp),
        cornerRadius = 24.dp,
        bezel = 20.dp,
        refraction = 20.dp,
        tint = Color(0x70100D0B), // assez clair pour voir le verre, le flou garde le texte lisible
        frost = 26.dp,
    ) {
        LazyColumn(contentPadding = PaddingValues(vertical = 6.dp)) {
            items(fromHistory, key = { "historique:$it" }) { text ->
                PastSearchRow(text, typed, onPick, onFill, onDelete = { history.remove(text) })
            }
            items(fromYouTube, key = { "youtube:$it" }) { text ->
                SuggestionRow(MkIcons.Search, text, typed, onPick, onFill)
            }
        }
    }
}

/** Une recherche passée : glisser vers la gauche la supprime (aussi possible au lecteur d'écran). */
@Composable
private fun PastSearchRow(text: String, typed: String, onPick: (String) -> Unit, onFill: (String) -> Unit, onDelete: () -> Unit) {
    val swipe = rememberSwipeToDismissBoxState()
    val removeLabel = stringResource(R.string.search_history_remove)
    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = false,
        onDismiss = { onDelete() },
        backgroundContent = {
            // Rouge seulement pendant le glissé (le verre du panneau est transparent)
            if (swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Box(Modifier.fillMaxSize().background(Pelage.Roux).padding(end = 22.dp), contentAlignment = Alignment.CenterEnd) {
                    Icon(MkIcons.Trash, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        },
        modifier = Modifier.semantics {
            customActions = listOf(CustomAccessibilityAction(removeLabel) { onDelete(); true })
        },
    ) {
        SuggestionRow(MkIcons.Clock, text, typed, onPick, onFill)
    }
}

@Composable
private fun SuggestionRow(icon: ImageVector, text: String, typed: String, onPick: (String) -> Unit, onFill: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(50.dp).clickable { onPick(text) }.padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            highlight(text, typed),
            Modifier.weight(1f),
            style = TextStyle(fontFamily = Rounded, color = Color.White, fontSize = 15.sp),
            maxLines = 1,
            // Trop long : on coupe le début (déjà tapé), pour voir la fin proposée
            overflow = TextOverflow.StartEllipsis,
        )
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = stringResource(R.string.search_fill, text)) { onFill(text) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(MkIcons.ArrowUpLeft, stringResource(R.string.search_fill, text), tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        }
    }
}

/** Comme sur YouTube : ce qui est déjà tapé en normal, la suite proposée en gras. */
private fun highlight(text: String, typed: String): AnnotatedString = buildAnnotatedString {
    if (typed.isNotEmpty() && text.startsWith(typed, ignoreCase = true)) {
        append(text.substring(0, typed.length))
        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(text.substring(typed.length)) }
    } else {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text) }
    }
}

/**
 * Appelle [onScroll] quand le doigt se met à faire défiler la page (vers le haut ou le bas), tant
 * que [active] : sert à ranger les suggestions et le clavier dès qu'on reprend les résultats.
 * N'empêche ni le défilement ni les taps (on ne fait qu'observer les gestes).
 */
fun Modifier.onUserScroll(active: () -> Boolean, onScroll: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!active()) return@awaitEachGesture
        var moved = Offset.Zero
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            moved += change.positionChange()
            if (abs(moved.y) > viewConfiguration.touchSlop && abs(moved.y) > abs(moved.x)) {
                onScroll()
                break
            }
        }
    }
}
