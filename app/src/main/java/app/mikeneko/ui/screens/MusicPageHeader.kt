package app.mikeneko.ui.screens

import androidx.compose.foundation.layout.Spacer
import kotlin.math.abs
import app.mikeneko.ui.components.CONNECTED_GAP
import app.mikeneko.ui.components.ConnectedChip
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.data.Music
import app.mikeneko.data.Playlist
import app.mikeneko.data.Playlists
import app.mikeneko.data.VideoItem
import app.mikeneko.data.sameTrack
import app.mikeneko.data.trackKey
import app.mikeneko.player.PlayerHolder
import app.mikeneko.ui.components.Capsule
import app.mikeneko.ui.components.rememberCoverColor
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.jellyClickable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------------------------
// En-tête commun : pochette, titre, « Tout lire » et « Aléatoire »
// ---------------------------------------------------------------------------------------------

@Composable
internal fun PageHeader(
    title: String,
    subtitle: String,
    cover: Any?,
    round: Boolean = false,
    icon: ImageVector = MkIcons.Music,
    /** Le gros bouton lecture rond, à droite (null = pas de bouton). */
    onPlay: (() -> Unit)? = null,
    /** Le bouton lecture vient se ranger dans la barre du haut en défilant (voir [PlayAnchor]). */
    play: PlayAnchor? = null,
    actions: @Composable () -> Unit = {},
    /** Sous la ligne du bouton lecture, sur toute la largeur (2.11.3 : Ajouter | Modifier | Réordonner). */
    below: @Composable () -> Unit = {},
) {
    val density = LocalDensity.current
    var headerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Un halo de la couleur de la pochette derrière l'en-tête, qui s'efface vers la page
    val glow by rememberCoverColor(cover)
    Column(
        Modifier
            .fillMaxWidth()
            .onPlaced { headerCoords = it }
            // Le halo part du haut de l'écran (derrière la barre et le bouton retour), sans coupure
            .background(Brush.verticalGradient(0f to glow.copy(alpha = 0.85f), 0.55f to glow.copy(alpha = 0.4f), 1f to Color.Transparent))
            .padding(top = pageTop() + 12.dp)
            .padding(horizontal = 20.dp),
    ) {
        Cover(
            cover,
            220.dp,
            if (round) 110.dp else 14.dp,
            Modifier.align(Alignment.CenterHorizontally).shadow(18.dp, if (round) CircleShape else RoundedCornerShape(14.dp)),
            icon = icon,
        )
        Text(
            title,
            Modifier.padding(top = 18.dp),
            color = Pelage.Texte,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) Text(subtitle, Modifier.padding(top = 2.dp), color = Color(0xFFCFC6BD), fontSize = 14.sp, maxLines = 2)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // En plusieurs lignes s'il le faut (téléchargement, aléatoire, pochette…) : rien ne s'écrase
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { actions() }
            if (onPlay != null) {
                Box(
                    Modifier
                        .then(
                            if (play == null) {
                                Modifier
                            } else {
                                Modifier
                                    .onGloballyPositioned { c ->
                                        val header = headerCoords?.takeIf { it.isAttached } ?: return@onGloballyPositioned
                                        val inHeader = header.localPositionOf(c, Offset.Zero)
                                        // Dans le repère de la barre du haut (pas l'écran : voir PlayAnchor.space)
                                        val root = play.space()?.takeIf { it.isAttached }?.localPositionOf(c, Offset.Zero) ?: c.positionInRoot()
                                        // Des valeurs fixes, appelées à chaque image du défilement : on ne les
                                        // réécrit que si elles bougent vraiment (2.11.8). Recalculées par différence
                                        // de deux positions qui défilent, elles changeaient d'un arrondi à chaque
                                        // image et relançaient la mise en page du bouton qui voyage (un lag au
                                        // défilement rapide, absent en mode Modifier, sans ce bouton).
                                        val known = play.inHeader
                                        if (known == null || abs(known.x - inHeader.x) > 0.5f || abs(known.y - inHeader.y) > 0.5f) play.inHeader = inHeader
                                        if (abs(play.left - root.x) > 0.5f) play.left = root.x
                                        play.headerTop.value?.let { top ->
                                            val listTop = root.y - top - inHeader.y
                                            if (abs(play.listTop - listTop) > 0.5f) play.listTop = listTop
                                        }
                                    }
                                    // Parti vers la barre : c'est le bouton de MusicOverlay qui est dessiné
                                    .graphicsLayer { alpha = if ((play.spot(density)?.t ?: 0f) > 0f) 0f else 1f }
                            },
                        )
                        .size(PLAY_BIG).shadow(10.dp, CircleShape).clip(CircleShape).background(Pelage.Roux).jellyClickable(role = Role.Button, onClickLabel = stringResource(R.string.play_all), onClick = onPlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(MkIcons.Play, stringResource(R.string.play_all), tint = Pelage.Encre, modifier = Modifier.size(28.dp))
                }
            }
        }
        below()
    }
}

