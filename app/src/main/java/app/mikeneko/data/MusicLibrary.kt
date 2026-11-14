package app.mikeneko.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * La bibliothèque musique : les albums (et playlists YouTube Music) et les artistes sauvegardés,
 * le plus récent en premier. Gardée sur le téléphone, comme les playlists perso.
 */
class MusicLibrary private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("bibliotheque_musique", Context.MODE_PRIVATE)
    private val _albums = MutableStateFlow(loadAlbums())
    private val _artists = MutableStateFlow(loadArtists())
    val albums: StateFlow<List<MusicCollection>> = _albums.asStateFlow()
    val artists: StateFlow<List<ChannelItem>> = _artists.asStateFlow()

    fun hasAlbum(url: String) = _albums.value.any { it.url == url }
    fun hasArtist(artist: ChannelItem) = _artists.value.any { sameArtist(it, artist) }

    /** Sauvegarde (en premier) ou retire. Renvoie true si l'album est maintenant dans la bibliothèque. */
    fun toggleAlbum(album: MusicCollection): Boolean {
        val added = !hasAlbum(album.url)
        _albums.value = if (added) listOf(album) + _albums.value else _albums.value.filterNot { it.url == album.url }
        save()
        if (added) Deletions.back(albumKey(album)) else Deletions.gone(albumKey(album))
        return added
    }

    fun toggleArtist(artist: ChannelItem): Boolean {
        val added = !hasArtist(artist)
        _artists.value = if (added) listOf(artist) + _artists.value else _artists.value.filterNot { sameArtist(it, artist) }
        save()
        if (added) Deletions.back(artistKey(artist)) else Deletions.gone(artistKey(artist))
        return added
    }

    /** Retire d'un coup les albums et artistes de clés [keys] ([albumKey], [artistKey] ; sélection, 3.1.1). */
    fun remove(keys: Set<String>) {
        val albums = _albums.value.filterNot { albumKey(it) in keys }
        val artists = _artists.value.filterNot { artistKey(it) in keys }
        val gone = (_albums.value - albums.toSet()).map(::albumKey) + (_artists.value - artists.toSet()).map(::artistKey)
        if (gone.isEmpty()) return
        _albums.value = albums
        _artists.value = artists
        save()
        Deletions.gone(gone)
    }

    /** Import : ajoute à la fin ce qui manque. Renvoie le nombre ajouté. */
    fun addAll(albums: List<MusicCollection>, artists: List<ChannelItem>): Int {
        val newAlbums = albums.filter { a -> !hasAlbum(a.url) }.distinctBy { it.url }
        val newArtists = artists.filter { a -> !hasArtist(a) }.distinctBy { it.url + it.name }
        _albums.value = _albums.value + newAlbums
        _artists.value = _artists.value + newArtists
        save()
        Deletions.back(newAlbums.map(::albumKey) + newArtists.map(::artistKey))
        return newAlbums.size + newArtists.size
    }

    // ---- les suppressions, pour la synchro (2.23.0, voir Deletions)
    fun albumKey(album: MusicCollection) = "al:" + album.url
    fun artistKey(artist: ChannelItem) = "ar:" + artist.url.ifBlank { artist.name.lowercase() }

    /** La synchro : ce qui a été retiré sur un autre appareil est retiré ici aussi. */
    fun dropGone() {
        val albums = _albums.value.filterNot { Deletions.isGone(albumKey(it)) }
        val artists = _artists.value.filterNot { Deletions.isGone(artistKey(it)) }
        if (albums.size == _albums.value.size && artists.size == _artists.value.size) return
        _albums.value = albums
        _artists.value = artists
        save()
    }

    /** Même chaîne, ou même nom quand l'adresse manque (artiste venu d'un morceau). */
    private fun sameArtist(a: ChannelItem, b: ChannelItem) =
        (a.url.isNotBlank() && a.url == b.url) || a.name.equals(b.name, ignoreCase = true)

    private fun loadAlbums(): List<MusicCollection> = runCatching {
        val array = JSONArray(prefs.getString("albums", "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            MusicCollection(
                url = o.getString("url"),
                title = o.optString("title"),
                artist = o.optString("artist"),
                thumbnail = o.optString("thumbnail").ifEmpty { null },
                count = o.optLong("count", -1),
            )
        }
    }.getOrDefault(emptyList())

    private fun loadArtists(): List<ChannelItem> = runCatching {
        val array = JSONArray(prefs.getString("artistes", "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            ChannelItem(
                url = o.optString("url"),
                name = o.optString("name"),
                avatar = o.optString("avatar").ifEmpty { null },
                channelId = o.optString("channelId").ifEmpty { null },
                subscribers = o.optLong("subscribers", -1),
            )
        }
    }.getOrDefault(emptyList())

    private fun save() {
        val albums = JSONArray()
        _albums.value.forEach {
            albums.put(
                JSONObject().put("url", it.url).put("title", it.title).put("artist", it.artist)
                    .put("thumbnail", it.thumbnail ?: "").put("count", it.count),
            )
        }
        val artists = JSONArray()
        _artists.value.forEach {
            artists.put(
                JSONObject().put("url", it.url).put("name", it.name).put("avatar", it.avatar ?: "")
                    .put("channelId", it.channelId ?: "").put("subscribers", it.subscribers),
            )
        }
        prefs.edit().putString("albums", albums.toString()).putString("artistes", artists.toString()).apply()
    }

    companion object {
        @Volatile private var instance: MusicLibrary? = null
        fun get(context: Context): MusicLibrary =
            instance ?: synchronized(this) { instance ?: MusicLibrary(context.applicationContext).also { instance = it } }
    }
}
