package app.mikeneko.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.mikeneko.ui.theme.Pelage
import coil3.compose.AsyncImage

/**
 * Une image en grand (3.0.1) : l'avatar d'une chaîne, touché sur sa page, dans un carré aux coins
 * arrondis. Un tap (dessus ou à côté) ou retour la referme.
 */
@Composable
fun ImageViewer(url: String, onDismiss: () -> Unit) {
    AnimatedDialog(onDismissRequest = onDismiss) { close ->
        AsyncImage(
            model = largeImage(url),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(32.dp))
                .background(Pelage.Coussin)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() },
        )
    }
}

/** Les images de YouTube (avatars : « …=s88-c-k… ») existent en plus grand : on demande 1024 px. */
fun largeImage(url: String): String = url.replace(Regex("=s\\d+(-)"), "=s1024$1").replace(Regex("=s\\d+$"), "=s1024")
