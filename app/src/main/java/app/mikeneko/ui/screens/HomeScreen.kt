package app.mikeneko.ui.screens

import androidx.compose.foundation.layout.offset

import app.mikeneko.ui.components.GlassChip

import app.mikeneko.ui.components.columnsFor
import app.mikeneko.ui.components.adaptiveItems
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import app.mikeneko.ui.theme.statusBarSpace
import app.mikeneko.ui.theme.statusBarSpacePadding
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import app.mikeneko.data.SearchHistory
import app.mikeneko.data.ChannelItem
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.delay
import android.widget.Toast
import androidx.compose.ui.text.TextRange
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import app.mikeneko.ui.components.VideoMenu
import app.mikeneko.data.Feedback
import app.mikeneko.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import androidx.annotation.StringRes
import app.mikeneko.settings.AppLocale
import androidx.compose.ui.platform.LocalContext
import app.mikeneko.data.ForYou
import app.mikeneko.data.VideoItem
import app.mikeneko.data.YouTubeRepository
import app.mikeneko.ui.components.Capsule
import app.mikeneko.ui.components.EmptyCat
import app.mikeneko.ui.components.GlassCircleButton
import app.mikeneko.ui.components.Loading
import app.mikeneko.ui.components.Mascot
import app.mikeneko.ui.components.VideoCard
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.glass.LiquidGlass
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.GlassTextShadow
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.Rounded

class HomeState {
    var category by mutableStateOf("for_you")
    var searching by mutableStateOf(false)
    var query by mutableStateOf("")
    var submitted by mutableStateOf<String?>(null)
    /** Le panneau historique / suggestions sous la barre de recherche est ouvert. */
    var suggesting by mutableStateOf(false)
    /** L'ordre des résultats de recherche (gardé d'une recherche à l'autre, jusqu'à la fermeture). */
    var sort by mutableStateOf(SearchSort.Best)
    var filters by mutableStateOf(SearchFilters())
    /** Le panneau des filtres est ouvert. */
    var filtersOpen by mutableStateOf(false)
    /** La chaîne ouverte depuis la recherche (par-dessus les résultats). */
    var channel by mutableStateOf<ChannelItem?>(null)
    /** Dernière liste affichée par rubrique : reste visible pendant qu'une nouvelle se calcule. */
    internal val lastShown = HashMap<Any, List<VideoItem>>()
    /** Position de la liste (gardée ici pour que le bouton retour puisse remonter en haut). */
    var scrolledDown by mutableStateOf(false)
        internal set
    /** De combien le haut de la liste (sous lequel sont les rubriques en verre) a défilé, en px ; MAX = parti. */
    var categoriesShift by androidx.compose.runtime.mutableIntStateOf(0)
        internal set
    internal var scrollToTop by mutableStateOf(0)
    /** La liste et son « tirer pour rafraîchir » : les rubriques en verre (au-dessus) leur passent les glissements verticaux. */
    internal var list by mutableStateOf<androidx.compose.foundation.lazy.LazyListState?>(null)
    internal var pull by mutableStateOf<PullToRefreshState?>(null)
    internal var refreshing by mutableStateOf(false)
    internal var onRefresh: () -> Unit = {}

    /** Ce que le bouton retour peut encore « défaire » avant de quitter l'appli. */
    val canGoBack: Boolean get() = channel != null || searching || submitted != null || category != "for_you" || scrolledDown

    /**
     * On refait défiler la page pendant que les suggestions sont ouvertes : on les range (comme le
     * retour). Sur des résultats, le texte cherché revient ; sans rien de tapé, la recherche se ferme.
     */
    fun dismissSuggestions() {
        if (!suggesting) return
        when {
            submitted != null -> {
                suggesting = false
                query = submitted.orEmpty()
            }
            query.isEmpty() -> goBack()
            else -> suggesting = false
        }
    }

