package app.mikeneko.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.delay
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.data.Comment
import app.mikeneko.data.Comments
import app.mikeneko.data.formatCount
import app.mikeneko.player.PlayerHolder
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.glass.LiquidGlass
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.Rounded
import app.mikeneko.ui.theme.statusBarSpace
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private val Secondary = Color(0xFFCFC6BD)

/**
 * Les commentaires dans la page de la vidéo. Repliée : le nombre et le premier commentaire. Un tap
 * l'étire sur place à une hauteur fixe, et on fait défiler les commentaires dedans (la page,
 * elle, reprend son défilement aux deux bouts de la liste). Un tap sur l'en-tête, ou retour, la
 * replie. Changer de vidéo ([resetKey]) la replie aussi. (Avant 2.13 : une fenêtre par-dessus.)
 */
@Composable
fun CommentsCard(resetKey: Any?, modifier: Modifier = Modifier) {
    val state by Comments.state.collectAsState()
    val first = state.items.firstOrNull()
    var expanded by rememberSaveable(resetKey) { mutableStateOf(false) }
    if (state.disabled) expanded = false
    val bringIntoView = remember { BringIntoViewRequester() }

    BackHandler(enabled = expanded) { expanded = false }
    // Étirée : la page défile pour la montrer en entier (une fois la taille finale atteinte)
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(220)
            bringIntoView.bringIntoView()
        }
    }

    Box(modifier.fillMaxWidth()) {
        val screen = LocalConfiguration.current.screenHeightDp.dp
        val openHeight = (screen * 0.62f).coerceIn(320.dp, 620.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoView)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .animateContentSize(tween(320)),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = !state.disabled,
                        role = Role.Button,
                        onClickLabel = stringResource(if (expanded) R.string.comments_close else R.string.comments_open),
                    ) { expanded = !expanded }
                    .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = if (expanded) 8.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.comments), Modifier.semantics { heading() }, color = Pelage.Texte, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                if (state.count > 0) {
                    Text("  ${formatCount(state.count.toLong())}", color = Secondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                if (!state.disabled) {
                    val turn by animateFloatAsState(if (expanded) -90f else 90f, tween(320), label = "chevron")
                    Icon(MkIcons.ChevronRight, null, tint = Secondary, modifier = Modifier.size(18.dp).rotate(turn))
                }
            }
            if (expanded) {
                CommentList(Modifier.fillMaxWidth().height(openHeight))
            } else {
                Box(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 12.dp)) {
                    when {
                        state.disabled -> Text(stringResource(R.string.comments_disabled_video), color = Secondary, fontSize = 13.sp)
                        state.loading -> Text(stringResource(R.string.comments_loading), color = Secondary, fontSize = 13.sp)
                        state.error != null -> Text(stringResource(R.string.comments_error), color = Secondary, fontSize = 13.sp)
                        first == null -> Text(stringResource(R.string.comments_be_first), color = Secondary, fontSize = 13.sp)
                        else -> Row(
                            Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.comments_open)) { expanded = true },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Avatar(first, 26.dp)
                            Text(
                                plainText(first),
                                color = Pelage.Texte,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Le panneau des commentaires, en verre, sous la vidéo (qui continue de jouer au-dessus). Lecture seule.
 * Seulement pour le lecteur de shorts (pas de page sous la vidéo) ; le lecteur normal a [CommentsCard].
 */
@Composable
fun CommentsPanel(backdrop: Backdrop) {
    val open by Comments.open.collectAsState()

    BackHandler(enabled = open) {
        Comments.open.value = false
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Juste sous la vidéo : barre d'état + « Lecture en cours » + image en 16:9
        val density = LocalDensity.current
        val statusBar = with(density) { WindowInsets.statusBarSpace.getTop(density).toDp() }
        val top = statusBar + 56.dp + maxWidth * 9f / 16f + 6.dp

        AnimatedVisibility(
            visible = open,
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
        ) {
            LiquidGlass(
                backdrop = backdrop,
                modifier = Modifier
                    .padding(top = top)
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                cornerRadius = 28.dp,
                bezel = 26.dp,
                refraction = 26.dp,
                tint = Color(0x801A1613), // assez clair pour voir le verre, le flou garde le texte lisible
                frost = 32.dp,
            ) {
                Column(Modifier.fillMaxSize()) {
                    PanelHeader()
                    CommentList(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PanelHeader() {
    val state by Comments.state.collectAsState()
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.comments), Modifier.semantics { heading() }, color = Pelage.Texte, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        if (state.count > 0) Text("  ${formatCount(state.count.toLong())}", color = Secondary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { Comments.open.value = false },
            contentAlignment = Alignment.Center,
        ) {
            Icon(MkIcons.Close, stringResource(R.string.comments_close), tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun CommentList(modifier: Modifier) {
    val state by Comments.state.collectAsState()
    val list = rememberLazyListState()

    // Charge la suite quand on arrive près du bas
    LaunchedEffect(list) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index to list.layoutInfo.totalItemsCount }
            .distinctUntilChanged()
            .filter { (last, total) -> last != null && total > 0 && last >= total - 4 }
            .collect { Comments.loadMore() }
    }

    LazyColumn(modifier.fillMaxWidth(), state = list, contentPadding = PaddingValues(vertical = 6.dp)) {
        when {
            state.loading -> item { Loading(Modifier.fillMaxWidth().padding(32.dp)) }
            state.error != null -> item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.comments_error), color = Pelage.Texte, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.retry),
                        Modifier.padding(top = 8.dp).clip(CircleShape).clickable(role = Role.Button) { Comments.retry() }.padding(horizontal = 14.dp, vertical = 10.dp),
                        color = Pelage.RouxClair,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
            state.disabled -> item { EmptyCat(stringResource(R.string.comments_disabled), stringResource(R.string.comments_disabled_text), Modifier.padding(24.dp)) }
            state.items.isEmpty() -> item { EmptyCat(stringResource(R.string.comments_empty), stringResource(R.string.comments_empty_text), Modifier.padding(24.dp)) }
            else -> {
                items(state.items, key = { it.id }) { comment ->
                    val thread = state.replies[comment.id]
                    CommentRow(comment)
                    if (comment.replyCount > 0 || thread?.items?.isNotEmpty() == true) {
                        RepliesToggle(comment, thread?.open == true)
                    }
                    if (thread?.open == true) {
                        thread.items.forEach { reply ->
                            CommentRow(reply, indent = 46.dp)
                        }
                        when {
                            thread.loading -> Loading(Modifier.fillMaxWidth().padding(8.dp))
                            thread.next != null -> TextAction(stringResource(R.string.replies_more), Modifier.padding(start = 66.dp, bottom = 6.dp)) { Comments.moreReplies(comment) }
                        }
                    }
                }
                if (state.loadingMore) item { Loading(Modifier.fillMaxWidth().padding(16.dp)) }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: Comment, indent: Dp = 0.dp) {
    var expanded by remember(comment.id) { mutableStateOf(false) }
    var overflows by remember(comment.id) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp + indent, end = 16.dp, top = 10.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Avatar(comment, if (indent > 0.dp) 26.dp else 34.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (comment.pinned) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(MkIcons.Pin, null, tint = Secondary, modifier = Modifier.size(13.dp))
                    Text(stringResource(R.string.pinned), color = Secondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.author,
                    Modifier
                        .weight(1f, fill = false)
                        .then(if (comment.byCreator) Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 7.dp, vertical = 1.dp) else Modifier),
                    color = if (comment.mine) Pelage.RouxClair else Pelage.Texte,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (comment.verified) Icon(MkIcons.Check, stringResource(R.string.verified_channel), tint = Secondary, modifier = Modifier.padding(start = 3.dp).size(12.dp))
                Text(
                    listOfNotNull(comment.ago, if (comment.edited) stringResource(R.string.edited) else null).joinToString(" · ", prefix = " · "),
                    color = Secondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
            Text(
                commentText(comment),
                Modifier.clickable(enabled = overflows || expanded, interactionSource = null, indication = null) { expanded = !expanded },
                style = TextStyle(fontFamily = Rounded, color = Pelage.Texte, fontSize = 14.sp, lineHeight = 19.sp),
                maxLines = if (expanded) Int.MAX_VALUE else 5,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
            )
            if (overflows && !expanded) TextAction(stringResource(R.string.read_more), Modifier) { expanded = true }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(MkIcons.ThumbUp, stringResource(R.string.like), tint = Secondary, modifier = Modifier.size(15.dp))
                    if (comment.likes > 0) Text(" ${formatCount(comment.likes.toLong())}", color = Secondary, fontSize = 12.sp)
                }
                if (comment.hearted) Icon(MkIcons.HeartFilled, stringResource(R.string.hearted), tint = Pelage.Rose, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun RepliesToggle(comment: Comment, open: Boolean) {
    val count = comment.replyCount
    TextAction(
        when {
            open -> stringResource(R.string.replies_hide)
            else -> pluralStringResource(R.plurals.replies_count, count, formatCount(count.toLong()))
        },
        Modifier.padding(start = 60.dp),
        color = Pelage.RouxClair,
    ) { Comments.toggleReplies(comment) }
}

// ---------------------------------------------------------------- petits morceaux

@Composable
private fun Avatar(comment: Comment, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Pelage.RouxNuit),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            comment.author.removePrefix("@").take(1).uppercase(),
            color = Pelage.RouxClair,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.ExtraBold,
        )
        if (comment.avatar != null) {
            AsyncImage(comment.avatar, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun TextAction(label: String, modifier: Modifier, color: Color = Secondary, onClick: () -> Unit) {
    Text(
        label,
        modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
    )
}

/**
 * Le texte du commentaire avec ses liens. YouTube envoie souvent les adresses et les
 * horodatages en texte brut : on les rend cliquables. Un horodatage (« 2:15 ») fait sauter
 * la vidéo en cours au bon moment au lieu d'ouvrir YouTube.
 */
@Composable
private fun commentText(comment: Comment): AnnotatedString = linkedText(comment.id, comment.text, comment.html)

/**
 * Un texte YouTube (commentaire, description) avec ses liens cliquables : adresses, et
 * horodatages (« 2:15 ») qui font sauter la vidéo en cours au bon moment.
 */
@Composable
fun linkedText(key: String, text: String, html: Boolean): AnnotatedString {
    val uriHandler = LocalUriHandler.current
    val videoId = PlayerHolder.nowPlaying.collectAsState().value?.videoId
    return remember(key, text, html, videoId) {
        val styles = TextLinkStyles(SpanStyle(color = Pelage.RouxClair, fontWeight = FontWeight.Bold))
        fun openUrl(url: String) {
            val seconds = URL_TIME.find(url)?.groupValues?.get(1)?.toLongOrNull()
            if (seconds != null && videoId != null && url.contains(videoId)) {
                PlayerHolder.seekTo(seconds * 1000)
            } else {
                runCatching { uriHandler.openUri(url) }
            }
        }
        val base = if (html) {
            AnnotatedString.fromHtml(text, linkStyles = styles, linkInteractionListener = { link ->
                (link as? LinkAnnotation.Url)?.url?.let(::openUrl)
            })
        } else {
            AnnotatedString(text)
        }
        linkify(base, styles, ::openUrl)
    }
}

/** Ajoute des liens sur les adresses et les horodatages qui n'en ont pas encore. */
private fun linkify(base: AnnotatedString, styles: TextLinkStyles, openUrl: (String) -> Unit): AnnotatedString {
    val text = base.text
    fun free(range: IntRange) = base.getLinkAnnotations(range.first, range.last + 1).isEmpty()
    return buildAnnotatedString {
        append(base)
        BARE_URL.findAll(text).filter { free(it.range) }.forEach { match ->
            val url = match.value.trimEnd('.', ',', ')', '!', '?', ';', ':')
            addLink(
                LinkAnnotation.Url(url, styles) { openUrl(url) },
                match.range.first,
                match.range.first + url.length,
            )
        }
        TIME_CODE.findAll(text).filter { free(it.range) }.forEach { match ->
            val parts = match.value.split(':').map { it.toLong() }
            val seconds = parts.fold(0L) { acc, n -> acc * 60 + n }
            addLink(
                LinkAnnotation.Clickable("t$seconds", styles) { PlayerHolder.seekTo(seconds * 1000) },
                match.range.first,
                match.range.last + 1,
            )
        }
    }
}

private val URL_TIME = Regex("[?&#]t=(\\d+)")
private val BARE_URL = Regex("https?://[^\\s<>\"]+")
private val TIME_CODE = Regex("(?<![\\d:])(?:\\d{1,2}:)?\\d{1,2}:\\d{2}(?![\\d:])")

/** Le texte sans balises, pour l'aperçu. */
private fun plainText(comment: Comment): String =
    if (comment.html) AnnotatedString.fromHtml(comment.text).text else comment.text
