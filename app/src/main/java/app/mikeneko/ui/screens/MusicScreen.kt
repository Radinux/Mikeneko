package app.mikeneko.ui.screens

import androidx.compose.animation.core.tween
import app.mikeneko.ui.components.CONNECTED_GAP
import app.mikeneko.ui.components.ConnectedChip
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.data.MusicKind
import app.mikeneko.data.SearchHistory
import app.mikeneko.ui.components.GlassCircleButton
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.glass.LiquidGlass
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.GlassTextShadow
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.Rounded
import app.mikeneko.ui.theme.jellyClickable
import app.mikeneko.ui.theme.statusBarSpacePadding
import kotlin.math.roundToInt

/**
 * Ce qui est en verre par-dessus la page Musique : la barre du haut (loupe de la recherche musique,
 * ou retour quand une page est ouverte). Le dock musique et sa fenêtre sont dans [MusicDockWindow].
 */
@Composable
fun MusicOverlay(state: MusicState, backdrop: Backdrop) {
    Box(Modifier.fillMaxSize()) {
        MusicHeader(state, backdrop)
    }
}

/**
 * Le bouton lecture dans la pilule du haut (2.11.9) : il apparaît (fondu, léger grossissement) quand
 * l'en-tête est passé sous la barre, et part quand il revient. Deux états seulement, tous deux
 * cliquables : dans l'en-tête, ou ici. Avant, un bouton voyageait de l'un à l'autre en suivant le
 * défilement à chaque image (il accrochait au défilement rapide). Seul à se recomposer quand il change.
 */
@Composable
private fun PillPlayButton(state: MusicState, onPlay: () -> Unit) {
    val shown by remember(state) { androidx.compose.runtime.derivedStateOf { state.barProgress > 0.6f } }
    androidx.compose.animation.AnimatedVisibility(
        shown,
        enter = androidx.compose.animation.fadeIn(tween(160)) + androidx.compose.animation.scaleIn(tween(200), initialScale = 0.6f),
        exit = androidx.compose.animation.fadeOut(tween(120)) + androidx.compose.animation.scaleOut(tween(140), targetScale = 0.6f),
    ) {
        Box(
            Modifier
                .padding(start = 8.dp)
                .size(PLAY_SMALL)
                .clip(CircleShape)
                .background(Pelage.Roux)
                .jellyClickable(role = Role.Button, onClickLabel = stringResource(R.string.play_all), onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(MkIcons.Play, stringResource(R.string.play_all), tint = Pelage.Encre, modifier = Modifier.size(20.dp))
        }
    }
}

/** La barre du haut de la Musique, en verre : loupe, champ de recherche, ou retour. */
@Composable
private fun MusicHeader(state: MusicState, backdrop: Backdrop) {
    val page = state.page
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    /** Lance la recherche (clavier, suggestion ou historique) et la garde dans l'historique musique. */
    fun submit(text: String) {
        val clean = text.trim()
        state.query = clean
        state.submitted = clean.ifEmpty { null }
        state.suggesting = false
        if (clean.isNotEmpty()) SearchHistory.music(context).add(clean)
        keyboard?.hide()
    }
    Column(Modifier.fillMaxWidth().statusBarSpacePadding().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                page != null -> {
                    GlassCircleButton(backdrop, MkIcons.ChevronLeft, stringResource(R.string.back)) { state.goBack() }
                    val bar = state.bar
                    // Une pilule en verre avec le titre de la page et le bouton lecture : elle arrive à mesure
                    // que l'en-tête passe sous la barre (la liste se déforme dessous en défilant)
                    val filtering = page is MusicPage.MyPlaylist && state.playlistFilter != null
                    Box(Modifier.weight(1f).padding(horizontal = 10.dp).onGloballyPositioned { state.barPill = it.boundsInRoot() }) {
                        if (filtering) {
                            PlaylistFilterField(state, backdrop)
                        } else if (bar != null) {
                            LiquidGlass(
                                backdrop,
                                Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .graphicsLayer {
                                        val p = state.barProgress
                                        alpha = p
                                        translationY = (1f - p) * 14.dp.toPx()
                                        scaleX = 0.9f + 0.1f * p
                                        scaleY = 0.9f + 0.1f * p
                                    },
                                cornerRadius = 25.dp,
                                bezel = 16.dp,
                                refraction = 16.dp,
                            ) {
                                Row(
                                    Modifier.fillMaxSize().padding(start = 18.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        bar.title,
                                        Modifier.weight(1f),
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = TextStyle(shadow = GlassTextShadow),
                                    )
                                    // La place du bouton lecture : c'est celui de l'en-tête qui vient s'y ranger
                                    // (dessiné par TravellingPlayButton, au-dessus de la pilule)
                                    bar.onPlay?.let { PillPlayButton(state, it) }
                                }
                            }
                            // Visible, elle arrête les taps (sinon ils traversent jusqu'au morceau dessous) : un
                            // bloqueur à part (2.11.5). Avant, le détecteur était ajouté / retiré sur la vitre
                            // elle-même, qui était reconstruite à mi-course, en plein défilement (un petit arrêt).
                            PillTapBlocker(state, Modifier.matchParentSize())
                        }
                    }
                    // Playlist perso : une loupe pour chercher dedans (2.11.3, à la place du ⋮ : renommer et
                    // supprimer sont dans « Modifier »)
                    if (page is MusicPage.MyPlaylist) {
                        if (filtering) {
                            GlassCircleButton(backdrop, MkIcons.Close, stringResource(R.string.search_close)) { state.playlistFilter = null }
                        } else {
                            GlassCircleButton(backdrop, MkIcons.Search, stringResource(R.string.playlist_search)) { state.playlistFilter = "" }
                        }
                    }
                }
                state.searching -> {
                    MusicSearchField(state, backdrop, focus, Modifier.weight(1f), onSubmit = ::submit)
                    Spacer(Modifier.width(10.dp))
                    val empty = state.query.isEmpty() && state.submitted == null
                    GlassCircleButton(backdrop, MkIcons.Close, stringResource(if (empty) R.string.search_close else R.string.search_clear)) {
                        if (empty) {
                            state.closeSearch() // plus rien à effacer : on ferme
                        } else {
                            // ✕ efface le champ sans fermer, clavier ouvert (les résultats restent dessous)
                            state.query = ""
                            state.suggesting = true
                            runCatching { focus.requestFocus() }
                            keyboard?.show()
                        }
                    }
                }
                else -> {
                    Spacer(Modifier.weight(1f))
                    GlassCircleButton(backdrop, MkIcons.Search, stringResource(R.string.music_search)) { state.openSearch() }
                }
            }
        }
        // Les filtres en verre, posés sur la liste : bibliothèque, ou genre de résultat dans la recherche
        if (page == MusicPage.Library) {
            GlassChipsRow(backdrop, LibraryFilter.entries, state.libraryFilter, { libraryFilterLabel(it) }) { state.libraryFilter = it }
        } else if (page == null && state.searching && !state.suggesting) {
            GlassChipsRow(backdrop, MusicKind.entries, state.kind, { musicKindLabel(it) }) { state.kind = it }
        }
        AnimatedSearchSuggestions(
            visible = page == null && state.searching && state.suggesting,
            query = state.query,
            backdrop = backdrop,
            onPick = ::submit,
            onFill = { state.query = it }, // complète le champ, sans chercher
            modifier = Modifier.padding(horizontal = 16.dp),
            music = true,
            )
    }
}