    /**
     * L'onglet Accueil touché : retour au fil « Pour toi » (recherche et chaîne fermées).
     * Renvoie false s'il y était déjà (l'appelant remonte alors en haut de la liste).
     */
    fun showFeed(): Boolean {
        if (channel == null && !searching && submitted == null && category == "for_you") return false
        channel = null
        searching = false
        suggesting = false
        sort = SearchSort.Best
        filters = SearchFilters()
        filtersOpen = false
        query = ""
        submitted = null
        category = "for_you"
        return true
    }

    /** Retour sur l'accueil : fermer la recherche, puis revenir à « Pour toi », puis remonter en haut. */
    fun goBack() {
        when {
            channel != null -> channel = null // d'abord, on revient aux résultats
            filtersOpen -> filtersOpen = false
            // Suggestions ouvertes par-dessus des résultats : on revient d'abord aux résultats
            suggesting && submitted != null -> {
                suggesting = false
                query = submitted.orEmpty()
            }
            searching || submitted != null -> {
                searching = false
                suggesting = false
                sort = SearchSort.Best
                filters = SearchFilters()
                filtersOpen = false
                query = ""
                submitted = null
            }
            category != "for_you" -> category = "for_you"
            scrolledDown -> scrollToTop++
        }
    }
}

/**
 * Une rubrique de l'accueil : un fil de YouTube ([kiosk]) quand il existe, sinon (ou s'il échoue)
 * une recherche ([query]). Les directs ne vont que dans « En direct » ([lives]).
 * « Pour toi » (ni l'un ni l'autre) = le fil par défaut. La musique est dans l'onglet Radio.
 */
private class Category(
    val id: String,
    @StringRes val label: Int,
    val kiosk: String? = null,
    @StringRes val query: Int? = null,
    val lives: Boolean = false,
)

private val CATEGORIES = listOf(
    Category("for_you", R.string.cat_for_you),
    Category("live", R.string.cat_live, kiosk = "live", query = R.string.query_live, lives = true),
    Category("gaming", R.string.cat_gaming, kiosk = "trending_gaming", query = R.string.query_gaming),
    Category("sport", R.string.cat_sport, query = R.string.query_sport),
    Category("cooking", R.string.cat_cooking, query = R.string.query_cooking),
    Category("science", R.string.cat_science, query = R.string.query_science),
    Category("tech", R.string.cat_tech, query = R.string.query_tech),
    Category("comedy", R.string.cat_comedy, query = R.string.query_comedy),
    Category("movies", R.string.cat_movies, kiosk = "trending_movies_and_shows", query = R.string.query_movies),
    Category("podcasts", R.string.cat_podcasts, kiosk = "trending_podcasts_episodes", query = R.string.query_podcasts),
    Category("travel", R.string.cat_travel, query = R.string.query_travel),
    Category("animals", R.string.cat_animals, query = R.string.query_animals),
    Category("diy", R.string.cat_diy, query = R.string.query_diy),
)

/** La recherche d'une rubrique, dans la langue de l'appli (« recette cuisine facile » / « easy cooking recipe »). */
private fun Category.searchText(): String? = query?.let { AppLocale.string(it) }

/** Ce que montre l'accueil : une recherche tapée, ou une rubrique. */
private sealed interface HomeFeed {
    data class Search(val query: String) : HomeFeed
    data class Section(val category: Category) : HomeFeed
}

private fun feedOf(state: HomeState): HomeFeed =
    state.submitted?.let { HomeFeed.Search(it) }
        ?: HomeFeed.Section(CATEGORIES.firstOrNull { it.id == state.category } ?: CATEGORIES.first())

/** Garde les directs seulement là où on les veut. */
private fun List<VideoItem>.forFeed(feed: HomeFeed): List<VideoItem> = when (feed) {
    is HomeFeed.Search -> this // une recherche montre tout
    is HomeFeed.Section -> if (feed.category.lives) filter { it.isLive }.ifEmpty { this } else filterNot { it.isLive }
}

private fun cachedFeed(context: Context, feed: HomeFeed): List<VideoItem>? = when (feed) {
    is HomeFeed.Search -> YouTubeRepository.cachedSearch(feed.query)
    is HomeFeed.Section -> with(feed.category) {
        val query = searchText()
        when {
            kiosk != null -> YouTubeRepository.cachedKiosk(kiosk) ?: query?.let(YouTubeRepository::cachedSearch)
            query != null -> YouTubeRepository.cachedSearch(query)
            else -> ForYou.cached(context)
        }
    }
}?.forFeed(feed)

