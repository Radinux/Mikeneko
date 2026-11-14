package app.mikeneko.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.mikeneko.R
import app.mikeneko.data.FoundSongs
import app.mikeneko.data.PlaylistImport
import app.mikeneko.data.RecognizedSong
import app.mikeneko.data.SongListener
import app.mikeneko.data.VideoItem
import app.mikeneko.player.PlayerHolder
import app.mikeneko.ui.components.AnimatedDialog
import app.mikeneko.ui.components.DialogClose
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage

private sealed interface FinderState {
    data object NoMic : FinderState
    data object Listening : FinderState
    /** Reconnu : on cherche le morceau sur YouTube Music. */
    data class Matching(val song: RecognizedSong) : FinderState
    data class Found(val song: RecognizedSong, val item: VideoItem?, val playlistId: String?, val fresh: Boolean) : FinderState
    data object NotFound : FinderState
    data object Failed : FinderState
}

/**
 * Reconnaître le morceau qui passe autour de soi (2.25.0) : quelques secondes au micro, le titre,
 * et le morceau entre dans la playlist « Found songs ». La musique de l'app se met en pause le temps
 * d'écouter, et reprend en fermant si on n'a rien lancé d'autre.
 */
@Composable
fun SongFinderDialog(onDismiss: () -> Unit, onOpenPlaylist: (String) -> Unit) {
    val context = LocalContext.current
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    var state by remember { mutableStateOf(if (granted()) FinderState.Listening else FinderState.NoMic) }
    var attempt by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) {
            state = FinderState.Listening
            attempt++
        }
    }
    LaunchedEffect(Unit) { if (!granted()) ask.launch(Manifest.permission.RECORD_AUDIO) }

    // Notre musique se tait pendant l'écoute (sinon c'est elle qu'on reconnaît)
    val wasPlaying = remember { PlayerHolder.isPlaying.value }
    var startedOther by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        if (wasPlaying) PlayerHolder.pause()
        onDispose { if (wasPlaying && !startedOther && !PlayerHolder.isPlaying.value) PlayerHolder.togglePlay() }
    }

    // Une écoute par essai (le premier, ou « Réessayer », ou juste après avoir autorisé le micro)
    LaunchedEffect(attempt) {
        if (state !is FinderState.Listening) return@LaunchedEffect
        progress = 0f
        val outcome = runCatching { SongListener.listen { progress = it } }.getOrElse { SongListener.Outcome.Failed }
        state = when (outcome) {
            is SongListener.Outcome.Found -> FinderState.Matching(outcome.song)
            SongListener.Outcome.NotFound -> FinderState.NotFound
            SongListener.Outcome.Failed -> FinderState.Failed
        }
        val song = (outcome as? SongListener.Outcome.Found)?.song ?: return@LaunchedEffect
        val item = runCatching { PlaylistImport.findSong(song.title, song.artist) }.getOrNull()
        val added = item?.let { FoundSongs.add(context, it) }
        state = FinderState.Found(song, item, added?.first, added?.second == true)
    }

    AnimatedDialog(onDismissRequest = onDismiss) { close ->
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Pelage.Coussin).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(state, transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) }, label = "trouver") { s ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (s) {
                        FinderState.NoMic -> Message(
                            stringResource(R.string.recognize_mic),
                            stringResource(R.string.recognize_mic_text),
                        ) {
                            Button(onClick = { ask.launch(Manifest.permission.RECORD_AUDIO) }) {
                                Text(stringResource(R.string.recognize_allow), fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        FinderState.Listening -> Listening(progress)
                        is FinderState.Matching -> SongCard(s.song, null)
                        is FinderState.Found -> SongCard(
                            s.song,
                            when {
                                s.item == null -> stringResource(R.string.recognize_not_on_youtube)
                                s.fresh -> stringResource(R.string.recognize_added, FoundSongs.name(context))
                                else -> stringResource(R.string.recognize_already, FoundSongs.name(context))
                            },
                        )
                        FinderState.NotFound -> Message(stringResource(R.string.recognize_none), stringResource(R.string.recognize_none_text)) {
                            Button(onClick = { state = FinderState.Listening; attempt++ }) { Text(stringResource(R.string.recognize_retry), fontWeight = FontWeight.ExtraBold) }
                        }
                        FinderState.Failed -> Message(stringResource(R.string.recognize_failed), stringResource(R.string.recognize_failed_text)) {
                            Button(onClick = { state = FinderState.Listening; attempt++ }) { Text(stringResource(R.string.recognize_retry), fontWeight = FontWeight.ExtraBold) }
                        }
                    }
                }
            }
            Buttons(state, close, onOpenPlaylist = { id -> close { onDismiss(); onOpenPlaylist(id) } }) { item ->
                startedOther = true
                close { onDismiss(); playTracks(context, listOf(item), 0, mix = true) }
            }
        }
    }
}

