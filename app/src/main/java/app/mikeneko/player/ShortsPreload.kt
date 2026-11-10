package app.mikeneko.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheWriter
import app.mikeneko.data.NewPipeDownloader
import app.mikeneko.data.VideoItem
import app.mikeneko.data.YouTubeRepository
import app.mikeneko.data.videoIdOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Les shorts autour de celui qu'on regarde (2.20.0) : les [AHEAD] suivants sont préparés (leur fiche
 * YouTube est lue et le début de leur vidéo est déjà dans le cache), les [BEHIND] précédents restent
 * prêts (fiche gardée, vidéo déjà dans le cache puisqu'elle vient d'être lue). Glisser vers l'un
 * d'eux le lance alors presque tout de suite, sans nouvelle requête à YouTube.
 *
 * Tout se passe sur le thread principal (comme [PlayerHolder.play]) ; seuls le réseau et l'écriture
 * du cache partent en arrière-plan.
 */
@OptIn(UnstableApi::class)
object ShortsPreload {
    const val AHEAD = 3
    const val BEHIND = 2
    /** Le début d'un short gardé d'avance : de quoi démarrer et jouer quelques secondes. */
    private const val VIDEO_BYTES = 2L * 1024 * 1024
    private const val AUDIO_BYTES = 384L * 1024

    private val scope = MainScope()
    /** Les fiches lues (ou en cours de lecture), par identifiant de vidéo. */
    private val infos = HashMap<String, Deferred<StreamInfo>>()
    /** Les shorts dont le début est déjà dans le cache. */
    private val warmed = HashSet<String>()
    private var worker: Job? = null

    /**
     * Le short [index] de [items] est à l'écran : on garde ce qui l'entoure, on oublie le reste, et
     * les suivants se préparent l'un après l'autre (le plus proche d'abord).
     */
    fun window(context: Context, items: List<VideoItem>, index: Int) {
        val app = context.applicationContext
        val ahead = (index + 1..index + AHEAD).mapNotNull { items.getOrNull(it)?.url }
        val around = (index - BEHIND..index + AHEAD).mapNotNull { items.getOrNull(it)?.url?.let(::videoIdOf) }.toSet()
        infos.entries.removeAll { (id, loading) -> (id !in around).also { gone -> if (gone) loading.cancel() } }
        warmed.retainAll(around)
        // Le début d'une vidéo en cours d'écriture s'arrête là ; les fiches demandées, elles, continuent
        // (celle du short où on vient d'arriver est peut-être presque là)
        worker?.cancel()
        worker = scope.launch {
            for (url in ahead) {
                val id = videoIdOf(url) ?: continue
                val info = info(url, start = true) ?: continue
                if (id !in warmed) {
                    runCatching { warm(app, info) }.onFailure { if (it is CancellationException) throw it }
                    warmed += id
                }
            }
        }
    }

    /**
     * La fiche de [url] si elle est prête ou en route (on l'attend) ; null : il faut la demander
     * (ou, avec [start], on la demande ici).
     */
    suspend fun info(url: String, start: Boolean = false): StreamInfo? {
        val id = videoIdOf(url) ?: return null
        val known = infos[id]
            ?: if (start) scope.async { YouTubeRepository.stream(url) }.also { infos[id] = it } else return null
        return try {
            known.await()
        } catch (e: Throwable) {
            currentCoroutineContext().ensureActive() // c'est nous qu'on arrête : on ne touche à rien
            // Ratée ou abandonnée : la lecture la redemandera elle-même
            if (infos[id] === known) infos.remove(id)
            null
        }
    }

    /** Un short vient d'être lancé : sa fiche est gardée (il reste prêt quand on revient en arrière). */
    fun remember(info: StreamInfo) {
        val id = info.id ?: return
        val known = infos[id]
        if (known == null || known.isCancelled) infos[id] = CompletableDeferred(info)
    }

    /** On quitte les Shorts. */
    fun clear() {
        worker?.cancel()
        worker = null
        infos.values.forEach { it.cancel() }
        infos.clear()
        warmed.clear()
    }

    /** Le début des flux que la lecture choisira (mêmes choix et mêmes clés) passe dans le cache des vidéos. */
    private suspend fun warm(context: Context, info: StreamInfo) {
        if (MediaSources.isLive(info)) return
        val plan = MediaSources.downloadPlan(info, audioOnly = false, PlayerHolder.quality.value)
        val cache = OfflineCache.cache(context, audioOnly = false)
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(NewPipeDownloader.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        val upstream = ChunkedDataSource.Factory(http)
        suspend fun head(uri: String, key: String, bytes: Long) {
            // La taille du fichier est à la fin de la clé quand on la connaît : jamais au-delà
            val total = key.substringAfterLast(':').toLongOrNull()
            val spec = DataSpec.Builder().setUri(uri).setKey(key).setPosition(0).setLength(if (total != null) minOf(bytes, total) else bytes).build()
            val writer = CacheWriter(OfflineCache.factory(cache, upstream).createDataSource(), spec, null, null)
            coroutineScope {
                val work = async(Dispatchers.IO) { runCatching { writer.cache() } }
                try {
                    work.await()
                } finally {
                    writer.cancel() // on a glissé ailleurs : l'écriture s'arrête là
                }
            }
        }
        if (plan.videoKey != null && plan.videoUrl != null) {
            head(plan.videoUrl, plan.videoKey, VIDEO_BYTES)
            head(plan.audioUrl, plan.audioKey, AUDIO_BYTES)
        } else {
            head(plan.audioUrl, plan.audioKey, VIDEO_BYTES) // un seul flux, image et son ensemble
        }
    }
}