@Composable
private fun MusicSearchField(state: MusicState, backdrop: Backdrop, focus: FocusRequester, modifier: Modifier, onSubmit: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    // À l'ouverture (pas en revenant d'un album) : le clavier pour taper tout de suite
    LaunchedEffect(Unit) {
        if (state.submitted == null) {
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }
    fun submit() = onSubmit(state.query)
    // Texte + curseur : quand la recherche change d'ailleurs (suggestion, ↖), le curseur va à la fin
    var field by remember { mutableStateOf(TextFieldValue(state.query, TextRange(state.query.length))) }
    LaunchedEffect(state.query) {
        if (field.text != state.query) field = TextFieldValue(state.query, TextRange(state.query.length))
    }
    LiquidGlass(
        backdrop,
        modifier
            .height(50.dp)
            // Toucher le champ rouvre l'historique / les suggestions (même après une recherche)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    state.suggesting = true
                }
            },
        cornerRadius = 25.dp,
        bezel = 16.dp,
        refraction = 16.dp,
    ) {
        BasicTextField(
            value = field,
            onValueChange = {
                field = it
                if (it.text != state.query) {
                    state.suggesting = true // on tape : les suggestions reviennent
                    state.query = it.text
                }
            },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontFamily = Rounded, fontWeight = FontWeight.Bold, shadow = GlassTextShadow),
            cursorBrush = SolidColor(Pelage.RouxClair),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(horizontal = 18.dp).focusRequester(focus),
            decorationBox = { inner ->
                if (state.query.isEmpty()) Text(stringResource(R.string.music_search_hint), color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                inner()
            },
        )
    }
}

/** Arrête les taps sur la pilule du haut une fois visible ; seul à se recomposer quand elle le devient. */
@Composable
private fun PillTapBlocker(state: MusicState, modifier: Modifier) {
    val active by remember(state) { androidx.compose.runtime.derivedStateOf { state.barProgress > 0.5f } }
    if (active) Box(modifier.pointerInput(Unit) { detectTapGestures { } })
}

/** Le champ de recherche dans une playlist perso, en verre, dans la barre du haut (2.11.3). */
@Composable
private fun PlaylistFilterField(state: MusicState, backdrop: Backdrop) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LiquidGlass(backdrop, Modifier.fillMaxWidth().height(50.dp), cornerRadius = 25.dp, bezel = 16.dp, refraction = 16.dp) {
        BasicTextField(
            value = state.playlistFilter.orEmpty(),
            onValueChange = { state.playlistFilter = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontFamily = Rounded, fontWeight = FontWeight.Bold, shadow = GlassTextShadow),
            cursorBrush = SolidColor(Pelage.RouxClair),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(horizontal = 18.dp).focusRequester(focus),
            decorationBox = { inner ->
                if (state.playlistFilter.isNullOrEmpty()) {
                    Text(stringResource(R.string.playlist_search), color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                inner()
            },
        )
    }
}

/** Une rangée de capsules (défile de côté), sous la barre du haut : un groupe connecté Material 3 (2.10.0). */
@Composable
private fun <T> GlassChipsRow(backdrop: Backdrop, options: List<T>, selected: T, label: (T) -> Int, onPick: (T) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(CONNECTED_GAP),
    ) {
        items(options.size) { i ->
            val option = options[i]
            ConnectedChip(stringResource(label(option)), option == selected, first = i == 0, last = i == options.lastIndex) { onPick(option) }
        }
    }
}
