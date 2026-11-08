package app.mikeneko.ui.components

import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.withTimeoutOrNull

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.GraphicsLayerScope

/**
 * Tourner le téléphone sur une vidéo : seule la vidéo tourne, pas toute l'interface (comme YouTube).
 * Sur l'écran du lecteur, Android n'anime plus la rotation (`ROTATION_ANIMATION_JUMPCUT`, voir
 * MainActivity) : la nouvelle orientation s'affiche d'un coup, et la vidéo part de là où elle était
 * vue juste avant (couchée de ±90°) pour pivoter jusqu'à sa nouvelle place.
 *
 * - Vers le plein écran : elle part de sa place en haut du lecteur portrait, couchée sur le côté,
 *   et pivote en grandissant jusqu'à remplir l'écran.
 * - Vers le portrait : elle part du plein écran (au centre, couchée), et pivote en rétrécissant
 *   jusqu'à sa place en haut de la page ; la page apparaît derrière elle en fondu.
 */
object RotationMorph {
    /** La rotation de départ de la vidéo (degrés, ±90), dans le sens où l'on voyait l'ancienne image. */
    var from by mutableFloatStateOf(0f)
        private set
    var toFullscreen by mutableStateOf(false)
        private set
    /** Change à chaque rotation : relance l'animation (voir [animate]). */
    var token by mutableIntStateOf(0)
        private set
    /** Posé dans la composition, avant l'animation : la vidéo reste à son point de départ (pas d'image finale volée). */
    private var pending by mutableStateOf(false)
    private val progress = Animatable(1f)

    /** Où est la vidéo du lecteur portrait (root, px). Gardé même quand le lecteur portrait n'est plus affiché. */
    var portraitVideo by mutableStateOf<Rect?>(null)

    /**
     * Où est la vidéo de la page lecteur en paysage (root, px), pour le zoom (2.0.4). Un état
     * (2.11.17) : la transformation de la vidéo se recalcule dès que la page donne sa place ; avant,
     * au premier retour du plein écran, la vidéo restait quelques images à sa petite place puis
     * sautait en grand avant de rétrécir (les boutons « se levaient » d'un coup).
     */
    var landscapeVideo by mutableStateOf<Rect?>(null)

    /** Entrer / sortir du plein écran sans tourner (téléphone tenu en paysage) : un zoom, pas une rotation. */
    private var zoom by mutableStateOf(false)

    /** 0 au départ, 1 à l'arrivée. */
    private val p: Float get() = if (pending) 0f else progress.value

    val running: Boolean get() = pending || progress.value < 1f

    /**
     * L'écran vient de tourner de [oldRotation] à [newRotation] (`Surface.ROTATION_*`, en degrés).
     * L'ancienne image paraît tournée de l'inverse de la rotation de l'écran.
     */
    fun start(oldRotation: Int, newRotation: Int, toFullscreen: Boolean) {
        var turn = -(newRotation - oldRotation).toFloat()
        while (turn > 180f) turn -= 360f
        while (turn <= -180f) turn += 360f
        if (turn == 0f) return
        zoom = false
        from = turn
        this.toFullscreen = toFullscreen
        pending = true
        token++
    }

    /**
     * Téléphone en paysage, plein écran ouvert ou fermé avec le bouton (ou le retour) : la vidéo grandit
     * depuis sa place dans la page paysage jusqu'au plein écran, ou y revient en rétrécissant.
     */
    fun startZoom(toFullscreen: Boolean) {
        // Entrer : il faut la place de départ. Sortir : la page paysage la donne à sa première mise en
        // page (2.11.16 : au premier retour, elle n'avait encore jamais été affichée et le zoom était sauté)
        if (toFullscreen && landscapeVideo == null) return
        zoom = true
        from = 0f
        this.toFullscreen = toFullscreen
        pending = true
        token++
    }

