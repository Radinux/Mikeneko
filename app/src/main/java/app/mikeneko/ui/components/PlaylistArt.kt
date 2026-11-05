package app.mikeneko.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import app.mikeneko.data.Playlist
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import java.io.File

/**
 * La pochette d'une playlist perso, en mosaïque : les pochettes de ses 4 premiers morceaux (2.4.0).
 * Coil la fabrique comme n'importe quelle image (voir [PlaylistArtFetcher]), donc elle s'affiche
 * partout où une pochette s'affiche.
 */
data class PlaylistArt(val urls: List<String>)

/**
 * Ce qu'il faut afficher comme pochette d'une playlist : la photo choisie, sinon la mosaïque des
 * 4 premières pochettes (différentes), sinon la seule pochette, sinon rien (icône).
 */
fun Playlist.coverModel(): Any? {
    cover?.let { path -> File(path).takeIf { it.exists() }?.let { return it } }
    val thumbs = items.mapNotNull { it.thumbnail }.distinct()
    return if (thumbs.size >= 4) PlaylistArt(thumbs.take(4)) else thumbs.firstOrNull()
}

class PlaylistArtFetcher(
    private val art: PlaylistArt,
    private val options: Options,
    private val imageLoader: ImageLoader,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val half = SIDE / 2
        val out = Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(0xFF2B1A12.toInt()) // roux sombre, le temps qu'une case manque
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        art.urls.take(4).forEachIndexed { i, url ->
            val request = ImageRequest.Builder(options.context).data(url).size(half, half).allowHardware(false).build()
            val bitmap = (imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap() ?: return@forEachIndexed
            // Chaque pochette recadrée au carré, au centre
            val side = minOf(bitmap.width, bitmap.height)
            val src = Rect((bitmap.width - side) / 2, (bitmap.height - side) / 2, (bitmap.width + side) / 2, (bitmap.height + side) / 2)
            val x = (i % 2) * half
            val y = (i / 2) * half
            canvas.drawBitmap(bitmap, src, Rect(x, y, x + half, y + half), paint)
        }
        return ImageFetchResult(image = out.asImage(), isSampled = false, dataSource = DataSource.MEMORY)
    }

    class Factory : Fetcher.Factory<PlaylistArt> {
        override fun create(data: PlaylistArt, options: Options, imageLoader: ImageLoader): Fetcher =
            PlaylistArtFetcher(data, options, imageLoader)
    }

    /** La clé du cache mémoire : les 4 adresses (la mosaïque change quand les premiers morceaux changent). */
    class Key : Keyer<PlaylistArt> {
        override fun key(data: PlaylistArt, options: Options): String = "mosaique:" + data.urls.joinToString("|")
    }

    private companion object {
        const val SIDE = 512
    }
}
