package app.mikeneko.ui.components

import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.data.VideoItem
import app.mikeneko.data.WatchPositions
import app.mikeneko.data.formatDuration
import app.mikeneko.data.formatViews
import app.mikeneko.data.uploadedLabel
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.glass.LiquidGlass
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.GlassTextShadow
import app.mikeneko.ui.theme.Pelage
import app.mikeneko.ui.theme.clay
import app.mikeneko.ui.theme.jellyClickable
import app.mikeneko.ui.theme.plopOnSelect
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Rond en liquid glass avec une icône blanche. */
@Composable
fun GlassCircleButton(
    backdrop: Backdrop,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 50.dp,
    tint: Color = Color(0x40100D0B),
    iconTint: Color = Color.White,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    LiquidGlass(
        backdrop = backdrop,
        // Grisé : le tap est absorbé (sinon il traverse jusqu'à la vidéo et masque les commandes)
        modifier = modifier.size(size).alpha(if (enabled) 1f else 0.35f)
            .then(if (enabled) Modifier else Modifier.pointerInput(Unit) { detectTapGestures { } }),
        cornerRadius = size / 2,
        bezel = size * 0.32f,
        refraction = size * 0.32f,
        tint = tint,
        onClick = if (enabled) onClick else null,
    ) {
        Icon(icon, contentDescription, tint = iconTint, modifier = Modifier.align(Alignment.Center).size(size * 0.46f))
    }
}

/**
 * Une capsule = un bouton Material 3 (2.9.0, avant en pâte à modeler) : choisie, un bouton plein de
 * la couleur d'accent ; sinon un bouton tonal.
 */
@Composable
fun Capsule(text: String, modifier: Modifier = Modifier, selected: Boolean = false, icon: ImageVector? = null, onClick: () -> Unit) {
    val padding = if (icon != null) ButtonDefaults.ButtonWithIconContentPadding else ButtonDefaults.ContentPadding
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        }
        Text(text, fontSize = 14.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold, maxLines = 1)
    }
    if (selected) Button(onClick, modifier, contentPadding = padding, content = content)
    else FilledTonalButton(onClick, modifier, contentPadding = padding, content = content)
}

/**
 * Petite étiquette (durée, EN DIRECT…) : une surface Material 3 plate aux coins de 8 dp, dans les
 * couleurs du thème (2.11.11 ; avant, une pastille « pâte à modeler » avec son ombre).
 */
@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.secondaryContainer,
    color: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    androidx.compose.material3.Surface(modifier, shape = RoundedCornerShape(8.dp), color = background, contentColor = color) {
        Text(
            text,
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun VideoThumbnail(video: VideoItem, modifier: Modifier = Modifier, corner: Dp = 22.dp) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .shadow(10.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Pelage.Coussin),
    ) {
        AsyncImage(
            model = video.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        // Un léger volume sur l'image (reflet en haut, lisière claire) : la miniature a l'air moulée
        Box(
            Modifier.matchParentSize().drawWithContent {
                drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.16f), 0.3f to Color.Transparent))
                drawRect(Brush.verticalGradient(0.7f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.18f)))
                drawOutline(
                    shape.createOutline(size, layoutDirection, this),
                    Brush.verticalGradient(0f to Color.White.copy(alpha = 0.4f), 0.4f to Color.White.copy(alpha = 0.04f)),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            },
        )
        when {
            video.isLive -> Tag(stringResource(R.string.live_badge), Modifier.align(Alignment.BottomEnd).padding(10.dp), background = Pelage.Roux)
            video.durationSec > 0 -> Tag(formatDuration(video.durationSec), Modifier.align(Alignment.BottomEnd).padding(10.dp))
        }
        WatchProgressBar(video.url, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * La barre sous une miniature : jusqu'où on a regardé la vidéo (parmi les 10 dernières commencées,
 * voir [app.mikeneko.data.WatchPositions]). Rien si elle n'est pas commencée. À poser en bas, sur
 * toute la largeur, dans la miniature (ses coins arrondis la découpent).
 */
@Composable
fun WatchProgressBar(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val progress by remember { WatchPositions.progress(context) }.collectAsState()
    val id = remember(url) { app.mikeneko.data.videoIdOf(url) }
    val fraction = id?.let { progress[it] } ?: return
    Box(modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.35f))) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Pelage.Roux))
    }
}

