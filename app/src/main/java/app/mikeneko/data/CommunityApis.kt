package app.mikeneko.data

import app.mikeneko.R
import app.mikeneko.settings.AppLocale
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** SponsorBlock : les segments à dégager d'un coup de patte. https://sponsor.ajay.app */
object SponsorBlock {
    val CATEGORIES = listOf(
        "sponsor", "selfpromo", "interaction", "intro", "outro", "preview", "music_offtopic", "filler",
    )

    /**
     * Envoie un passage marqué à la main. [userId] : l'identifiant anonyme et privé de l'appareil
     * (SponsorBlock le hache, il ne sert qu'à grouper les envois). Lève une exception au message
     * lisible si SponsorBlock refuse.
     */
    suspend fun submit(videoId: String, userId: String, startMs: Long, endMs: Long, category: String, durationMs: Long) =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("videoID", videoId)
                .put("userID", userId)
                .put("userAgent", "Mikeneko/Android")
                .put("videoDuration", durationMs / 1000.0)
                .put(
                    "segments",
                    JSONArray().put(
                        JSONObject()
                            .put("segment", JSONArray().put(startMs / 1000.0).put(endMs / 1000.0))
                            .put("category", category)
                            .put("actionType", "skip"),
                    ),
                )
            val request = Request.Builder()
                .url("https://sponsor.ajay.app/api/skipSegments")
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            NewPipeDownloader.client.newCall(request).execute().use { response ->
                if (response.isSuccessful) return@use
                val text = response.body?.string().orEmpty().take(200)
                throw IllegalStateException(
                    when (response.code) {
                        409 -> AppLocale.string(R.string.sb_err_duplicate)
                        429 -> AppLocale.string(R.string.sb_err_rate)
                        400, 403 -> AppLocale.string(R.string.sb_err_rejected, text.ifBlank { response.code.toString() })
                        else -> AppLocale.string(R.string.sb_err_http, response.code)
                    },
                )
            }
        }


    suspend fun segments(videoId: String): List<Segment> = withContext(Dispatchers.IO) {
        runCatching {
            val categories = URLEncoder.encode(JSONArray(CATEGORIES).toString(), "UTF-8")
            val request = Request.Builder()
                .url("https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=$categories")
                .build()
            NewPipeDownloader.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val array = JSONArray(response.body!!.string())
                (0 until array.length()).map { i ->
                    val item = array.getJSONObject(i)
                    val segment = item.getJSONArray("segment")
                    Segment(
                        startMs = (segment.getDouble(0) * 1000).toLong(),
                        endMs = (segment.getDouble(1) * 1000).toLong(),
                        category = item.getString("category"),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}

/** Return YouTube Dislike : le compteur de pouces en bas. https://returnyoutubedislike.com */
object ReturnDislike {
    data class Votes(val likes: Long, val dislikes: Long)

    suspend fun votes(videoId: String): Votes? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://returnyoutubedislikeapi.com/votes?videoId=$videoId")
                .build()
            NewPipeDownloader.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val json = JSONObject(response.body!!.string())
                Votes(json.optLong("likes"), json.optLong("dislikes"))
            }
        }.getOrNull()
    }
}
