package app.mikeneko.ui.screens

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.data.VideoItem
import app.mikeneko.data.YouTubeRepository
import app.mikeneko.ui.components.Capsule
import app.mikeneko.ui.components.EmptyCat
import app.mikeneko.ui.components.Loading
import app.mikeneko.ui.components.VideoCard
import app.mikeneko.ui.components.VideoMenu
import app.mikeneko.ui.components.adaptiveItems
import app.mikeneko.ui.components.columnsFor
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.glass.LiquidGlass
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.GlassTextShadow
import app.mikeneko.ui.theme.Rounded
import app.mikeneko.ui.theme.statusBarSpacePadding
import kotlin.math.exp
import kotlin.math.log10
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val MAX_CHANNELS = 12

/** L'ordre des résultats d'une recherche. */
enum class SearchSort(@StringRes val label: Int) {
    /** L'ordre de YouTube, rééquilibré vers les vidéos récentes et très vues. */
    Best(R.string.sort_best),
    Recent(R.string.sort_recent),
    Popular(R.string.sort_popular),
}

/** Trie des résultats (rangés dans l'ordre de pertinence de YouTube). */
fun sortResults(videos: List<VideoItem>, sort: SearchSort, now: Long = System.currentTimeMillis()): List<VideoItem> = when (sort) {
    SearchSort.Recent -> videos.sortedByDescending { it.uploadedAt ?: Long.MIN_VALUE }
    SearchSort.Popular -> videos.sortedByDescending { it.views }
    SearchSort.Best -> videos.withIndex().sortedByDescending { (rank, video) ->
        // Pertinence : les premiers résultats de YouTube restent devant…
        val relevance = 1.0 / (1.0 + rank / 8.0)
        // … mais une vidéo très vue (log : 10 k ≈ 0,6 ; 1 M ≈ 0,9 ; 100 M ≈ 1,1) et récente remonte
        val popularity = if (video.views > 0) (log10(video.views.toDouble()) / 7.0).coerceIn(0.2, 1.3) else 0.7
        val days = video.uploadedAt?.let { (now - it) / 86_400_000.0 }
        val freshness = if (days != null) 0.6 + 0.6 * exp(-days.coerceAtLeast(0.0) / 120.0) else 0.85
        relevance * (0.5 + 0.5 * popularity) * freshness
    }.map { it.value }
}

/**
 * Les résultats d'une recherche tapée : triés selon [HomeState.sort], la suite se charge en
 * arrivant en bas (ajoutée à la fin, sans faire sauter ce qui est déjà affiché).
 */
@Composable
fun SearchResultsContent(state: HomeState, query: String, onOpen: (VideoItem) -> Unit) {
    val cardColumns = columnsFor(300.dp) // une colonne en portrait, plusieurs en paysage
    val context = LocalContext.current
    val cached = remember(query) { YouTubeRepository.cachedSearchPage(query) }
    var all by remember(query) { mutableStateOf(cached?.videos) } // ordre de YouTube
    var next by remember(query) { mutableStateOf(cached?.next) }
    fun arrange(videos: List<VideoItem>) = sortResults(videos.filter { state.filters.accepts(it) }, state.sort)
    var shown by remember(query) { mutableStateOf(cached?.videos?.let(::arrange)) }
    var loadingMore by remember(query) { mutableStateOf(false) }
    var channels by remember(query) { mutableStateOf(cached?.channels.orEmpty()) }
    val list = rememberLazyListState()
    // Les capsules de tri (au-dessus, voir HomeOverlay) suivent le haut de la liste
    SideEffect { state.searchList = list }
    LaunchedEffect(list) {
        snapshotFlow { if (list.firstVisibleItemIndex == 0) list.firstVisibleItemScrollOffset else Int.MAX_VALUE }
            .collect { state.searchChipsShift = it }
    }

    // Les chaînes : celles des résultats, complétées par une recherche de chaînes (en parallèle)
    LaunchedEffect(query) {
        val found = runCatching { YouTubeRepository.searchChannels(query) }.getOrDefault(emptyList())
        channels = (channels + found).distinctBy { it.url }.take(MAX_CHANNELS)
    }

    LaunchedEffect(query) {
        if (all != null) return@LaunchedEffect
        val page = runCatching { YouTubeRepository.searchFirst(query) }.getOrNull()
        all = page?.videos.orEmpty()
        next = page?.next
        channels = (page?.channels.orEmpty() + channels).distinctBy { it.url }.take(MAX_CHANNELS)
        shown = arrange(all.orEmpty())
    }
    // Autre tri ou autres filtres : tout est refait, et on repart du haut
    LaunchedEffect(state.sort, state.filters) {
        all?.let { shown = arrange(it) }
        list.scrollToItem(0)
    }
    // Presque en bas : la page suivante, triée à part et ajoutée à la fin
    val nearEnd by remember {
        derivedStateOf {
            val info = list.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 4
        }
    }
    // Un seul surveillant, qui charge les pages l'une après l'autre (jamais annulé en plein milieu)
    LaunchedEffect(query) {
        snapshotFlow { nearEnd to next }.collect { (near, page) ->
            if (!near || page == null) return@collect
            loadingMore = true
            val more = try {
                YouTubeRepository.searchMore(query, page)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (more == null) {
                next = null // plus rien à charger (ou erreur réseau)
            } else {
                val known = all.orEmpty().map { it.url }.toSet()
                val fresh = more.videos.filter { it.url !in known }
                all = all.orEmpty() + fresh
                shown = shown.orEmpty() + arrange(fresh)
                next = more.next
            }
            loadingMore = false
        }
    }

    // Tirer pour rafraîchir : la recherche refaite sans le cache
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    val refreshFailed = stringResource(R.string.refresh_failed)
    val pull = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            scope.launch {
                val page = runCatching { YouTubeRepository.searchFirst(query, fresh = true) }.getOrNull()
                if (page == null) {
                    Toast.makeText(context, refreshFailed, Toast.LENGTH_SHORT).show()
                } else {
                    all = page.videos
                    next = page.next
                    shown = arrange(page.videos)
                }
                refreshing = false
            }
        },
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
            val items = shown
            when {
                items == null -> item { Loading(Modifier.padding(top = 220.dp)) }
                items.isEmpty() -> item {
                    EmptyCat(stringResource(R.string.nothing_found), stringResource(R.string.nothing_found_text), Modifier.padding(top = 200.dp))
                }
                else -> {
                    // Sous le header en verre (barre de recherche + capsules de tri)
                    item(key = "haut") { Spacer(Modifier.statusBarSpacePadding().height(118.dp)) }
                    if (channels.isNotEmpty()) item(key = "chaines") { ChannelsRow(channels) { state.channel = it } }
                    adaptiveItems(items, cardColumns, key = { it.url }) { video, modifier ->
                        VideoCard(video, modifier, onMore = { VideoMenu.open(video) }) { onOpen(video) }
                    }
                    if (loadingMore) item(key = "suite") { Loading(Modifier.size(64.dp)) }
                }
            }
        }
    }
}

