package app.mikeneko.ui.components

import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Button
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.data.Segment
import app.mikeneko.data.SponsorBlock
import app.mikeneko.data.formatClock
import app.mikeneko.player.PlayerHolder
import app.mikeneko.settings.AppSettings
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.clay
import app.mikeneko.ui.theme.jellyClickable
import kotlinx.coroutines.launch

private val Secondary = Color(0xFFCFC6BD)

/** Le passage en cours de marquage (positions en ms). */
@Stable
class SegmentDraft {
    var start by mutableStateOf<Long?>(null)
    var end by mutableStateOf<Long?>(null)
    var category by mutableStateOf("sponsor")

    val valid: Boolean get() = start != null && end != null && end!! - start!! >= 500

    fun reset() {
        start = null
        end = null
    }

    /** Pour la barre de progression. */
    fun fractions(duration: Long): Pair<Float, Float>? {
        if (duration <= 0) return null
        val s = start ?: return null
        val e = end ?: (s + 1)
        return s.toFloat() / duration to e.toFloat() / duration
    }
}

/**
 * « Marquer un passage » : début et fin à la position actuelle (avec ±1 s pour ajuster),
 * la catégorie, « Revoir » et l'envoi anonyme à SponsorBlock.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SegmentMarker(draft: SegmentDraft, videoId: String, position: Long, duration: Long, onClose: () -> Unit) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }

    fun send() {
        val start = draft.start ?: return
        val end = draft.end ?: return
        if (!draft.valid) {
            Toast.makeText(context, context.getString(R.string.mark_invalid), Toast.LENGTH_SHORT).show()
            return
        }
        sending = true
        scope.launch {
            runCatching {
                SponsorBlock.submit(videoId, AppSettings.sponsorBlockUserId(), start, end, draft.category, duration)
            }.onSuccess {
                PlayerHolder.addSegment(Segment(start, end, draft.category))
                Toast.makeText(context, context.getString(R.string.mark_sent), Toast.LENGTH_SHORT).show()
                draft.reset()
                onClose()
            }.onFailure {
                Toast.makeText(context, it.message ?: context.getString(R.string.unknown_error), Toast.LENGTH_LONG).show()
            }
            sending = false
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.5.dp, Pelage.Dore.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(MkIcons.Paw, null, tint = Pelage.Dore, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.mark_title), Modifier.weight(1f).padding(start = 8.dp), color = Pelage.Texte, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(MkIcons.Close, stringResource(R.string.mark_close), tint = Secondary, modifier = Modifier.size(18.dp))
            }
        }
        if (draft.start == null) {
            Text(stringResource(R.string.mark_hint), color = Secondary, fontSize = 13.sp)
        }

        TimeRow(
            label = stringResource(R.string.mark_start),
            value = draft.start,
            setLabel = stringResource(R.string.mark_set_start),
            onSet = {
                draft.start = position
                if ((draft.end ?: Long.MAX_VALUE) <= position) draft.end = null
            },
            onNudge = { delta -> draft.start = draft.start?.plus(delta)?.coerceIn(0L, (draft.end ?: duration) - 500) },
        )
        TimeRow(
            label = stringResource(R.string.mark_end),
            value = draft.end,
            setLabel = stringResource(R.string.mark_set_end),
            onSet = { draft.end = position.coerceAtMost(duration) },
            onNudge = { delta -> draft.end = draft.end?.plus(delta)?.coerceIn((draft.start ?: 0L) + 500, duration) },
        )

        Text(stringResource(R.string.mark_category), color = Pelage.Texte, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppSettings.SPONSOR_CATEGORIES.forEach { category ->
                val on = draft.category == category
                Text(
                    sponsorLabel(category),
                    Modifier
                        .clip(CircleShape)
                        .background(if (on) Pelage.Dore else Color.White.copy(alpha = 0.08f))
                        .clickable(role = Role.RadioButton) { draft.category = category }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = if (on) Pelage.Encre else Pelage.Texte,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallButton(stringResource(R.string.mark_preview), enabled = draft.start != null, filled = false) {
                // On reprend 2 s avant le début pour vérifier
                PlayerHolder.seekTo(((draft.start ?: 0L) - 2_000).coerceAtLeast(0))
                if (!PlayerHolder.playRequested.value) PlayerHolder.togglePlay()
            }
            Spacer(Modifier.weight(1f))
            if (sending) {
                Loading(Modifier.size(28.dp))
            } else {
                SmallButton(stringResource(R.string.mark_send), enabled = draft.valid, filled = true, onClick = ::send)
            }
        }

        Text(stringResource(R.string.mark_note), color = Secondary, fontSize = 12.sp)
        Text(
            stringResource(R.string.mark_rules),
            Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button) { runCatching { uri.openUri("https://wiki.sponsor.ajay.app/w/Guidelines") } }.padding(vertical = 4.dp),
            color = Pelage.RouxClair,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TimeRow(label: String, value: Long?, setLabel: String, onSet: () -> Unit, onNudge: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(56.dp), color = Pelage.Texte, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Nudge("−1 s", stringResource(R.string.mark_earlier), enabled = value != null) { onNudge(-1_000) }
        Text(
            value?.let { formatClock(it) + ".%d".format((it % 1000) / 100) } ?: "—",
            Modifier.width(78.dp),
            color = if (value != null) Pelage.Dore else Secondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
        Nudge("+1 s", stringResource(R.string.mark_later), enabled = value != null) { onNudge(1_000) }
        Spacer(Modifier.weight(1f))
        FilledTonalButton(onClick = onSet, modifier = Modifier.semantics { onClick(setLabel) { onSet(); true } }) {
            Text(stringResource(R.string.mark_here), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun Nudge(text: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(52.dp, 40.dp).semantics { contentDescription = label },
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SmallButton(text: String, enabled: Boolean, filled: Boolean, onClick: () -> Unit) {
    val label: @Composable RowScope.() -> Unit = { Text(text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
    if (filled) Button(onClick = onClick, enabled = enabled, content = label)
    else FilledTonalButton(onClick = onClick, enabled = enabled, content = label)
}

/** Nom lisible d'une catégorie SponsorBlock. */
@Composable
fun sponsorLabel(category: String): String = stringResource(
    when (category) {
        "sponsor" -> R.string.sb_sponsor
        "selfpromo" -> R.string.sb_selfpromo
        "interaction" -> R.string.sb_interaction
        "intro" -> R.string.sb_intro
        "outro" -> R.string.sb_outro
        "preview" -> R.string.sb_preview
        "music_offtopic" -> R.string.sb_music_offtopic
        else -> R.string.sb_filler
    },
)