/**
 * Donne à la barre du haut le titre et le bouton lecture de la page ; ils y apparaissent quand le
 * titre de l'en-tête passe sous la barre.
 */
@Composable
internal fun PageBarEffect(state: MusicState, list: LazyListState, title: String, onPlay: (() -> Unit)?): PlayAnchor {
    val owner = remember { Any() }
    // Le fondu suit le doigt : il commence quand le bas de l'en-tête (son bouton lecture) arrive à
    // [FADE_LEAD] de la barre, et se fait sur [FADE] de défilement (le bouton de l'en-tête passe dessous)
    val density = LocalDensity.current
    val bar = with(density) { pageTop().toPx() }
    val fade = with(density) { FADE.toPx() }
    val lead = with(density) { FADE_LEAD.toPx() }
    val passed = remember(list, bar, fade, lead) {
        androidx.compose.runtime.derivedStateOf {
            val header = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
            when {
                list.firstVisibleItemIndex > 0 -> 1f
                header == null -> 0f
                else -> ((list.firstVisibleItemScrollOffset - (header.size - bar - lead)) / fade).coerceIn(0f, 1f)
            }
        }
    }
    // Le haut de l'en-tête dans la liste, pour le bouton lecture qui vient se ranger dans la barre
    val headerTop = remember(list) {
        androidx.compose.runtime.derivedStateOf { list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.offset?.toFloat() }
    }
    val anchor = remember(headerTop, passed, density) { PlayAnchor(headerTop, passed, { state.barPill }, { state.overlayRoot }, density) }
    SideEffect {
        state.bar = PageBar(title, onPlay, owner, passed, anchor)
    }
    DisposableEffect(owner) {
        onDispose {
            if (state.bar?.owner === owner) state.bar = null
        }
    }
    // Pendant que la page défile, la petite égalisation du morceau en cours se fige (voir PlayingBars)
    DisposableEffect(list) {
        onDispose { PlayingBarsClock.scrolling = false }
    }
    LaunchedEffect(list) {
        androidx.compose.runtime.snapshotFlow { list.isScrollInProgress }.collect { PlayingBarsClock.scrolling = it }
    }
    return anchor
}

/** Une page sans grand en-tête (bibliothèque) : son titre est tout de suite dans la barre du haut. */
@Composable
internal fun FixedPageBar(state: MusicState, title: String) {
    val owner = remember { Any() }
    val shown = remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    SideEffect { state.bar = PageBar(title, null, owner, shown) }
    DisposableEffect(owner) {
        onDispose { if (state.bar?.owner === owner) state.bar = null }
    }
}

/** La distance de défilement sur laquelle le titre et le bouton lecture arrivent dans la barre. */
private val FADE = 90.dp

/** À quelle distance de la barre le bas de l'en-tête doit arriver pour que le fondu commence. */
private val FADE_LEAD = 20.dp

/** Lire dans l'ordre (le gros bouton rond de l'en-tête). */
internal fun playInOrder(context: Context, tracks: List<VideoItem>): (() -> Unit)? =
    if (tracks.isEmpty()) null else ({
        PlayerHolder.setShuffle(false)
        playTracks(context, tracks, 0)
    })

/**
 * À côté du bouton lecture : l'aléatoire, un interrupteur. Allumé (orange) quand cette liste joue en
 * aléatoire. Éteint : l'appui mélange la liste en cours si c'est elle qui joue (le morceau continue),
 * sinon il la lance en aléatoire. Allumé : l'appui coupe l'aléatoire (l'ordre revient, le morceau continue).
 */