/** Date de mise en ligne : au plus [maxDays] jours (null = n'importe quand). */
enum class DateFilter(@StringRes val label: Int, val maxDays: Int?) {
    Any(R.string.date_any, null),
    Today(R.string.date_today, 1),
    Week(R.string.date_week, 7),
    Month(R.string.date_month, 31),
    Year(R.string.date_year, 366),
}

/** Durée, en secondes : [min] inclus, [max] exclu (null = pas de limite). */
enum class DurationFilter(@StringRes val label: Int, val min: Long?, val max: Long?) {
    Any(R.string.duration_any, null, null),
    Short(R.string.duration_short, null, 4 * 60L),
    Medium(R.string.duration_medium, 4 * 60L, 20 * 60L),
    Long(R.string.duration_long, 20 * 60L, null),
}

/** Les filtres d'une recherche, appliqués aux résultats chargés (la suite se charge si besoin). */
data class SearchFilters(
    val date: DateFilter = DateFilter.Any,
    val duration: DurationFilter = DurationFilter.Any,
    val hideShorts: Boolean = false,
) {
    /** Nombre de filtres actifs (affiché sur le bouton). */
    val active: Int get() = listOf(date != DateFilter.Any, duration != DurationFilter.Any, hideShorts).count { it }

    fun accepts(video: VideoItem, now: Long = System.currentTimeMillis()): Boolean {
        if (hideShorts && video.isShort) return false
        date.maxDays?.let { days ->
            val at = video.uploadedAt ?: return false
            if (now - at > days * 86_400_000L) return false
        }
        if (duration != DurationFilter.Any) {
            val seconds = video.durationSec.takeIf { it > 0 } ?: return false
            if (duration.min != null && seconds < duration.min) return false
            if (duration.max != null && seconds >= duration.max) return false
        }
        return true
    }
}

/** Le panneau des filtres, en verre sous les capsules de tri. */
@Composable
fun SearchFiltersPanel(filters: SearchFilters, backdrop: Backdrop, onChange: (SearchFilters) -> Unit, modifier: Modifier = Modifier) {
    LiquidGlass(
        backdrop = backdrop,
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        bezel = 14.dp,
        refraction = 12.dp,
        tint = Color(0xB8100D0B),
        frost = 22.dp,
    ) {
        Column(
            Modifier.padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilterSection(R.string.filter_date, DateFilter.entries, filters.date, { it.label }) { onChange(filters.copy(date = it)) }
            FilterSection(R.string.filter_duration, DurationFilter.entries, filters.duration, { it.label }) { onChange(filters.copy(duration = it)) }
            FilterSection(R.string.filter_shorts, listOf(false, true), filters.hideShorts, { if (it) R.string.shorts_hide else R.string.shorts_show }) {
                onChange(filters.copy(hideShorts = it))
            }
            if (filters.active > 0) {
                Row(Modifier.padding(horizontal = 16.dp)) {
                    Capsule(stringResource(R.string.filters_reset), icon = MkIcons.Close) {
                        onChange(SearchFilters())
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> FilterSection(@StringRes title: Int, options: List<T>, selected: T, label: (T) -> Int, onPick: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(title),
            Modifier.padding(horizontal = 18.dp),
            style = TextStyle(
                fontFamily = Rounded,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                shadow = GlassTextShadow,
            ),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(options.size) { i ->
                val option = options[i]
                Capsule(stringResource(label(option)), selected = option == selected) { onPick(option) }
            }
        }
    }
}
