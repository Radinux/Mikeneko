package app.mikeneko.ui.screens

import app.mikeneko.ui.components.columnsFor
import app.mikeneko.ui.components.adaptiveItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.combinedClickable
import app.mikeneko.data.ChannelItem
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import app.mikeneko.ui.theme.statusBarSpace
import app.mikeneko.ui.theme.jellyClickable
import app.mikeneko.ui.theme.clay
import app.mikeneko.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.data.FeedVideo
import app.mikeneko.data.GoogleAccount
import app.mikeneko.data.Subscription
import app.mikeneko.data.SubscriptionFeed
import app.mikeneko.data.SubscriptionImport
import app.mikeneko.data.SubscriptionStore
import app.mikeneko.data.VideoItem
import app.mikeneko.data.formatAgo
import app.mikeneko.ui.components.Capsule
import app.mikeneko.ui.components.EmptyCat
import app.mikeneko.ui.components.Loading
import app.mikeneko.ui.components.Mascot
import app.mikeneko.ui.components.VideoThumbnail
import app.mikeneko.ui.components.drawEar
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Abonnements : uniquement les chaînes suivies, par ordre chronologique,
 * séparé des recommandations de l'Accueil. Un tap sur un chat = une seule chaîne.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubscriptionsContent(onOpen: (VideoItem) -> Unit, onOpenChannel: (ChannelItem) -> Unit, reset: Int = 0) {
    val context = LocalContext.current
    val store = remember { SubscriptionStore.get(context) }
    val subs by store.items.collectAsState()
    val connected = store.connected.collectAsState().value && GoogleAccount.ENABLED
    val scope = rememberCoroutineScope()
    var unwatchedOnly by remember { mutableStateOf(false) }
    var channel by remember { mutableStateOf<String?>(null) }

    val watched by store.watched.collectAsState()

    // L'onglet Abonnements touché : toutes les chaînes, tous les filtres retirés, en haut de la liste
    val listState = rememberLazyListState()
    LaunchedEffect(reset) {
        if (reset > 0) {
            channel = null
            unwatchedOnly = false
            listState.animateScrollToItem(0)
        }
    }

    // Si une chaîne est ajoutée, l'ancien flux reste affiché pendant le chargement
    // (seules les chaînes absentes du cache sont rechargées).
    val feed by produceState(initialValue = SubscriptionFeed.cached(subs), subs) {
        value = SubscriptionFeed.cached(subs) ?: SubscriptionFeed.latest(subs)
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    suspend fun importFromGoogle(token: String) {
        runCatching { GoogleAccount.fetchSubscriptions(token) }
            .onSuccess { list ->
                store.addAll(list)
                store.setConnected(true)
                toast(context.resources.getQuantityString(R.plurals.subs_synced_count, list.size, list.size))
            }
            .onFailure { toast(context.getString(R.string.subs_sync_failed, it.message.orEmpty())) }
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        runCatching { GoogleAccount.resultFromIntent(context, result.data) }
            .onSuccess { auth -> auth.accessToken?.let { token -> scope.launch { importFromGoogle(token) } } }
            .onFailure { toast(context.getString(R.string.login_cancelled)) }
    }

    fun connectGoogle() {
        scope.launch {
            runCatching { GoogleAccount.authorize(context) }
                .onSuccess { auth ->
                    val pending = auth.pendingIntent
                    if (auth.hasResolution() && pending != null) {
                        consent.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                    } else {
                        auth.accessToken?.let { importFromGoogle(it) }
                    }
                }
                .onFailure { toast(context.getString(R.string.google_refused)) }
        }
    }

    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            val list = SubscriptionImport.parse(text)
            store.addAll(list)
            toast(if (list.isEmpty()) context.getString(R.string.subs_import_none) else context.resources.getQuantityString(R.plurals.subs_imported_count, list.size, list.size))
        }
    }

    val visible = feed.orEmpty().filter { item ->
        (channel == null || item.channelId == channel) &&
            (!unwatchedOnly || item.video.url !in watched)
    }
    val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
    val weekAgo = Instant.now().minus(Duration.ofDays(7))
    val groups = listOf(
        stringResource(R.string.group_today) to visible.filter { it.published >= today },
        stringResource(R.string.group_week) to visible.filter { it.published < today && it.published >= weekAgo },
        stringResource(R.string.group_earlier) to visible.filter { it.published < weekAgo },
    ).filter { it.second.isNotEmpty() }

    val rowColumns = columnsFor(330.dp) // une colonne en portrait, deux en paysage
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = WindowInsets.statusBarSpace.asPaddingValues().calculateTopPadding() + 30.dp, bottom = 200.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.subs_title), color = Pelage.Texte, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (subs.isEmpty()) stringResource(R.string.subs_none) else pluralStringResource(R.plurals.channels_count, subs.size, subs.size),
                        color = Pelage.Gris,
                        fontSize = 14.sp,
                    )
                }
                if (GoogleAccount.ENABLED) Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (connected) Pelage.Roux else Pelage.Creme)
                        .clickable(role = Role.Button, onClickLabel = stringResource(if (connected) R.string.google_connected else R.string.google_sign_in)) {
                            if (connected) store.setConnected(false) else connectGoogle()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(MkIcons.Account, stringResource(if (connected) R.string.google_connected else R.string.google_sign_in), tint = Pelage.Encre, modifier = Modifier.size(22.dp))
                }
            }
        }

        item {
            if (!connected) {
                AccountCard(
                    onGoogle = ::connectGoogle,
                    onImport = { importFile.launch(arrayOf("application/json", "text/*", "application/xml", "application/octet-stream")) },
                )
            } else {
                Row(
                    Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Pelage.Coussin).padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(MkIcons.Check, null, tint = Pelage.RouxClair, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.subs_synced), Modifier.weight(1f).padding(start = 10.dp), color = Pelage.Texte, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.disconnect),
                        Modifier.clip(CircleShape).clickable(role = Role.Button) { store.setConnected(false) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        color = Pelage.RouxClair,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        if (subs.isNotEmpty()) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(subs, key = { it.channelId }) { sub ->
                        // Tap : filtrer sur cette chaîne ; appui long : ouvrir sa page
                        ChannelAvatar(sub, selected = channel == sub.channelId, onLongClick = { onOpenChannel(sub.toChannelItem()) }) {
                            channel = if (channel == sub.channelId) null else sub.channelId
                        }
                    }
                }
            }
            item {
                FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Capsule(stringResource(R.string.filter_all), selected = !unwatchedOnly) { unwatchedOnly = false }
                    Capsule(stringResource(R.string.filter_unwatched), selected = unwatchedOnly) { unwatchedOnly = true }
                    val selectedId = channel
                    val name = subs.firstOrNull { it.channelId == selectedId }?.name
                    if (selectedId != null && name != null) {
                        Row(
                            Modifier.height(44.dp).clip(CircleShape).background(Pelage.RouxNuit).clickable(role = Role.Button) { channel = null }.padding(start = 14.dp, end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.only_channel, name), color = Pelage.RouxClair, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(MkIcons.Close, stringResource(R.string.all_channels), tint = Pelage.RouxClair, modifier = Modifier.padding(start = 6.dp).size(18.dp))
                        }
                        subs.firstOrNull { it.channelId == selectedId }?.let { sub ->
                            Capsule(stringResource(R.string.channel_open)) { onOpenChannel(sub.toChannelItem()) }
                        }
                        // Se désabonner : un premier tap demande confirmation (4 s), le second retire la chaîne
                        var confirming by remember(selectedId) { mutableStateOf(false) }
                        LaunchedEffect(confirming) {
                            if (confirming) {
                                delay(4_000)
                                confirming = false
                            }
                        }
                        val unsubscribed = stringResource(R.string.unsubscribed, name)
                        Capsule(
                            stringResource(if (confirming) R.string.unsubscribe_confirm else R.string.unsubscribe),
                            selected = confirming,
                            icon = if (confirming) MkIcons.Check else MkIcons.Close,
                        ) {
                            if (confirming) {
                                store.remove(selectedId)
                                channel = null
                                toast(unsubscribed)
                            } else {
                                confirming = true
                            }
                        }
                    }
                }
            }
        }

        when {
            subs.isEmpty() -> Unit
            feed == null -> item { Loading() }
            groups.isEmpty() -> item { EmptyCat(stringResource(R.string.subs_empty), stringResource(R.string.subs_empty_text)) }
            else -> groups.forEach { (title, list) ->
                item {
                    Text(
                        title.uppercase(),
                        Modifier.padding(start = 20.dp, top = 10.dp),
                        color = Pelage.Gris,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                    )
                }
                adaptiveItems(list, rowColumns, key = { it.video.url }, horizontalPadding = 0.dp, spacing = 0.dp) { item, modifier ->
                    FeedRow(item, watched = item.video.url in watched, modifier) { onOpen(item.video) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountCard(onGoogle: () -> Unit, onImport: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Pelage.Coussin)
            .border(1.5.dp, Pelage.Bord, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val google = GoogleAccount.ENABLED
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Mascot(size = 48.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(if (google) R.string.subs_card_title else R.string.subs_card_import_title), color = Pelage.Texte, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Text(stringResource(if (google) R.string.subs_card_text else R.string.subs_card_import_text), color = Pelage.Gris, fontSize = 13.sp)
            }
        }
        if (google) Row(
            Modifier.fillMaxWidth().jellyClickable(onClick = onGoogle).height(48.dp).clay(Pelage.Creme),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(MkIcons.Account, null, tint = Pelage.Encre, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.google_sign_in), Modifier.padding(start = 8.dp), color = Pelage.Encre, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
        if (google) Text(stringResource(R.string.subs_import_without_account), Modifier.fillMaxWidth(), color = Pelage.Gris, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp), // de l'air quand une capsule passe à la ligne
        ) {
            listOf("NewPipe", "FreeTube", "Google Takeout", "Mikeneko .xml").forEach { label ->
                Text(
                    label,
                    Modifier.jellyClickable(onClick = onImport).clay(Pelage.Bord).padding(horizontal = 14.dp, vertical = 11.dp),
                    color = Pelage.Texte,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Avatar de chaîne avec des oreilles de chat (et un trait orange si sélectionnée). */
@Composable
private fun ChannelAvatar(sub: Subscription, selected: Boolean, onLongClick: () -> Unit, onClick: () -> Unit) {
    val color = remember(sub.channelId) { AVATAR_COLORS[(sub.channelId.hashCode() and 0x7fffffff) % AVATAR_COLORS.size] }
    Column(
        Modifier.width(70.dp).combinedClickable(
            role = Role.Button,
            onLongClickLabel = stringResource(R.string.channel_open),
            onLongClick = onLongClick,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(70.dp, 76.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val s = size.width / 70f
                // Oreilles écartées vers l'extérieur et bien arrondies
                drawEar(Offset(10 * s, 36 * s), Offset(7 * s, 5 * s), Offset(30 * s, 20 * s), color, round = 0.3f)
                drawEar(Offset(60 * s, 36 * s), Offset(63 * s, 5 * s), Offset(40 * s, 20 * s), color, round = 0.3f)
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .size(60.dp)
                    .border(if (selected) 4.dp else 3.dp, if (selected) Pelage.Roux else Pelage.Creme, CircleShape)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center,
            ) {
                Text(sub.name.take(2).uppercase(), color = Pelage.Encre, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                if (sub.avatar != null) {
                    AsyncImage(sub.avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
        Text(
            sub.name,
            color = if (selected) Pelage.RouxClair else Pelage.Texte,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Une chaîne suivie, pour ouvrir sa page. */
private fun Subscription.toChannelItem() = ChannelItem(
    url = url.ifEmpty { "https://www.youtube.com/channel/$channelId" },
    name = name,
    avatar = avatar,
    channelId = channelId,
)

@Composable
private fun FeedRow(item: FeedVideo, watched: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VideoThumbnail(item.video, Modifier.width(150.dp), corner = 14.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.video.title, color = Pelage.Texte, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.video.channel, color = Pelage.Gris, fontSize = 12.sp, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!watched) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Pelage.Roux))
                    Text(stringResource(R.string.new_dot), Modifier.padding(start = 6.dp), color = Pelage.Gris, fontSize = 12.sp)
                }
                Text(formatAgo(item.published), color = Pelage.Gris, fontSize = 12.sp)
            }
        }
    }
}

private val AVATAR_COLORS = listOf(
    Color(0xFFF3C9A5), Color(0xFFC9C1E6), Color(0xFFF2D59A), Color(0xFFF4B6D6), Color(0xFF9CC3F0),
)