/** [fresh] : tirer pour rafraîchir, sans le cache et avec du contenu renouvelé. */
private suspend fun loadFeed(context: Context, feed: HomeFeed, fresh: Boolean = false): List<VideoItem> = when (feed) {
    is HomeFeed.Search -> YouTubeRepository.search(feed.query, fresh)
    is HomeFeed.Section -> with(feed.category) {
        val query = searchText()
        val fromKiosk = kiosk?.let { runCatching { YouTubeRepository.kiosk(it, fresh) }.getOrNull() }?.forFeed(feed)
        when {
            !fromKiosk.isNullOrEmpty() -> fromKiosk
            query != null -> YouTubeRepository.search(query, fresh)
            else -> ForYou.build(context, fresh) // « Pour toi » : l'algo local
        }
    }
}.forFeed(feed)

/** Les vidéos vues en haut de la liste avant de rafraîchir : elles passent après les nouvelles. */
private const val SEEN_ON_TOP = 12

/** Le contenu qui défile (c'est lui que le verre réfracte). */
@Composable
fun HomeContent(state: HomeState, onOpen: (VideoItem) -> Unit) {
    val context = LocalContext.current
    val feed = feedOf(state)
    // Une chaîne ouverte depuis la recherche
    state.channel?.let {
        ChannelContent(it, onOpen)
        return
    }
    // Une recherche tapée a son propre écran (tri, suite au défilement, chaînes)
    if (feed is HomeFeed.Search) {
        SearchResultsContent(state, feed.query, onOpen)
        return
    }
    var videos by remember(feed) { mutableStateOf(cachedFeed(context, feed) ?: state.lastShown[feed]) }
    LaunchedEffect(feed) {
        val cached = cachedFeed(context, feed)
        if (cached != null) {
            videos = cached
        } else {
            // « Pour toi » se recalcule après chaque vidéo regardée : l'ancienne liste reste à l'écran en attendant
            videos = state.lastShown[feed]
            val loaded = runCatching { loadFeed(context, feed) }.getOrDefault(emptyList())
            if (loaded.isNotEmpty() || videos == null) videos = loaded
        }
        videos?.takeIf { it.isNotEmpty() }?.let { state.lastShown[feed] = it }
    }
    // « Pas intéressé » / chaîne bloquée : la vidéo disparaît tout de suite
    // (sauf dans une recherche tapée, comme sur YouTube)
    val feedback = remember { Feedback.get(context) }
    val hidden by feedback.hidden.collectAsState()
    val blocked by feedback.blocked.collectAsState()

    val shownVideos = remember(videos, hidden, blocked, feed) {
        videos?.let { list -> if (feed is HomeFeed.Search) list else list.filter(feedback::allows) }
    }

    // Nouvelle rubrique : on repart du haut
    val list = rememberLazyListState()
    LaunchedEffect(feed) { list.scrollToItem(0) }
    LaunchedEffect(state.scrollToTop) { if (state.scrollToTop > 0) list.animateScrollToItem(0) }
    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex > 0 }.collect { state.scrolledDown = it }
    }
    // Les rubriques en verre (au-dessus, voir HomeOverlay) suivent le haut de la liste
    LaunchedEffect(list) {
        snapshotFlow { if (list.firstVisibleItemIndex == 0) list.firstVisibleItemScrollOffset else Int.MAX_VALUE }
            .collect { state.categoriesShift = it }
    }

    // Tirer vers le bas : de nouvelles vidéos (celles déjà vues en haut passent à la fin)
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    val refreshFailed = stringResource(R.string.refresh_failed)
    fun refresh() {
        if (refreshing) return
        refreshing = true
        val seenOnTop = shownVideos.orEmpty().take(SEEN_ON_TOP).map { it.url }.toSet()
        scope.launch {
            val started = System.currentTimeMillis()
            val fresh = runCatching { loadFeed(context, feed, fresh = true) }.getOrNull()
            delay((600 - (System.currentTimeMillis() - started)).coerceAtLeast(0)) // que le chat ait le temps de tourner
            if (fresh.isNullOrEmpty()) {
                Toast.makeText(context, refreshFailed, Toast.LENGTH_SHORT).show()
            } else {
                val (already, new) = fresh.partition { it.url in seenOnTop }
                videos = new + already
                state.lastShown[feed] = new + already
                list.scrollToItem(0)
            }
            refreshing = false
        }
    }
    val pull = rememberPullToRefreshState()
    androidx.compose.runtime.SideEffect {
        state.list = list
        state.pull = pull
        state.refreshing = refreshing
        state.onRefresh = ::refresh
    }

    val cardColumns = columnsFor(300.dp) // une colonne en portrait, plusieurs en paysage
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = ::refresh,
        state = pull,
        modifier = Modifier.fillMaxSize(),
        indicator = { CatRefreshIndicator(pull, refreshing, Modifier.align(Alignment.TopCenter)) },
    ) {
    LazyColumn(
        Modifier.fillMaxSize(),
        state = list,
        contentPadding = PaddingValues(bottom = 200.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        val items = shownVideos
        // En haut de la page, sous le header en verre : la place des rubriques en verre (dessinées
        // au-dessus de la liste, dans HomeOverlay ; elles partent avec le défilement)
        item(key = "haut") {
            Column(Modifier.statusBarSpacePadding().padding(top = 62.dp)) { Spacer(Modifier.height(CATEGORIES_HEIGHT)) }
        }
        when {
            items == null -> item { Loading(Modifier.padding(top = 80.dp)) }
            items.isEmpty() -> item {
                EmptyCat(stringResource(R.string.nothing_found), stringResource(R.string.nothing_found_text), Modifier.padding(top = 60.dp))
            }
            else -> {
                adaptiveItems(items, cardColumns, key = { it.url }) { video, modifier ->
                    VideoCard(video, modifier, onMore = { VideoMenu.open(video) }) { onOpen(video) }
                }
            }
        }
    }
    }
}

