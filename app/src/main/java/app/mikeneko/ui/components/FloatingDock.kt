package app.mikeneko.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import app.mikeneko.ui.theme.clay
import app.mikeneko.ui.theme.jellyClickable
import app.mikeneko.ui.theme.plopOnSelect
import kotlin.math.abs
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import app.mikeneko.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.ui.Route
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.glass.LiquidGlass
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.GlassTextShadow
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.Rounded

/** Les onglets du dock, dans l'ordre. */
private val TABS = listOf(Route.Accueil, Route.Abonnements, Route.Radio, Route.Bibliotheque)

/** Largeur du dock en colonne (paysage). */
val DOCK_RAIL_WIDTH = 78.dp

/** Place laissée au contenu à gauche en paysage : marge + dock en colonne + écart. */
val DOCK_RAIL_SPACE = 12.dp + DOCK_RAIL_WIDTH + 6.dp

/**
 * Le dock flottant en liquid glass, toujours affiché. L'onglet actif est
 * marqué par une goutte en pâte à modeler qui glisse d'un onglet à l'autre : elle s'étire pendant
 * le trajet et tremblote en arrivant.
 */
@Composable
fun FloatingDock(
    backdrop: Backdrop,
    current: Route,
    visible: Boolean,
    modifier: Modifier = Modifier,
    /** Paysage : une colonne sur le côté gauche (toute la hauteur reste au contenu). */
    vertical: Boolean = false,
    onSelect: (Route) -> Unit,
) {
    if (vertical) {
        DockRail(backdrop, current, visible, modifier, onSelect)
        return
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow)) { it * 2 },
        exit = slideOutVertically(tween(300)) { it * 2 },
    ) {
        LiquidGlass(backdrop, Modifier.fillMaxWidth().height(68.dp), cornerRadius = 34.dp, bezel = 24.dp, refraction = 24.dp) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(6.dp)) {
                val gap = 4.dp
                val itemWidth = (maxWidth - gap * (TABS.size - 1)) / TABS.size
                val index = TABS.indexOf(current).coerceAtLeast(0)
                // La position de la goutte, en onglets (0, 1, 2…) ; sa vitesse l'étire
                val position = remember { Animatable(index.toFloat()) }
                // Raideur 197 : 15 % plus lent que 260 (la durée d'un ressort varie comme 1/√raideur)
                LaunchedEffect(index) { position.animateTo(index.toFloat(), spring(dampingRatio = 0.55f, stiffness = 197f)) }
                Box(
                    Modifier
                        .size(itemWidth, maxHeight)
                        .graphicsLayer {
                            translationX = position.value * (itemWidth + gap).toPx()
                            val stretch = (abs(position.velocity) * 0.09f).coerceAtMost(0.32f)
                            scaleX = 1f + stretch
                            scaleY = 1f - stretch * 0.55f
                        }
                        .clay(Pelage.Creme.copy(alpha = 0.92f), RoundedCornerShape(28.dp), elevation = 4.dp),
                )
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    DockItems(current, Modifier.weight(1f).fillMaxHeight(), onSelect)
                }
            }
        }
    }
}

/** Les quatre onglets, avec le modificateur de place donné par la rangée ou la colonne. */
@Composable
private fun DockItems(current: Route, itemModifier: Modifier, onSelect: (Route) -> Unit) {
    DockItem(stringResource(R.string.tab_home), MkIcons.Home, current == Route.Accueil, itemModifier) { onSelect(Route.Accueil) }
    DockItem(stringResource(R.string.tab_subscriptions), MkIcons.Subscriptions, current == Route.Abonnements, itemModifier) { onSelect(Route.Abonnements) }
    DockItem(stringResource(R.string.tab_radio), MkIcons.Radio, current == Route.Radio, itemModifier) { onSelect(Route.Radio) }
    DockItem(stringResource(R.string.tab_library), MkIcons.Library, current == Route.Bibliotheque, itemModifier) { onSelect(Route.Bibliotheque) }
}

/** Le dock en colonne (paysage) : même verre, même goutte, qui glisse de haut en bas. */
@Composable
private fun DockRail(backdrop: Backdrop, current: Route, visible: Boolean, modifier: Modifier, onSelect: (Route) -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInHorizontally(spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow)) { -it * 2 },
        exit = slideOutHorizontally(tween(300)) { -it * 2 },
    ) {
        LiquidGlass(backdrop, Modifier.width(DOCK_RAIL_WIDTH).height(292.dp), cornerRadius = 34.dp, bezel = 24.dp, refraction = 24.dp) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(6.dp)) {
                val gap = 4.dp
                val itemHeight = (maxHeight - gap * (TABS.size - 1)) / TABS.size
                val index = TABS.indexOf(current).coerceAtLeast(0)
                val position = remember { Animatable(index.toFloat()) }
                LaunchedEffect(index) { position.animateTo(index.toFloat(), spring(dampingRatio = 0.55f, stiffness = 197f)) }
                Box(
                    Modifier
                        .size(maxWidth, itemHeight)
                        .graphicsLayer {
                            translationY = position.value * (itemHeight + gap).toPx()
                            val stretch = (abs(position.velocity) * 0.09f).coerceAtMost(0.32f)
                            scaleY = 1f + stretch
                            scaleX = 1f - stretch * 0.55f
                        }
                        .clay(Pelage.Creme.copy(alpha = 0.92f), RoundedCornerShape(28.dp), elevation = 4.dp),
                )
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    DockItems(current, Modifier.weight(1f).fillMaxWidth(), onSelect)
                }
            }
        }
    }
}

@Composable
private fun DockItem(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color by animateColorAsState(if (selected) Pelage.Encre else Pelage.Texte, tween(220), label = "onglet")
    Column(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .jellyClickable(role = Role.Tab, onClick = onClick)
            .plopOnSelect(selected),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        // Le texte rétrécit tout seul pour tenir dans sa bulle (« Abonnements », grandes polices…)
        BasicText(
            label,
            modifier = Modifier.padding(horizontal = 6.dp),
            style = TextStyle(
                color = color,
                fontFamily = Rounded,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                shadow = if (selected) null else GlassTextShadow,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
        )
    }
}
