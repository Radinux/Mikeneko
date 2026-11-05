package app.mikeneko.ui.components

import app.mikeneko.ui.theme.statusBarSpace
import app.mikeneko.ui.theme.jellyClickable
import app.mikeneko.ui.theme.clay
import app.mikeneko.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
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
import app.mikeneko.data.Comment
import app.mikeneko.data.Comments
import app.mikeneko.data.GoogleAccount
import app.mikeneko.data.SubscriptionStore
import app.mikeneko.data.formatCount
import app.mikeneko.player.PlayerHolder
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.glass.LiquidGlass
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.Rounded
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private val Secondary = Color(0xFFCFC6BD)

/** L'aperçu sous la vidéo : nombre de commentaires et le premier. Un tap ouvre le panneau. */
@Composable
fun CommentsPreview(modifier: Modifier = Modifier) {
    val state by Comments.state.collectAsState()
    val first = state.items.firstOrNull()
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .clickable(enabled = !state.disabled, role = Role.Button, onClickLabel = stringResource(R.string.comments_open)) { Comments.open.value = true }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.comments), color = Pelage.Texte, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            if (state.count > 0) {
                Text("  ${formatCount(state.count.toLong())}", color = Secondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            if (!state.disabled) Icon(MkIcons.ChevronRight, null, tint = Secondary, modifier = Modifier.size(18.dp))
        }
        when {
            state.disabled -> Text(stringResource(R.string.comments_disabled_video), color = Secondary, fontSize = 13.sp)
            state.loading -> Text(stringResource(R.string.comments_loading), color = Secondary, fontSize = 13.sp)
            state.error != null -> Text(stringResource(R.string.comments_error), color = Secondary, fontSize = 13.sp)
            first == null -> Text(stringResource(R.string.comments_be_first), color = Secondary, fontSize = 13.sp)
            else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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

/**
 * Le panneau des commentaires, en verre, sous la vidéo (qui continue de jouer au-dessus).
 * En bas, la barre pour commenter : elle ne marche qu'avec le compte Google connecté.
 */
@Composable
fun CommentsPanel(backdrop: Backdrop) {
    val open by Comments.open.collectAsState()
    var replyTo by remember { mutableStateOf<Comment?>(null) }

    BackHandler(enabled = open) {
        if (replyTo != null) replyTo = null else Comments.open.value = false
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
                    CommentList(Modifier.weight(1f), onReply = { replyTo = it })
                    // Écrire passe par le compte Google : la barre n'apparaît que si la connexion est activée
                    if (GoogleAccount.ENABLED) CommentBar(replyTo = replyTo, onCancelReply = { replyTo = null })
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
private fun CommentList(modifier: Modifier, onReply: (Comment) -> Unit) {
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
                    CommentRow(comment, onReply = { onReply(comment) })
                    if (comment.replyCount > 0 || thread?.items?.isNotEmpty() == true) {
                        RepliesToggle(comment, thread?.open == true)
                    }
                    if (thread?.open == true) {
                        thread.items.forEach { reply ->
                            CommentRow(reply, indent = 46.dp, onReply = { onReply(comment) })
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
private fun CommentRow(comment: Comment, indent: Dp = 0.dp, onReply: () -> Unit) {
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
                if (GoogleAccount.ENABLED) Text(
                    stringResource(R.string.reply),
                    Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onReply).padding(horizontal = 6.dp, vertical = 6.dp),
                    color = Secondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
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

/**
 * La barre pour commenter. Sans compte Google connecté, elle propose de le connecter
 * (YouTube n'accepte les commentaires que d'un compte).
 */
@Composable
private fun CommentBar(replyTo: Comment?, onCancelReply: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val store = remember { SubscriptionStore.get(context) }
    val connected by store.connected.collectAsState()
    val now by PlayerHolder.nowPlaying.collectAsState()
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    // Ce qu'il faut faire une fois que Google a donné le jeton (après l'écran d'accord)
    var pending by remember { mutableStateOf<(suspend (String) -> Unit)?>(null) }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val action = pending
        pending = null
        runCatching { GoogleAccount.resultFromIntent(context, result.data) }
            .onSuccess { auth -> auth.accessToken?.let { token -> action?.let { scope.launch { it(token) } } } ?: toast(context.getString(R.string.login_cancelled)) }
            .onFailure { toast(context.getString(R.string.login_cancelled)) }
    }

    /**
     * Obtient un jeton avec le droit de commenter, puis lance [action]. Si Google doit d'abord
     * afficher son écran d'accord, [action] attend le retour de cet écran.
     */
    fun withWriteToken(action: suspend (String) -> Unit) {
        scope.launch {
            runCatching { GoogleAccount.authorize(context, write = true) }
                .onSuccess { auth ->
                    val intent = auth.pendingIntent
                    when {
                        auth.hasResolution() && intent != null -> {
                            pending = action
                            consent.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                        }
                        auth.accessToken != null -> action(auth.accessToken!!)
                        else -> toast(context.getString(R.string.google_no_access))
                    }
                }
                .onFailure { toast(context.getString(R.string.google_refused)) }
        }
    }

    fun connect() = withWriteToken { token ->
        store.setConnected(true)
        toast(context.getString(R.string.google_connected))
        // Tant qu'à faire, on récupère aussi les abonnements
        runCatching { GoogleAccount.fetchSubscriptions(token) }.onSuccess { store.addAll(it) }
    }

    fun send() {
        val message = text.trim()
        val videoId = now?.videoId ?: return
        if (message.isEmpty() || sending) return
        val parent = replyTo
        withWriteToken { token ->
            sending = true
            runCatching {
                if (parent != null) GoogleAccount.postReply(token, parent.id, message)
                else GoogleAccount.postComment(token, videoId, message)
            }.onSuccess { posted ->
                Comments.addMine(posted, parent?.id)
                text = ""
                onCancelReply()
                keyboard?.hide()
                toast(context.getString(if (parent != null) R.string.reply_posted else R.string.comment_posted))
            }.onFailure { toast(it.message ?: context.getString(R.string.post_failed)) }
            sending = false
        }
    }

    // Réponse : on commence par mentionner l'auteur, comme sur YouTube
    LaunchedEffect(replyTo) {
        val target = replyTo ?: return@LaunchedEffect
        val mention = target.author.let { if (it.startsWith("@")) it else "@$it" }
        if (!text.startsWith(mention)) text = "$mention $text"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!connected) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(MkIcons.Account, null, tint = Secondary, modifier = Modifier.padding(start = 4.dp).size(20.dp))
                Text(
                    stringResource(R.string.comment_need_google),
                    Modifier.weight(1f).padding(horizontal = 10.dp),
                    color = Secondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.sign_in),
                    Modifier.jellyClickable { connect() }.clay(Pelage.Creme).padding(horizontal = 14.dp, vertical = 10.dp),
                    color = Pelage.Encre,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            return@Column
        }

        if (replyTo != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.reply_to, replyTo.author),
                    Modifier.weight(1f).padding(start = 6.dp),
                    color = Pelage.RouxClair,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onCancelReply), contentAlignment = Alignment.Center) {
                    Icon(MkIcons.Close, stringResource(R.string.reply_cancel), tint = Secondary, modifier = Modifier.size(16.dp))
                }
            }
        }

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(10_000) }, // limite de YouTube
                    maxLines = 5,
                    textStyle = TextStyle(fontFamily = Rounded, color = Pelage.Texte, fontSize = 15.sp),
                    cursorBrush = SolidColor(Pelage.RouxClair),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { field ->
                        if (text.isEmpty()) {
                            Text(
                                stringResource(if (replyTo != null) R.string.reply_hint else R.string.comment_hint),
                                color = Secondary.copy(alpha = 0.8f),
                                fontSize = 15.sp,
                            )
                        }
                        field()
                    },
                )
            }
            val canSend = text.isNotBlank() && !sending
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (canSend) Pelage.Roux else Color.White.copy(alpha = 0.1f))
                    .clickable(enabled = canSend, role = Role.Button, onClickLabel = stringResource(R.string.publish)) { send() },
                contentAlignment = Alignment.Center,
            ) {
                if (sending) {
                    Loading(Modifier.size(22.dp))
                } else {
                    Icon(MkIcons.Send, stringResource(R.string.publish), tint = if (canSend) Pelage.Encre else Secondary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- petits morceaux

@Composable
private fun Avatar(comment: Comment, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Pelage.Violet),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            comment.author.removePrefix("@").take(1).uppercase(),
            color = Color.White,
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