/**
 * L'indicateur du « tirer pour rafraîchir » : le chat de Mikeneko, sous les capsules du header.
 * Il gonfle sur place et tourne avec le doigt (sans glisser de sous les capsules, qui le
 * cacheraient), descend un peu si on tire plus loin, puis tourne en rebondissant pendant le chargement.
 */
@Composable
internal fun CatRefreshIndicator(state: PullToRefreshState, refreshing: Boolean, modifier: Modifier = Modifier) {
    val spin = rememberInfiniteTransition(label = "chat")
    val turning by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "tour")
    val hop by spin.animateFloat(1f, 1.12f, infiniteRepeatable(tween(300), RepeatMode.Reverse), label = "rebond")
    Box(
        modifier
            .statusBarSpacePadding()
            .padding(top = 128.dp) // bien sous les capsules : rien n'est caché derrière le verre
            .graphicsLayer {
                val pulled = if (refreshing) 1f else state.distanceFraction.coerceIn(0f, 1.4f)
                translationY = (pulled - 1f).coerceAtLeast(0f) * 40.dp.toPx() // seulement vers le bas
                alpha = (pulled * 2f).coerceIn(0f, 1f)
                val grow = 0.2f + 0.8f * pulled.coerceAtMost(1f)
                scaleX = grow * (if (refreshing) hop else 1f)
                scaleY = grow * (if (refreshing) hop else 1f)
                rotationZ = if (refreshing) turning else pulled * 270f
            }
            .size(48.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(Pelage.Creme),
        contentAlignment = Alignment.Center,
    ) {
        Mascot(size = 34.dp)
    }
}

