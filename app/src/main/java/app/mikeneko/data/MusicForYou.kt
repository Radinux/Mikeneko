package app.mikeneko.data

import android.content.Context
import app.mikeneko.R
import app.mikeneko.settings.AppLocale
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Une playlist fabriquée par l'appli d'après ton écoute (« Mix Daft Punk », « Redécouvertes »…). */
data class GeneratedMix(val id: String, val title: String, val subtitle: String, val tracks: List<VideoItem>)

/** Ce que la Musique te propose : des morceaux à découvrir et des mix thématiques. */
class MusicSuggestions(val tracks: List<VideoItem>, val mixes: List<GeneratedMix>)

/**
 * La Musique « Pour toi » (2.6.0), tirée de ce que tu écoutes :
 * - tes artistes les plus présents dans « Récemment écoutés » (3 au plus) donnent chacun un
 *   « Mix artiste » : le mix YouTube Music d'un de leurs morceaux (une seule demande par artiste) ;
 * - « Pour toi » mêle ces mix, sans ce que tu as déjà écouté ou mis en playlist ;
 * - « Redécouvertes » : des morceaux de tes playlists que tu n'as pas écoutés récemment (sans réseau).
 * Gardé 6 h en mémoire, et recalculé quand tes artistes changent.
 */
object MusicForYou {
    private const val MAX_ARTISTS = 3
    private const val MAX_TRACKS = 30
    private val cache = TimedCache<MusicSuggestions>(6 * 60 * 60 * 1000L)

    /** Les artistes qui reviennent le plus dans l'écoute récente, avec leur dernier morceau (la graine). */
    private fun seeds(recent: List<VideoItem>): List<Pair<String, VideoItem>> =
        recent.filter { !it.isLive && it.channel.isNotBlank() && videoIdOf(it.url) != null }
            .groupBy { cleanArtist(it.channel) }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, List<VideoItem>>> { it.value.size }.thenBy { recent.indexOf(it.value.first()) })
            .take(MAX_ARTISTS)
            .map { it.key to it.value.first() }

    private fun key(recent: List<VideoItem>) = seeds(recent).joinToString("|") { it.first }

    /** Ce qui est déjà prêt (sans rien demander à YouTube). */
    fun cached(context: Context): MusicSuggestions? = cache.peek(key(MusicHistory.get(context).items.value))

    suspend fun load(context: Context, fresh: Boolean = false): MusicSuggestions {
        val recent = MusicHistory.get(context).items.value
        return cache.get(key(recent), fresh) { build(context, recent) }
    }

    private suspend fun build(context: Context, recent: List<VideoItem>): MusicSuggestions = coroutineScope {
        val seeds = seeds(recent)
        val mixes = seeds.map { (artist, seed) ->
            async {
                val tracks = runCatching { Music.mix(videoIdOf(seed.url)!!).tracks }.getOrDefault(emptyList())
                    .filter { !it.isLive }
                    .distinctBy { videoIdOf(it.url) ?: it.url }
                artist to tracks
            }
        }.awaitAll().filter { it.second.size >= 4 }

        // Ce que tu connais déjà : écouté récemment, ou dans tes playlists
        val playlists = Playlists.get(context).items.value
        val known = (recent + playlists.flatMap { it.items }).mapNotNull { videoIdOf(it.url) }.toSet()

        // « Pour toi » : les mix mêlés un à un (pour varier les artistes), sans le connu
        val suggestions = mutableListOf<VideoItem>()
        val seen = known.toMutableSet()
        val lists = mixes.map { it.second }
        var i = 0
        while (suggestions.size < MAX_TRACKS && lists.any { i < it.size }) {
            lists.forEach { list ->
                val track = list.getOrNull(i) ?: return@forEach
                val id = videoIdOf(track.url) ?: return@forEach
                if (seen.add(id) && suggestions.size < MAX_TRACKS) suggestions += track
            }
            i++
        }

        val generated = buildList {
            mixes.forEach { (artist, tracks) ->
                add(GeneratedMix("mix:$artist", AppLocale.string(R.string.mix_of, artist), AppLocale.string(R.string.mix_of_text, artist), tracks.take(MAX_TRACKS)))
            }
            // Redécouvertes : tes playlists, sans l'écoute récente, mélangées (stable sur la journée)
            val recentIds = recent.mapNotNull { videoIdOf(it.url) }.toSet()
            val forgotten = playlists.flatMap { it.items }
                .filter { !it.isLive && videoIdOf(it.url)?.let { id -> id !in recentIds } == true }
                .distinctBy { videoIdOf(it.url) }
                .shuffled(kotlin.random.Random(System.currentTimeMillis() / 86_400_000L))
                .take(MAX_TRACKS)
            if (forgotten.size >= 4) {
                add(GeneratedMix("redecouvertes", AppLocale.string(R.string.mix_rediscover), AppLocale.string(R.string.mix_rediscover_text), forgotten))
            }
        }
        MusicSuggestions(suggestions, generated)
    }
}
