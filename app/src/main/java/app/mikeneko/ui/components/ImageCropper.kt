package app.mikeneko.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.ui.theme.Pelage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Recadrer une image avant de la prendre (3.0.1) : la photo de profil dans un rond, la bannière dans
 * un cadre large. Deux doigts pour zoomer, un pour déplacer ; l'image couvre toujours le cadre.
 * [aspect] = largeur / hauteur du cadre ; [round] : un rond (la photo). [onCrop] reçoit la partie
 * cadrée, réduite à [maxSide] px au plus.
 */
@Composable
fun ImageCropper(
    uri: Uri,
    aspect: Float,
    round: Boolean,
    maxSide: Int,
    onDismiss: () -> Unit,
    onCrop: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    var source by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(uri) {
        source = withContext(Dispatchers.IO) { runCatching { decodeUpright(context, uri, 2048) }.getOrNull() }
        if (source == null) failed = true
    }
    LaunchedEffect(failed) { if (failed) onDismiss() }

    AnimatedDialog(onDismissRequest = onDismiss, fullScreen = true) { close ->
        Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            Text(
                stringResource(R.string.crop_hint),
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                color = Pelage.Texte,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val density = LocalDensity.current
                val boxW = with(density) { maxWidth.toPx() }
                val boxH = with(density) { maxHeight.toPx() }
                val margin = with(density) { 20.dp.toPx() }
                // Le cadre : le plus grand qui tient, centré
                val window = remember(boxW, boxH, aspect) {
                    var w = boxW - 2 * margin
                    var h = w / aspect
                    if (h > boxH - 2 * margin) {
                        h = boxH - 2 * margin
                        w = h * aspect
                    }
                    Rect(Offset((boxW - w) / 2, (boxH - h) / 2), Size(w, h))
                }
                val bitmap = source
                if (bitmap != null) {
                    val image = remember(bitmap) { bitmap.asImageBitmap() }
                    val minScale = remember(bitmap, window) { max(window.width / bitmap.width, window.height / bitmap.height) }
                    var scale by remember(bitmap, window) { mutableFloatStateOf(minScale) }
                    var offsetX by remember(bitmap, window) { mutableFloatStateOf(window.center.x - bitmap.width * minScale / 2) }
                    var offsetY by remember(bitmap, window) { mutableFloatStateOf(window.center.y - bitmap.height * minScale / 2) }
                    fun clamp() {
                        // L'image couvre toujours tout le cadre
                        offsetX = offsetX.coerceIn(window.right - bitmap.width * scale, window.left)
                        offsetY = offsetY.coerceIn(window.bottom - bitmap.height * scale, window.top)
                    }
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(bitmap, window) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val next = (scale * zoom).coerceIn(minScale, minScale * 8)
                                // Le zoom se fait autour des doigts
                                offsetX = centroid.x - (centroid.x - offsetX) * (next / scale) + pan.x
                                offsetY = centroid.y - (centroid.y - offsetY) * (next / scale) + pan.y
                                scale = next
                                clamp()
                            }
                        },
                    ) {
                        drawImage(
                            image,
                            dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt()),
                            dstSize = IntSize((bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt()),
                        )
                        // Le voile autour du cadre, et son contour
                        val hole = Path().apply {
                            if (round) addOval(window) else addRoundRect(RoundRect(window, CornerRadius(18.dp.toPx())))
                        }
                        val veil = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(Rect(0f, 0f, size.width, size.height))
                            addPath(hole)
                        }
                        drawPath(veil, Color.Black.copy(alpha = 0.6f))
                        drawPath(hole, Color.White.copy(alpha = 0.9f), style = Stroke(2.dp.toPx()))
                    }
                    Row(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FilledTonalButton(onClick = { close() }, modifier = Modifier.weight(1f).height(52.dp)) {
                            Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                val left = ((window.left - offsetX) / scale).roundToInt().coerceIn(0, bitmap.width - 1)
                                val top = ((window.top - offsetY) / scale).roundToInt().coerceIn(0, bitmap.height - 1)
                                val width = (window.width / scale).roundToInt().coerceIn(1, bitmap.width - left)
                                val height = (window.height / scale).roundToInt().coerceIn(1, bitmap.height - top)
                                val cropped = Bitmap.createBitmap(bitmap, left, top, width, height)
                                val shrink = min(1f, maxSide.toFloat() / max(width, height))
                                val result = if (shrink < 1f) Bitmap.createScaledBitmap(cropped, (width * shrink).roundToInt().coerceAtLeast(1), (height * shrink).roundToInt().coerceAtLeast(1), true) else cropped
                                close { onDismiss(); onCrop(result) }
                            },
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Text(stringResource(R.string.crop_use), fontWeight = FontWeight.ExtraBold)
                        }
                    }
                } else {
                    Loading(Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

/** L'image, réduite à [maxSide] px au plus, et tournée dans le bon sens (les photos ont leur sens à part, en EXIF). */
private fun decodeUpright(context: Context, uri: Uri, maxSide: Int): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val decoded = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("image illisible")
    val degrees = runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }
    }.getOrNull() ?: 0f
    return if (degrees == 0f) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(degrees) }, true)
}