/** Le header flottant en liquid glass, au-dessus du contenu. */
@Composable
fun HomeOverlay(state: HomeState, backdrop: Backdrop, onSettings: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val searchHistory = remember { SearchHistory.get(context) }
    state.channel?.let {
        ChannelOverlay(it, backdrop, onBack = { state.channel = null })
        return
    }

    val focus = remember { FocusRequester() }

    /** Lance la recherche (clavier, suggestion ou historique) et la garde dans l'historique. */
    fun submit(text: String) {
        val clean = text.trim()
        state.query = clean
        state.submitted = clean.ifEmpty { null }
        state.suggesting = false
        if (clean.isNotEmpty()) searchHistory.add(clean)
        // Ferme le clavier pour voir les résultats, SANS retirer le focus : sinon Compose
        // redonne le focus au seul champ de l'écran et le clavier revient aussitôt.
        keyboard?.hide()
    }
    Box(Modifier.fillMaxSize()) {
    // Les rubriques de l'accueil, en verre sur la liste ; elles montent et s'effacent avec le haut de la liste
    if (state.submitted == null) HomeCategories(state, backdrop)
    Column(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBarSpace).padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp), // + 6 dp de marge des rubriques = 12 dp comme avant
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.searching) {
                LiquidGlass(
                    backdrop,
                    Modifier
                        .weight(1f)
                        .height(50.dp)
                        // Toucher le champ rouvre l'historique / les suggestions (même après une recherche)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                state.suggesting = true
                                state.filtersOpen = false
                            }
                        },
                    cornerRadius = 25.dp,
                    bezel = 16.dp,
                    refraction = 16.dp,
                ) {
                    // Champ à état : on peut le faire défiler au doigt, et après la recherche il
                    // réaffiche le début du texte (au lieu de rester bloqué sur la fin)
                    val field = rememberTextFieldState(state.query)
                    val scroll = rememberScrollState()
                    val scope = rememberCoroutineScope()
                    // Champ → état. Dans l'autre sens, seulement si la recherche a été changée d'ailleurs
                    // (sinon une lettre tapée entre deux synchronisations est effacée : « tarte » → « trte »)
                    var fromField by remember { mutableStateOf(state.query) }
                    LaunchedEffect(field) {
                        snapshotFlow { field.text.toString() }.collect {
                            if (it != state.query) state.suggesting = true // on tape : les suggestions reviennent
                            fromField = it
                            state.query = it
                        }
                    }
                    LaunchedEffect(state.query) {
                        if (state.query != fromField && field.text.toString() != state.query) field.setTextAndPlaceCursorAtEnd(state.query)
                    }
                    // À l'ouverture de la recherche : le clavier tout de suite
                    LaunchedEffect(Unit) {
                        if (state.submitted == null) {
                            runCatching { focus.requestFocus() }
                            keyboard?.show()
                        }
                    }
                    // Après une recherche, le champ réaffiche le début du texte
                    LaunchedEffect(state.submitted) {
                        if (state.submitted != null) {
                            field.edit { selection = TextRange(0) }
                            scroll.animateScrollTo(0)
                        }
                    }
                    BasicTextField(
                        state = field,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        scrollState = scroll,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontFamily = Rounded, fontWeight = FontWeight.Bold, shadow = GlassTextShadow),
                        cursorBrush = SolidColor(Pelage.RouxClair),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        onKeyboardAction = { submit(field.text.toString()) },
                        modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(horizontal = 18.dp).focusRequester(focus),
                        decorator = { inner ->
                            if (state.query.isEmpty()) Text(stringResource(R.string.search_hint), color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            inner()
                        },
                    )
                }
            } else {
                LiquidGlass(backdrop, Modifier.height(50.dp), cornerRadius = 25.dp, bezel = 16.dp, refraction = 16.dp) {
                    Row(Modifier.fillMaxHeight().padding(start = 9.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Mascot(size = 36.dp)
                        Spacer(Modifier.width(7.dp))
                        Text("Mikeneko", style = TextStyle(fontFamily = Rounded, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, shadow = GlassTextShadow))
                    }
                }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.width(10.dp))
            val clearLabel = stringResource(if (state.query.isEmpty() && state.submitted == null) R.string.search_close else R.string.search_clear)
            GlassCircleButton(backdrop, if (state.searching) MkIcons.Close else MkIcons.Search, if (state.searching) clearLabel else stringResource(R.string.search)) {
                when {
                    !state.searching -> {
                        state.searching = true
                        state.suggesting = true // à l'ouverture : l'historique des recherches
                    }
                    // Champ déjà vide, rien de cherché : il n'y a plus rien à effacer, on ferme
                    state.query.isEmpty() && state.submitted == null -> state.goBack()
                    else -> {
                        // ✕ efface le champ sans fermer : retour à la barre de recherche, clavier ouvert
                        // (les résultats restent dessous : le retour y ramène)
                        state.query = ""
                        state.suggesting = true
                        state.filtersOpen = false
                        runCatching { focus.requestFocus() }
                        keyboard?.show()
                    }
                }
            }
            if (!state.searching) {
                Spacer(Modifier.width(10.dp))
                GlassCircleButton(backdrop, MkIcons.Settings, stringResource(R.string.settings), onClick = onSettings)
            }
        }
        AnimatedSearchSuggestions(
                visible = state.searching && state.suggesting,
                query = state.query,
                backdrop = backdrop,
                onPick = ::submit,
                onFill = { state.query = it }, // complète le champ, sans chercher
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        if (state.searching && state.submitted != null && !state.suggesting) {
            // Sous les résultats : l'ordre (meilleurs, récentes, plus vues)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // D'abord les filtres (orange quand il y en a d'actifs, avec leur nombre)
                item(key = "filtres") {
                    val active = state.filters.active
                    GlassChip(
                        backdrop,
                        if (active > 0) stringResource(R.string.filters_count, active) else stringResource(R.string.filters),
                        selected = active > 0 || state.filtersOpen,
                        icon = MkIcons.Settings,
                    ) { state.filtersOpen = !state.filtersOpen }
                }
                items(SearchSort.entries, key = { it.name }) { sort ->
                    GlassChip(backdrop, stringResource(sort.label), selected = state.sort == sort) { state.sort = sort }
                }
            }
            if (state.filtersOpen) {
                SearchFiltersPanel(state.filters, backdrop, onChange = { state.filters = it }, Modifier.padding(horizontal = 16.dp))
            }
        }
    }
    }
}

