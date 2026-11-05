package app.mikeneko.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * La Bibliothèque sans compte : « À regarder plus tard », « Vidéos J'aime » et « Tes shorts » (2.3.0,
 * à part des vidéos), gardées sur le téléphone (la plus récente en premier). Les playlists de vidéos
 * sont dans [Playlists.videos].
 */
class VideoLibrary private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("bibliotheque", Context.MODE_PRIVATE)

    private val _later = MutableStateFlow(load("plus_tard"))
    val later: StateFlow<List<VideoItem>> = _later.asStateFlow()

    private val _liked = MutableStateFlow(load("jaime"))
    val liked: StateFlow<List<VideoItem>> = _liked.asStateFlow()

    /** Les shorts gardés (bouton « Garder » du lecteur Shorts). */
    private val _shorts = MutableStateFlow(load("shorts").map { it.copy(isShort = true) })
    val shorts: StateFlow<List<VideoItem>> = _shorts.asStateFlow()

    /** Ajoute (en premier) ou retire. Renvoie true si la vidéo est maintenant dans la liste. */
    fun toggleLater(video: VideoItem): Boolean = toggle(_later, "plus_tard", video)
    fun toggleLiked(video: VideoItem): Boolean = toggle(_liked, "jaime", video)
    fun toggleShort(video: VideoItem): Boolean = toggle(_shorts, "shorts", video.copy(isShort = true))

    /** Import : ajoute à la fin ce qui manque. Renvoie le nombre ajouté. */
    fun addLater(list: List<VideoItem>) = addMissing(_later, "plus_tard", list)
    fun addLiked(list: List<VideoItem>) = addMissing(_liked, "jaime", list)
    fun addShorts(list: List<VideoItem>) = addMissing(_shorts, "shorts", list.map { it.copy(isShort = true) })

    private fun addMissing(flow: MutableStateFlow<List<VideoItem>>, key: String, list: List<VideoItem>): Int {
        val fresh = list.filter { v -> flow.value.none { sameTrack(it.url, v.url) } }.distinctBy { it.url }
        if (fresh.isNotEmpty()) {
            flow.value = flow.value + fresh
            save(key, flow.value)
        }
        return fresh.size
    }

    fun removeLater(url: String) = remove(_later, "plus_tard", url)
    fun removeLiked(url: String) = remove(_liked, "jaime", url)
    fun removeShort(url: String) = remove(_shorts, "shorts", url)

    private fun toggle(flow: MutableStateFlow<List<VideoItem>>, key: String, video: VideoItem): Boolean {
        val there = flow.value.any { sameTrack(it.url, video.url) }
        flow.value = if (there) flow.value.filterNot { sameTrack(it.url, video.url) } else listOf(video) + flow.value
        save(key, flow.value)
        return !there
    }

    private fun remove(flow: MutableStateFlow<List<VideoItem>>, key: String, url: String) {
        flow.value = flow.value.filterNot { sameTrack(it.url, url) }
        save(key, flow.value)
    }

    private fun load(key: String): List<VideoItem> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]"))
        (0 until array.length()).map { trackFromJson(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun save(key: String, list: List<VideoItem>) {
        val array = JSONArray()
        list.forEach { array.put(it.toTrackJson()) }
        prefs.edit().putString(key, array.toString()).apply()
    }

    companion object {
        @Volatile private var instance: VideoLibrary? = null
        fun get(context: Context): VideoLibrary =
            instance ?: synchronized(this) { instance ?: VideoLibrary(context.applicationContext).also { instance = it } }
    }
}

/** La miniature d'une vidéo d'après son identifiant (pour l'historique, qui ne la garde pas). */
fun thumbnailFor(url: String): String? = videoIdOf(url)?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
