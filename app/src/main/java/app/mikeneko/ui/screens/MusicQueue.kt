package app.mikeneko.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.mikeneko.R
import app.mikeneko.player.PlayerHolder
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.glass.LiquidGlass
import app.mikeneko.ui.icons.MkIcons
import androidx.compose.ui.geometry.Rect
import app.mikeneko.ui.components.MorphSheet
import app.mikeneko.ui.theme.Pelage
import kotlinx.coroutines.launch

/**
 * La file, en verre, qui sort de la capsule des boutons du lecteur. Un tap lance l'élément ; la poignée ≡ le déplace ; la croix
 * le retire. En haut, « Mix auto » : la file se remplit toute seule de titres proches.
 */
@Composable
internal fun QueueSheet(backdrop: Backdrop, visible: Boolean, anchor: Rect?, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val queue by PlayerHolder.queue.collectAsState()
    val current by PlayerHolder.queueIndex.collectAsState()
    val mix by PlayerHolder.autoMix.collectAsState()
    val list = rememberLazyListState()
    val reorder = remember { ReorderState(list, firstIndex = 0, count = { PlayerHolder.queue.value.size }) { from, to -> PlayerHolder.moveInQueue(from, to) } }
    BackHandler(enabled = visible, onBack = onClose)
    LaunchedEffect(visible) { if (visible && current > 0) list.scrollToItem((current - 1).coerceAtLeast(0)) }

    // Comme les paroles (2.21.1) : la file sort de la capsule des boutons du lecteur et s'y referme
    MorphSheet(
        backdrop, visible, anchor, onClose,
        closeLabel = stringResource(R.string.queue_close),
        heightFraction = 0.7f,
        tint = Color(0xC71A1613),
        frost = 28.dp,
        canPull = { reorder.dragging == null }, // pas pendant qu'on déplace un morceau
    ) { handle ->
        Row(Modifier.fillMaxWidth().then(handle).padding(start = 22.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.queue), Modifier.weight(1f).semantics { heading() }, color = Pelage.Texte, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            // Mix auto : une petite pastille qui s'allume
            Row(
                Modifier.clip(RoundedCornerShape(18.dp))
                    .background(if (mix) Pelage.Roux else Color.White.copy(alpha = 0.1f))
                    .clickable(role = Role.Switch, onClickLabel = stringResource(R.string.mix_auto)) {
                        PlayerHolder.setAutoMix(!mix)
                        if (!mix) Toast.makeText(context, context.getString(R.string.mix_auto_on), Toast.LENGTH_SHORT).show()
                    }
                    .semantics { stateDescription = if (mix) "✓" else "" }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(MkIcons.Mix, null, tint = if (mix) Pelage.Encre else Color.White, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.mix_auto), color = if (mix) Pelage.Encre else Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(MkIcons.Close, stringResource(R.string.queue_close), tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(vertical = 6.dp)) {
            itemsIndexed(queue, key = { _, it -> it.url }) { index, station ->
                val playing = index == current
                val dragged = reorder.dragging == station.url
                Row(
                    Modifier
                        .then(
                            if (dragged) Modifier.zIndex(1f).graphicsLayer { translationY = reorder.offset }.background(Pelage.Bord)
                            else Modifier.animateItem(),
                        )
                        .fillMaxWidth()
                        .clickable(role = Role.Button) {
                            if (!playing) scope.launch {
                                runCatching { PlayerHolder.playAt(context, index) }
                                    .onFailure { Toast.makeText(context, context.getString(R.string.radio_station_failed), Toast.LENGTH_SHORT).show() }
                            }
                        }
                        .background(if (playing) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                        .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Cover(station.thumbnail, 50.dp, 12.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            station.title,
                            color = if (playing) Pelage.RouxClair else Pelage.Texte,
                            fontSize = 14.sp,
                            fontWeight = if (playing) FontWeight.ExtraBold else FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(station.channel, color = Pelage.Gris, fontSize = 12.sp, maxLines = 1)
                    }
                    if (playing) {
                        Icon(MkIcons.Waves, stringResource(R.string.playing_now), tint = Pelage.RouxClair, modifier = Modifier.size(22.dp))
                    } else {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).clickable(role = Role.Button) { PlayerHolder.removeFromQueue(index) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(MkIcons.Close, stringResource(R.string.queue_remove), tint = Pelage.Gris, modifier = Modifier.size(18.dp))
                        }
                    }
                    DragHandle(reorder, station.url)
                }
            }
        }
    }
}
