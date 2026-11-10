package app.mikeneko.ui.screens

import app.mikeneko.ui.components.CONNECTED_GAP
import app.mikeneko.ui.components.ConnectedChip
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import app.mikeneko.ui.theme.Rounded
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.data.ChannelItem
import app.mikeneko.data.FeedVideo
import app.mikeneko.data.Subscription
import app.mikeneko.data.SubscriptionAvatars
import app.mikeneko.data.SubscriptionFeed
import app.mikeneko.data.SubscriptionStore
import app.mikeneko.data.VideoItem
import app.mikeneko.data.formatAgo
import app.mikeneko.data.looksShort
import app.mikeneko.settings.AppSettings
import app.mikeneko.ui.components.Capsule
import app.mikeneko.ui.components.EmptyCat
import app.mikeneko.ui.components.Loading
import app.mikeneko.ui.components.VideoThumbnail
import app.mikeneko.ui.components.adaptiveItems
import app.mikeneko.ui.components.columnsFor
import app.mikeneko.ui.components.drawEar
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.statusBarSpace
import coil3.compose.AsyncImage
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay

/**
 * Abonnements : uniquement les chaînes suivies, par ordre chronologique,
 * séparé des recommandations de l'Accueil. Un tap sur un chat = une seule chaîne.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubscriptionsContent(onOpen: (VideoItem) -> Unit, onOpenChannel: (ChannelItem) -> Unit, onOpenSettings: () -> Unit, reset: Int = 0) {
    val context = LocalContext.current
    val store = remember { SubscriptionStore.get(context) }
    val subs by store.items.collectAsState()
    var unwatchedOnly by remember { mutableStateOf(false) }
    var channel by remember { mutableStateOf<String?>(null) }
    // La loupe (2.14.0) : filtre les chaînes (par nom) et les vidéos (titre ou chaîne) pendant qu'on tape
    var search by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = search != null) { search = null }

    val watched by store.watched.collectAsState()
    // Les chaînes sans image (importées) : leurs avatars arrivent un par un, doucement
    LaunchedEffect(store) { SubscriptionAvatars.fill(store) }

    // L'onglet Abonnements touché : toutes les chaînes, tous les filtres retirés, en haut de la liste
    val listState = rememberLazyListState()
    LaunchedEffect(reset) {
        if (reset > 0) {
            channel = null
            search = null
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

    val hideShorts by AppSettings.hideShorts.collectAsState()
    val needle = search?.trim()?.let(::folded).orEmpty()
    val shownSubs = if (needle.isEmpty()) subs else subs.filter { needle in folded(it.name) }
    val visible = feed.orEmpty().filter { item ->
        (channel == null || item.channelId == channel) &&
            (needle.isEmpty() || needle in folded(item.video.title) || needle in folded(item.video.channel)) &&
            !(hideShorts && item.video.looksShort) &&
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
                if (subs.isNotEmpty()) {
                    val open = search != null
                    Box(
                        Modifier
                            .padding(top = 4.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (open) Pelage.RouxNuit else Color.White.copy(alpha = 0.08f))
                            .clickable(role = Role.Button) { search = if (open) null else "" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (open) MkIcons.Close else MkIcons.Search,
                            stringResource(if (open) R.string.search_close else R.string.subs_search),
                            tint = if (open) Pelage.RouxClair else Pelage.Texte,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
        search?.let { text ->
            item(key = "recherche") {
                SubsSearchField(text, Modifier.padding(horizontal = 20.dp)) { search = it }
            }
        }

        // L'import de fichiers est dans Réglages › Sauvegarde (1.18.0) : ici, seulement un mot quand
        // il n'y a encore aucune chaîne (plus bas)

        if (subs.isNotEmpty()) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(shownSubs, key = { it.channelId }) { sub ->
                        // Tap : filtrer sur cette chaîne ; appui long : ouvrir sa page
                        ChannelAvatar(sub, selected = channel == sub.channelId, onLongClick = { onOpenChannel(sub.toChannelItem()) }) {
                            channel = if (channel == sub.channelId) null else sub.channelId
                        }
                    }
                }
            }
            item {
                FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(CONNECTED_GAP)) {
                        ConnectedChip(stringResource(R.string.filter_all), !unwatchedOnly, first = true, last = false) { unwatchedOnly = false }
                        ConnectedChip(stringResource(R.string.filter_unwatched), unwatchedOnly, first = false, last = true) { unwatchedOnly = true }
                    }
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
            subs.isEmpty() -> item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    EmptyCat(stringResource(R.string.subs_none), stringResource(R.string.subs_none_text))
                    Capsule(stringResource(R.string.subs_go_import), icon = MkIcons.Download, onClick = onOpenSettings)
                }
            }
            feed == null -> item { Loading() }
            groups.isEmpty() && needle.isNotEmpty() -> item { EmptyCat(stringResource(R.string.nothing_found), stringResource(R.string.nothing_found_text)) }
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
                    FeedRow(item, watched = item.video.url in watched, modifier) { Shorts.offer(visible.map { it.video }); onOpen(item.video) }
                }
            }
        }
    }
}

/** Pour chercher sans tenir compte des majuscules ni des accents (« eté » trouve « Été »). */
private fun folded(text: String): String =
    java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

/** Le champ de la loupe des abonnements ; le clavier s'ouvre tout de suite. */
@Composable
private fun SubsSearchField(text: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(Pelage.Nuit).border(1.5.dp, Pelage.Bord, RoundedCornerShape(24.dp)).padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = text,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Pelage.Texte, fontSize = 15.sp, fontFamily = Rounded, fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(Pelage.RouxClair),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            decorationBox = { inner ->
                if (text.isEmpty()) Text(stringResource(R.string.subs_search), color = Pelage.Gris, fontSize = 15.sp, maxLines = 1)
                inner()
            },
        )
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

