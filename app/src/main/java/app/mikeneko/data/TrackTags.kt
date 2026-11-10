package app.mikeneko.data

import android.content.Context
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Ce qu'on sait d'un morceau en plus de YouTube (2.15.0) : son tempo ([bpm]), sa tonalité sur la roue
 * Camelot ([camelot], « 8A » = la mineur…) et son [album]. Le tempo et la tonalité viennent de
 * l'analyse du son dans l'appli (player/TrackAnalysis.kt), l'album de la fiche YouTube du morceau.
 */
data class TrackTag(
    val bpm: Int? = null,
    val camelot: String? = null,
    val album: String? = null,
    /** L'analyse du son a été faite (même sans résultat : on ne la refait pas). */
    val analyzed: Boolean = false,
    /** La fiche YouTube a été lue pour l'album (même sans album trouvé). */
    val albumChecked: Boolean = false,
)

/** La clé d'un morceau : son identifiant YouTube, ou l'adresse d'un fichier téléchargé. */
fun trackKey(url: String): String = videoIdOf(url) ?: url

/** Le numéro (1–12) et la lettre (A mineur, B majeur) d'une case de la roue Camelot. */
fun camelotParts(code: String?): Pair<Int, Char>? {
    val c = code?.trim()?.uppercase() ?: return null
    val number = c.dropLast(1).toIntOrNull() ?: return null
    val letter = c.lastOrNull() ?: return null
    return if (number in 1..12 && (letter == 'A' || letter == 'B')) number to letter else null
}

object TrackTags {
    private val _tags = MutableStateFlow<Map<String, TrackTag>>(emptyMap())
    val tags: StateFlow<Map<String, TrackTag>> = _tags.asStateFlow()
    private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("tags_morceaux", Context.MODE_PRIVATE)

    fun load(context: Context): StateFlow<Map<String, TrackTag>> {
        synchronized(this) {
            if (!loaded) {
                loaded = true
                _tags.value = prefs(context).all.mapNotNull { (key, raw) ->
                    runCatching {
                        val o = JSONObject(raw as String)
                        key to TrackTag(
                            bpm = o.optInt("bpm", 0).takeIf { it > 0 },
                            camelot = o.optString("camelot").ifEmpty { null },
                            album = o.optString("album").ifEmpty { null },
                            analyzed = o.optBoolean("analyse"),
                            albumChecked = o.optBoolean("album_lu"),
                        )
                    }.getOrNull()
                }.toMap()
            }
        }
        return tags
    }

    fun get(context: Context, url: String): TrackTag? = load(context).value[trackKey(url)]

    private fun change(context: Context, url: String, block: (TrackTag) -> TrackTag) {
        load(context)
        val key = trackKey(url)
        val next = block(_tags.value[key] ?: TrackTag())
        synchronized(this) { _tags.value = _tags.value + (key to next) }
        val json = JSONObject()
            .put("bpm", next.bpm ?: 0)
            .put("camelot", next.camelot ?: "")
            .put("album", next.album ?: "")
            .put("analyse", next.analyzed)
            .put("album_lu", next.albumChecked)
        prefs(context).edit().putString(key, json.toString()).apply()
    }

    /** Le résultat de l'analyse du son (null : pas trouvé, mais c'est fait). */
    fun setAnalysis(context: Context, url: String, bpm: Int?, camelot: String?) =
        change(context, url) { it.copy(bpm = bpm ?: it.bpm, camelot = camelot ?: it.camelot, analyzed = true) }

    /** L'album lu sur la fiche YouTube (null : il n'y en a pas). */
    fun setAlbum(context: Context, url: String, album: String?) =
        change(context, url) { it.copy(album = album?.takeIf { a -> a.isNotBlank() } ?: it.album, albumChecked = true) }

    // ---- L'album des morceaux des playlists, lu doucement en fond ----

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val albumQueue = LinkedHashSet<String>()
    private var albumJob: Job? = null

    /** Les morceaux de [tracks] dont l'album n'a pas encore été cherché : leur fiche est lue, un à la fois. */
    fun fillAlbums(context: Context, tracks: List<VideoItem>) {
        val app = context.applicationContext
        val known = load(app).value
        val wanted = tracks.filter { !LocalMusic.isLocal(it.url) && known[trackKey(it.url)]?.albumChecked != true }
        if (wanted.isEmpty()) return
        synchronized(albumQueue) { wanted.forEach { albumQueue.add(it.url) } }
        if (albumJob?.isActive == true) return
        albumJob = scope.launch {
            while (true) {
                val url = synchronized(albumQueue) { albumQueue.firstOrNull()?.also { albumQueue.remove(it) } } ?: break
                if (get(app, url)?.albumChecked == true) continue
                val album = runCatching {
                    albumNameFrom(YouTubeRepository.stream(url).description?.content.orEmpty())
                }
                // Une erreur (réseau, YouTube) : on réessaiera la prochaine fois, sans le marquer
                album.onSuccess { setAlbum(app, url, it) }
                delay(if (album.isSuccess) 4_000 else 30_000) // doucement : YouTube n'aime pas les rafales
            }
        }
    }

    // ---- Tri ----

    /** Les tris automatiques d'une playlist ([Playlist.sort]). */
    enum class Sort(val id: String) {
        Title("titre"), Artist("artiste"), Album("album"), Bpm("bpm"), Camelot("camelot");

        companion object {
            fun of(id: String?): Sort? = entries.firstOrNull { it.id == id }
        }
    }

    /** [items] rangés selon [sort] (null : l'ordre de la playlist). Ce qui n'est pas encore connu va à la fin. */
    fun sorted(items: List<VideoItem>, sort: Sort?, tags: Map<String, TrackTag>): List<VideoItem> {
        if (sort == null) return items
        val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
        val byTitle = Comparator<VideoItem> { a, b -> collator.compare(a.title, b.title) }
        val byArtist = Comparator<VideoItem> { a, b -> collator.compare(cleanArtist(a.channel), cleanArtist(b.channel)) }
        fun tag(v: VideoItem) = tags[trackKey(v.url)]
        val comparator: Comparator<VideoItem> = when (sort) {
            Sort.Title -> byTitle
            Sort.Artist -> byArtist.then(byTitle)
            Sort.Album -> compareBy<VideoItem> { tag(it)?.album == null }
                .then { a, b -> collator.compare(tag(a)?.album.orEmpty(), tag(b)?.album.orEmpty()) }
                .then(byArtist)
            Sort.Bpm -> compareBy<VideoItem> { tag(it)?.bpm == null }.thenBy { tag(it)?.bpm ?: 0 }.then(byTitle)
            // La roue dans l'ordre : 1A, 1B, 2A, 2B… (deux cases voisines s'enchaînent bien), puis le tempo
            Sort.Camelot -> compareBy<VideoItem> { camelotParts(tag(it)?.camelot) == null }
                .thenBy { camelotParts(tag(it)?.camelot)?.first ?: 0 }
                .thenBy { camelotParts(tag(it)?.camelot)?.second ?: 'A' }
                .thenBy { tag(it)?.bpm ?: 0 }
        }
        return items.sortedWith(comparator) // (tri stable : à égalité, l'ordre de la playlist)
    }
}
