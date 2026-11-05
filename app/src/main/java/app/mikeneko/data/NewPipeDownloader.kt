package app.mikeneko.data

import app.mikeneko.R
import app.mikeneko.settings.AppLocale
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/** Pont entre NewPipeExtractor et OkHttp (même principe que dans NewPipe). */
class NewPipeDownloader private constructor(private val http: OkHttpClient) : Downloader() {

    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody()
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)
            .header("User-Agent", USER_AGENT)

        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }

        http.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException(AppLocale.string(R.string.youtube_captcha), request.url())
            }
            val raw = response.body?.string()
            // YouTube en français : vues et dates abrégées réécrites pour que NewPipe sache les lire
            val text = if (raw != null && AppLocale.language() == "fr" && "/youtubei/" in request.url()) YouTubeFrench.fix(raw) else raw
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                text,
                response.request.url.toString(),
            )
        }
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

        val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val instance = NewPipeDownloader(client)
    }
}
