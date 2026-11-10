package app.mikeneko.ui.theme

import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest

/*
 * Le style « pâte à chat » : des formes moelleuses en pâte à modeler (claymorphisme) qui
 * réagissent comme des gouttes d'eau (elles s'écrasent sous le doigt, puis rebondissent).
 */

private fun Color.lighter(amount: Float) = lerp(this, Color.White, amount)
private fun Color.darker(amount: Float) = lerp(this, Color.Black, amount)

/**
 * Une forme en pâte à modeler : volume (plus clair en haut, plus sombre en bas), reflet doux,
 * lisière claire sur le bord du haut et ombre portée teintée de sa couleur (pas grise).
 * Tout est dessiné, sans recomposition : on peut en mettre partout dans les listes.
 */
fun Modifier.clay(color: Color, shape: Shape = CircleShape, elevation: Dp = 7.dp): Modifier = this
    .shadow(elevation, shape, ambientColor = color.darker(0.55f), spotColor = color.darker(0.45f))
    .clip(shape)
    .drawBehind {
        // Le corps, bombé
        drawRect(Brush.verticalGradient(0f to color.lighter(0.14f), 0.55f to color, 1f to color.darker(0.12f)))
        // Le reflet : une lumière douce qui tombe du haut
        drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.34f), 0.45f to Color.Transparent))
        // L'ombre interne du bas, qui donne l'épaisseur
        drawRect(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.13f)))
    }
    .drawWithContent {
        drawContent()
        // La lisière : un trait clair sur le haut du bord, qui s'efface vers le bas
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(
            outline,
            Brush.verticalGradient(0f to Color.White.copy(alpha = 0.7f), 0.5f to Color.White.copy(alpha = 0.05f), 1f to Color.Transparent),
            style = Stroke(width = 1.6.dp.toPx()),
        )
    }

/**
 * Un clic « goutte d'eau » : sous le doigt, l'élément s'écrase vers le bas (plus large, moins
 * haut), puis rebondit en s'étirant et tremblote avant de se poser. Même un tap très court se voit.
 * [strength] : 1 pour un bouton, moins pour les grands éléments (cartes).
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.jellyClickable(
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    strength: Float = 1f,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val squash = remember { Animatable(0f) } // 0 au repos, 1 écrasé ; en dessous de 0 : étiré (rebond)
    LaunchedEffect(interaction) {
        interaction.interactions.collectLatest { event ->
            when (event) {
                is PressInteraction.Press -> squash.animateTo(1f, spring(stiffness = Spring.StiffnessHigh))
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    if (squash.value < 0.7f) squash.animateTo(1f, tween(70))
                    squash.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 420f))
                }
            }
        }
    }
    this
        .graphicsLayer {
            val s = squash.value * strength
            scaleX = 1f + 0.05f * s
            scaleY = 1f - 0.1f * s
            transformOrigin = TransformOrigin(0.5f, 0.85f) // s'écrase vers le « sol », comme une goutte
        }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            role = role,
            onClickLabel = onClickLabel,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

/**
 * Un petit « plop » quand [selected] devient vrai (capsule choisie, onglet…) : la forme se
 * gonfle d'un coup puis se pose en tremblotant.
 */
fun Modifier.plopOnSelect(selected: Boolean): Modifier = composed {
    val plop = remember { Animatable(0f) }
    val wasSelected = remember { booleanArrayOf(selected) } // pas de « plop » à l'affichage, seulement au choix
    LaunchedEffect(selected) {
        val changed = selected && !wasSelected[0]
        wasSelected[0] = selected
        if (changed) {
            plop.snapTo(1f)
            plop.animateTo(0f, spring(dampingRatio = 0.28f, stiffness = 300f))
        }
    }
    graphicsLayer {
        val p = plop.value
        scaleX = 1f + 0.08f * p
        scaleY = 1f - 0.06f * p
    }
}

/**
 * Le retour visuel de tout ce qui se touche (2.22.0) : sous le doigt, l'élément rétrécit un peu et
 * s'éclaire, puis reprend sa taille avec un petit rebond. Posé une fois pour toute l'appli
 * ([MikenekoTheme]) à la place de l'onde de Material, peu visible sur le fond sombre : lignes de
 * listes, boutons ronds, capsules, cartes… Les éléments qui ont déjà leur propre effet
 * ([jellyClickable], ou `indication = null`) gardent le leur.
 */
object PressIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressNode(interactionSource)
    override fun equals(other: Any?) = other === this
    override fun hashCode() = 0x6d696b65
}

private class PressNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private val press = Animatable(0f) // 0 au repos, 1 sous le doigt ; un peu sous 0 pendant le rebond

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collectLatest { event ->
                when (event) {
                    is PressInteraction.Press -> press.animateTo(1f, spring(stiffness = Spring.StiffnessHigh))
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        // Même un tap très court se voit
                        if (press.value < 0.7f) press.animateTo(1f, tween(70))
                        press.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = 420f))
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val p = press.value
        if (p == 0f) {
            drawContent()
            return
        }
        // Jamais plus de 5 dp de moins par côté : une ligne large ne « saute » pas, un petit bouton se voit
        val shrink = minOf(0.07f, 10.dp.toPx() / maxOf(size.width, size.height, 1f))
        val s = 1f - shrink * p
        scale(s, s) { this@draw.drawContent() }
        val light = (p * 0.1f).coerceIn(0f, 0.1f)
        if (light > 0f) scale(s, s) { drawRect(Color.White.copy(alpha = light)) }
    }
}
