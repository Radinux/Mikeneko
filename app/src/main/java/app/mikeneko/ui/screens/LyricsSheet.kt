package app.mikeneko.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.data.LocalMusic
import app.mikeneko.data.Lyrics
import app.mikeneko.data.LyricsRepository
import app.mikeneko.data.guessSong
import app.mikeneko.player.PlayerHolder
import app.mikeneko.ui.components.EmptyCat
import app.mikeneko.ui.components.Loading
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.glass.LiquidGlass
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Où en est la recherche des paroles du morceau en cours. */
private sealed interface LyricsState {
    data object Loading : LyricsState
    data object NotFound : LyricsState
    data class Found(val lyrics: Lyrics) : LyricsState
}

/**
 * Les paroles du morceau en cours, en verre, qui montent du bas. Synchronisées : la ligne chantée
 * s'allume et reste au milieu, un tap sur une ligne y saute. Sinon, le texte brut.
 */
@Composable
fun LyricsSheet(backdrop: Backdrop, visible: Boolean, onClose: () -> Unit) {
    val now by PlayerHolder.nowPlaying.collectAsState()
    val queue by PlayerHolder.queue.collectAsState()
    val queueIndex by PlayerHolder.queueIndex.collectAsState()
    BackHandler(enabled = visible, onBack = onClose)
    // Paroles ouvertes : l'écran reste allumé (on les lit sans toucher le téléphone)
    val view = LocalView.current
    DisposableEffect(visible) {
        if (visible) view.keepScreenOn = true
        onDispose { if (visible) view.keepScreenOn = false }
    }

    // Une requête seulement quand le panneau est ouvert (et une fois par morceau)
    var state by remember { mutableStateOf<LyricsState>(LyricsState.Loading) }
    val track = now
    LaunchedEffect(track?.url, visible) {
        if (!visible || track == null) return@LaunchedEffect
        state = LyricsState.Loading
        // La durée sert à choisir la bonne version : celle de la file, sinon celle du lecteur
        val known = queue.getOrNull(queueIndex)?.durationSec?.takeIf { it > 0 }
        val duration = known ?: run {
            var d = PlayerHolder.progress().second
            var tries = 0
            while (d <= 0 && tries < 10) {
                delay(200)
                d = PlayerHolder.progress().second
                tries++
            }
            d / 1000
        }
        val guess = guessSong(track.title, track.channel)
        val found = runCatching { LyricsRepository.find(guess, duration) }.getOrNull()
        state = if (found == null || found.isEmpty) LyricsState.NotFound else LyricsState.Found(found)
    }

    // Tirer le panneau vers le bas le ferme (2.4.0) : par la barre du haut, ou en continuant de tirer
    // quand les paroles sont déjà en haut. Il suit le doigt, et revient en place si on lâche trop tôt.
    val density = LocalDensity.current
    val closeAt = with(density) { 120.dp.toPx() }
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    LaunchedEffect(visible) { if (visible) drag.snapTo(0f) }
    fun release(velocity: Float) {
        scope.launch {
            if (drag.value > closeAt || (velocity > 1_500f && drag.value > 0f)) onClose()
            else drag.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 500f))
        }
    }
    val pullDown = remember {
        object : NestedScrollConnection {
            // Le panneau descendu remonte d'abord avant que les paroles ne défilent
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && drag.value > 0f) {
                    val used = maxOf(available.y, -drag.value)
                    scope.launch { drag.snapTo(drag.value + used) }
                    return Offset(0f, used)
                }
                return Offset.Zero
            }
            // Les paroles sont en haut et on tire encore : c'est le panneau qui descend
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    scope.launch { drag.snapTo(drag.value + available.y) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drag.value <= 0f) return Velocity.Zero
                release(available.y)
                return available
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
                    .clickable(interactionSource = null, indication = null, onClickLabel = stringResource(R.string.lyrics_close), onClick = onClose),
            )
        }
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
        ) {
            LiquidGlass(
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(10.dp).fillMaxHeight(0.78f)
                    .graphicsLayer { translationY = drag.value }
                    .nestedScroll(pullDown),
                cornerRadius = 30.dp,
                bezel = 26.dp,
                refraction = 26.dp,
                tint = Color(0xD01A1613),
                frost = 30.dp,
            ) {
                Column(Modifier.fillMaxSize()) {
                    // La barre du haut : on l'attrape pour tirer le panneau vers le bas
                    Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).size(40.dp, 4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
                    Row(
                        Modifier.fillMaxWidth()
                            .pointerInput(Unit) {
                                val tracker = VelocityTracker()
                                detectVerticalDragGestures(
                                    onDragStart = { tracker.resetTracking() },
                                    onDragEnd = { release(tracker.calculateVelocity().y) },
                                    onDragCancel = { release(0f) },
                                ) { change, amount ->
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    scope.launch { drag.snapTo((drag.value + amount).coerceAtLeast(0f)) }
                                }
                            }
                            .padding(start = 22.dp, end = 8.dp, top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.lyrics), Modifier.semantics { heading() }, color = Pelage.Texte, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                            Text(track?.title.orEmpty(), color = Pelage.Gris, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose), contentAlignment = Alignment.Center) {
                            Icon(MkIcons.Close, stringResource(R.string.lyrics_close), tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (val s = state) {
                            LyricsState.Loading -> Loading(Modifier.align(Alignment.Center))
                            LyricsState.NotFound -> EmptyCat(
                                stringResource(R.string.lyrics_none),
                                stringResource(if (LocalMusic.isLocal(track?.url)) R.string.lyrics_none_local else R.string.lyrics_none_text),
                                Modifier.align(Alignment.Center),
                            )
                            is LyricsState.Found -> when {
                                s.lyrics.synced.isNotEmpty() -> SyncedLyrics(s.lyrics)
                                !s.lyrics.plain.isNullOrBlank() -> Text(
                                    s.lyrics.plain,
                                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 16.dp),
                                    color = Pelage.Texte,
                                    fontSize = 17.sp,
                                    lineHeight = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                else -> EmptyCat(stringResource(R.string.lyrics_instrumental), stringResource(R.string.lyrics_instrumental_text), Modifier.align(Alignment.Center))
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.lyrics_source),
                        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
                        color = Pelage.Gris.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncedLyrics(lyrics: Lyrics) {
    val lines = lyrics.synced
    val list = rememberLazyListState()
    var position by remember { mutableLongStateOf(PlayerHolder.progress().first) }
    LaunchedEffect(lines) {
        while (true) {
            position = PlayerHolder.progress().first
            delay(200)
        }
    }
    // Un léger temps d'avance : la ligne s'allume au moment où elle est chantée, pas après
    val current = lines.indexOfLast { it.timeMs <= position + 250 }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val middle = with(LocalDensity.current) { (maxHeight * 0.38f).roundToPx() }
        LaunchedEffect(current) {
            if (current >= 0 && !list.isScrollInProgress) list.animateScrollToItem(current, -middle)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            state = list,
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 16.dp, bottom = maxHeight * 0.6f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(lines) { index, line ->
                val active = index == current
                val color by animateColorAsState(
                    when {
                        active -> Pelage.RouxClair
                        index < current -> Pelage.Texte.copy(alpha = 0.45f)
                        else -> Pelage.Texte.copy(alpha = 0.8f)
                    },
                    tween(250),
                    label = "ligne",
                )
                Text(
                    line.text.ifBlank { "♪" },
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.lyrics_seek)) { PlayerHolder.seekTo(line.timeMs) },
                    color = color,
                    fontSize = if (active) 22.sp else 19.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}
