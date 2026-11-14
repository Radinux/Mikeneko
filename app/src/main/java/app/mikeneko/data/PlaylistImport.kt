package app.mikeneko.data

import android.content.Context
import java.text.Normalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Importer sa musique d'autres services (2.8.0) à partir d'un export **TuneMyMusic**
 * (d'une autre appli de musique vers un fichier) : CSV ou TXT.
 *
 * - CSV : colonnes reconnues par leur nom, séparées par `,` `;` ou une tabulation, champs entre
 *   guillemets. Vu sur un vrai export : `Track name, Artist name, Album, Playlist name, Type,
 *   ISRC`, puis l'identifiant du service, avec `Type` = Playlist, Favorite (titres likés), Artist (artistes suivis :
 *   le nom est dans « Track name ») ou Album (albums sauvegardés : le nom de l'album dans « Track name »).
 * - TXT : une ligne par morceau, « Artiste - Titre ».
 *
 * On choisit d'abord quoi importer ([preview], une case par playlist) : un gros export (un millier
 * de morceaux) prendrait longtemps, une recherche YouTube Music par morceau, à vitesse adaptative
 * (3 en parallèle au départ, plus lent dès que YouTube résiste) pour ne pas se faire bloquer. Si YouTube refuse plusieurs recherches d'affilée, l'import s'arrête
 * ([Result.refused]) ; ce qui est trouvé est gardé.
 *
 * Les doublons sont fusionnés : un même morceau répété dans une playlist (titres comparés sans
 * accents, majuscules, « feat. », « Remastered », « Live »…), un morceau déjà dans la playlist
 * Mikeneko du même nom, deux lignes qui tombent sur la même vidéo. Un morceau présent dans plusieurs
 * playlists n'est cherché qu'une fois. Les artistes et albums vont dans Ta bibliothèque (musique).
 */
object PlaylistImport {
    enum class Kind { Song, Artist, Album }

    data class Item(val kind: Kind, val title: String, val artist: String, val playlist: String)

    /** Une case à cocher : une playlist (ou les artistes, ou les albums) du fichier. */
    data class Group(val name: String, val kind: Kind, val count: Int)

    class Preview(val items: List<Item>) {
        val groups: List<Group> = items.groupBy { it.playlist to it.kind }.map { (k, v) -> Group(k.first, k.second, v.distinctBy { key(it) }.size) }
        /** Les recherches qu'il faudra pour ces groupes (un morceau en double n'est cherché qu'une fois). */
        fun searches(chosen: Set<Group>): Int = items.filter { it.group() in chosen }.distinctBy { it.kind to key(it) }.size
        internal fun Item.group() = groups.first { it.name == playlist && it.kind == kind }
    }

    sealed interface State {
        data object Idle : State
        data class Running(val done: Int, val total: Int, val found: Int) : State
        data class Done(val result: Result) : State
    }

    data class Result(
        val songsAdded: Int,
        val playlists: Int,
        val artists: Int,
        val albums: Int,
        val duplicates: Int,
        val notFound: List<String>,
        val stopped: Boolean,
        val refused: Boolean,
    )

    private const val MAX_PARALLEL = 3
    private const val MIN_PAUSE_MS = 250L
    private const val MAX_PAUSE_MS = 4_000L
    private const val SPEED_UP_AFTER = 20

    /** Le temps estimé pour [searches] recherches, à pleine vitesse (secondes). */
    fun estimateSeconds(searches: Int): Int = (searches * 0.6).toInt()
    private const val SEARCH_TIMEOUT_MS = 12_000L
    private const val MAX_FAILURES_IN_A_ROW = 5

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Un import en cours : l'appli ne doit pas se vider en arrière-plan. */
    val busy: Boolean get() = _state.value is State.Running

    fun preview(text: String, fileName: String): Preview = Preview(parse(text, playlistNameOf(fileName)))

    fun start(context: Context, preview: Preview, chosen: Set<Group>) {
        if (busy || chosen.isEmpty()) return
        val app = context.applicationContext
        val items = with(preview) { preview.items.filter { it.group() in chosen } }
        _state.value = State.Running(0, preview.searches(chosen), 0)
        job = scope.launch { run(app, items) }
    }

