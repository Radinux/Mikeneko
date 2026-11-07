package app.mikeneko.data

import android.content.Context
import java.text.Normalizer
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Le profil d'intérêts du « Pour toi » (2.7.0), calculé sur le téléphone, jamais envoyé nulle part.
 *
 * Comme les grandes plateformes (YouTube vise le temps regardé, d'autres la vidéo regardée jusqu'au
 * bout), une vidéo compte selon ce qu'on en a vraiment regardé : ouverte puis fermée tout de suite,
 * elle ne compte presque pas. S'y ajoutent les J'aime (fort), « À regarder plus tard » et les
 * playlists vidéo, les abonnements, les goûts choisis, et « Pas intéressé » en négatif. Le temps
 * fait baisser doucement le poids des vieux visionnages (demi-vie d'un mois).
 *
 * Il apprend des **chaînes** et des **sujets** : les mots des titres et les tags, sans les mots vides
 * (le, the, video, officiel…) ni le nom de la chaîne. Un mot vu sur plusieurs chaînes est un vrai
 * sujet ; un mot d'une seule chaîne compte moins.
 */
object Interests {
    class Profile(
        /** Sujet → poids (négatif : plutôt pas). */
        val topics: Map<String, Double>,
        /** Clé de chaîne → poids (négatif : plutôt pas). */
        val channels: Map<String, Double>,
        /** Les goûts choisis (ids de [Tastes.ALL]). */
        val tastes: Set<String>,
        /** Les visionnages comptés, pondérés : 0 = on ne sait encore rien. */
        val strength: Double,
    ) {
        private val maxTopic = topics.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        private val maxChannel = channels.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0

        /** À quel point les sujets de [video] collent au profil : de -1 (rejetés) à 1. */
        fun topicMatch(video: VideoItem): Double {
            val words = words(video.title) - words(video.channel)
            if (words.isEmpty()) return 0.0
            val sum = words.sumOf { topics[it] ?: 0.0 }
            return (sum / (maxTopic * 1.6)).coerceIn(-1.0, 1.0)
        }

        /** Affinité avec la chaîne de [video] : de -1 à 1. */
        fun channelMatch(video: VideoItem): Double {
            val key = video.channelKey() ?: return 0.0
            return ((channels[key] ?: 0.0) / maxChannel).coerceIn(-1.0, 1.0)
        }

        /** Les sujets les plus forts, du plus fort au plus faible. */
        fun topTopics(n: Int): List<String> = topics.entries.filter { it.value > 0 }.sortedByDescending { it.value }.take(n).map { it.key }
    }

    private const val HALF_LIFE_DAYS = 30.0
    private const val LIKE = 2.0
    private const val SAVED = 1.0
    private const val SUBSCRIBED = 0.8
    private const val HIDDEN = -1.5
    private const val TASTE = 1.5

    fun build(context: Context): Profile {
        val now = System.currentTimeMillis()
        val topics = HashMap<String, Double>()
        val topicChannels = HashMap<String, MutableSet<String>>()
        val channels = HashMap<String, Double>()
        var strength = 0.0

        fun learn(title: String, tags: List<String>, channel: String, channelKey: String?, weight: Double) {
            val own = words(channel)
            val found = (words(title) + tags.flatMap { words(it) }) - own
            found.forEach { word ->
                topics.merge(word, weight, Double::plus)
                if (channelKey != null) topicChannels.getOrPut(word) { HashSet() } += channelKey
            }
            if (channelKey != null) channels.merge(channelKey, weight, Double::plus)
        }

        // 1. Ce qu'on a regardé, selon la part regardée et l'ancienneté
        WatchHistory.get(context).entries.value.forEach { e ->
            val weight = watchWeight(e, now)
            strength += weight
            learn(e.title, e.tags, e.channel, channelKeyOf(e.channelId, e.channel), weight)
        }
        // 2. Ce qu'on a gardé
        val library = VideoLibrary.get(context)
        library.liked.value.take(150).forEach { learn(it.title, emptyList(), it.channel, it.channelKey(), LIKE).also { strength += LIKE } }
        library.later.value.take(150).forEach { learn(it.title, emptyList(), it.channel, it.channelKey(), SAVED) }
        Playlists.videos(context).items.value.flatMap { it.items }.take(300).forEach {
            learn(it.title, emptyList(), it.channel, it.channelKey(), SAVED)
        }
        // 3. Les abonnements : des chaînes aimées (leurs titres n'ont pas de sujets)
        SubscriptionStore.get(context).items.value.forEach { sub ->
            channelKeyOf(sub.channelId.ifEmpty { null }, sub.name)?.let { channels.merge(it, SUBSCRIBED, Double::plus) }
        }
        // 4. « Pas intéressé » : la chaîne et les sujets de la vidéo en négatif
        Feedback.get(context).hidden.value.forEach { h ->
            val fade = (1.0 - (now - h.at) / (60 * 86_400_000.0)).coerceAtLeast(0.0)
            if (fade <= 0) return@forEach
            h.channelKey?.let { channels.merge(it, HIDDEN * fade, Double::plus) }
            words(h.title).forEach { topics.merge(it, HIDDEN * 0.6 * fade, Double::plus) }
        }
        // 5. Les goûts choisis : leurs recherches comme sujets de départ, qui pèsent de moins en moins
        //    à mesure que l'historique grandit
        val tastes = Tastes.get(context).chosen.value
        val tasteWeight = TASTE / (1.0 + strength / 10.0)
        Tastes.ALL.filter { it.id in tastes }.forEach { taste ->
            app.mikeneko.settings.AppLocale.stringArray(taste.queries).flatMap { words(it) }.toSet().forEach {
                topics.merge(it, tasteWeight, Double::plus)
            }
        }

        // Un mot vu sur une seule chaîne est souvent un nom (d'émission, de personne) plus qu'un sujet
        val adjusted = topics.mapValues { (word, w) ->
            val spread = topicChannels[word]?.size ?: 2
            if (w > 0) w * sqrt(spread.coerceAtMost(4) / 2.0).coerceAtMost(1.2) else w
        }.filterValues { it > 0.05 || it < -0.05 }
        return Profile(adjusted, channels, tastes, strength)
    }

    /** Combien compte ce visionnage aujourd'hui : la part regardée, et l'ancienneté (demi-vie d'un mois). */
    fun watchWeight(e: WatchHistory.Entry, now: Long): Double {
        val days = ((now - e.at) / 86_400_000.0).coerceAtLeast(0.0)
        return engagement(e.watched) * exp(-days * ln(2.0) / HALF_LIFE_DAYS)
    }

    /**
     * Combien compte un visionnage selon la part regardée : ouverte et fermée (< 10 %) presque rien,
     * regardée en entier (≥ 80 %) le maximum. Inconnue (anciens historiques, shorts) : à moitié.
     */
    private fun engagement(watched: Float): Double = when {
        watched < 0f -> 0.6
        watched < 0.1f -> 0.1
        else -> 0.3 + 0.7 * (watched.coerceAtMost(0.8f) / 0.8f)
    }

    private fun channelKeyOf(channelId: String?, name: String): String? =
        channelId ?: name.trim().lowercase().ifEmpty { null }

    /** Les mots qui peuvent être des sujets : en minuscules, sans accents, sans mots vides. */
    fun words(text: String): Set<String> {
        if (text.isBlank()) return emptySet()
        val plain = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(ACCENTS, "")
        return plain.split(NOT_WORD).filter { w ->
            w.length >= 3 && w.length <= 24 && !w.all(Char::isDigit) && w !in STOP
        }.toSet()
    }

    private val ACCENTS = Regex("\\p{Mn}+")
    private val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")

    /** Mots vides (anglais, français) et mots de YouTube qui ne disent rien du sujet. */
    private val STOP = setOf(
        // anglais
        "the", "and", "for", "you", "your", "with", "this", "that", "from", "are", "was", "were", "how", "what",
        "why", "who", "when", "where", "which", "all", "can", "will", "not", "but", "have", "has", "had", "its",
        "into", "out", "about", "more", "most", "best", "top", "new", "one", "two", "three", "first", "last",
        "get", "got", "make", "made", "just", "like", "every", "ever", "never", "than", "then", "them", "they",
        "their", "our", "his", "her", "she", "him", "did", "does", "doing", "after", "before", "over", "under",
        "really", "very", "much", "many", "some", "any", "here", "there", "these", "those", "way", "day", "days",
        "time", "year", "years", "week", "today", "now", "full", "part", "episode", "ep", "official", "video",
        "videos", "clip", "trailer", "teaser", "live", "stream", "shorts", "short", "vlog", "feat", "ft",
        "vs", "versus", "reaction", "react", "reacts", "edition", "version", "remastered", "hd", "uhd",
        "4k", "8k", "1080p", "720p", "update", "watch", "lyrics", "audio", "music", "song", "songs", "mix", "remix",
        "channel", "subscribe", "special", "season", "vol", "volume", "chapter", "series", "compilation",
        // français
        "les", "des", "une", "pour", "avec", "dans", "sur", "est", "pas", "que", "qui", "quoi", "plus", "tout",
        "tous", "toute", "toutes", "comment", "pourquoi", "mon", "ton", "son", "mes", "tes", "ses", "nos",
        "vos", "leur", "leurs", "notre", "votre", "cette", "ces", "cet", "aux", "elle", "elles", "ils", "nous",
        "vous", "moi", "toi", "lui", "ca", "quand", "tres", "faire", "fait", "sans", "entre", "apres", "avant",
        "vraiment", "voici", "voila", "partie", "officiel", "officielle", "video", "videos", "extrait",
        "bande", "annonce", "direct", "integrale", "meilleur", "meilleurs", "meilleure", "nouveau", "nouvelle",
        "premier", "premiere", "jour", "jours", "fois", "annee", "ans", "semaine", "aujourd", "hui", "chez",
        "encore", "deja", "jamais", "rien", "bien", "trop", "peu", "moins", "comme", "mais", "donc", "car",
        "parce", "etre", "avoir", "suis", "sont", "ete", "veux", "peut", "faut", "abonne", "abonnez",
        "saison", "chapitre", "serie", "compilation", "reaction", "paroles", "musique", "chanson",
    )
}
