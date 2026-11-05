package app.mikeneko.data

import app.mikeneko.settings.AppLocale
import app.mikeneko.R
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Connexion au compte Google, par la voie officielle :
 * autorisation OAuth (API Identity des Play Services) + YouTube Data API v3.
 *
 * Avant que ça marche, il faut créer un identifiant OAuth « Android » dans la
 * Google Cloud Console (voir le README). Sans ça, Google refuse la connexion.
 */
object GoogleAccount {
    /**
     * Connexion Google affichée dans l'appli. Désactivée tant que l'identifiant OAuth Android
     * n'est pas déclaré dans la Google Cloud Console (voir README) : sans lui, Google refuse
     * avec UNREGISTERED_ON_API_CONSOLE. Passer à true une fois la console configurée.
     */
    const val ENABLED = false

    /** Lecture seule : abonnements, playlists et vidéos aimées. */
    private const val YOUTUBE_READONLY = "https://www.googleapis.com/auth/youtube.readonly"

    /** Écriture : publier des commentaires et des réponses (demandé seulement au moment de commenter). */
    private const val YOUTUBE_WRITE = "https://www.googleapis.com/auth/youtube.force-ssl"

    private fun request(write: Boolean): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOfNotNull(Scope(YOUTUBE_READONLY), if (write) Scope(YOUTUBE_WRITE) else null))
            .build()

    /**
     * Si le résultat a une « resolution », il faut lancer son PendingIntent (écran de choix du compte).
     * [write] : demande aussi le droit de commenter (Google affiche alors un nouvel écran d'accord).
     */
    suspend fun authorize(context: Context, write: Boolean = false): AuthorizationResult =
        Identity.getAuthorizationClient(context).authorize(request(write)).await()

    fun resultFromIntent(context: Context, data: Intent?): AuthorizationResult =
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)

    suspend fun fetchSubscriptions(accessToken: String): List<Subscription> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Subscription>()
        var pageToken: String? = null
        do {
            val url = buildString {
                append("https://www.googleapis.com/youtube/v3/subscriptions?part=snippet&mine=true&maxResults=50&order=alphabetical")
                pageToken?.let { append("&pageToken=").append(it) }
            }
            val request = Request.Builder().url(url).header("Authorization", "Bearer $accessToken").build()
            val json = NewPipeDownloader.client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { AppLocale.string(R.string.api_http, response.code) }
                JSONObject(response.body!!.string())
            }
            val items = json.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val snippet = items.getJSONObject(i).getJSONObject("snippet")
                val id = snippet.getJSONObject("resourceId").getString("channelId")
                out += Subscription(
                    name = snippet.getString("title"),
                    url = "https://www.youtube.com/channel/$id",
                    channelId = id,
                    avatar = snippet.optJSONObject("thumbnails")?.optJSONObject("medium")?.optString("url"),
                )
            }
            pageToken = json.optString("nextPageToken").ifEmpty { null }
        } while (pageToken != null && out.size < 2000)
        out
    }

    /** Publie un commentaire sous une vidéo (YouTube Data API, 50 unités de quota). */
    suspend fun postComment(accessToken: String, videoId: String, text: String): Comment = withContext(Dispatchers.IO) {
        val body = JSONObject().put(
            "snippet",
            JSONObject()
                .put("videoId", videoId)
                .put("topLevelComment", JSONObject().put("snippet", JSONObject().put("textOriginal", text))),
        )
        val json = send(accessToken, "https://www.googleapis.com/youtube/v3/commentThreads?part=snippet", body)
        val top = json.getJSONObject("snippet").getJSONObject("topLevelComment")
        commentFrom(top.getString("id"), top.getJSONObject("snippet"))
    }

    /** Répond à un commentaire ([parentId] = le commentaire principal du fil). */
    suspend fun postReply(accessToken: String, parentId: String, text: String): Comment = withContext(Dispatchers.IO) {
        val body = JSONObject().put("snippet", JSONObject().put("parentId", parentId).put("textOriginal", text))
        val json = send(accessToken, "https://www.googleapis.com/youtube/v3/comments?part=snippet", body)
        commentFrom(json.getString("id"), json.getJSONObject("snippet"))
    }

    private fun send(accessToken: String, url: String, body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        NewPipeDownloader.client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(errorMessage(response.code, text))
            return JSONObject(text)
        }
    }

    /** Les erreurs de l'API YouTube, en phrases compréhensibles. */
    private fun errorMessage(code: Int, body: String): String {
        val reason = runCatching {
            JSONObject(body).getJSONObject("error").optJSONArray("errors")?.optJSONObject(0)?.optString("reason")
        }.getOrNull().orEmpty()
        return when {
            reason == "commentsDisabled" -> AppLocale.string(R.string.api_comments_disabled)
            reason == "quotaExceeded" -> AppLocale.string(R.string.api_quota)
            reason == "insufficientPermissions" || code == 403 && body.contains("SCOPE_INSUFFICIENT") ->
                AppLocale.string(R.string.api_scope)
            reason == "forbidden" -> AppLocale.string(R.string.api_forbidden)
            reason == "processingFailure" || reason == "invalidCommentMetadata" -> AppLocale.string(R.string.api_rejected)
            code == 401 -> AppLocale.string(R.string.api_expired)
            else -> AppLocale.string(R.string.api_http, code) + if (reason.isNotEmpty()) " ($reason)" else ""
        }
    }

    private fun commentFrom(id: String, snippet: JSONObject) = Comment(
        id = id,
        author = snippet.optString("authorDisplayName"),
        avatar = snippet.optString("authorProfileImageUrl").ifEmpty { null },
        text = snippet.optString("textDisplay").ifEmpty { snippet.optString("textOriginal") },
        likes = 0,
        ago = AppLocale.string(R.string.just_now),
        mine = true,
    )
}
