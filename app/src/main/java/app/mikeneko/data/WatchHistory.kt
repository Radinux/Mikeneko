package app.mikeneko.data

import android.content.Context
import app.mikeneko.settings.AppSettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * L'historique local qui nourrit le « Pour toi » : les vidéos ouvertes, leur chaîne, et les
 * vidéos que YouTube suggère à côté (déjà reçues avec la vidéo, donc gratuites).
 * Reste sur le téléphone (fichier privé de l'appli), jamais envoyé nulle part.
 */
class WatchHistory private constructor(context: Context) {
    data class Entry(
        val url: String,
        val title: String,
        val channel: String,
        val channelId: String?,
        val at: Long,
        val related: List<VideoItem>,
        /** Part de la vidéo regardée (0…1), la plus grande atteinte ; -1 si on ne sait pas (2.7.0). */
        val watched: Float = -1f,
        /** Les tags de la vidéo (quelques-uns) : des sujets pour le profil du « Pour toi » (2.7.0). */
        val tags: List<String> = emptyList(),
    )

    private val file = File(context.filesDir, "historique.json")
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    /** Plus récente en premier. */
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    /** Le fichier est lu (avant, [entries] est vide même s'il y a un historique). */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        io.launch {
            _entries.value = load()
            _loaded.value = true
        }
    }

    /** Une vidéo vient d'être ouverte (pas la radio). */
    fun add(info: StreamInfo) {
        if (AppSettings.historyPaused.value) return
        val related = info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .map { it.toVideo() }
            .filter { !it.isLive && it.url != info.url }
            .take(MAX_RELATED)
        val entry = Entry(
            url = info.url,
            title = info.name,
            channel = info.uploaderName.orEmpty(),
            channelId = info.uploaderUrl?.let(::channelIdFrom),
            at = System.currentTimeMillis(),
            related = related,
            watched = _entries.value.firstOrNull { it.url == info.url }?.watched ?: -1f,
            tags = info.tags.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_TAGS),
        )
        // Seules les plus récentes gardent leurs suggestions (le fichier resterait sinon très gros)
        _entries.value = (listOf(entry) + _entries.value.filterNot { it.url == entry.url }).take(MAX_ENTRIES)
            .mapIndexed { i, e -> if (i >= MAX_WITH_RELATED && e.related.isNotEmpty()) e.copy(related = emptyList()) else e }
        save()
    }

    /**
     * La lecture de [videoId] en est à [positionMs] sur [durationMs] : garde la plus grande part
     * regardée (appelé avec la position de reprise : pause, fin, changement de vidéo, toutes les 10 s).
     */
    fun recordProgress(videoId: String, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0 || AppSettings.historyPaused.value) return
        val fraction = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        val list = _entries.value
        val i = list.indexOfFirst { videoIdOf(it.url) == videoId }
        if (i < 0 || fraction <= list[i].watched + 0.02f) return
        _entries.value = list.toMutableList().also { it[i] = list[i].copy(watched = fraction) }
        save()
    }

    fun clear() {
        _entries.value = emptyList()
        io.launch { file.delete() }
    }

    private fun save() {
        val snapshot = _entries.value
        io.launch {
            val array = JSONArray()
            snapshot.forEach { e ->
                array.put(
                    JSONObject()
                        .put("url", e.url).put("title", e.title).put("channel", e.channel)
                        .put("channelId", e.channelId ?: "").put("at", e.at)
                        .put("watched", e.watched.toDouble())
                        .put("tags", JSONArray().apply { e.tags.forEach { put(it) } })
                        .put("related", JSONArray().apply { e.related.forEach { put(it.toJson()) } }),
                )
            }
            runCatching {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(array.toString())
                tmp.renameTo(file)
            }
        }
    }

    private fun load(): List<Entry> = runCatching {
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val related = o.optJSONArray("related") ?: JSONArray()
            Entry(
                url = o.getString("url"),
                title = o.optString("title"),
                channel = o.optString("channel"),
                channelId = o.optString("channelId").ifEmpty { null },
                at = o.optLong("at"),
                related = (0 until related.length()).map { videoFromJson(related.getJSONObject(it)) },
                watched = o.optDouble("watched", -1.0).toFloat(),
                tags = o.optJSONArray("tags")?.let { t -> (0 until t.length()).map { t.getString(it) } }.orEmpty(),
            )
        }
    }.getOrDefault(emptyList())

    private fun VideoItem.toJson() = JSONObject()
        .put("url", url).put("title", title).put("channel", channel).put("thumbnail", thumbnail ?: "")
        .put("duration", durationSec).put("views", views).put("uploaded", uploaded ?: "").put("channelId", channelId ?: "").put("uploadedAt", uploadedAt ?: 0L).put("short", isShort)

    private fun videoFromJson(o: JSONObject) = VideoItem(
        url = o.getString("url"),
        title = o.optString("title"),
        channel = o.optString("channel"),
        thumbnail = o.optString("thumbnail").ifEmpty { null },
        durationSec = o.optLong("duration", -1),
        views = o.optLong("views", -1),
        uploaded = o.optString("uploaded").ifEmpty { null },
        channelId = o.optString("channelId").ifEmpty { null },
        uploadedAt = o.optLong("uploadedAt", 0L).takeIf { it > 0 },
        isShort = o.optBoolean("short") || "/shorts/" in o.getString("url"),
    )

    companion object {
        private const val MAX_ENTRIES = 200 // 60 avant 2.7.0
        private const val MAX_WITH_RELATED = 60
        private const val MAX_RELATED = 20
        private const val MAX_TAGS = 10

        @Volatile private var instance: WatchHistory? = null
        fun get(context: Context): WatchHistory =
            instance ?: synchronized(this) { instance ?: WatchHistory(context.applicationContext).also { instance = it } }
    }
}
