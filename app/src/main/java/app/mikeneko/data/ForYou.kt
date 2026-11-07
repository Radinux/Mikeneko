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
 * Le « Pour toi » local (refait en 2.7.0) : il tourne sur le téléphone, sans compte Google.
 *
 * Même schéma que les grandes plateformes : on ramasse des candidates, on les classe avec le profil
 * d'intérêts ([Interests]), puis des règles de diversité.
 *
 * Candidates personnelles (4 vidéos sur 5) :
 *  - les vidéos que YouTube suggère à côté de celles **vraiment regardées** (pondérées par la part
 *    regardée et l'ancienneté : les suggestions de YouTube sont calculées sur tous ses utilisateurs,
 *    c'est notre « ceux qui ont aimé ça ont aussi aimé ») ;
 *  - les nouveautés des abonnements.
 * Découverte (1 sur 5) : des recherches tirées des goûts choisis et des sujets du profil, une
 * surprise (sujet au hasard) et « de fil en aiguille ». Au plus 3 recherches YouTube par calcul
 * (4 au tout début). Les résultats sont piochés au hasard, avec un tirage propre à chaque téléphone
 * ([Tastes.seed]) : deux téléphones n'ont pas le même fil. Plus de tendances ni de recherches fixes
 * (avant 2.7.0, elles montraient les mêmes vidéos à tout le monde).
 *
 * Classement : sujets et chaîne qui collent au profil, un peu de hasard, « Pas intéressé », et la
 * fatigue ([Impressions] : une vidéo montrée plusieurs fois sans être ouverte descend puis part).
 * Puis : pas deux fois la même chaîne d'affilée, 3 vidéos max par chaîne. Jamais de déjà-vu ni de direct.
 */
object ForYou {
    /** Le fil est refait quand l'historique, les goûts ou les retours changent, sinon gardé 10 min. */
    private val cache = TimedCache<List<VideoItem>>(10 * 60_000L)

    private const val SOURCES = 25 // vidéos regardées dont on prend les suggestions
    private const val RELATED_PER_SOURCE = 15
    private const val SUBS_MAX_AGE_DAYS = 21L
    private const val MAX_PER_CHANNEL = 3
    private const val DISCOVER_EVERY = 5 // 1 vidéo de découverte toutes les 5
    private const val MAX_ITEMS = 80
    private const val MIN_RELATED_VIEWS = 1_000L
    private const val MIN_SEARCH_VIEWS = 5_000L
    private const val SURPRISE_MIN_VIEWS = 20_000L
    private const val PER_QUERY = 6
    private const val SEARCH_TIMEOUT_MS = 6_000L

    private fun key(context: Context): String {
        val history = WatchHistory.get(context).entries.value
        val subs = SubscriptionStore.get(context).items.value
        val library = VideoLibrary.get(context)
        val tastes = Tastes.get(context).chosen.value.sorted().joinToString(",")
        return "pour-toi:${history.firstOrNull()?.at ?: 0}:${history.size}:${subs.size}:${Feedback.get(context).version}:" +
            "${library.liked.value.size}:${library.later.value.size}:$tastes"
    }

    fun cached(context: Context): List<VideoItem>? = cache.peek(key(context))

    /** Langue changée (les raisons affichées sont traduites), historique effacé ou goûts changés. */
    fun clearCache() = cache.clear()

    /** [fresh] : tirer pour rafraîchir, un nouveau tirage (hasard et découverte renouvelés). */
    suspend fun build(context: Context, fresh: Boolean = false): List<VideoItem> =
        cache.get(key(context), fresh) { compute(context, fresh) }

    private suspend fun compute(context: Context, fresh: Boolean): List<VideoItem> {
        val history = WatchHistory.get(context).entries.value
        val store = SubscriptionStore.get(context)
        val seen = store.watched.value + history.map { it.url }
        val feedback = Feedback.get(context)
        val impressions = Impressions.get(context)
        val tastes = Tastes.get(context)
        val profile = Interests.build(context)
        val now = System.currentTimeMillis()
        // Propre à ce téléphone ; stable pendant 10 min, sauf si on rafraîchit
        val random = Random(tastes.seed xor (if (fresh) now else now / 600_000))
        val usable = { v: VideoItem -> !v.isLive && v.url !in seen && feedback.allows(v) && !impressions.tired(v.url) }

        val personal = rank(history, subscriptionVideos(store.items.value), profile, usable, feedback, impressions, random)
        val discover = discover(history, profile, usable, random, fresh, cold = personal.size < 12)
            .filter { d -> personal.none { it.url == d.url } }
        // Rien de personnel encore (téléphone neuf) : la découverte seule, tirée des goûts choisis
        if (personal.isEmpty()) return diversify(discover).take(MAX_ITEMS)
        return mix(personal, discover).take(MAX_ITEMS)
    }

    private fun rank(
        history: List<WatchHistory.Entry>,
        subsFeed: List<FeedVideo>,
        profile: Interests.Profile,
        usable: (VideoItem) -> Boolean,
        feedback: Feedback,
        impressions: Impressions,
        random: Random,
    ): List<VideoItem> {
        val now = System.currentTimeMillis()
        class Candidate(val video: VideoItem, var score: Double, var reason: String, var best: Double)
        val candidates = LinkedHashMap<String, Candidate>()

        fun offer(video: VideoItem, points: Double, reason: String) {
            if (points <= 0 || !usable(video)) return
            val c = candidates.getOrPut(video.url) { Candidate(video, 0.0, reason, 0.0) }
            c.score += points
            if (points > c.best) {
                c.best = points
                c.reason = reason
            }
        }

        // 1. Suggestions des vidéos vraiment regardées (une vidéo suggérée par plusieurs remonte)
        val sources = history.filter { it.related.isNotEmpty() }
            .map { it to Interests.watchWeight(it, now) }
            .sortedByDescending { it.second }
            .take(SOURCES)
        val strongest = sources.firstOrNull()?.second?.takeIf { it > 0 } ?: 1.0
        sources.forEach { (e, weight) ->
            val sourceWeight = weight / strongest
            e.related.take(RELATED_PER_SOURCE).forEachIndexed { rank, video ->
                // Les suggestions à moins de 1 000 vues sont souvent des ré-uploads douteux
                if (video.views in 0 until MIN_RELATED_VIEWS) return@forEachIndexed
                offer(video, sourceWeight * (1.0 - rank / (RELATED_PER_SOURCE + 5.0)), AppLocale.string(R.string.reason_watched, shorten(e.title)))
            }
        }

        // 2. Nouveautés des abonnements (plus fraîches et de chaînes plus regardées : plus haut)
        subsFeed.forEach { item ->
            val days = Duration.between(item.published, Instant.now()).toHours() / 24.0
            if (days in 0.0..SUBS_MAX_AGE_DAYS.toDouble()) {
                val liked = profile.channelMatch(item.video).coerceAtLeast(0.0)
                offer(item.video, 0.9 * exp(-days / 7.0) * (0.7 + 0.3 * liked), AppLocale.string(R.string.reason_subscription))
            }
        }

        // 3. Le profil (sujets, chaîne), un peu de hasard, les retours et la fatigue
        candidates.values.forEach { c ->
            val fit = (1.0 + 0.9 * profile.topicMatch(c.video) + 0.5 * profile.channelMatch(c.video)).coerceAtLeast(0.1)
            c.score *= fit * (0.85 + random.nextDouble() * 0.3) * feedback.penalty(c.video.channelKey()) * impressions.fatigue(c.video.url)
        }

        val ranked = candidates.values.sortedByDescending { it.score }.map { it.video.copy(reason = it.reason) }
        return diversify(ranked)
    }

    /**
     * La découverte : quelques recherches tirées des goûts et du profil, une surprise, et « de fil en
     * aiguille ». [cold] : presque rien de personnel encore, on cherche surtout dans les goûts.
     */
    private suspend fun discover(
        history: List<WatchHistory.Entry>,
        profile: Interests.Profile,
        usable: (VideoItem) -> Boolean,
        random: Random,
        fresh: Boolean,
        cold: Boolean,
    ): List<VideoItem> {
        class Query(val text: String, val reason: String, val minViews: Long)
        val queries = ArrayList<Query>()

        // Les goûts choisis : une recherche au hasard parmi celles du thème
        val tastes = Tastes.ALL.filter { it.id in profile.tastes }.shuffled(random)
        tastes.take(if (cold) 3 else 1).forEach { taste ->
            AppLocale.stringArray(taste.queries).randomOrNull(random)?.let {
                queries += Query(it, AppLocale.string(R.string.reason_taste, AppLocale.string(taste.label)), MIN_SEARCH_VIEWS)
            }
        }
        // Les sujets du profil : un sujet fort (tiré au hasard parmi les premiers), avec le mot qui
        // l'accompagne le plus souvent dans les titres regardés, pour une recherche moins vague
        if (!cold || tastes.isEmpty()) {
            profile.topTopics(10).takeIf { it.isNotEmpty() }?.let { top ->
                val topic = top[(random.nextDouble() * random.nextDouble() * top.size).toInt()] // les premiers plus souvent
                val partner = companion(topic, history, top)
                val text = listOfNotNull(topic, partner).joinToString(" ")
                queries += Query(text, AppLocale.string(R.string.reason_topic, text), MIN_SEARCH_VIEWS)
            }
        }
        // Une surprise : un sujet au hasard, pour sortir de sa bulle (plusieurs si on ne sait rien)
        val surprises = AppLocale.stringArray(R.array.surprise_topics).shuffled(random)
        val budget = if (cold) 4 else 3
        surprises.take((budget - queries.size).coerceAtLeast(1)).forEach {
            queries += Query(it, AppLocale.string(R.string.reason_surprise), SURPRISE_MIN_VIEWS)
        }

        val found = coroutineScope {
            queries.take(budget).map { q ->
                async {
                    val results = withTimeoutOrNull(SEARCH_TIMEOUT_MS) { runCatching { YouTubeRepository.search(q.text, fresh) }.getOrNull() }.orEmpty()
                    results
                        .filter { usable(it) && (it.views < 0 || it.views >= q.minViews) && (it.durationSec <= 0 || it.durationSec >= 60) }
                        // Pas toujours les premiers résultats (les mêmes pour tout le monde) : le profil et le hasard
                        .sortedByDescending { profile.topicMatch(it) + random.nextDouble() * 0.8 }
                        .take(PER_QUERY)
                        .map { it.copy(reason = q.reason) }
                }
            }.awaitAll()
        }
        // De fil en aiguille, sans requête : les suggestions (déjà gardées) d'une vidéo regardée il y a
        // plus longtemps. Pas de page vidéo chargée en douce (pour YouTube, ça ressemblerait à des
        // visionnages automatiques).
        val older = history.drop(SOURCES).filter { it.related.isNotEmpty() }.ifEmpty { history.filter { it.related.isNotEmpty() } }
        val thread = older.randomOrNull(random)?.let { source ->
            source.related.filter { usable(it) && (it.views < 0 || it.views >= SURPRISE_MIN_VIEWS) }.shuffled(random).take(2)
                .map { it.copy(reason = AppLocale.string(R.string.reason_thread, shorten(source.title))) }
        }.orEmpty()
        return interleave(found + listOf(thread)).distinctBy { it.url }
    }

    /** Le mot qui accompagne le plus souvent [topic] dans les titres regardés (parmi les sujets forts). */
    private fun companion(topic: String, history: List<WatchHistory.Entry>, top: List<String>): String? {
        val counts = HashMap<String, Int>()
        history.take(100).forEach { e ->
            val words = Interests.words(e.title) - Interests.words(e.channel)
            if (topic in words) (words - topic).filter { it in top }.forEach { counts.merge(it, 1, Int::plus) }
        }
        return counts.entries.filter { it.value >= 2 }.maxByOrNull { it.value }?.key
    }

    /** Les vidéos récentes des abonnements (depuis le cache si possible, sinon 8 s max). */
    private suspend fun subscriptionVideos(subs: List<Subscription>): List<FeedVideo> {
        if (subs.isEmpty()) return emptyList()
        return SubscriptionFeed.cached(subs)
            ?: withTimeoutOrNull(8_000) { runCatching { SubscriptionFeed.latest(subs) }.getOrNull() }
            ?: emptyList()
    }

    /** Pas deux fois la même chaîne d'affilée, et [MAX_PER_CHANNEL] vidéos maximum par chaîne. */
    private fun diversify(ranked: List<VideoItem>): List<VideoItem> {
        val out = ArrayList<VideoItem>()
        val perChannel = HashMap<String, Int>()
        val waiting = ArrayDeque(ranked)
        var skipped = 0
        while (waiting.isNotEmpty() && skipped < waiting.size) {
            val video = waiting.removeFirst()
            val key = video.channelKey() ?: video.url
            val lastKey = out.lastOrNull()?.channelKey()
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

    /** 4 vidéos personnelles, 1 découverte, et ainsi de suite. */
    private fun mix(personal: List<VideoItem>, discover: List<VideoItem>): List<VideoItem> {
        val out = ArrayList<VideoItem>()
        val p = personal.iterator()
        val d = discover.iterator()
        while (p.hasNext() || d.hasNext()) {
            repeat(DISCOVER_EVERY - 1) { if (p.hasNext()) out += p.next() }
            if (d.hasNext()) out += d.next()
        }
        return out.distinctBy { it.url }
    }

    /** a1, b1, c1, a2, b2, c2… */
    private fun interleave(lists: List<List<VideoItem>>): List<VideoItem> {
        val longest = lists.maxOfOrNull { it.size } ?: 0
        return (0 until longest).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } }
    }

    private fun shorten(title: String): String = if (title.length <= 40) title else title.take(38).trimEnd() + "…"
}
