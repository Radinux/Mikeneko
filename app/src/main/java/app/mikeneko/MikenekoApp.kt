package app.mikeneko

import app.mikeneko.player.TrackAnalyzer
import app.mikeneko.data.LocalSync
import app.mikeneko.update.Notifier
import android.app.Application
import app.mikeneko.data.NewPipeDownloader
import app.mikeneko.player.OfflineCache
import app.mikeneko.player.PlayerHolder
import app.mikeneko.player.PlaylistDownloads
import app.mikeneko.settings.AppLocale
import app.mikeneko.settings.AppSettings
import app.mikeneko.ui.components.PlaylistArtFetcher
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okio.Path.Companion.toOkioPath
import org.schabi.newpipe.extractor.NewPipe

class MikenekoApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        app.mikeneko.data.Deletions.init(this) // avant les listes : ce qu'on retire est retenu pour la synchro (2.23.0)
        PlayerHolder.init(this)
        NewPipe.init(NewPipeDownloader.instance)
        AppLocale.init(this) // langue et pays des contenus, d'après les réglages
        // La sauvegarde hors ligne s'ouvre en fond (lire son index au premier morceau bloquerait l'écran)
        Thread { OfflineCache.warmUp(this) }.start()
        PlaylistDownloads.init(this) // reprend les playlists à télécharger
        Notifier.createChannels(this) // notifications : mises à jour, nouvelles vidéos (2.10.0)
        Notifier.schedule(this)
        TrackAnalyzer.watch(this) // tempo et tonalité des morceaux écoutés (2.15.0)
        LocalSync.start(this) // les appareils liés se resynchronisent sur le même réseau (2.17.0)
        app.mikeneko.data.AutoSync.start(this) // et dès qu'on change quelque chose (2.19.2)
    }

    /**
     * Les miniatures : cache disque plafonné à 64 Mo (sinon Coil peut monter à 2 % du stockage, jusqu'à
     * 250 Mo), et le même client réseau que le reste de l'appli (connexions partagées).
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { NewPipeDownloader.client }))
                // Les mosaïques des playlists perso (4 pochettes en une)
                add(PlaylistArtFetcher.Factory())
                add(PlaylistArtFetcher.Key())
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("coil3_disk_cache").toOkioPath())
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .build()
}
