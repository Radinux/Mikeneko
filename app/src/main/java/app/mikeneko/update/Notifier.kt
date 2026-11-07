package app.mikeneko.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.mikeneko.MainActivity
import app.mikeneko.R
import app.mikeneko.data.Subscription
import app.mikeneko.data.SubscriptionFeed
import app.mikeneko.data.SubscriptionStore
import app.mikeneko.data.VideoItem
import app.mikeneko.data.looksShort
import app.mikeneko.data.videoIdOf
import app.mikeneko.settings.AppSettings
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Les notifications de Mikeneko (2.10.0 ; avant, aucune) :
 * - une nouvelle version disponible (vérifiée en fond une fois par jour) ou prête à installer ;
 * - les nouvelles vidéos des chaînes dont on a allumé la cloche (à côté de « S'abonner »), vérifiées
 *   en fond toutes les ~2 h par le flux RSS de chaque chaîne (léger). Cloche éteinte par défaut.
 * La vérification en fond passe par WorkManager ([WatchWorker]), même appli fermée.
 */
object Notifier {
    private const val CHANNEL_UPDATES = "mises_a_jour"
    private const val CHANNEL_VIDEOS = "nouvelles_videos"
    private const val UPDATE_ID = 1
    const val EXTRA_UPDATE = "mikeneko.mise_a_jour"
    private const val MAX_PER_CHANNEL = 3

    /** Les canaux de notification (réglables dans Android : Mises à jour, Nouvelles vidéos). */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL_UPDATES, context.getString(R.string.notif_channel_updates), NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(NotificationChannel(CHANNEL_VIDEOS, context.getString(R.string.notif_channel_videos), NotificationManager.IMPORTANCE_DEFAULT))
    }

    /** La vérification en fond toutes les 2 h, avec du réseau (une seule, gardée d'un lancement à l'autre). */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WatchWorker>(2, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("veille", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Android autorise-t-il nos notifications ? (Android 13+ : une permission à accorder.) */
    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun prefs(context: Context) = context.getSharedPreferences("notifications", Context.MODE_PRIVATE)

    // ------------------------------------------------------------------ mises à jour

    /** Une version plus récente existe (vue en fond) : une fois par version. */
    fun updateAvailable(context: Context, version: String) =
        update(context, version, context.getString(R.string.notif_update_available, version), context.getString(R.string.notif_update_available_text))

    /** La mise à jour est téléchargée : elle s'installera en quittant l'appli, ou tout de suite d'un tap. */
    fun updateReady(context: Context, version: String) =
        update(context, version, context.getString(R.string.notif_update_ready, version), context.getString(R.string.notif_update_ready_text), ready = true)

    private fun update(context: Context, version: String, title: String, text: String, ready: Boolean = false) {
        val key = if (ready) "maj_prete" else "maj_annoncee"
        if (prefs(context).getString(key, null) == version || !allowed(context)) return
        prefs(context).edit().putString(key, version).apply()
        val open = Intent(context, MainActivity::class.java).putExtra(EXTRA_UPDATE, true)
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context, UPDATE_ID, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(UPDATE_ID, notification) }
    }

    /** En fond, une fois par jour au plus : une nouvelle version sur GitHub ? */
    internal fun checkUpdate(context: Context) {
        val now = System.currentTimeMillis()
        if (now - prefs(context).getLong("maj_verifiee", 0L) < 20 * 3_600_000L) return
        prefs(context).edit().putLong("maj_verifiee", now).apply()
        val release = AppUpdates.latest() ?: return
        if (AppUpdates.newer(release.version, AppUpdates.installedVersion(context))) updateAvailable(context, release.version)
    }

    // ------------------------------------------------------------------ nouvelles vidéos

    /**
     * Les chaînes à cloche : ce qui est sorti depuis la dernière vérification (3 vidéos au plus par
     * chaîne). La première fois pour une chaîne, on note seulement où on en est (pas d'avalanche).
     */
    internal suspend fun checkNewVideos(context: Context) {
        val store = SubscriptionStore.get(context)
        val bells = store.bells.value
        if (bells.isEmpty() || !allowed(context)) return
        val seenPrefs = context.getSharedPreferences("cloches_vues", Context.MODE_PRIVATE)
        store.items.value.filter { it.channelId in bells }.forEach { sub ->
            val videos = runCatching { SubscriptionFeed.latest(listOf(sub)) }.getOrDefault(emptyList()).filterNot { it.video.isLive }
            val newest = videos.maxOfOrNull { it.published.toEpochMilli() } ?: return@forEach
            val seen = seenPrefs.getLong(sub.channelId, 0L)
            if (seen > 0L) {
                val watched = store.watched.value
                videos
                    .filter { it.published.toEpochMilli() > seen && it.video.url !in watched }
                    .filterNot { AppSettings.hideShorts.value && it.video.looksShort }
                    .sortedBy { it.published }
                    .takeLast(MAX_PER_CHANNEL)
                    .forEach { newVideo(context, it.video, sub) }
            }
            if (newest > seen) seenPrefs.edit().putLong(sub.channelId, newest).apply()
            delay(1_500) // une chaîne à la fois, sans rafale
        }
    }

    private suspend fun newVideo(context: Context, video: VideoItem, sub: Subscription) {
        val id = (videoIdOf(video.url) ?: video.url).hashCode()
        val open = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(video.url.toUri())
        val builder = NotificationCompat.Builder(context, CHANNEL_VIDEOS)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(sub.name)
            .setContentText(video.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(video.title))
            .setAutoCancel(true)
            .setGroup(CHANNEL_VIDEOS)
            .setContentIntent(PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        thumbnail(context, video.thumbnail)?.let { picture ->
            builder.setLargeIcon(picture).setStyle(NotificationCompat.BigPictureStyle().bigPicture(picture).bigLargeIcon(null as Bitmap?).setSummaryText(video.title))
        }
        runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }
    }

    private suspend fun thumbnail(context: Context, url: String?): Bitmap? {
        if (url == null) return null
        return withTimeoutOrNull(8_000) {
            runCatching {
                val request = ImageRequest.Builder(context).data(url).size(640, 360).allowHardware(false).build()
                (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
            }.getOrNull()
        }
    }
}

/** La vérification en fond (toutes les ~2 h, avec du réseau) : nouvelles vidéos des chaînes à cloche, mises à jour. */
class WatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { Notifier.checkNewVideos(applicationContext) }
        runCatching { Notifier.checkUpdate(applicationContext) }
        return Result.success()
    }
}