/** [onMore] : ouvre le menu de la vidéo (bouton ⋮ ou appui long). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCard(video: VideoItem, modifier: Modifier = Modifier, onMore: (() -> Unit)? = null, onClick: () -> Unit) {
    val moreLabel = stringResource(R.string.more_options)
    // Au tap, la miniature s'envole vers le lecteur (VideoMorph) : on voit tout de suite quelle vidéo
    // a été touchée, même si la page change aussitôt. Elle est donc enregistrée dans un calque.
    val thumbnail = rememberGraphicsLayer()
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf(false) }
    fun open() {
        if (opening) return
        opening = true
        scope.launch {
            val image = withTimeoutOrNull(150) { runCatching { thumbnail.toImageBitmap() }.getOrNull() }
            VideoMorph.startWith(image, placed[0]?.takeIf { it.isAttached }?.boundsInRoot())
            onClick()
            opening = false
        }
    }
    Column(
        // Toute la carte s'écrase un peu sous le doigt (moins qu'un bouton : elle est grande)
        modifier.jellyClickable(role = null, strength = 0.35f, onLongClick = onMore, onLongClickLabel = if (onMore != null) moreLabel else null, onClick = ::open),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VideoThumbnail(
            video,
            Modifier
                .fillMaxWidth()
                .onPlaced { placed[0] = it }
                .drawWithContent {
                    thumbnail.record { this@drawWithContent.drawContent() }
                    drawLayer(thumbnail)
                },
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(video.title, Modifier.weight(1f), color = Pelage.Texte, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (onMore != null) {
                    Box(
                        Modifier.padding(start = 4.dp).size(36.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = moreLabel, onClick = onMore),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(MkIcons.More, moreLabel, tint = Pelage.Gris, modifier = Modifier.size(20.dp))
                    }
                }
            }
            if (video.reason != null) {
                Text(video.reason, color = Pelage.RouxClair, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                listOfNotNull(video.channel.ifEmpty { null }, formatViews(video.views), video.uploadedLabel()).joinToString(" · "),
                color = Pelage.Gris,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** La tête de Mikeneko en sticker. */
@Composable
fun Mascot(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Image(painterResource(R.drawable.ic_mikeneko), contentDescription = null, modifier = modifier.size(size))
}

/**
 * Le chargement : le chat qui rebondit, s'écrase en touchant le sol et s'étire en remontant,
 * avec son ombre. S'adapte à la place (bouton, ligne, page entière).
 */
@Composable
fun Loading(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val small = maxHeight < 90.dp
        val cat = if (small) minOf(maxHeight, maxWidth) * 0.55f else 44.dp
        val jump = if (small) cat * 0.4f else 30.dp
        Box(if (small) Modifier else Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
            BouncingCat(cat, jump)
        }
    }
}

@Composable
private fun BouncingCat(size: Dp, jump: Dp) {
    val bounce = rememberInfiniteTransition(label = "chat")
    val t by bounce.animateFloat(0f, 1f, infiniteRepeatable(tween(760, easing = LinearEasing)), label = "saut")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Mascot(
            Modifier.graphicsLayer {
                val height = 4f * t * (1f - t) // une parabole : 0 au sol, 1 en haut
                translationY = -height * jump.toPx()
                // Au sol (début et fin du saut) : écrasé ; en l'air : un peu étiré
                val ground = (1f - height * 3f).coerceIn(0f, 1f)
                scaleX = 1f + 0.16f * ground - 0.04f * height
                scaleY = 1f - 0.2f * ground + 0.06f * height
                transformOrigin = TransformOrigin(0.5f, 1f)
            },
            size = size,
        )
        // L'ombre au sol (claire : le fond est sombre), plus petite et plus pâle quand le chat est en l'air
        Box(
            Modifier
                .padding(top = 3.dp)
                .size(size * 0.72f, size * 0.13f)
                .graphicsLayer {
                    val height = 4f * t * (1f - t)
                    scaleX = 1f - 0.45f * height
                    alpha = 0.3f - 0.18f * height
                }
                .background(Pelage.Creme, CircleShape),
        )
    }
}

