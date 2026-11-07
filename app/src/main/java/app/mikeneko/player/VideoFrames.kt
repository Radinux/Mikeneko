package app.mikeneko.player

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.TextureView
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * L'image de transition entre deux écrans vidéo (page, plein écran, mini-lecteur) : quand un écran
 * vidéo en remplace un autre, il montre la dernière image du précédent le temps de se brancher.
 * Sorti de [PlayerHolder] lors du grand nettoyage.
 */
object VideoFrames {
    private var texture: WeakReference<TextureView>? = null

    /** Compté à chaque fois que la vidéo affiche sa première image sur un nouvel écran. */
    val firstFrames = MutableStateFlow(0)

    /** Compté à chaque saut dans la vidéo (barre, double tap, chapitre…). */
    val seeks = MutableStateFlow(0)

    /**
     * En pause, un nouvel écran vidéo affiche l'image *suivante* (ExoPlayer n'a plus l'image en
     * cours à redessiner). La première photo prise pendant la pause est donc la seule exacte : on la
     * garde, et chaque écran suivant la montre par-dessus sa vidéo jusqu'à la reprise ou un saut.
     */
    private var pausedFrame: Bitmap? = null

    /** La lecture reprend (ou on saute, ou la vidéo change) : la photo de la pause n'est plus la bonne. */
    fun forgetPausedFrame() {
        pausedFrame = null
    }

    /** L'écran vidéo actuellement branché sur le lecteur. */
    fun attachTexture(view: TextureView?) {
        if (view != null) texture = WeakReference(view)
    }

    /**
     * Un écran vidéo disparaît : on garde sa dernière image, car le suivant peut se construire
     * juste après (le mini-lecteur, par exemple) et n'aurait plus rien à photographier.
     */
    fun detachTexture(view: TextureView?) {
        if (view == null) return
        if (PlayerHolder.nowPlaying.value?.audioOnly == false) {
            // En pause, la surface peut montrer l'image suivante : c'est la photo de la pause qui compte
            (pausedFrame.takeIf { !PlayerHolder.playRequested.value } ?: capture(view))?.let { lastFrame = it to SystemClock.uptimeMillis() }
        }
        if (texture?.get() === view) texture = null
    }

    private var lastFrame: Pair<Bitmap, Long>? = null

    /** La dernière image affichée, pour la montrer dans le nouvel écran le temps qu'il se branche. */
    fun snapshot(): Bitmap? {
        val paused = !PlayerHolder.playRequested.value
        if (paused) pausedFrame?.let { return it }
        val shot = texture?.get()?.let(::capture)
            // L'écran précédent vient de disparaître (moins d'une seconde) : sa dernière image
            ?: lastFrame?.takeIf { SystemClock.uptimeMillis() - it.second < 1_000 }?.first
        if (paused) pausedFrame = shot
        return shot
    }

    private var liveBitmap: Bitmap? = null

    /**
     * L'image en cours de l'écran vidéo branché, réduite à [width] pixels de large (2.9.0) : le morph
     * mini-lecteur ↔ grand lecteur la reprend à chaque image d'écran, pour que la vidéo continue de
     * jouer pendant le vol (avant, une photo figée pendant ~1 s). L'écran d'arrivée est caché
     * (transparent) mais reçoit déjà les images. Le même Bitmap est réutilisé d'une image à l'autre.
     */
    fun live(width: Int): Bitmap? {
        val view = texture?.get() ?: return null
        if (!view.isAvailable || view.width <= 0 || view.height <= 0) return null
        val w = width.coerceIn(1, view.width)
        val h = (w * view.height.toFloat() / view.width).toInt().coerceAtLeast(1)
        val reuse = liveBitmap?.takeIf { it.width == w && it.height == h }
            ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { liveBitmap = it }
        return runCatching { view.getBitmap(reuse) }.getOrNull()
    }

    private fun capture(view: TextureView): Bitmap? {
        if (!view.isAvailable || view.width <= 0 || view.height <= 0) return null
        val bitmap = runCatching { view.bitmap }.getOrNull() ?: return null
        return cropToVideo(bitmap)
    }

    /** Retire les bandes noires éventuelles : on garde la zone centrale au format de la vidéo. */
    private fun cropToVideo(bitmap: Bitmap): Bitmap {
        val size = PlayerHolder.videoSize() ?: return bitmap
        if (size.width <= 0 || size.height <= 0) return bitmap
        val videoRatio = size.width * size.pixelWidthHeightRatio / size.height
        val ratio = bitmap.width.toFloat() / bitmap.height
        if (abs(ratio - videoRatio) / videoRatio < 0.02f) return bitmap
        return if (ratio > videoRatio) {
            val w = (bitmap.height * videoRatio).toInt().coerceIn(1, bitmap.width)
            Bitmap.createBitmap(bitmap, (bitmap.width - w) / 2, 0, w, bitmap.height)
        } else {
            val h = (bitmap.width / videoRatio).toInt().coerceIn(1, bitmap.height)
            Bitmap.createBitmap(bitmap, 0, (bitmap.height - h) / 2, bitmap.width, h)
        }
    }
}
