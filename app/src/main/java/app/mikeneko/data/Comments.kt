package app.mikeneko.data

import app.mikeneko.R
import app.mikeneko.settings.AppLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description

/** Un commentaire YouTube (lu via NewPipe, ou publié par la personne via l'API). */
data class Comment(
    val id: String,
    val author: String,
    val avatar: String?,
    /** Texte en HTML (liens, gras, horodatages) quand [html] est vrai. */
    val text: String,
    val likes: Int,
    val ago: String?,
    val html: Boolean = true,
    val pinned: Boolean = false,
    val hearted: Boolean = false,
    val verified: Boolean = false,
    val byCreator: Boolean = false,
    val edited: Boolean = false,
    val replyCount: Int = 0,
    val repliesPage: Page? = null,
    /** Publié depuis Mikeneko pendant cette session. */
    val mine: Boolean = false,
)

/** Les réponses d'un fil, chargées à la demande. */
data class Replies(
    val open: Boolean = false,
    val items: List<Comment> = emptyList(),
    val next: Page? = null,
    val loading: Boolean = false,
)

data class CommentsState(
    val url: String? = null,
    val loading: Boolean = false,
    val items: List<Comment> = emptyList(),
    val next: Page? = null,
    val loadingMore: Boolean = false,
    /** -1 si YouTube ne donne pas le nombre. */
    val count: Int = -1,
    val disabled: Boolean = false,
    val error: String? = null,
    val replies: Map<String, Replies> = emptyMap(),
)

/** Les commentaires de la vidéo en cours, partagés entre l'aperçu et le panneau. */
object Comments {
    private val scope = MainScope()
    private val yt get() = ServiceList.YouTube
    private var job: Job? = null

    val state = MutableStateFlow(CommentsState())
    /** Panneau des commentaires ouvert sous la vidéo. */
    val open = MutableStateFlow(false)

    fun load(url: String) {
        val current = state.value
        if (current.url == url && (current.loading || current.items.isNotEmpty() || current.disabled)) return
        job?.cancel()
        state.value = CommentsState(url = url, loading = true)
        job = scope.launch {
            runCatching { YouTubeRepository.ensureClient(); withContext(Dispatchers.IO) { CommentsInfo.getInfo(yt, url) } }
                .onSuccess { info ->
                    if (state.value.url != url) return@onSuccess
                    state.value = state.value.copy(
                        loading = false,
                        items = info.relatedItems.map { it.toComment() },
                        next = info.nextPage,
                        count = info.commentsCount,
                        disabled = info.isCommentsDisabled,
                    )
                }
                .onFailure { e ->
                    if (state.value.url == url) state.value = state.value.copy(loading = false, error = e.message ?: AppLocale.string(R.string.unknown_error))
                }
        }
    }

    /** Langue / région changée : les dates des commentaires sont à refaire. */
    fun reset() {
        job?.cancel()
        state.value = CommentsState()
    }

    fun retry() {
        val url = state.value.url ?: return
        state.value = CommentsState()
        load(url)
    }

    fun loadMore() {
        val current = state.value
        val url = current.url ?: return
        val page = current.next ?: return
        if (current.loading || current.loadingMore) return
        state.value = current.copy(loadingMore = true)
        scope.launch {
            runCatching { YouTubeRepository.ensureClient(); withContext(Dispatchers.IO) { CommentsInfo.getMoreItems(yt, url, page) } }
                .onSuccess { result ->
                    state.update {
                        if (it.url != url) it
                        else it.copy(
                            items = (it.items + result.items.map { c -> c.toComment() }).distinctBy { c -> c.id },
                            next = result.nextPage,
                            loadingMore = false,
                        )
                    }
                }
                .onFailure { state.update { it.copy(loadingMore = false) } }
        }
    }

    /** Ouvre / ferme les réponses d'un commentaire (chargées la première fois). */
    fun toggleReplies(comment: Comment) {
        val thread = state.value.replies[comment.id] ?: Replies()
        if (thread.open) {
            setReplies(comment.id) { it.copy(open = false) }
            return
        }
        setReplies(comment.id) { it.copy(open = true) }
        if (thread.items.isEmpty() && comment.repliesPage != null) loadReplies(comment.id, comment.repliesPage)
    }

    fun moreReplies(comment: Comment) {
        val page = state.value.replies[comment.id]?.next ?: return
        loadReplies(comment.id, page)
    }

    private fun loadReplies(id: String, page: Page) {
        val url = state.value.url ?: return
        if (state.value.replies[id]?.loading == true) return
        setReplies(id) { it.copy(loading = true) }
        scope.launch {
            runCatching { YouTubeRepository.ensureClient(); withContext(Dispatchers.IO) { CommentsInfo.getMoreItems(yt, url, page) } }
                .onSuccess { result ->
                    if (state.value.url != url) return@onSuccess
                    setReplies(id) {
                        it.copy(
                            items = (it.items + result.items.map { c -> c.toComment() }).distinctBy { c -> c.id },
                            next = result.nextPage,
                            loading = false,
                        )
                    }
                }
                .onFailure { setReplies(id) { it.copy(loading = false) } }
        }
    }

    private fun setReplies(id: String, change: (Replies) -> Replies) {
        state.update { it.copy(replies = it.replies + (id to change(it.replies[id] ?: Replies()))) }
    }

    private fun CommentsInfoItem.toComment() = Comment(
        id = commentId,
        author = uploaderName.orEmpty(),
        avatar = uploaderAvatars.maxByOrNull { it.height }?.url,
        text = commentText.content.orEmpty(),
        html = commentText.type == Description.Type.HTML,
        likes = likeCount,
        ago = uploadDate?.offsetDateTime()?.toInstant()?.let(::formatAgo) ?: textualUploadDate,
        pinned = isPinned,
        hearted = isHeartedByUploader,
        verified = isUploaderVerified,
        byCreator = isChannelOwner,
        edited = isEdited,
        replyCount = replyCount.coerceAtLeast(0),
        repliesPage = replies,
    )
}
