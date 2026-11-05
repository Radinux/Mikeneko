package app.mikeneko.data

import android.content.Context
import android.util.Xml
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

/** Abonnements gardés sur le téléphone (SharedPreferences), synchronisables avec Google. */
class SubscriptionStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("abonnements", Context.MODE_PRIVATE)

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Subscription>> = _items.asStateFlow()

    private val _watched = MutableStateFlow(prefs.getStringSet("vues", emptySet())!!.toSet())
    /** Les vidéos déjà ouvertes, observable pour que « Nouveau » / « Pas vues » se mettent à jour. */
    val watched: StateFlow<Set<String>> = _watched.asStateFlow()

    fun addAll(list: List<Subscription>) {
        // Les nouvelles infos d'abord : un import ou une synchro Google met à jour noms et avatars
        val merged = (list + _items.value).distinctBy { it.channelId }.sortedBy { it.name.lowercase() }
        _items.value = merged
        save(merged)
    }

    /** L'image d'une chaîne suivie, reçue en passant (vidéo, page de la chaîne) ou cherchée exprès. */
    fun setAvatar(channelId: String, avatar: String?) {
        if (avatar.isNullOrBlank()) return
        val list = _items.value
        val index = list.indexOfFirst { it.channelId == channelId }
        if (index < 0 || list[index].avatar == avatar) return
        val updated = list.toMutableList().also { it[index] = it[index].copy(avatar = avatar) }
        _items.value = updated
        save(updated)
    }

    fun remove(channelId: String) {
        val left = _items.value.filterNot { it.channelId == channelId }
        _items.value = left
        save(left)
    }


    /** Retire toutes les marques « vu » (effacement de l'historique). */
    fun clearWatched() {
        _watched.value = emptySet()
        prefs.edit().remove("vues").apply()
    }

    fun markWatched(url: String) {
        if (url in _watched.value) return
        val next = _watched.updateAndGet { it + url }
        prefs.edit().putStringSet("vues", next).apply()
    }

    private fun load(): List<Subscription> {
        val raw = prefs.getString("liste", null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Subscription(o.getString("name"), o.getString("url"), o.getString("id"), o.optString("avatar").ifEmpty { null })
        }
    }

    private fun save(list: List<Subscription>) {
        val array = JSONArray()
        list.forEach { s ->
            array.put(JSONObject().put("name", s.name).put("url", s.url).put("id", s.channelId).put("avatar", s.avatar ?: ""))
        }
        prefs.edit().putString("liste", array.toString()).apply()
    }

    companion object {
        @Volatile private var instance: SubscriptionStore? = null
        fun get(context: Context): SubscriptionStore =
            instance ?: synchronized(this) { instance ?: SubscriptionStore(context.applicationContext).also { instance = it } }
    }
}

/** Import sans compte : NewPipe (.json), FreeTube (.db), Google Takeout (.csv, toutes langues) et notre sauvegarde (.xml). */
object SubscriptionImport {
    fun parse(text: String): List<Subscription> {
        // (Takeout commence parfois par un BOM invisible)
        val trimmed = text.removePrefix("\uFEFF").trimStart()
        return when {
            // Notre fichier de sauvegarde : ici, seulement ses abonnements
            MikenekoBackup.looksLike(trimmed) -> runCatching { MikenekoBackup.read(trimmed).subscriptions }.getOrDefault(emptyList())
            trimmed.startsWith("{") -> json(trimmed)
            else -> takeout(trimmed).ifEmpty { json(trimmed) }
        }
    }

    /** NewPipe : un seul objet. FreeTube : un objet JSON par ligne. Les deux ont une liste "subscriptions". */
    private fun json(text: String): List<Subscription> {
        val objects = runCatching { listOf(JSONObject(text)) }.getOrElse {
            text.lineSequence().filter { it.isNotBlank() }.mapNotNull { runCatching { JSONObject(it) }.getOrNull() }.toList()
        }
        return objects.flatMap { obj ->
            val subs = obj.optJSONArray("subscriptions") ?: return@flatMap emptyList()
            (0 until subs.length()).mapNotNull { i ->
                val s = subs.getJSONObject(i)
                val id = s.optString("id").takeIf { it.startsWith("UC") } ?: channelIdFrom(s.optString("url")) ?: return@mapNotNull null
                Subscription(
                    name = s.optString("name", id),
                    url = "https://www.youtube.com/channel/$id",
                    channelId = id,
                    avatar = s.optString("thumbnail").ifEmpty { null },
                )
            }
        }.distinctBy { it.channelId }
    }

    /**
     * Takeout : « Channel Id,Channel Url,Channel Title », mais l'en-tête est traduit selon la langue du
     * compte (« ID des chaînes,URL des chaînes,Titres des chaînes » en français) : on ne le lit pas. Chaque
     * ligne qui contient un identifiant de chaîne (UC…) en est une ; le titre est le champ qui n'est ni
     * l'identifiant ni l'adresse.
     */
    private fun takeout(text: String): List<Subscription> =
        text.lineSequence().mapNotNull { line ->
            val fields = csvFields(line.trimEnd('\r'))
            val id = fields.firstNotNullOfOrNull { f -> CHANNEL_ID.matchEntire(f.trim())?.value ?: channelIdFrom(f) } ?: return@mapNotNull null
            val title = fields.lastOrNull { f -> f.isNotBlank() && CHANNEL_ID.matchEntire(f.trim()) == null && !f.contains("youtube.com") }
            Subscription(title?.trim() ?: id, "https://www.youtube.com/channel/$id", id)
        }.distinctBy { it.channelId }.toList()