@Composable
private fun Buttons(state: FinderState, close: DialogClose, onOpenPlaylist: (String) -> Unit, onPlay: (VideoItem) -> Unit) {
    val found = state as? FinderState.Found
    // « Écouter » sur toute la largeur, au-dessus (3.1.1 : à trois sur une ligne, il était écrasé)
    if (found?.item != null) {
        Button(onClick = { onPlay(found.item) }, Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Icon(MkIcons.Play, null, Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.recognize_play), fontWeight = FontWeight.ExtraBold)
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = if (found?.item != null) 6.dp else 16.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        if (found?.playlistId != null) {
            TextButton(onClick = { onOpenPlaylist(found.playlistId) }) {
                Text(stringResource(R.string.recognize_open), color = Pelage.Texte, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
        }
        TextButton(onClick = { close() }) {
            Text(
                stringResource(if (state is FinderState.Listening) R.string.recognize_stop else R.string.sync_close),
                color = Pelage.Texte,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Le rond qui pulse au rythme de l'écoute, avec l'anneau qui se remplit. */
@Composable
private fun Listening(progress: Float) {
    val pulse = rememberInfiniteTransition(label = "ecoute")
    val scale by pulse.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pouls")
    val halo by pulse.animateFloat(0.15f, 0.4f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "halo")
    val ring by animateFloatAsState(progress, tween(300), label = "anneau")
    Text(stringResource(R.string.recognize_listening), Modifier.semantics { heading() }, color = Pelage.Texte, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    Box(Modifier.padding(vertical = 22.dp).size(150.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(150.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(Pelage.Roux.copy(alpha = halo)))
        CircularProgressIndicator(
            progress = { ring },
            modifier = Modifier.size(124.dp),
            color = Pelage.RouxClair,
            trackColor = Pelage.Bord,
            strokeWidth = 4.dp,
        )
        Box(Modifier.size(96.dp).clip(CircleShape).background(Pelage.Roux), contentAlignment = Alignment.Center) {
            Icon(MkIcons.Listen, null, tint = Pelage.Encre, modifier = Modifier.size(44.dp))
        }
    }
    Text(stringResource(R.string.recognize_listening_text), color = Pelage.Gris, fontSize = 14.sp, textAlign = TextAlign.Center)
}

@Composable
private fun SongCard(song: RecognizedSong, status: String?) {
    Cover(song.cover, 168.dp, 18.dp)
    Text(
        song.title,
        Modifier.padding(top = 16.dp).semantics { heading() },
        color = Pelage.Texte,
        fontSize = 21.sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Text(song.artist, Modifier.padding(top = 2.dp), color = Pelage.Gris, fontSize = 15.sp, textAlign = TextAlign.Center, maxLines = 2)
    Box(Modifier.padding(top = 12.dp).height(20.dp), contentAlignment = Alignment.Center) {
        if (status == null) {
            CircularProgressIndicator(Modifier.size(18.dp), color = Pelage.RouxClair, strokeWidth = 2.dp)
        } else {
            Text(status, color = Pelage.RouxClair, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Message(title: String, text: String, action: @Composable () -> Unit) {
    Text(title, Modifier.semantics { heading() }, color = Pelage.Texte, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
    Text(text, Modifier.padding(top = 10.dp, bottom = 16.dp), color = Pelage.Gris, fontSize = 14.sp, textAlign = TextAlign.Center)
    action()
}