    fun stop() {
        job?.cancel()
    }

    /** Le résumé a été lu. */
    fun dismiss() {
        if (_state.value is State.Done) _state.value = State.Idle
    }

    private suspend fun run(context: Context, items: List<Item>) {
        var duplicates = 0
        val songs = items.filter { it.kind == Kind.Song }.groupBy { it.playlist }.mapValues { (_, list) ->
            list.distinctBy { key(it) }.also { duplicates += list.size - it.size } // répété dans la playlist
        }
        val artistItems = items.filter { it.kind == Kind.Artist }.distinctBy { key(it) }
        val albumItems = items.filter { it.kind == Kind.Album }.distinctBy { key(it) }
        val toFind = songs.values.flatten().distinctBy { key(it) } // une recherche par morceau
        val total = toFind.size + artistItems.size + albumItems.size

        val foundSongs = HashMap<String, VideoItem?>()
        val artists = ArrayList<ChannelItem>()
        val albums = ArrayList<MusicCollection>()
        val notFound = ArrayList<String>()
        var stopped = false

        // Vitesse adaptative (2.10.2) : jusqu'à [MAX_PARALLEL] recherches en même temps, presque sans
        // pause ; chaque erreur (ou délai dépassé) retire une recherche parallèle et double la pause,
        // [SPEED_UP_AFTER] réussites d'affilée réaccélèrent. [MAX_FAILURES_IN_A_ROW] échecs d'affilée :
        // YouTube refuse, on s'arrête en gardant ce qui est trouvé.
        val queue = java.util.concurrent.ConcurrentLinkedQueue<Pair<Item, suspend () -> Unit>>()
        val lock = Mutex()
        var done = 0
        var failures = 0
        var streak = 0
        var parallel = MAX_PARALLEL
        var pause = MIN_PAUSE_MS
        var refused = false

        suspend fun <T> search(item: Item, find: suspend () -> T?, keep: (T?) -> Unit) {
            val result = withTimeoutOrNull(SEARCH_TIMEOUT_MS) { runCatching { find() } }
            val failed = result == null || result.isFailure
            val found = result?.getOrNull()
            lock.withLock {
                if (failed) {
                    failures++
                    streak = 0
                    parallel = (parallel - 1).coerceAtLeast(1)
                    pause = (pause * 2).coerceAtMost(MAX_PAUSE_MS)
                    if (failures >= MAX_FAILURES_IN_A_ROW) refused = true
                } else {
                    failures = 0
                    if (++streak >= SPEED_UP_AFTER) {
                        streak = 0
                        parallel = (parallel + 1).coerceAtMost(MAX_PARALLEL)
                        pause = (pause / 2).coerceAtLeast(MIN_PAUSE_MS)
                    }
                }
                keep(found)
                if (found == null) notFound += label(item)
                done++
                _state.value = State.Running(done, total, done - notFound.size)
            }
        }
        toFind.forEach { song -> queue += song to suspend { search(song, { findSong(song) }) { foundSongs[key(song)] = it } } }
        artistItems.forEach { a -> queue += a to suspend { search(a, { findArtist(a) }) { it?.let { found -> artists += found } } } }
        albumItems.forEach { a -> queue += a to suspend { search(a, { findAlbum(a) }) { it?.let { found -> albums += found } } } }
        try {
            coroutineScope {
                repeat(MAX_PARALLEL) { worker ->
                    launch {
                        while (!refused) {
                            // Ralenti : les recherches parallèles en trop attendent (sans prendre de travail)
                            while (worker >= parallel && !refused && queue.isNotEmpty()) delay(300)
                            val (_, task) = queue.poll() ?: break
                            task()
                            delay(pause)
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            stopped = true
        }

        // On garde ce qui a été trouvé, même après un arrêt
        val playlists = Playlists.get(context)
        var added = 0
        var touched = 0
        songs.forEach { (name, list) ->
            val tracks = list.mapNotNull { foundSongs[key(it)] }
            val distinct = tracks.distinctBy { videoIdOf(it.url) ?: it.url }
            duplicates += tracks.size - distinct.size // deux lignes, une seule vidéo
            if (distinct.isEmpty()) return@forEach
            val before = playlists.items.value.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.items.orEmpty()
            val fresh = distinct.filter { v -> before.none { sameTrack(it.url, v.url) } }
            duplicates += distinct.size - fresh.size // déjà dans la playlist Mikeneko
            if (fresh.isEmpty()) return@forEach
            playlists.merge(name, fresh)
            touched++
            added += fresh.size
        }
        val library = MusicLibrary.get(context)
        val savedBefore = library.artists.value.size to library.albums.value.size
        library.addAll(albums.distinctBy { it.url }, artists.distinctBy { it.url })
        _state.value = State.Done(
            Result(
                songsAdded = added,
                playlists = touched,
                artists = library.artists.value.size - savedBefore.first,
                albums = library.albums.value.size - savedBefore.second,
                duplicates = duplicates,
                notFound = notFound,
                stopped = stopped,
                refused = refused,
            ),
        )
    }

    // ------------------------------------------------------------------ recherches

    /** Un morceau connu par son titre et son artiste (reconnaissance de musique, 2.25.0). */
    suspend fun findSong(title: String, artist: String): VideoItem? = findSong(Item(Kind.Song, title, artist, ""))

    /** Le morceau sur YouTube Music : le résultat dont le titre (et l'artiste) colle le mieux. */
    private suspend fun findSong(song: Item): VideoItem? {
        val title = cleanTitle(song.title)
        // Une version dérivée demandée (« Slowed + Reverb », « Live »…) reste dans la recherche, même si le
        // nettoyage du titre l'a enlevée avec sa parenthèse (« (Slowed + Reverb Version) ») : sinon YouTube ne
        // renvoie que l'original (3.1.1)
        val kept = words(title)
        val version = derived(song.title).filter { it !in kept }.joinToString(" ")
        val query = listOf(song.artist, title, version).filter { it.isNotBlank() }.joinToString(" ")
        return bestMatch(Music.songs(query), song, title)
            // Pas un morceau de YouTube Music (ASMR, podcast, live…) : parmi les vidéos
            ?: run {
                delay(300)
                bestMatch(YouTubeRepository.search(query).filterNot { it.isLive }, song, title)
            }
    }

    private fun bestMatch(results: List<VideoItem>, song: Item, title: String): VideoItem? =
        results.take(8).map { r -> r to matchScore(song.title, song.artist, r.title, r.channel) }
            .filter { (_, score) -> score >= MIN_MATCH }
            .maxByOrNull { it.second }?.first // à score égal, le premier (le plus pertinent pour YouTube)

    private const val MIN_MATCH = 0.6

    /**
     * À quel point un résultat (titre, chaîne) est le morceau voulu, de 0 à 1 (2.26.3 / 3.0.0). Avant,
     * un bon titre suffisait (l'artiste n'était qu'un bonus) et un titre plus long comptait comme
     * parfait : « même titre, autre chanson », remix, reprise… 10 à 15 % d'erreurs sur un vrai import.
     * - l'artiste doit correspondre quand on le connaît (un des artistes crédités suffit) ;
     * - le titre se compare dans les deux sens (les mots en trop comptent) ;
     * - une version dérivée (remix, live, reprise, ralentie…) est écartée si on ne l'a pas demandée.
     */
    internal fun matchScore(wantedTitle: String, wantedArtist: String, gotTitle: String, gotChannel: String): Double {
        val channel = norm(cleanArtist(gotChannel))
        val gotRaw = norm(gotTitle)
        if (wantedArtist.isNotBlank() && !artistMatches(wantedArtist, channel, gotRaw)) return 0.0
        val wantedRaw = norm(wantedTitle)
        val gotDerived = derived(gotTitle)
        val wantedDerived = derived(wantedTitle)
        if (gotDerived.any { it !in wantedDerived }) return 0.0
        // Et l'inverse (3.1.1) : une version dérivée demandée, l'original n'est pas elle (« Golden Brown »
        // pour « Golden Brown Slowed + Reverb »). Un de ses mots suffit (« (Slowed) » va)
        if (wantedDerived.isNotEmpty() && gotDerived.none { it in wantedDerived }) return 0.0
        // Le titre sans « (feat. …) », « - Remastered »… ni le nom de l'artiste (« Artiste - Titre » des vidéos)
        val artistWords = artistNames(wantedArtist).flatMap { words(it) }.toSet()
        val wanted = words(cleanTitle(wantedTitle)) - DRESSING
        val got = words(cleanTitle(gotTitle)) - (artistWords - wanted) - DRESSING
        if (wanted.isEmpty() || got.isEmpty()) return if (wantedRaw == gotRaw) 1.0 else 0.0
        val common = wanted.count { it in got }.toDouble()
        // Les deux sens : ce qui manque du titre voulu, et ce que le résultat a en trop
        return minOf(common / wanted.size, common / got.size)
    }

    /** Un des artistes crédités se retrouve dans la chaîne (« Artiste - Topic »…) ou dans le titre. */
    private fun artistMatches(wantedArtist: String, channel: String, gotTitle: String): Boolean =
        artistNames(wantedArtist).any { name ->
            val n = norm(name)
            n.isNotEmpty() && (
                containsWords(channel, n) || containsWords(gotTitle, n) ||
                    // Chaîne plus courte que le nom (« Daft Punk » pour « Daft Punk & Pharrell ») : assez longue
                    (channel.length >= 4 && containsWords(n, channel))
                )
        }

    /** « A, B & C feat. D » → A, B, C, D. */
    private fun artistNames(artist: String): List<String> =
        artist.split(ARTIST_SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }

    /** [part] est dans [whole], mot pour mot (pas « art » dans « party »). */
    private fun containsWords(whole: String, part: String): Boolean =
        " $whole ".contains(" $part ")

    private val ARTIST_SEPARATORS = Regex("""\s*(,|&|/|;|\+|\bx\b|\band\b|\bet\b|\bfeat\.?|\bft\.?|\bwith\b)\s*""", RegexOption.IGNORE_CASE)

    /** Les mots des versions dérivées : écartées si le titre voulu ne les a pas. */
    private val DERIVED = setOf(
        "remix", "rmx", "cover", "covers", "karaoke", "instrumental", "live", "acoustic", "acoustique",
        "slowed", "reverb", "sped", "nightcore", "8d", "bootleg", "mashup", "extended", "piano", "lofi",
        "tutorial", "reaction",
    )

    private fun derived(title: String): Set<String> = words(title).filterTo(HashSet()) { it in DERIVED }

    /** Les mots d'habillage des vidéos (« Official Video », « Lyrics »…) : ni manquants ni en trop. */
    private val DRESSING = setOf(
        "official", "officiel", "officielle", "video", "videoclip", "clip", "audio", "lyric", "lyrics", "paroles",
        "visualizer", "visualiser", "music", "musique", "hd", "hq", "4k", "mv", "topic", "vevo", "version", "original",
    )

    private suspend fun findArtist(item: Item): ChannelItem? {
        val wanted = norm(item.title)
        return Music.artists(item.title).take(5).firstOrNull { norm(it.name) == wanted }
            ?: Music.artists(item.title).firstOrNull()?.takeIf { wanted in norm(it.name) || norm(it.name) in wanted }
    }

    private suspend fun findAlbum(item: Item): MusicCollection? {
        val wanted = words(cleanTitle(item.title))
        val artistWords = words(item.artist)
        return Music.collections("${item.artist} ${item.title}".trim(), MusicKind.Albums).take(6).map { c ->
            val got = words(cleanTitle(c.title))
            val overlap = if (wanted.isEmpty()) 0.0 else wanted.count { it in got }.toDouble() / wanted.size
            val artistHit = artistWords.isEmpty() || artistWords.any { it in words(c.artist) }
            c to overlap + if (artistHit) 0.35 else 0.0
        }.filter { (_, score) -> score >= 0.8 }.maxByOrNull { it.second }?.first
    }

    // ------------------------------------------------------------------ lecture du fichier

    fun parse(raw: String, defaultPlaylist: String): List<Item> {
        val text = raw.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val first = lines.first()
        val delimiter = listOf(',', ';', '\t').maxByOrNull { d -> first.count { it == d } }!!
        val header = fields(first, delimiter).map { norm(it) }
        val titleCol = header.indexOfFirst { it in TITLE_NAMES }
        if (titleCol >= 0) {
            val artistCol = header.indexOfFirst { it in ARTIST_NAMES }
            val playlistCol = header.indexOfFirst { it in PLAYLIST_NAMES }
            val typeCol = header.indexOf("type")
            return lines.drop(1).mapNotNull { line ->
                val f = fields(line, delimiter)
                val title = f.getOrNull(titleCol)?.trim().orEmpty()
                if (title.isEmpty()) return@mapNotNull null
                val kind = when (f.getOrNull(typeCol)?.trim()?.lowercase()) {
                    "artist" -> Kind.Artist
                    "album" -> Kind.Album
                    else -> Kind.Song
                }
                Item(
                    kind = kind,
                    title = title,
                    artist = f.getOrNull(artistCol)?.trim().orEmpty(),
                    playlist = f.getOrNull(playlistCol)?.trim()?.ifEmpty { null } ?: defaultPlaylist,
                )
            }
        }
        // TXT : « Artiste - Titre » (ou seulement le titre)
        return lines.mapNotNull { line ->
            val clean = line.trim()
            if (clean.isEmpty() || clean.startsWith("#")) return@mapNotNull null
            val dash = clean.indexOf(" - ")
            if (dash > 0) Item(Kind.Song, clean.substring(dash + 3).trim(), clean.substring(0, dash).trim(), defaultPlaylist)
            else Item(Kind.Song, clean, "", defaultPlaylist)
        }
    }

    /** Les champs d'une ligne CSV, guillemets compris (`"a, b"` et `""` pour un guillemet). */
    private fun fields(line: String, delimiter: Char): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && line.getOrNull(i + 1) == '"' -> { current.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == delimiter && !quoted -> { out += current.toString(); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        out += current.toString()
        return out
    }

    private fun playlistNameOf(fileName: String): String =
        fileName.substringBeforeLast('.').replace('_', ' ').trim().ifEmpty { "TuneMyMusic" }

    private val TITLE_NAMES = setOf("track name", "track", "title", "song", "song name", "name", "titre", "nom du titre", "morceau")
    private val ARTIST_NAMES = setOf("artist name", "artist", "artists", "artist name s", "artiste", "artistes", "nom de l'artiste")
    private val PLAYLIST_NAMES = setOf("playlist name", "playlist", "nom de la playlist")

    // ------------------------------------------------------------------ doublons

    /** La clé d'un élément : artiste + titre, sans ce qui varie d'un service à l'autre. */
    private fun key(item: Item): String = norm(item.artist) + "|" + norm(cleanTitle(item.title))

    private fun label(item: Item) = if (item.artist.isBlank()) item.title else "${item.artist} - ${item.title}"

    /** « Titre (feat. X) - Remastered 2011 [Live] » → « Titre ». */
    private fun cleanTitle(title: String): String = title
        .replace(BRACKETS_NOISE, "")
        .replace(DASH_NOISE, "")
        .replace(FEAT, "")
        .trim()
        .ifEmpty { title }

    private val BRACKETS_NOISE = Regex("""\s*[(\[][^)\]]*(feat|ft\.|with|remaster|live|version|edit|mono|stereo|deluxe|bonus|explicit|radio)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)
    private val DASH_NOISE = Regex("""\s+-\s+.*(remaster|live|version|edit|mono|stereo|deluxe|bonus|radio).*$""", RegexOption.IGNORE_CASE)
    private val FEAT = Regex("""\s+(feat\.?|ft\.)\s.*$""", RegexOption.IGNORE_CASE)
    private val MARKS = Regex("\\p{Mn}+")
    private val NOT_WORD = Regex("[^\\p{L}\\p{N}' ]+")
    private val SPACES = Regex("\\s+")

    private fun norm(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(MARKS, "")
            .replace('’', '\'').replace(NOT_WORD, " ").replace(SPACES, " ").trim()

    private fun words(text: String): Set<String> = norm(text).split(' ').filter { it.isNotEmpty() }.toSet()
}
