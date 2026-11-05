package app.mikeneko.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.mikeneko.ui.theme.Pelage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Les couleurs déjà calculées, par adresse de pochette. */
private val cache = object : LinkedHashMap<String, Color>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Color>?) = size > 120
}

/**
 * La couleur d'une pochette, assez sombre pour servir de fond (texte blanc lisible dessus), qui
 * glisse en douceur d'un morceau à l'autre. [fallback] tant qu'elle n'est pas connue.
 */
@Composable
fun rememberCoverColor(url: String?, fallback: Color = Pelage.RouxNuit): State<Color> {
    val context = LocalContext.current
    val target by produceState(cache[url] ?: fallback, url) {
        if (url == null) {
            value = fallback
            return@produceState
        }
        cache[url]?.let {
            value = it
            return@produceState
        }
        val request = ImageRequest.Builder(context).data(url).size(48).allowHardware(false).build()
        val bitmap = (runCatching { SingletonImageLoader.get(context).execute(request) }.getOrNull() as? SuccessResult)?.image?.toBitmap()
        val color = bitmap?.let { withContext(Dispatchers.Default) { dominantColor(it) } } ?: fallback
        cache[url] = color
        value = color
    }
    return animateColorAsState(target, tween(700), label = "couleur de la pochette")
}

/**
 * La teinte qui domine parmi les couleurs franches (le noir, le blanc et les gris comptent peu),
 * puis assombrie : saturation un peu relevée, luminosité plafonnée.
 */
private fun dominantColor(bitmap: Bitmap): Color {
    val buckets = Array(12) { FloatArray(4) } // poids, r, g, b par tranche de teinte
    var grayWeight = 0f
    val gray = FloatArray(3)
    val hsv = FloatArray(3)
    for (y in 0 until bitmap.height step 2) {
        for (x in 0 until bitmap.width step 2) {
            val p = bitmap.getPixel(x, y)
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            val (h, s, v) = Triple(hsv[0], hsv[1], hsv[2])
            if (v < 0.12f) continue // presque noir
            if (s < 0.18f) { // gris, blanc : seulement en dernier recours
                grayWeight += v
                gray[0] += r * v; gray[1] += g * v; gray[2] += b * v
                continue
            }
            val weight = s * v
            val bucket = buckets[((h / 30f).toInt()).coerceIn(0, 11)]
            bucket[0] += weight; bucket[1] += r * weight; bucket[2] += g * weight; bucket[3] += b * weight
        }
    }
    val best = buckets.maxByOrNull { it[0] }
    val (r, g, b) = if (best != null && best[0] > 0.5f) {
        Triple(best[1] / best[0], best[2] / best[0], best[3] / best[0])
    } else if (grayWeight > 0f) {
        Triple(gray[0] / grayWeight, gray[1] / grayWeight, gray[2] / grayWeight)
    } else {
        return Pelage.RouxNuit
    }
    android.graphics.Color.RGBToHSV(r.toInt(), g.toInt(), b.toInt(), hsv)
    hsv[1] = (hsv[1] * 1.1f).coerceIn(0f, 0.85f)
    hsv[2] = hsv[2].coerceIn(0.28f, 0.52f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}