@Composable
fun EmptyCat(title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Mascot(size = 72.dp)
        Text(title, color = Pelage.Texte, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        Text(text, color = Pelage.Gris, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

/**
 * Une oreille de chat (triangle [base] → [tip] → [inner]). [round] (0 à 0,5) arrondit les coins :
 * 0 = pointue, 0,3 = bien ronde et moelleuse.
 */
fun DrawScope.drawEar(base: Offset, tip: Offset, inner: Offset, color: Color, round: Float = 0f) {
    val corners = listOf(base, tip, inner)
    val path = Path().apply {
        if (round <= 0f) {
            moveTo(base.x, base.y)
            lineTo(tip.x, tip.y)
            lineTo(inner.x, inner.y)
        } else {
            // Chaque coin est remplacé par une courbe entre deux points pris sur ses côtés
            corners.indices.forEach { i ->
                val corner = corners[i]
                val before = corners[(i + 2) % 3]
                val after = corners[(i + 1) % 3]
                val start = corner + (before - corner) * round
                val end = corner + (after - corner) * round
                if (i == 0) moveTo(start.x, start.y) else lineTo(start.x, start.y)
                quadraticTo(corner.x, corner.y, end.x, end.y)
            }
        }
        close()
    }
    drawPath(path, Color.White, style = Stroke(width = 6.dp.toPx(), join = StrokeJoin.Round))
    drawPath(path, color)
    drawPath(path, Pelage.Contour, style = Stroke(width = 1.6.dp.toPx(), join = StrokeJoin.Round))
}

/** Une vidéo en ligne compacte (miniature à gauche) : pour « À suivre » sous le lecteur. */
@Composable
fun CompactVideoRow(video: VideoItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VideoThumbnail(video, Modifier.width(160.dp), corner = 14.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(video.title, color = Pelage.Texte, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(video.channel, color = Pelage.Gris, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(formatViews(video.views), video.uploadedLabel()).joinToString(" · "),
                color = Pelage.Gris,
                fontSize = 12.sp,
                maxLines = 2, // la colonne est étroite : la date passe à la ligne plutôt que d'être coupée
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Une capsule d'une rangée « groupe connecté » (Material 3 Expressive, 2.10.0) : les capsules d'une
 * rangée se touchent presque (3 dp), les bouts du groupe sont arrondis et l'intérieur a de petits
 * coins ; celle qui est choisie devient une pilule entière, pleine de la couleur d'accent.
 * Remplace les capsules en verre (GlassChip) et les rangées de [Capsule] servant de choix.
 */
@Composable
fun ConnectedChip(
    text: String,
    selected: Boolean,
    first: Boolean,
    last: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    /** false : choisie, elle garde ses petits coins intérieurs (bouton partagé : S'abonner | cloche). */
    pillWhenSelected: Boolean = true,
    /** Pour une capsule sans texte (icône seule). */
    description: String? = null,
    /** Marge de côté (moins dans une rangée qui partage la largeur, pour que le texte tienne). */
    sidePadding: Dp = 22.dp,
    onClick: () -> Unit,
) {
    val round = CONNECTED_HEIGHT / 2
    val pill = selected && pillWhenSelected
    val start by animateDpAsState(if (pill || first) round else CONNECTED_INNER, spring(stiffness = Spring.StiffnessMediumLow), label = "coin début")
    val end by animateDpAsState(if (pill || last) round else CONNECTED_INNER, spring(stiffness = Spring.StiffnessMediumLow), label = "coin fin")
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) colors.primary else colors.secondaryContainer, label = "capsule")
    val content = if (selected) colors.onPrimary else colors.onSecondaryContainer
    Surface(
        onClick = onClick,
        modifier = modifier.height(CONNECTED_HEIGHT).semantics { this.selected = selected },
        shape = RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end),
        color = container,
        contentColor = content,
    ) {
        Row(
            Modifier.padding(horizontal = if (text.isEmpty()) 14.dp else sidePadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (icon != null) Icon(icon, description, modifier = Modifier.size(18.dp))
            if (text.isNotEmpty()) Text(text, fontSize = 14.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold, maxLines = 1)
        }
    }
}

val CONNECTED_HEIGHT = 44.dp
private val CONNECTED_INNER = 6.dp

/** L'espace entre deux capsules d'un groupe connecté. */
val CONNECTED_GAP = 3.dp
