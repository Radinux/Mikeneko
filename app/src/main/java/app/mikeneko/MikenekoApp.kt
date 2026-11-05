package app.mikeneko

import okio.Path.Companion.toOkioPath
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.disk.DiskCache
import coil3.SingletonImageLoader
import coil3.PlatformContext
import coil3.ImageLoader
import android.app.Application
import app.mikeneko.data.NewPipeDownloader
import app.mikeneko.player.PlayerHolder
import app.mikeneko.player.OfflineCache
import app.mikeneko.player.PlaylistDownloads
import app.mikeneko.settings.AppLocale
import app.mikeneko.settings.AppSettings
import org.schabi.newpipe.extractor.NewPipe

class MikenekoApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        PlayerHolder.init(this)
        NewPipe.init(NewPipeDownloader.instance)
        AppLocale.init(this) // langue et pays des contenus, d'après les réglages
        // La sauvegarde hors ligne s'ouvre en fond (lire son index au premier morceau bloquerait l'écran)
        Thread { OfflineCache.warmUp(this) }.start()
        PlaylistDownloads.init(this) // reprend les playlists à télécharger
    }

    /**
     * Les miniatures : cache disque plafonné à 64 Mo (sinon Coil peut monter à 2 % du stockage, jusqu'à
     * 250 Mo), et le même client réseau que le reste de l'appli (connexions partagées).
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { NewPipeDownloader.client })) }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("coil3_disk_cache").toOkioPath())
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .build()
}
