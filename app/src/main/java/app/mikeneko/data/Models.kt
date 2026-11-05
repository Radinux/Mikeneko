package app.mikeneko.data

import app.mikeneko.R
import app.mikeneko.settings.AppLocale
import java.time.Duration
import java.time.Instant
import java.util.Locale

data class VideoItem(
    val url: String,
    val title: String,
    val channel: String,
    val thumbnail: String?,
    val durationSec: Long = -1,
    val views: Long = -1,
    val isLive: Boolean = false,
    val uploaded: String? = null,
    /** Date de mise en ligne (ms) quand on la connaît : « il y a … » est alors recalculé à l'affichage. */
    val uploadedAt: Long? = null,
    /** Identifiant de la chaîne (UC…) quand on le connaît : sert aux préférences du « Pour toi ». */
    val channelId: String? = null,
    /** Pourquoi cette vidéo est proposée (« Pour toi »), affiché sous la carte. */
    val reason: String? = null,
    /** Un short (vidéo verticale courte). */
    val isShort: Boolean = false,
)

/** Une playlist (onglet Playlists d'une chaîne). [count] : nombre de vidéos, -1 si inconnu. */
data class PlaylistItem(
    val url: String,
    val name: String,
    val thumbnail: String?,
    val count: Long,
    val uploader: String,
)

/** Une chaîne trouvée par la recherche. */
data class ChannelItem(
    val url: String,
    val name: String,
    val avatar: String?,
    val channelId: String?,
    val subscribers: Long = -1,
    val videos: Long = -1,
    val verified: Boolean = false,
)

data class Subscription(
    val name: String,
    val url: String,
    val channelId: String,
    val avatar: String? = null,
)

data class FeedVideo(
    val video: VideoItem,
    val published: Instant,
    val channelId: String,
)

data class Segment(val startMs: Long, val endMs: Long, val category: String)

data class Chapter(val title: String, val startMs: Long)

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** « il y a 5 min » / « 5 min ago »… dans la langue de l'appli. */
fun formatAgo(instant: Instant): String {
    val minutes = Duration.between(instant, Instant.now()).toMinutes()
    val days = (minutes / (60 * 24)).toInt()
    return when {
        minutes < 60 -> minutes.coerceAtLeast(1).toInt().let { AppLocale.plural(R.plurals.ago_minutes, it, it) }
        minutes < 60 * 24 -> (minutes / 60).toInt().let { AppLocale.plural(R.plurals.ago_hours, it, it) }
        days < 30 -> AppLocale.plural(R.plurals.ago_days, days, days)
        days < 365 -> (days / 30).let { AppLocale.plural(R.plurals.ago_months, it, it) }
        else -> (days / 365).let { AppLocale.plural(R.plurals.ago_years, it, it) }
    }
}

/** « 1,5 k » en français, « 1.5K » en anglais. */
fun formatCount(n: Long): String = when {
    n < 0 -> ""
    n < 1_000 -> n.toString()
    // 999 950 s'arrondirait en « 1000 k » : on passe en millions avant
    n < 999_950 -> AppLocale.string(R.string.count_thousands, compact(n / 1_000.0))
    n < 999_950_000 -> AppLocale.string(R.string.count_millions, compact(n / 1_000_000.0))
    else -> AppLocale.string(R.string.count_billions, compact(n / 1_000_000_000.0))
}

/** Ce qui identifie une chaîne : son identifiant UC… si on l'a, sinon son nom. */
fun VideoItem.channelKey(): String? = channelId ?: channel.trim().lowercase().ifEmpty { null }

/** « il y a 3 j » toujours à jour et dans la bonne langue, sinon le texte reçu de YouTube. */
fun VideoItem.uploadedLabel(): String? = uploadedAt?.let { formatAgo(Instant.ofEpochMilli(it)) } ?: uploaded

/** « 1,5 k vues » / « 1.5K views », ou null si inconnu. */
fun formatViews(n: Long): String? = formatCount(n).ifEmpty { null }?.let { AppLocale.string(R.string.views_count, it) }

/** Une décimale au plus, avec le séparateur de la langue (virgule en français). */
private fun compact(value: Double): String {
    val text = String.format(Locale.ROOT, "%.1f", value).removeSuffix(".0")
    return if (AppLocale.language() == "fr") text.replace('.', ',') else text
}


private val CHANNEL_ID = Regex("(UC[\\w-]{22})")

fun channelIdFrom(text: String): String? = CHANNEL_ID.find(text)?.groupValues?.get(1)

private val VIDEO_LINK = Regex("https?://\\S*(youtube\\.com|youtu\\.be)\\S*")

fun youtubeLinkIn(text: String?): String? = text?.let { VIDEO_LINK.find(it)?.value }
