package app.mikeneko.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.player.AudioPreset
import app.mikeneko.player.PlayerHolder
import app.mikeneko.ui.components.MorphSheet
import app.mikeneko.ui.components.presetName
import app.mikeneko.ui.components.presetVibe
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage

/**
 * Les effets audio dans le lecteur musique (3.6.1) : comme la file et les paroles, le panneau sort de la
 * capsule des boutons et s'y referme, avec une croix en haut. Avant, il s'ouvrait dans le panneau des
 * réglages, qui monte du bas sans rapport avec le bouton.
 */
@Composable
internal fun EffectsSheet(backdrop: Backdrop, visible: Boolean, anchor: Rect?, onClose: () -> Unit) {
    val preset by PlayerHolder.audioPreset.collectAsState()
    BackHandler(enabled = visible, onBack = onClose)
    MorphSheet(
        backdrop, visible, anchor, onClose,
        closeLabel = stringResource(R.string.sync_close),
        heightFraction = 0.7f,
        tint = Color(0xC71A1613),
        frost = 28.dp,
    ) { handle ->
        Row(Modifier.fillMaxWidth().then(handle).padding(start = 22.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.audio_effects), Modifier.weight(1f).semantics { heading() }, color = Pelage.Texte, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(MkIcons.Close, stringResource(R.string.sync_close), tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(AudioPreset.entries, key = { it.id }) { p ->
                val selected = p == preset
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.RadioButton) { PlayerHolder.setAudioPreset(p) }
                        .padding(horizontal = 22.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                        if (selected) Icon(MkIcons.Check, stringResource(R.string.selected), tint = Pelage.RouxClair, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(
                            presetName(p),
                            color = if (selected) Pelage.RouxClair else Pelage.Texte,
                            fontSize = 15.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        )
                        Text(presetVibe(p), color = Color(0xFFCFC6BD), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
