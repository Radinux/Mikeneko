package app.mikeneko.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.data.Tastes
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.Rounded

/**
 * Les capsules des goûts (2.7.0) : une par thème, de la couleur d'accent quand elle est choisie.
 * Des puces Material 3 (2.9.0, avant en pâte à modeler) ; pas de verre dans la liste de l'accueil.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasteChips(selected: Set<String>, modifier: Modifier = Modifier, onToggle: (String) -> Unit) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Tastes.ALL.forEach { taste ->
            val on = taste.id in selected
            FilterChip(
                selected = on,
                onClick = { onToggle(taste.id) },
                label = { Text("${taste.emoji}  ${stringResource(taste.label)}", fontSize = 15.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

/**
 * La page des goûts, à la place du « Pour toi » d'un téléphone neuf : on touche quelques thèmes,
 * « C'est parti » lance le fil ; « Passer » le lance sans goûts (sujets au hasard).
 */
@Composable
fun TastesPicker(modifier: Modifier = Modifier, onDone: (Set<String>) -> Unit, onSkip: () -> Unit) {
    var picked by remember { mutableStateOf(emptySet<String>()) }
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.tastes_title),
            color = Pelage.Texte,
            fontFamily = Rounded,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.tastes_text),
            Modifier.padding(top = 8.dp, bottom = 22.dp),
            color = Pelage.Gris,
            fontFamily = Rounded,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        TasteChips(picked, Modifier.fillMaxWidth()) { id -> picked = if (id in picked) picked - id else picked + id }
        Row(Modifier.padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onSkip) { Text(stringResource(R.string.tastes_skip), fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            Button(onClick = { onDone(picked) }, enabled = picked.isNotEmpty()) {
                Text(stringResource(R.string.tastes_start), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
