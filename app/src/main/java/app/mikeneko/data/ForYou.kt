package app.mikeneko.data

import android.content.Context
import app.mikeneko.R
import app.mikeneko.settings.AppLocale
import java.time.Duration
import java.time.Instant
import kotlin.math.exp
import kotlin.random.Random
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Le « Pour toi » local : un petit algorithme qui tourne sur le téléphone, sans compte Google.
 *
 * Candidates :
 *  - les vidéos que YouTube suggère à côté de celles regardées récemment (une vidéo suggérée
 *    par plusieurs vidéos regardées remonte) ;
 *  - les nouveautés des abonnements (plus c'est frais, mieux c'est).
 * Bonus pour les chaînes regardées souvent, un peu de hasard pour que le fil bouge.
 * Puis : pas deux fois la même chaîne d'affilée, 3 vidéos max par chaîne, et une vidéo sur 4
 * vient du mélange des rubriques pour découvrir autre chose. Jamais de déjà-vu ni de direct.
 * Enfin, environ 1 vidéo sur 20 est une surprise : un sujet tiré au sort, ou « de fil en
 * aiguille » (une suggestion d'une vidéo regardée il y a plus longtemps).
 */
object ForYou {
    /** Le fil est refait quand l'historique change, sinon gardé 10 min. */
    private val cache = TimedCache<List<VideoItem>>(10 * 60_000L)

    private const val RECENT_SOURCES = 15 // vidéos regardées prises en compte pour les suggestions
    private const val RELATED_PER_SOURCE = 15
    private const val SUBS_MAX_AGE_DAYS = 21L
    private const val MAX_PER_CHANNEL = 3
    private const val EXPLORE_EVERY = 4 // 1 vidéo « découverte » toutes les 4
    private const val MAX_ITEMS = 80
    private const val MIN_RELATED_VIEWS = 1_000L
    private const val SURPRISE_EVERY = 20 // ~5 % de surprises
    private const val SURPRISE_TOPICS = 2 // sujets tirés au sort à chaque calcul
    private const val SURPRISE_MIN_VIEWS = 20_000L
    private const val SURPRISE_TIMEOUT_MS = 6_000L

    private fun key(context: Context): String {
        val history = WatchHistory.get(context).entries.value
        val subs = SubscriptionStore.get(context).items.value
        return "pour-toi:${history.firstOrNull()?.at ?: 0}:${history.size}:${subs.size}:${Feedback.get(context).version}"
    }

    fun cached(context: Context): List<VideoItem>? = cache.peek(key(context))

    /** Langue changée (les raisons affichées sont traduites) ou historique effacé. */
    fun clearCache() = cache.clear()

    /** [fresh] : tirer pour rafraîchir, un nouveau tirage (hasard et vidéos « découverte » renouvelés). */
    suspend fun build(context: Context, fresh: Boolean = false): List<VideoItem> =
        cache.get(key(context), fresh) { compute(context, fresh) }

    private suspend fun compute(context: Context, fresh: Boolean): List<VideoItem> {
        val history = WatchHistory.get(context).entries.value
        val store = SubscriptionStore.get(context)
        val subs = store.items.value
        val seen = store.watched.value + history.map { it.url }
        val feedback = Feedback.get(context)
        val now = System.currentTimeMillis()
        val random = if (fresh) Random(now) else Random(now / 600_000) // stable pendant 10 min, sauf si on rafraîchit
        val (explore, surprises) = coroutineScope {
            val explore = async { runCatching { YouTubeRepository.home(fresh) }.getOrDefault(emptyList()).filter(feedback::allows) }
            val surprises = async { surprises(history, seen, feedback, random) }
            explore.await() to surprises.await()
        }

        val personal = rank(history, subscriptionVideos(subs), seen, feedback, random)
        // Pas encore d'historique ni d'abonnements : le mélange des rubriques (et quelques surprises)
        if (personal.isEmpty()) return sprinkle(explore, surprises, random).take(MAX_ITEMS)

        val discover = explore.filter { it.url !in seen && personal.none { p -> p.url == it.url } }
        return sprinkle(mix(personal, discover), surprises, random).take(MAX_ITEMS)
    }

    /**
     * Les surprises : quelques vidéos d'un sujet tiré au sort, et quelques-unes « de fil en aiguille »
     * (on part d'une suggestion d'une vidéo regardée, et on pioche dans *ses* suggestions).
     * Seulement des vidéos assez vues et pas trop courtes : du hasard, mais du hasard qui peut plaire.
     */
    private suspend fun surprises(history: List<WatchHistory.Entry>, seen: Set<String>, feedback: Feedback, random: Random): List<VideoItem> {
        fun ok(v: VideoItem) = !v.isLive && v.url !in seen && feedback.allows(v) &&
            (v.views < 0 || v.views >= SURPRISE_MIN_VIEWS) && (v.durationSec <= 0 || v.durationSec >= 60)

        return coroutineScope {
            val topics = AppLocale.stringArray(R.array.surprise_topics).shuffled(random).take(SURPRISE_TOPICS)
            val fromTopics = topics.map { topic ->
                async {
                    val found = withTimeoutOrNull(SURPRISE_TIMEOUT_MS) { runCatching { YouTubeRepository.search(topic) }.getOrNull() }.orEmpty()
                    found.filter(::ok).shuffled(random).take(2).map { it.copy(reason = AppLocale.string(R.string.reason_surprise)) }
                }
            }
            // De fil en aiguille, sans requête : les suggestions (déjà gardées) d'une vidéo regardée il y a
            // plus longtemps, que le fil normal (les 15 dernières) ne regarde plus. Pas de page vidéo chargée
            // en douce : pour YouTube, ça ressemblait à des visionnages automatiques.
            val older = history.drop(RECENT_SOURCES).filter { it.related.isNotEmpty() }.ifEmpty { history.filter { it.related.isNotEmpty() } }
            val thread = older.randomOrNull(random)?.let { source ->
                source.related.filter(::ok).shuffled(random).take(2)
                    .map { it.copy(reason = AppLocale.string(R.string.reason_thread, shorten(source.title))) }
            }.orEmpty()
            (fromTopics.awaitAll().flatten() + thread).shuffled(random).distinctBy { it.url }
        }
    }

    /** Glisse les surprises dans le fil : la première vers la 5e-9e place, puis environ toutes les 20. */
    private fun sprinkle(feed: List<VideoItem>, surprises: List<VideoItem>, random: Random): List<VideoItem> {
        if (surprises.isEmpty()) return feed
        val out = feed.filterNot { f -> surprises.any { it.url == f.url } }.toMutableList()
        var at = 4 + random.nextInt(5)
        for (surprise in surprises) {
            if (at > out.size) break
            out.add(at, surprise)
            at += SURPRISE_EVERY - 2 + random.nextInt(5)
        }
        return out
    }

    /** Les vidéos récentes des abonnements (depuis le cache si possible, sinon 8 s max). */
    private suspend fun subscriptionVideos(subs: List<Subscription>): List<FeedVideo> {
        if (subs.isEmpty()) return emptyList()
        return SubscriptionFeed.cached(subs)
            ?: withTimeoutOrNull(8_000) { runCatching { SubscriptionFeed.latest(subs) }.getOrNull() }
            ?: emptyList()
    }

    private fun rank(history: List<WatchHistory.Entry>, subsFeed: List<FeedVideo>, seen: Set<String>, feedback: Feedback, random: Random): List<VideoItem> {
        val now = System.currentTimeMillis()

        // Affinité avec chaque chaîne : chaque visionnage compte, les récents plus (demi-vie ~10 jours)
        val affinity = HashMap<String, Double>()
        history.forEach { e ->
            val days = (now - e.at) / 86_400_000.0
            channelKey(e.channelId, e.channel)?.let { affinity.merge(it, exp(-days / 14.0), Double::plus) }
        }
        val maxAffinity = affinity.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0

        class Candidate(val video: VideoItem, var score: Double, var reason: String, var best: Double)
        val candidates = LinkedHashMap<String, Candidate>()

        fun offer(video: VideoItem, points: Double, reason: String) {
            if (video.isLive || video.url in seen || !feedback.allows(video)) return
            val c = candidates.getOrPut(video.url) { Candidate(video, 0.0, reason, 0.0) }
            c.score += points
            if (points > c.best) {
                c.best = points
                c.reason = reason
            }
        }

        // 1. Suggestions des vidéos regardées récemment
        history.take(RECENT_SOURCES).forEachIndexed { i, e ->
            val sourceWeight = 1.0 / (1.0 + i * 0.3)
            e.related.take(RELATED_PER_SOURCE).forEachIndexed { rank, video ->
                // Les suggestions à moins de 1 000 vues sont souvent des ré-uploads douteux
                if (video.views in 0 until MIN_RELATED_VIEWS) return@forEachIndexed
                offer(video, sourceWeight * (1.0 - rank / (RELATED_PER_SOURCE + 5.0)), AppLocale.string(R.string.reason_watched, shorten(e.title)))
            }
        }

        // 2. Nouveautés des abonnements
        subsFeed.forEach { item ->
            val days = Duration.between(item.published, Instant.now()).toHours() / 24.0
            if (days in 0.0..SUBS_MAX_AGE_DAYS.toDouble()) {
                offer(item.video, 0.95 * exp(-days / 7.0), AppLocale.string(R.string.reason_subscription))
            }
        }

        // 3. Bonus chaînes aimées + un peu de hasard
        candidates.values.forEach { c ->
            val a = channelKey(c.video.channelId, c.video.channel)?.let { affinity[it] }?.div(maxAffinity) ?: 0.0
            c.score *= (1.0 + 0.6 * a) * (0.85 + random.nextDouble() * 0.3) * feedback.penalty(c.video.channelKey())
        }

        val ranked = candidates.values.sortedByDescending { it.score }.map { it.video.copy(reason = it.reason) }
        return diversify(ranked)
    }

    /** Pas deux fois la même chaîne d'affilée, et [MAX_PER_CHANNEL] vidéos maximum par chaîne. */
    private fun diversify(ranked: List<VideoItem>): List<VideoItem> {
        val out = ArrayList<VideoItem>()
        val perChannel = HashMap<String, Int>()
        val waiting = ArrayDeque(ranked)
        var skipped = 0
        while (waiting.isNotEmpty() && skipped < waiting.size) {
            val video = waiting.removeFirst()
            val key = channelKey(video.channelId, video.channel) ?: video.url
            val lastKey = out.lastOrNull()?.let { channelKey(it.channelId, it.channel) }
            val count = perChannel[key] ?: 0
            when {
                count >= MAX_PER_CHANNEL -> Unit // trop de cette chaîne : écartée
                key == lastKey -> {
                    waiting.addLast(video) // réessayée plus loin
                    skipped++
                }
                else -> {
                    out += video
                    perChannel[key] = count + 1
                    skipped = 0
                }
            }
        }
        return out
    }

    /** 3 vidéos personnelles, 1 découverte, et ainsi de suite. */
    private fun mix(personal: List<VideoItem>, discover: List<VideoItem>): List<VideoItem> {
        val out = ArrayList<VideoItem>()
        val p = personal.iterator()
        val d = discover.iterator()
        while (p.hasNext() || d.hasNext()) {
            repeat(EXPLORE_EVERY - 1) { if (p.hasNext()) out += p.next() }
            if (d.hasNext()) out += d.next()
        }
        return out.distinctBy { it.url }
    }

    private fun channelKey(channelId: String?, name: String): String? =
        channelId ?: name.trim().lowercase().ifEmpty { null }

    private fun shorten(title: String): String = if (title.length <= 40) title else title.take(38).trimEnd() + "…"
}
