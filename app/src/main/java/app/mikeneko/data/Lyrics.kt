package app.mikeneko.data

import android.content.Context
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** Une ligne de paroles synchronisées : le moment où elle commence. */
data class LyricLine(val timeMs: Long, val text: String)

/** Les paroles d'un morceau : synchronisées si possible, sinon le texte brut. */
data class Lyrics(val synced: List<LyricLine>, val plain: String?, val instrumental: Boolean) {
    val isEmpty get() = synced.isEmpty() && plain.isNullOrBlank() && !instrumental
}

/** L'artiste et le titre devinés à partir d'un titre de vidéo YouTube et de sa chaîne. */
data class SongGuess(val artist: String, val track: String)

/**
 * Paroles via LRCLIB (https://lrclib.net) : base libre et gratuite, sans clé ni compte.
 * Une seule requête de recherche par morceau, gardée en mémoire.
 */
object LyricsRepository {
    private const val USER_AGENT = "Mikeneko (client YouTube libre pour Android)"
    private val cache = TimedCache<Lyrics>(60 * 60_000L)
    /** Les morceaux sans paroles trouvées : pas de nouvelle requête à chaque ouverture. */
    private val missing = HashSet<String>()

    fun cached(key: String): Lyrics? = cache.peek(key)

    /** null : rien trouvé. */
    suspend fun find(guess: SongGuess, durationSec: Long): Lyrics? {
        val key = "${guess.artist}|${guess.track}".lowercase()
        cache.peek(key)?.let { return it }
        if (synchronized(missing) { key in missing }) return null
        val found = withContext(Dispatchers.IO) {
            search(guess.artist, guess.track, durationSec)
                // Sans « feat. … » : LRCLIB ne range pas toujours les invités dans le titre
                ?: stripFeat(guess.track).takeIf { it != guess.track }?.let { search(guess.artist, it, durationSec) }
                // En dernier recours, une recherche libre « artiste titre »
                ?: searchFree("${guess.artist} ${guess.track}", durationSec)
        }
        if (found == null) synchronized(missing) { missing += key } else cache.get(key) { found }
        return found
    }

    private fun search(artist: String, track: String, durationSec: Long): Lyrics? {
        if (track.isBlank()) return null
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", track)
            .apply { if (artist.isNotBlank()) addQueryParameter("artist_name", artist) }
            .build()
        return best(request(url.toString()), durationSec)
    }

    private fun searchFree(query: String, durationSec: Long): Lyrics? {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder().addQueryParameter("q", query.trim()).build()
        return best(request(url.toString()), durationSec)
    }

    private fun request(url: String): JSONArray? = runCatching {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        NewPipeDownloader.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else JSONArray(response.body?.string().orEmpty())
        }
    }.getOrNull()

    /**
     * Le résultat le plus proche en durée (à 8 s près quand on la connaît), synchronisé de préférence.
     * 2.24.0 : vraiment le plus proche. Avant, c'était le premier synchronisé dans l'ordre de LRCLIB,
     * qui pouvait être une autre version (intro plus longue : tout arrivait en retard).
     */
    private fun best(results: JSONArray?, durationSec: Long): Lyrics? {
        if (results == null || results.length() == 0) return null
        fun gap(o: JSONObject): Double {
            val d = o.optDouble("duration", 0.0)
            return if (durationSec <= 0 || d.isNaN() || d <= 0) 0.0 else abs(d - durationSec)
        }
        val candidates = (0 until results.length()).map { results.getJSONObject(it) }
            .filter { gap(it) <= 8 }
            .sortedBy(::gap)
        val pick = candidates.firstOrNull { !it.isNull("syncedLyrics") && it.optString("syncedLyrics").isNotBlank() }
            ?: candidates.firstOrNull { !it.isNull("plainLyrics") && it.optString("plainLyrics").isNotBlank() }
            ?: candidates.firstOrNull { it.optBoolean("instrumental") }
            ?: return null
        return pick.toLyrics()
    }

    private fun JSONObject.toLyrics() = Lyrics(
        synced = if (isNull("syncedLyrics")) emptyList() else parseLrc(optString("syncedLyrics")),
        plain = if (isNull("plainLyrics")) null else optString("plainLyrics").ifBlank { null },
        instrumental = optBoolean("instrumental"),
    )
}

/**
 * Le recalage des paroles synchronisées, par morceau (2.24.0), en ms ajoutées aux temps des lignes :
 * sur LRCLIB, une même version mal calée est parfois recopiée sur toutes les autres (vu : 13 s de
 * retard). Gardé sur ce téléphone seulement.
 */
object LyricsOffsets {
    private const val PREFS = "paroles_decalage"
    const val LIMIT = 120_000L

    fun get(context: Context, track: String): Long = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(track, 0L)

    fun set(context: Context, track: String, ms: Long) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (ms == 0L) edit.remove(track) else edit.putLong(track, ms.coerceIn(-LIMIT, LIMIT))
        edit.apply()
    }
}

private val LRC_TIME = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")

/** « [01:23.45] texte » → lignes triées par temps (une ligne peut avoir plusieurs temps). */
fun parseLrc(text: String): List<LyricLine> = text.lines().flatMap { line ->
    val times = LRC_TIME.findAll(line).toList()
    if (times.isEmpty()) return@flatMap emptyList()
    val words = line.substring(times.last().range.last + 1).trim()
    times.map { m ->
        val (min, sec, frac) = m.destructured
        val ms = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 100
            2 -> frac.toLong() * 10
            else -> frac.take(3).toLong()
        }
        LyricLine(min.toLong() * 60_000 + sec.toLong() * 1000 + ms, words)
    }
}.sortedBy { it.timeMs }

/** Ce qu'on retire des titres de vidéos : « (Official Video) », « [HD] », « (Paroles) »… */
private val NOISE = Regex(
    "\\s*[(\\[][^)\\]]*\\b(official|officiel|video|vidéo|clip|audio|lyrics?|paroles|visuali[sz]er|hd|4k|hq|remaster(ed)?|mv)\\b[^)\\]]*[)\\]]",
    RegexOption.IGNORE_CASE,
)
private val FEAT = Regex("\\s*[(\\[]?\\b(feat\\.?|ft\\.?|featuring)\\s[^)\\]]*[)\\]]?", RegexOption.IGNORE_CASE)
private val DASH = Regex("\\s+[-–—]\\s+")

private fun stripFeat(text: String) = FEAT.replace(text, "").trim()

/**
 * Devine l'artiste et le titre. « Artiste - Topic » (YouTube Music) : la chaîne est l'artiste et le
 * titre est propre. Sinon, « Artiste - Titre (Official Video) » est découpé au tiret.
 */
fun guessSong(title: String, channel: String): SongGuess {
    val cleanTitle = NOISE.replace(title, "").replace("|", " ").trim()
    val artistFromChannel = cleanArtist(channel).removeSuffix("VEVO").removeSuffix(" Official").trim()
    if (channel.endsWith("Topic")) return SongGuess(artistFromChannel, cleanTitle)
    val parts = DASH.split(cleanTitle, limit = 2)
    return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
        SongGuess(stripFeat(parts[0]), parts[1].trim().trim('"', '«', '»', ' '))
    } else {
        SongGuess(artistFromChannel, cleanTitle.trim('"', '«', '»', ' '))
    }
}
