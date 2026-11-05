package app.mikeneko.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Combien de colonnes de [minWidth] tiennent dans la largeur de l'écran (moins le dock en colonne en
 * paysage). En portrait, une seule : les listes restent comme avant.
 */
@Composable
fun columnsFor(minWidth: Dp): Int {
    val config = LocalConfiguration.current
    val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val width = config.screenWidthDp.dp - 40.dp - if (landscape) DOCK_RAIL_SPACE else 0.dp
    return (width / minWidth).toInt().coerceAtLeast(1)
}

/**
 * Comme `items()`, mais rangé en [columns] colonnes (une rangée par élément de la liste paresseuse).
 * Avec une seule colonne, chaque élément reçoit exactement le modificateur d'avant (animation +
 * marges) : rien ne change en portrait.
 */
fun <T> LazyListScope.adaptiveItems(
    list: List<T>,
    columns: Int,
    key: (T) -> Any,
    horizontalPadding: Dp = 20.dp,
    spacing: Dp = 16.dp,
    content: @Composable (T, Modifier) -> Unit,
) {
    if (columns <= 1) {
        items(list, key = key) { item -> content(item, Modifier.animateItem().padding(horizontal = horizontalPadding)) }
        return
    }
    items(list.chunked(columns), key = { key(it.first()) }) { row ->
        Row(Modifier.animateItem().padding(horizontal = horizontalPadding), horizontalArrangement = Arrangement.spacedBy(spacing)) {
            row.forEach { item -> content(item, Modifier.weight(1f)) }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