/** La hauteur de la rangée des rubriques (capsule de 44 dp + marges pour son ombre). */
private val CATEGORIES_HEIGHT = 56.dp

/**
 * Les rubriques de l'accueil, en verre, posées sur le haut de la liste (au même endroit que la place
 * qu'elle leur garde) : elles défilent de côté, et montent en s'effaçant quand la liste défile.
 */
@Composable
private fun HomeCategories(state: HomeState, backdrop: Backdrop) {
    val shift = state.categoriesShift
    val density = androidx.compose.ui.platform.LocalDensity.current
    val gone = with(density) { CATEGORIES_HEIGHT.toPx() }
    val list = state.list
    val pull = state.pull
    // La rangée reste en place même partie (vide) : la retirer couperait un glissement commencé
    // sur une capsule, et son élan
    LazyRow(
        Modifier
            .windowInsetsPadding(WindowInsets.statusBarSpace)
            .padding(top = 62.dp)
            // au placement : le verre suit (borné : MAX = la liste est descendue)
            .offset { androidx.compose.ui.unit.IntOffset(0, -state.categoriesShift.coerceAtMost(4 * gone.toInt())) }
            .graphicsLayer { alpha = (1f - state.categoriesShift / gone).coerceIn(0f, 1f) }
            // Glisser verticalement sur une capsule fait défiler la liste dessous (et tire pour
            // rafraîchir en haut), comme si on la touchait elle ; le défilement de côté reste aux capsules
            .then(
                if (pull != null) {
                    Modifier.pullToRefresh(state.refreshing, pull, onRefresh = { state.onRefresh() })
                } else Modifier,
            )
            .then(if (list != null) Modifier.scrollable(list, Orientation.Vertical) else Modifier),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (shift < gone) items(CATEGORIES, key = { it.id }) { category ->
            GlassChip(backdrop, stringResource(category.label), selected = state.category == category.id) {
                state.category = category.id
                state.submitted = null
            }
        }
    }
}
