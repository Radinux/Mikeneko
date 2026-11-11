package app.mikeneko.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
import androidx.compose.ui.platform.LocalContext
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
import app.mikeneko.data.LyricsOffsets
import app.mikeneko.data.LyricsRepository
import app.mikeneko.data.guessSong
import app.mikeneko.player.PlayerHolder
import app.mikeneko.ui.components.Capsule
import app.mikeneko.ui.components.EmptyCat
import app.mikeneko.ui.components.Loading
import app.mikeneko.ui.components.MorphSheet
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
 * Les paroles du morceau en cours, en verre. Synchronisées : la ligne chantée s'allume et reste au
 * milieu, un tap sur une ligne y saute. Sinon, le texte brut.
 *
 * 2.15.2 : le panneau sort de la barre de boutons du lecteur ([anchor], la capsule File · Paroles ·
 * Ajouter…) en s'étirant, et s'y referme sur lui-même. Avant, il glissait d'un bloc vers le bas.
 */
@Composable
fun LyricsSheet(backdrop: Backdrop, visible: Boolean, anchor: Rect?, onClose: () -> Unit) {
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
    // Le recalage de ce morceau (2.24.0) : mémorisé, et le mode « caler » le temps de le régler
    val context = LocalContext.current
    val trackKey = now?.url
    var offset by remember(trackKey) { mutableLongStateOf(trackKey?.let { LyricsOffsets.get(context, it) } ?: 0L) }
    var adjusting by remember(trackKey) { mutableStateOf(false) }
    if (!visible) adjusting = false
    fun shift(ms: Long) {
        offset = ms.coerceIn(-LyricsOffsets.LIMIT, LyricsOffsets.LIMIT)
        trackKey?.let { LyricsOffsets.set(context, it, offset) }
    }
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

    // Le panneau sort de la capsule des boutons et s'y referme ; on le tire vers le bas pour le fermer
    // (2.4.0, 2.15.2, 2.15.3 : tout ça vit maintenant dans MorphSheet, partagé avec la file et les menus)
    MorphSheet(backdrop, visible, anchor, onClose, closeLabel = stringResource(R.string.lyrics_close)) { handle ->
                    Row(
                        Modifier.fillMaxWidth().then(handle).padding(start = 22.dp, end = 8.dp, top = 6.dp),
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
                                s.lyrics.synced.isNotEmpty() -> SyncedLyrics(s.lyrics, offset, adjusting, ::shift)
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
                    val synced = (state as? LyricsState.Found)?.lyrics?.synced?.isNotEmpty() == true
                    AnimatedVisibility(synced && adjusting) {
                        SyncBar(offset, ::shift)
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.lyrics_source),
                            Modifier.weight(1f).padding(vertical = 4.dp),
                            color = Pelage.Gris.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                        )
                        if (synced) {
                            Capsule(
                                when {
                                    adjusting -> stringResource(R.string.lyrics_sync_done)
                                    offset != 0L -> stringResource(R.string.lyrics_sync_offset, seconds(offset))
                                    else -> stringResource(R.string.lyrics_sync)
                                },
                                selected = adjusting,
                                icon = if (adjusting) MkIcons.Check else MkIcons.Clock,
                            ) { adjusting = !adjusting }
                        }
                    }
    }
}

/** « +1.5 s », « −13.0 s » : le décalage des paroles (négatif : elles arrivent plus tôt). */
private fun seconds(ms: Long): String = (if (ms < 0) "\u2212" else "+") + "%.1f s".format(kotlin.math.abs(ms) / 1000f)

/** En mode « caler » : l'aide, puis plus tôt / le décalage / plus tard (et remettre à zéro). */
@Composable
private fun SyncBar(offset: Long, shift: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.lyrics_sync_hint), color = Pelage.Texte, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Capsule(stringResource(R.string.lyrics_sync_earlier)) { shift(offset - 500) }
            Text(seconds(offset), Modifier.padding(horizontal = 4.dp), color = Pelage.RouxClair, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Capsule(stringResource(R.string.lyrics_sync_later)) { shift(offset + 500) }
        }
        AnimatedVisibility(offset != 0L) {
            Capsule(stringResource(R.string.lyrics_sync_reset), Modifier.padding(top = 4.dp)) { shift(0L) }
        }
    }
}

/** Le temps de réaction d'un tap « c'est cette ligne » : la ligne avait commencé un peu avant. */
private const val REACTION_MS = 300L

/**
 * [offset] s'ajoute au temps de chaque ligne (2.24.0). En mode [adjusting], un tap sur une ligne ne
 * saute plus : il dit « c'est elle qu'on entend », et le décalage se cale dessus.
 */
@Composable
private fun SyncedLyrics(lyrics: Lyrics, offset: Long, adjusting: Boolean, shift: (Long) -> Unit) {
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
    val current = lines.indexOfLast { it.timeMs + offset <= position + 250 }
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
                        .clickable(role = Role.Button, onClickLabel = stringResource(if (adjusting) R.string.lyrics_sync_line else R.string.lyrics_seek)) {
                            if (adjusting) {
                                val heard = PlayerHolder.progress().first
                                position = heard
                                shift(heard - REACTION_MS - line.timeMs)
                            } else {
                                PlayerHolder.seekTo((line.timeMs + offset).coerceAtLeast(0L))
                            }
                        },
                    color = color,
                    fontSize = if (active) 22.sp else 19.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}