@Composable
internal fun ShuffleButton(tracks: List<VideoItem>) {
    val context = LocalContext.current
    if (tracks.isEmpty()) return
    val shuffle by PlayerHolder.shuffle.collectAsState()
    val queue by PlayerHolder.queue.collectAsState()
    // Cette liste est celle qui joue : tous ses morceaux sont dans la file (qui peut en avoir plus)
    // Par identifiants, en un seul passage (2.23.2). Avant : chaque morceau de la liste comparé à chaque
    // morceau de la file, à chaque retour de l'en-tête à l'écran. Sur une grosse playlist en cours de
    // lecture (140 morceaux : près de 20 000 comparaisons), ça bloquait une ou deux images, pile pendant
    // le trajet du bouton lecture. Ailleurs, la recherche s'arrêtait au premier morceau absent de la file.
    val playingThis = remember(queue, tracks) {
        val queued = queue.mapTo(HashSet(queue.size * 2)) { trackKey(it.url) }
        queued.isNotEmpty() && tracks.all { trackKey(it.url) in queued }
    }
    val on = shuffle && playingThis
    Capsule(stringResource(R.string.shuffle), selected = on, icon = MkIcons.Shuffle) {
        when {
            on -> PlayerHolder.setShuffle(false)
            playingThis -> PlayerHolder.setShuffle(true)
            else -> shuffleTracks(context, tracks)
        }
    }
}

/**
 * La pochette d'une playlist perso : « Pochette » choisit une photo (gardée dans l'appli, réduite à
 * 1024 px) ; « Mosaïque » revient aux pochettes des 4 premiers morceaux.
 */
/**
 * Le mode Modifier d'une playlist perso (2.11.0) : le nom (modifié en direct), la
 * pochette (photo ou mosaïque), « Supprimer » (toucher deux fois) et « Terminé ». Avant, la pochette
 * était en capsules dans l'en-tête, et le nom et la suppression cachés dans le menu ⋮.
 */
@Composable
internal fun PlaylistEditPanel(playlist: Playlist, onDone: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { Playlists.get(context) }
    var name by remember(playlist.id) { mutableStateOf(playlist.name) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(confirm) {
        if (confirm) {
            delay(3_000)
            confirm = false
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) { runCatching { saveCover(context, playlist.id, uri) }.getOrNull() }
            if (path == null) {
                Toast.makeText(context, context.getString(R.string.playlist_cover_failed), Toast.LENGTH_SHORT).show()
                return@launch
            }
            playlist.cover?.let { old -> withContext(Dispatchers.IO) { File(old).delete() } }
            store.setCover(playlist.id, path)
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                if (it.isNotBlank()) store.rename(playlist.id, it)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.playlist_name_label)) },
            singleLine = true,
        )
        // La pochette : une photo, ou la mosaïque des 4 premières pochettes
        Row(horizontalArrangement = Arrangement.spacedBy(CONNECTED_GAP)) {
            ConnectedChip(
                stringResource(R.string.playlist_cover),
                selected = playlist.cover != null,
                first = true,
                last = playlist.cover == null,
                icon = MkIcons.Image,
                pillWhenSelected = false,
            ) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            if (playlist.cover != null) {
                ConnectedChip(stringResource(R.string.playlist_cover_mosaic), selected = false, first = false, last = true, icon = MkIcons.Close) {
                    playlist.cover?.let { File(it).delete() }
                    store.setCover(playlist.id, null)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = {
                    if (!confirm) {
                        confirm = true
                    } else {
                        playlist.cover?.let { File(it).delete() }
                        store.delete(playlist.id)
                        onDeleted()
                    }
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8A80)),
            ) {
                Icon(MkIcons.Trash, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(if (confirm) R.string.confirm_touch_again else R.string.playlist_delete), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onDone) { Text(stringResource(R.string.playlist_done), fontWeight = FontWeight.ExtraBold) }
        }
    }
}


/** Copie la photo choisie dans l'appli, réduite (1024 px au plus), en JPEG. Renvoie son chemin. */
private fun saveCover(context: Context, id: String, uri: Uri): String {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1024) sample *= 2
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("image illisible")
    val dir = File(context.filesDir, "pochettes").apply { mkdirs() }
    // Un nouveau nom à chaque fois : l'image d'avant ne reste pas dans le cache des pochettes
    val file = File(dir, "$id-${System.currentTimeMillis()}.jpg")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    return file.absolutePath
}
