package app.mikeneko.data

import app.mikeneko.settings.AppSettings
import android.content.Context
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
import java.io.File

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
    )

    private val file = File(context.filesDir, "historique.json")
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    /** Plus récente en premier. */
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    init {
        io.launch { _entries.value = load() }
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
        )
        _entries.value = (listOf(entry) + _entries.value.filterNot { it.url == entry.url }).take(MAX_ENTRIES)
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
            )
        }
    }.getOrDefault(emptyList())

    private fun VideoItem.toJson() = JSONObject()
        .put("url", url).put("title", title).put("channel", channel).put("thumbnail", thumbnail ?: "")
        .put("duration", durationSec).put("views", views).put("uploaded", uploaded ?: "").put("channelId", channelId ?: "").put("uploadedAt", uploadedAt ?: 0L)

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
    )

    companion object {
        private const val MAX_ENTRIES = 60
        private const val MAX_RELATED = 20

        @Volatile private var instance: WatchHistory? = null
        fun get(context: Context): WatchHistory =
            instance ?: synchronized(this) { instance ?: WatchHistory(context.applicationContext).also { instance = it } }
    }
}
