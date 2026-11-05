package app.mikeneko.player

import app.mikeneko.data.Chapter
import app.mikeneko.data.Segment
import app.mikeneko.data.VideoItem
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.Frameset

// Ce que le lecteur partagé expose : le morceau / la vidéo en cours, les pages du menu ⚙, le minuteur, la répétition.

data class NowPlaying(
    val url: String,
    val videoId: String,
    val title: String,
    val channel: String,
    val thumbnail: String?,
    val audioOnly: Boolean,
    val live: Boolean,
    val views: Long,
    val likes: Long,
    val dislikes: Long? = null,
    val chapters: List<Chapter> = emptyList(),
    val segments: List<Segment> = emptyList(),
    /** Description de la vidéo (HTML chez YouTube, avec liens et horodatages). */
    val description: String = "",
    val descriptionHtml: Boolean = false,
    /** Date de mise en ligne (ms), si YouTube la donne. */
    val uploadedAt: Long? = null,
    /** Vidéos proposées à la suite (« À suivre »), sans directs ni ce qu'on a écarté. */
    val related: List<VideoItem> = emptyList(),
    /** La chaîne de la vidéo : adresse, avatar, abonnés (-1 si inconnu). */
    val channelUrl: String? = null,
    val channelAvatar: String? = null,
    val channelSubscribers: Long = -1,
    /** Les planches d'aperçu de YouTube (petites images à intervalles réguliers), pour la barre de lecture. */
    val previewFrames: Frameset? = null,
    /** Un short (YouTube le dit, ou vidéo en hauteur de 3 min au plus) : il s'ouvre dans le lecteur Shorts. */
    val isShort: Boolean = false,
)

/** Les pages du menu de réglages du lecteur (null = menu fermé). */
enum class SettingsPage { Menu, Qualite, Vitesse, SousTitres, Minuteur }

/** Minuteur de veille : arrêter la lecture après un délai, ou à la fin de la vidéo. */
sealed interface SleepTimer {
    data object Off : SleepTimer
    data class At(val elapsedRealtime: Long, val minutes: Int) : SleepTimer
    data object EndOfVideo : SleepTimer
}

/** Répéter dans la Musique : rien, toute la file, ou le morceau en cours. */
enum class Repeat { Off, All, One }
