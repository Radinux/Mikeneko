package app.mikeneko.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Le profil (2.18.0) : un pseudo, une photo, une bannière et un cadre autour de la photo. Tout reste sur
 * le téléphone (aucun compte, aucun serveur) ; le pseudo est le nom que voient les appareils liés
 * (Réglages › Synchro).
 */
data class Profile(
    val pseudo: String = "",
    /** La photo, copiée dans l'appli (512 px au plus). */
    val avatar: String? = null,
    /** La bannière, copiée dans l'appli (1600 px au plus). */
    val banner: String? = null,
    /** Le cadre autour de la photo ([ProfileFrame.id]). */
    val frame: String = ProfileFrame.Calico.id,
)

/** Les cadres proposés autour de la photo (dessinés par l'appli, voir ui/components/ProfileAvatar.kt). */
enum class ProfileFrame(val id: String) {
    None("aucun"), Calico("calico"), Sakura("sakura"), Gold("or"), Halo("halo"), Night("nuit");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: Calico
    }
}

object ProfileStore {
    private val _profile = MutableStateFlow(Profile())
    val profile: StateFlow<Profile> = _profile.asStateFlow()
    private var loaded = false

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("profil", Context.MODE_PRIVATE)

    fun load(context: Context): StateFlow<Profile> {
        synchronized(this) {
            if (!loaded) {
                loaded = true
                val p = prefs(context)
                _profile.value = Profile(
                    pseudo = p.getString("pseudo", "").orEmpty(),
                    avatar = p.getString("photo", null)?.takeIf { File(it).isFile },
                    banner = p.getString("banniere", null)?.takeIf { File(it).isFile },
                    frame = p.getString("cadre", ProfileFrame.Calico.id) ?: ProfileFrame.Calico.id,
                )
            }
        }
        return profile
    }

    private fun save(context: Context, next: Profile) {
        _profile.value = next
        prefs(context).edit()
            .putString("pseudo", next.pseudo)
            .putString("photo", next.avatar)
            .putString("banniere", next.banner)
            .putString("cadre", next.frame)
            .apply()
    }

    fun setPseudo(context: Context, pseudo: String) = save(context, load(context).value.copy(pseudo = pseudo.take(MAX_PSEUDO)))

    fun setFrame(context: Context, frame: ProfileFrame) = save(context, load(context).value.copy(frame = frame.id))

    /** La photo choisie (null : l'enlever). À appeler hors du fil principal. */
    fun setAvatar(context: Context, uri: Uri?) {
        val current = load(context).value
        val path = uri?.let { saveImage(context, it, "photo", 512) }
        current.avatar?.let { File(it).delete() }
        save(context, current.copy(avatar = path))
    }

    /** La bannière choisie (null : l'enlever). À appeler hors du fil principal. */
    fun setBanner(context: Context, uri: Uri?) {
        val current = load(context).value
        val path = uri?.let { saveImage(context, it, "banniere", 1600) }
        current.banner?.let { File(it).delete() }
        save(context, current.copy(banner = path))
    }

    /** La photo recadrée (3.0.1, voir ImageCropper). À appeler hors du fil principal. */
    fun setAvatar(context: Context, bitmap: Bitmap) {
        val current = load(context).value
        val path = saveBitmap(context, bitmap, "photo")
        current.avatar?.let { File(it).delete() }
        save(context, current.copy(avatar = path))
    }

    /** La bannière recadrée (3.0.1). À appeler hors du fil principal. */
    fun setBanner(context: Context, bitmap: Bitmap) {
        val current = load(context).value
        val path = saveBitmap(context, bitmap, "banniere")
        current.banner?.let { File(it).delete() }
        save(context, current.copy(banner = path))
    }

    private fun saveBitmap(context: Context, bitmap: Bitmap, name: String): String {
        val dir = File(context.filesDir, "profil").apply { mkdirs() }
        val file = File(dir, "$name-${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file.absolutePath
    }

    /** Copie l'image dans l'appli, réduite ([maxSide] px au plus), en JPEG ; un nouveau nom à chaque fois (pas de vieux cache). */
    private fun saveImage(context: Context, uri: Uri, name: String, maxSide: Int): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("image illisible")
        val scale = maxSide.toFloat() / maxOf(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true) else decoded
        val dir = File(context.filesDir, "profil").apply { mkdirs() }
        val file = File(dir, "$name-${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file.absolutePath
    }

    const val MAX_PSEUDO = 32
}
