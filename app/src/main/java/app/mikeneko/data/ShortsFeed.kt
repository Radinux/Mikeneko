package app.mikeneko.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Le fil de l'onglet Shorts (2.20.0) : les shorts récents des chaînes suivies, mêlés à des shorts
 * trouvés sur les sujets qu'on regarde le plus. Ceux déjà vus passent à la fin. Une fois dans le
 * lecteur, les shorts suggérés par YouTube à partir de celui en cours prolongent la liste.
 */
object ShortsFeed {
    suspend fun load(context: Context): List<VideoItem> = coroutineScope {
        val app = context.applicationContext
        val store = SubscriptionStore.get(app)
        val subs = store.items.value
        val followed = async {
            runCatching { SubscriptionFeed.cached(subs) ?: SubscriptionFeed.latest(subs) }.getOrDefault(emptyList())
                .map { it.video }
                .filter { it.isShort || "/shorts/" in it.url }
        }
        val topics = withContext(Dispatchers.Default) { runCatching { Interests.build(app).topTopics(2) }.getOrDefault(emptyList()) }
        val found = (topics.map { "$it shorts" } + "#shorts").map { query ->
            async { runCatching { YouTubeRepository.search(query) }.getOrDefault(emptyList()).filter { it.looksShort } }
        }
        val lists = listOf(followed.await()) + found.awaitAll()

        // Un de chaque liste à tour de rôle, sans doublon, sans ce qu'on a écarté (« Pas intéressé », chaînes bloquées)
        val feedback = Feedback.get(app)
        val seen = HashSet<String>()
        val mixed = ArrayList<VideoItem>()
        for (rank in 0 until (lists.maxOfOrNull { it.size } ?: 0)) {
            for (list in lists) {
                val video = list.getOrNull(rank) ?: continue
                val id = videoIdOf(video.url) ?: video.url
                if (feedback.allows(video) && seen.add(id)) mixed += video.copy(isShort = true)
            }
        }
        val watched = store.watched.value.mapNotNullTo(HashSet()) { videoIdOf(it) }
        val (old, fresh) = mixed.partition { videoIdOf(it.url) in watched }
        fresh + old
    }
}