    /** À lancer depuis un `LaunchedEffect(token)` toujours présent (MikenekoRoot). */
    suspend fun animate() {
        if (!pending) return
        // La page d'arrivée se met en page pendant ses premières images (lourdes, la première fois
        // surtout) : on attend qu'elle ait donné sa place et soit dessinée, la vidéo tenue à son point
        // de départ. Sinon l'animation se jouait pendant ces images et paraissait instantanée.
        if (zoom && !toFullscreen) withTimeoutOrNull(400) { while (landscapeVideo == null) withFrameNanos { } }
        withFrameNanos { }
        withFrameNanos { }
        progress.snapTo(0f)
        pending = false
        // ~350 ms en vrai (ANIMATION_SPEED = 2)
        progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }

    /**
     * La vidéo du plein écran (une boîte qui remplit l'écran, image 16:9 au centre). Au départ :
     * couchée, à 9/16 de sa taille (sa longueur = la largeur de l'ancien écran), à la place qu'elle
     * avait en haut du portrait (à gauche ou à droite selon le sens de rotation).
     */
    fun GraphicsLayerScope.fullscreenVideo() {
        if (!toFullscreen || !running) return
        val t = p
        val u = 1f - t
        if (zoom) {
            // Depuis la vidéo de la page paysage : même taille d'image au départ, puis tout l'écran
            val page = landscapeVideo ?: return
            val image = minOf(size.width, size.height * 16f / 9f)
            val s = page.width / image * u + t
            scaleX = s
            scaleY = s
            translationX = (page.center.x - size.width / 2f) * u
            translationY = (page.center.y - size.height / 2f) * u
            return
        }
        val h = size.height
        val w = size.width
        // Le centre de l'ancienne vidéo, le long de l'ancien axe vertical (= la largeur d'ici)
        val old = portraitVideo?.center?.y ?: (h * 9f / 32f + 40f * density)
        val oldX = if (from < 0f) old else w - old
        rotationZ = from * u
        val s = 9f / 16f + (1f - 9f / 16f) * t
        scaleX = s
        scaleY = s
        translationX = (oldX - w / 2f) * u
    }

    /**
     * La vidéo du lecteur portrait. Au départ : couchée au centre de l'écran, à 16/9 de sa taille
     * (sa longueur = la hauteur de l'ancien plein écran = la largeur d'ici).
     */
    fun GraphicsLayerScope.portraitVideo(screenHeight: Float) {
        if (toFullscreen || !running || zoom) return
        val t = p
        val u = 1f - t
        val centerY = portraitVideo?.center?.y ?: (size.height / 2f)
        rotationZ = from * u
        val s = 16f / 9f + (1f - 16f / 9f) * t
        scaleX = s
        scaleY = s
        translationY = (screenHeight / 2f - centerY) * u
    }

    /**
     * La vidéo de la page paysage, au retour du plein écran : elle part de la taille du plein écran,
     * au centre, et rétrécit jusqu'à sa place.
     */
    fun GraphicsLayerScope.landscapeVideo(screenWidth: Float, screenHeight: Float) {
        if (toFullscreen || !running || !zoom) return
        // Place pas encore connue (première mise en page) : cachée une image plutôt que montrée petite
        val page = landscapeVideo ?: run {
            alpha = 0f
            return
        }
        val t = p
        val u = 1f - t
        val image = minOf(screenWidth, screenHeight * 16f / 9f)
        val s = image / page.width * u + t
        scaleX = s
        scaleY = s
        translationX = (screenWidth / 2f - page.center.x) * u
        translationY = (screenHeight / 2f - page.center.y) * u
    }

    /** La vidéo de la page (portrait, ou paysage pour le zoom) : sa place, pour le voile noir. */
    fun pageVideo(landscape: Boolean): Rect? = if (landscape) landscapeVideo else portraitVideo

    /** Le voile noir derrière la vidéo qui rentre dans la page (1 → 0). */
    val scrim: Float get() = if (!toFullscreen && running) 1f - p else 0f
}
