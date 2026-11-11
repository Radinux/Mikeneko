package app.mikeneko.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.ui.components.AnimatedDialog
import app.mikeneko.R
import app.mikeneko.data.Profile
import app.mikeneko.data.ProfileFrame
import app.mikeneko.data.ProfileStore
import app.mikeneko.ui.components.Capsule
import app.mikeneko.ui.components.FRAME_MARGIN
import app.mikeneko.ui.components.ProfileAvatar
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.Rounded
import coil3.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** La bannière du profil, ou un dégradé de l'accent quand il n'y en a pas. */
@Composable
private fun Banner(profile: Profile, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Pelage.RouxFonce, Pelage.RouxNuit)))) {
        profile.banner?.let { AsyncImage(File(it), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/** Modifier le profil : la bannière, la photo, le pseudo et le cadre. Tout est gardé tout de suite. */
@Composable
fun ProfileEditor(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by remember { ProfileStore.load(context) }.collectAsState()
    var pseudo by remember { mutableStateOf(profile.pseudo) }
    fun failed() = Toast.makeText(context, context.getString(R.string.profile_image_failed), Toast.LENGTH_SHORT).show()
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { ProfileStore.setAvatar(context, uri) }.isSuccess }
            if (!ok) failed()
        }
    }
    val pickBanner = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { ProfileStore.setBanner(context, uri) }.isSuccess }
            if (!ok) failed()
        }
    }
    val image = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    AnimatedDialog(onDismissRequest = onDismiss, margin = 0.dp) { close ->
        Column(
            Modifier.padding(16.dp).fillMaxWidth().heightIn(max = 720.dp).clip(RoundedCornerShape(28.dp)).background(Pelage.Coussin)
                .verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            Text(stringResource(R.string.profile_title), Modifier.semantics { heading() }, color = Pelage.Texte, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(stringResource(R.string.profile_local), color = Pelage.Gris, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))

            // La bannière (un tap la change) et la photo par-dessus
            val avatar = 96.dp
            Box(Modifier.fillMaxWidth()) {
                Banner(profile, Modifier.fillMaxWidth().aspectRatio(3f).clickable(role = Role.Button, onClickLabel = stringResource(R.string.profile_banner_change)) { pickBanner.launch(image) })
                ProfileAvatar(
                    profile.avatar,
                    pseudo,
                    ProfileFrame.of(profile.frame),
                    avatar,
                    Modifier.align(Alignment.BottomStart).offset(y = avatar * 0.75f)
                        .clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClickLabel = stringResource(R.string.profile_photo_change)) { pickAvatar.launch(image) },
                )
            }
            Spacer(Modifier.height(avatar * 0.75f + 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Capsule(stringResource(R.string.profile_photo_change), icon = MkIcons.Image) { pickAvatar.launch(image) }
                Capsule(stringResource(R.string.profile_banner_change), icon = MkIcons.Image) { pickBanner.launch(image) }
            }
            // Connecté à GitHub (2.19.0) : reprendre son nom et sa photo d'un tap
            val github by app.mikeneko.data.GitHubSync.login.collectAsState()
            if (github is app.mikeneko.data.GitHubSync.Login.In) {
                Row(Modifier.padding(top = 8.dp)) {
                    Capsule(stringResource(R.string.github_profile_import), icon = MkIcons.Globe) {
                        scope.launch {
                            val ok = app.mikeneko.data.GitHubSync.importProfile(context)
                            ProfileStore.profile.value.pseudo.let { pseudo = it }
                            if (!ok) failed()
                        }
                    }
                }
            }
            if (profile.avatar != null || profile.banner != null) {
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (profile.avatar != null) Capsule(stringResource(R.string.profile_photo_remove), icon = MkIcons.Close) {
                        scope.launch(Dispatchers.IO) { ProfileStore.setAvatar(context, null) }
                    }
                    if (profile.banner != null) Capsule(stringResource(R.string.profile_banner_remove), icon = MkIcons.Close) {
                        scope.launch(Dispatchers.IO) { ProfileStore.setBanner(context, null) }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.profile_pseudo), color = Pelage.Texte, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(16.dp)).background(Pelage.Nuit)
                    .border(1.5.dp, Pelage.Bord, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = pseudo,
                    onValueChange = {
                        pseudo = it.take(ProfileStore.MAX_PSEUDO)
                        ProfileStore.setPseudo(context, pseudo.trim())
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = Pelage.Texte, fontSize = 16.sp, fontFamily = Rounded, fontWeight = FontWeight.Bold),
                    cursorBrush = SolidColor(Pelage.RouxClair),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (pseudo.isEmpty()) Text(stringResource(R.string.profile_pseudo_hint), color = Pelage.Gris, fontSize = 15.sp)
                        inner()
                    },
                )
            }
            Text(stringResource(R.string.profile_pseudo_text), Modifier.padding(top = 4.dp), color = Pelage.Gris, fontSize = 12.sp)

            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.profile_frame), color = Pelage.Texte, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            // Les cadres, sur ta photo : trois par ligne
            ProfileFrame.entries.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { option ->
                        val selected = option.id == profile.frame
                        Column(
                            Modifier.clip(RoundedCornerShape(18.dp))
                                .background(if (selected) Pelage.RouxNuit else Pelage.Coussin)
                                .border(if (selected) 2.dp else 0.dp, if (selected) Pelage.RouxClair else Pelage.Coussin, RoundedCornerShape(18.dp))
                                .clickable(role = Role.RadioButton, onClickLabel = stringResource(frameLabel(option))) { ProfileStore.setFrame(context, option) }
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ProfileAvatar(profile.avatar, pseudo, option, 54.dp)
                            Text(
                                stringResource(frameLabel(option)),
                                Modifier.width(54.dp * (1 + 2 * FRAME_MARGIN)),
                                color = if (selected) Pelage.RouxClair else Pelage.Texte,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(8.dp))
            Button(onClick = { close() }, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.playlist_done), fontWeight = FontWeight.ExtraBold) }
        }
    }
}

private fun frameLabel(frame: ProfileFrame) = when (frame) {
    ProfileFrame.None -> R.string.frame_none
    ProfileFrame.Calico -> R.string.frame_calico
    ProfileFrame.Sakura -> R.string.frame_sakura
    ProfileFrame.Gold -> R.string.frame_gold
    ProfileFrame.Halo -> R.string.frame_halo
    ProfileFrame.Night -> R.string.frame_night
}