    private val CHANNEL_ID = Regex("UC[A-Za-z0-9_-]{22}")

    /** Les champs d'une ligne CSV : « a,"b, c","d ""e""" » → a | b, c | d "e". */
    private fun csvFields(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && line.getOrNull(i + 1) == '"' -> { current.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { fields += current.toString(); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        fields += current.toString()
        return fields
    }
}

/** Flux des abonnements : les flux RSS publics de YouTube, une requête légère par chaîne. */
/**
 * Les avatars des chaînes importées sans image (Takeout, NewPipe…) : cherchés doucement, une chaîne
 * toutes les [GAP_MS], [PER_LAUNCH] au plus par lancement, seulement pendant que l'onglet Abonnements
 * est ouvert (l'IP de l'utilisateur est sensible aux rafales vers YouTube). Une chaîne qui échoue
 * n'est pas retentée avant le prochain lancement.
 */
object SubscriptionAvatars {
    private const val GAP_MS = 4_000L
    private const val PER_LAUNCH = 30
    private var fetched = 0
    private val tried = mutableSetOf<String>()

    suspend fun fill(store: SubscriptionStore) {
        while (fetched < PER_LAUNCH) {
            val next = store.items.value.firstOrNull { it.avatar == null && it.channelId !in tried } ?: return
            tried += next.channelId
            fetched++
            runCatching { YouTubeRepository.channelAvatar(next.channelId) }.getOrNull()?.let { store.setAvatar(next.channelId, it) }
            kotlinx.coroutines.delay(GAP_MS)
        }
    }
}

object SubscriptionFeed {
    /** Une entrée par chaîne, gardée 15 min : seules les chaînes nouvelles ou périmées sont rechargées. */
    private val channels = TimedCache<List<FeedVideo>>(15 * 60_000L)

    /** Le flux complet si toutes les chaînes sont en cache, sinon null. */
    fun cached(subs: List<Subscription>): List<FeedVideo>? {
        if (subs.isEmpty()) return emptyList()
        val parts = subs.map { channels.peek(it.channelId) ?: return null }
        return parts.flatten().sortedByDescending { it.published }
    }

    suspend fun latest(subs: List<Subscription>): List<FeedVideo> = coroutineScope {
        val limit = Semaphore(8)
        subs.map { sub ->
            async(Dispatchers.IO) {
                limit.withPermit { runCatching { channels.get(sub.channelId) { fetch(sub) } }.getOrDefault(emptyList()) }
            }
        }.awaitAll().flatten().sortedByDescending { it.published }
    }

    /**
     * Jusqu'à quand on ne tente plus le flux RSS : YouTube le coupe parfois (404 pour toutes les
     * chaînes, 2026-11-03). Dans ce cas on passe directement par l'onglet « Vidéos » des chaînes.
     */
    @Volatile private var rssDownUntil = 0L

    private suspend fun fetch(sub: Subscription): List<FeedVideo> {
        if (System.currentTimeMillis() >= rssDownUntil) {
            runCatching { withContext(Dispatchers.IO) { fetchRss(sub) } }
                .onSuccess { return it }
                .onFailure { rssDownUntil = System.currentTimeMillis() + 30 * 60_000L }
        }
        return YouTubeRepository.channelLatest(sub.channelId, sub.name).map { video ->
            FeedVideo(
                video = video.copy(channel = sub.name, channelId = sub.channelId),
                published = video.uploadedAt?.let(Instant::ofEpochMilli) ?: Instant.EPOCH,
                channelId = sub.channelId,
            )
        }
    }

    private fun fetchRss(sub: Subscription): List<FeedVideo> {
        val request = Request.Builder().url("https://www.youtube.com/feeds/videos.xml?channel_id=${sub.channelId}").build()
        NewPipeDownloader.client.newCall(request).execute().use { response ->
            // Une erreur lève une exception : elle ne doit pas finir en cache comme « aucune vidéo »
            check(response.isSuccessful) { "RSS ${sub.channelId} : ${response.code}" }
            val parser = Xml.newPullParser()
            parser.setInput(response.body!!.charStream())
            val out = mutableListOf<FeedVideo>()
            var inEntry = false
            var id = ""
            var title = ""
            var published = ""
            var thumb: String? = null
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                // Xml.newPullParser() gère les espaces de noms : on reconstruit « yt:videoId »
                val tag = parser.prefix?.let { "$it:${parser.name}" } ?: parser.name
                when (event) {
                    XmlPullParser.START_TAG -> when (tag) {
                        "entry" -> { inEntry = true; id = ""; title = ""; published = ""; thumb = null }
                        "yt:videoId" -> if (inEntry) id = parser.nextText()
                        "title" -> if (inEntry) title = parser.nextText()
                        "published" -> if (inEntry) published = parser.nextText()
                        "media:thumbnail" -> if (inEntry) thumb = parser.getAttributeValue(null, "url")
                    }
                    XmlPullParser.END_TAG -> if (tag == "entry" && id.isNotEmpty()) {
                        inEntry = false
                        out += FeedVideo(
                            video = VideoItem(
                                url = "https://www.youtube.com/watch?v=$id",
                                title = title,
                                channel = sub.name,
                                thumbnail = thumb,
                                channelId = sub.channelId,
                            ),
                            published = runCatching { OffsetDateTime.parse(published).toInstant() }.getOrDefault(Instant.EPOCH),
                            channelId = sub.channelId,
                        )
                    }
                }
                event = parser.next()
            }
            return out
        }
    }
}
