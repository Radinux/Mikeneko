package app.mikeneko.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Les icônes de la maquette (trait arrondi 24 px), sans dépendre de material-icons. */
object MkIcons {
    val Home = stroke("accueil", "M4 11l8-7 8 7v9a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z")
    val Subscriptions = stroke("abonnements", "M6.5 8h11A2.5 2.5 0 0 1 20 10.5v7a2.5 2.5 0 0 1-2.5 2.5h-11A2.5 2.5 0 0 1 4 17.5v-7A2.5 2.5 0 0 1 6.5 8z M7 4.5h10 M10.5 11.5l4 2.5-4 2.5z")
    val Radio = stroke("radio", "M4 15v-3a8 8 0 0 1 16 0v3 M4.5 14h1A1.5 1.5 0 0 1 7 15.5V19a1.5 1.5 0 0 1-1.5 1.5h-1A1.5 1.5 0 0 1 3 19v-3.5A1.5 1.5 0 0 1 4.5 14z M18.5 14h1a1.5 1.5 0 0 1 1.5 1.5V19a1.5 1.5 0 0 1-1.5 1.5h-1A1.5 1.5 0 0 1 17 19v-3.5a1.5 1.5 0 0 1 1.5-1.5z")
    val Library = stroke("bibliotheque", "M5 4.5v15M9.5 4.5v15M14 5.5l5 13.5")
    val Download = stroke("telecharger", "M12 4v11M7 10l5 5 5-5M5 20h14")
    val Search = stroke("rechercher", "M11 4a7 7 0 1 1 0 14a7 7 0 1 1 0-14z M20 20l-4-4")
    val Settings = stroke("reglages", "M4 7h9M17 7h3M4 17h3M11 17h9 M15 5a2 2 0 1 1 0 4a2 2 0 1 1 0-4z M9 15a2 2 0 1 1 0 4a2 2 0 1 1 0-4z")
    val Close = stroke("fermer", "M6 6l12 12M18 6L6 18")
    val Expand = stroke("agrandir", "M14 4h6v6M20 4l-7 7M10 20H4v-6M4 20l7-7")
    val ChevronDown = stroke("reduire", "M6 9l6 6 6-6")
    val Heart = stroke("jaime", "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z")
    val HeartFilled = fill("jaime_plein", "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z")
    val Moon = stroke("minuteur", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z")
    val Queue = stroke("file", "M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01")
    val Share = stroke("partager", "M12 3v12M7 8l5-5 5 5M5 14v5a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-5")
    val Bell = stroke("cloche", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9 M10.3 21a1.94 1.94 0 0 0 3.4 0")
    val BellFilled = fill("cloche_pleine", "M12 2a6 6 0 0 0-6 6c0 7-3 9-3 9h18s-3-2-3-9a6 6 0 0 0-6-6z M10.3 21a1.94 1.94 0 0 0 3.4 0z")
    val Pencil = stroke("modifier", "M4 20h4L19 9l-4-4L4 16v4z M13.5 6.5l4 4")
    val MinusCircle = stroke("retirer", "M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18z M8 12h8")
    val Video = stroke("video", "M3 7a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z M17 10l4-2.5v9L17 14")
    val Check = stroke("ok", "M5 12.5l4.5 4.5L19 7.5", width = 2.6f)
    val Plus = stroke("plus", "M12 5v14M5 12h14")
    /** Une photo (pochette perso d'une playlist). */
    val Image = stroke("image", "M5 4.5h14a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-11a2 2 0 0 1 2-2z M3.5 16.5l5-5 4 4 2.5-2.5 5.5 5.5 M15.5 8.5a1.2 1.2 0 1 0 0 2.4a1.2 1.2 0 1 0 0-2.4z")
    /** Un short : un écran en hauteur avec un triangle de lecture. */
    val Shorts = stroke("shorts", "M8.5 2.5h7a3 3 0 0 1 3 3v13a3 3 0 0 1-3 3h-7a3 3 0 0 1-3-3v-13a3 3 0 0 1 3-3z M10.5 9.3v5.4l4.3-2.7z")
    /** Garder (un short) : un signet, plein quand c'est gardé. */
    val Bookmark = stroke("garder", "M7.5 3.5h9a1.5 1.5 0 0 1 1.5 1.5v15.5l-6-4.2-6 4.2V5a1.5 1.5 0 0 1 1.5-1.5z")
    val BookmarkFilled = ImageVector.Builder("garde", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            pathData = addPathNodes("M7.5 3.5h9a1.5 1.5 0 0 1 1.5 1.5v15.5l-6-4.2-6 4.2V5a1.5 1.5 0 0 1 1.5-1.5z"),
            fill = SolidColor(Color.White),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2.1f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()
    val Captions = stroke("sous_titres", "M6 6h12a3 3 0 0 1 3 3v6a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V9a3 3 0 0 1 3-3z M10.5 10.3a2 2 0 1 0 0 3.4M16.5 10.3a2 2 0 1 0 0 3.4")
    val Fullscreen = stroke("plein_ecran", "M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5")
    val ExitFullscreen = stroke("quitter_plein_ecran", "M9 4v5H4M15 4v5h5M9 20v-5H4M15 20v-5h5")
    val ThumbUp = stroke("pouce", "M7 11v9H4v-9z M7 11l4-7c1.3 0 2.3 1.2 2 2.5L12.3 10H18a2 2 0 0 1 2 2.3l-1.1 6A2 2 0 0 1 16.9 20H7")

    /** Le pouce plein (vidéo aimée) : la main remplie et arrondie, la manchette à part avec un fin écart. */
    val ThumbUpFilled = ImageVector.Builder("pouce_plein", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            pathData = addPathNodes("M7 11l4-7c1.3 0 2.3 1.2 2 2.5L12.3 10H18a2 2 0 0 1 2 2.3l-1.1 6A2 2 0 0 1 16.9 20H7z"),
            fill = SolidColor(Color.White),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2.1f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .addPath(pathData = addPathNodes("M4 9.95h1a1 1 0 0 1 1 1v9.1a1 1 0 0 1-1 1H4a1.05 1.05 0 0 1-1.05-1.05v-9a1.05 1.05 0 0 1 1.05-1.05z"), fill = SolidColor(Color.White))
        .build()
    val Bubble = stroke("bulle", "M12 6a7 7 0 1 1 0 14a7 7 0 1 1 0-14z M6.5 8.5L7 3l4 3.2M17.5 8.5L17 3l-4 3.2")
    // Flèches circulaires (tracés Lucide, licence ISC) : le « 10 » est posé par-dessus
    val Rewind = stroke("reculer", "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8 M3 3v5h5")
    val Forward = stroke("avancer", "M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8 M21 3v5h-5")
    // Tracés Lucide (licence ISC)
    val Globe = stroke("langue", "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20z M2 12h20 M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z")
    val MapPin = stroke("region", "M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0z M12 7a3 3 0 1 1 0 6a3 3 0 1 1 0-6z")
    val Clock = stroke("historique", "M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18z M12 7v5l3 2")
    val Trash = stroke("effacer", "M4 7h16 M10 11v6 M14 11v6 M6 7l1 13h10l1-13 M9 7V4h6v3")
    val Info = stroke("infos", "M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18z M12 11v6 M12 7.5h.01")
    val More = stroke("plus_d_options", "M12 5.5h.01M12 12h.01M12 18.5h.01", width = 3.2f)
    val Ban = stroke("bloquer", "M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18z M5.6 5.6l12.8 12.8")
    val EyeOff = stroke("masquer", "M3 3l18 18 M10.6 5.1A9.8 9.8 0 0 1 12 5c5 0 9 4.5 10 7a12.6 12.6 0 0 1-2.9 3.9 M6.5 6.6C4.3 8 2.7 10 2 12c1 2.5 5 7 10 7a9.6 9.6 0 0 0 4.8-1.3 M9.9 9.9a3 3 0 0 0 4.2 4.2")
    val Pin = stroke("epingle", "M9 4h6l-1 5 3 3v2H7v-2l3-3z M12 14v6")
    val ChevronLeft = stroke("retour", "M15 6l-6 6 6 6")
    val ArrowUpLeft = stroke("completer", "M17 17L7 7 M7 15V7h8")
    val ChevronRight = stroke("ouvrir", "M9 6l6 6-6 6")
    // Roue crantée (tracé Lucide, licence ISC) : dents régulières, même trait arrondi que les autres
    val Gear = stroke(
        "parametres",
        "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z M12 9a3 3 0 1 1 0 6a3 3 0 1 1 0-6z",
        width = 1.9f,
    )
    val Quality = stroke("qualite", "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z M7 9v6M7 12h3.5M10.5 9v6 M14 9v6h1.5a2.5 2.5 0 0 0 2.5-2.5v-1A2.5 2.5 0 0 0 15.5 9z")
    val Speed = stroke("vitesse", "M4.6 17.5a8.5 8.5 0 1 1 14.8 0 M12 13.5l3.5-4")
    val Loop = stroke("boucle", "M17 3l3 3-3 3 M4 11V9.5A3.5 3.5 0 0 1 7.5 6H20 M7 21l-3-3 3-3 M20 13v1.5a3.5 3.5 0 0 1-3.5 3.5H4")
    val Ambient = stroke("ambiant", "M12 8a4 4 0 1 1 0 8a4 4 0 1 1 0-8z M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4")
    val Waves = stroke("ronron", "M3 12h2M7 8v8M11 5v14M15 9v6M19 11v2")
    val Shuffle = stroke("aleatoire", "M16 3h5v5 M4 20L21 3 M21 16v5h-5 M15 15l6 6 M4 4l5 5")
    val Repeat = stroke("repeter", "M17 2l4 4-4 4 M3 11v-1a4 4 0 0 1 4-4h14 M7 22l-4-4 4-4 M21 13v1a4 4 0 0 1-4 4H3")
    val RepeatOne = stroke("repeter_un", "M17 2l4 4-4 4 M3 11v-1a4 4 0 0 1 4-4h14 M7 22l-4-4 4-4 M21 13v1a4 4 0 0 1-4 4H3 M11 10h1v4")
    val Lyrics = stroke("paroles", "M4 6h16M4 10h16M4 14h10M4 18h7 M18 14v6 M18 20a1.6 1.6 0 1 1-3.2 0a1.6 1.6 0 1 1 3.2 0z")
    val PlaylistAdd = stroke("ajouter_playlist", "M4 6h12M4 11h12M4 16h7 M18 13v7M14.5 16.5h7")
    /** Trier (2.15.1) : trois traits de plus en plus courts. */
    val Sort = stroke("trier", "M4 6h16 M7 12h10 M10 18h4")
    val DragHandle = stroke("deplacer", "M5 9h14M5 15h14")
    /** Réordonner (2.11.3) : une flèche vers le haut et une vers le bas, lisibles même petites. */
    val Reorder = stroke("reordonner", "M8 20V5 M4 9l4-4 4 4 M16 4v15 M12 15l4 4 4-4", width = 2.3f)
    val Mix = stroke("mix", "M12 12c-2-2.67-4-4-6-4a4 4 0 1 0 0 8c2 0 4-1.33 6-4zm0 0c2 2.67 4 4 6 4a4 4 0 0 0 0-8c-2 0-4 1.33-6 4z")
    val Music = stroke("musique", "M9 18V5l12-2v13 M9 18a3 3 0 1 1-6 0a3 3 0 1 1 6 0z M21 16a3 3 0 1 1-6 0a3 3 0 1 1 6 0z")
    val Album = stroke("album", "M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18z M12 10a2 2 0 1 1 0 4a2 2 0 1 1 0-4z")

    val Play = fill("lecture", "M8 5.5v13a1 1 0 0 0 1.5.9l10-6.5a1 1 0 0 0 0-1.8l-10-6.5A1 1 0 0 0 8 5.5z")
    val Pause = fill("pause", "M7.5 5h1A1.5 1.5 0 0 1 10 6.5v11A1.5 1.5 0 0 1 8.5 19h-1A1.5 1.5 0 0 1 6 17.5v-11A1.5 1.5 0 0 1 7.5 5z M15.5 5h1A1.5 1.5 0 0 1 18 6.5v11a1.5 1.5 0 0 1-1.5 1.5h-1A1.5 1.5 0 0 1 14 17.5v-11A1.5 1.5 0 0 1 15.5 5z")
    val Next = fill("suivant", "M17.5 5h.5a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1h-.5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z M5 5.5v13a1 1 0 0 0 1.5.9l8.7-6.5a1 1 0 0 0 0-1.8L6.5 4.6A1 1 0 0 0 5 5.5z")
    val Previous = fill("precedent", "M6 5h.5a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z M19 5.5v13a1 1 0 0 1-1.5.9L8.8 12.9a1 1 0 0 1 0-1.8l8.7-6.5a1 1 0 0 1 1.5.9z")
    val Paw = fill("patte", "M7 16a5 4 0 1 0 10 0a5 4 0 1 0-10 0z M3.3 10a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0-4.4 0z M7.3 6a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0-4.4 0z M12.3 6a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0-4.4 0z M16.3 10a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0-4.4 0z")
}

private fun stroke(name: String, path: String, width: Float = 2.1f): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(
            pathData = addPathNodes(path),
            stroke = SolidColor(Color.White),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()

private fun fill(name: String, path: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.White))
        .build()
