package app.mikeneko.ui.components

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mikeneko.R
import app.mikeneko.player.PlayerHolder
import app.mikeneko.player.SettingsPage
import app.mikeneko.player.SleepTimer
import app.mikeneko.player.StableVolume
import app.mikeneko.settings.AppLocale
import app.mikeneko.ui.glass.Backdrop
import app.mikeneko.ui.glass.LiquidGlass
import app.mikeneko.ui.icons.MkIcons
import app.mikeneko.ui.theme.Pelage
import kotlinx.coroutines.delay

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
private val TIMER_MINUTES = listOf(5, 10, 15, 30, 45, 60)
private val Secondary = Color(0xFFCFC6BD)

/** Le mode ambiant a besoin du flou natif (Android 12+). */
val ambientSupported: Boolean get() = Build.VERSION.SDK_INT >= 31

/**
 * Le menu ⚙ du lecteur, en liquid glass, qui monte du bas de l'écran.
 * Ouvert/fermé via [PlayerHolder.settingsPage]. [audioOnly] : seulement ce qui a du sens
 * sans image (vitesse, volume stable, minuteur), pour le mode radio.
 */
@Composable
fun PlayerSettingsSheet(backdrop: Backdrop, audioOnly: Boolean = false) {
    val page by PlayerHolder.settingsPage.collectAsState()
    // Garde la dernière page affichée pendant l'animation de fermeture
    var shown by remember { mutableStateOf(SettingsPage.Menu) }
    page?.let { shown = it }
    // Ouvert directement sur une sous-page (ex. minuteur depuis la radio) : « retour » ferme
    var openedOn by remember { mutableStateOf<SettingsPage?>(null) }
    LaunchedEffect(page == null) { openedOn = page }

    fun close() {
        PlayerHolder.settingsPage.value = null
    }

    fun back() {
        PlayerHolder.settingsPage.value = if (page != SettingsPage.Menu && openedOn == SettingsPage.Menu) SettingsPage.Menu else null
    }

    BackHandler(enabled = page != null) { back() }

    // Glisser vers le bas ferme le panneau (3.1.2), comme les menus de la musique : depuis la poignée et
    // tout le panneau, ou en continuant de tirer quand la liste est déjà en haut
    val scope = rememberCoroutineScope()
    val drag = remember { androidx.compose.animation.core.Animatable(0f) }
    val closeAt = with(androidx.compose.ui.platform.LocalDensity.current) { 120.dp.toPx() }
    LaunchedEffect(page != null) { if (page != null) drag.snapTo(0f) }
    fun release(velocity: Float) {
        scope.launch {
            if (drag.value > closeAt || (velocity > 1_500f && drag.value > 0f)) close()
            else drag.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 500f))
        }
    }
    val pullDown = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            // Descendu : il remonte d'abord avant que la liste ne défile
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (available.y < 0f && drag.value > 0f) {
                    val used = maxOf(available.y, -drag.value)
                    scope.launch { drag.snapTo(drag.value + used) }
                    return androidx.compose.ui.geometry.Offset(0f, used)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
            // La liste est en haut (ou ne défile pas) et on tire encore : c'est le panneau qui descend
            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (available.y > 0f && source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) {
                    scope.launch { drag.snapTo(drag.value + available.y) }
                    return androidx.compose.ui.geometry.Offset(0f, available.y)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (drag.value <= 0f) return androidx.compose.ui.unit.Velocity.Zero
                release(available.y)
                return available
            }
        }
    }
    val handle = Modifier.pointerInput(Unit) {
        val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
        detectVerticalDragGestures(
            onDragStart = { tracker.resetTracking() },
            onDragEnd = { release(tracker.calculateVelocity().y) },
            onDragCancel = { release(0f) },
        ) { change, amount ->
            tracker.addPosition(change.uptimeMillis, change.position)
            scope.launch { drag.snapTo((drag.value + amount).coerceAtLeast(0f)) }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxSheetHeight = maxHeight * 0.88f

        AnimatedVisibility(page != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(interactionSource = null, indication = null, onClickLabel = stringResource(R.string.settings_close), onClick = ::close),
            )
        }

        AnimatedVisibility(
            visible = page != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
            // La fermeture se voit (3.1.2) : avant, le fondu (200) finissait avant le glissé (260) et, à
            // vitesse d'animation ×2, le panneau semblait disparaître d'un coup. Il redescend d'abord,
            // et ne s'efface qu'à la fin
            exit = slideOutVertically(tween(360, easing = FastOutLinearInEasing)) { it } + fadeOut(tween(160, delayMillis = 200)),
        ) {
            LiquidGlass(
                backdrop = backdrop,
                modifier = Modifier
                    .graphicsLayer { translationY = drag.value }
                    .then(handle)
                    .nestedScroll(pullDown)
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(10.dp)
                    .heightIn(max = maxSheetHeight),
                cornerRadius = 30.dp,
                bezel = 26.dp,
                refraction = 26.dp,
                // Dépoli + sombre : le texte du menu reste lisible sur n'importe quelle vidéo
                tint = Color(0xC71A1613),
                frost = 28.dp,
            ) {
                AnimatedContent(
                    targetState = shown,
                    transitionSpec = {
                        val forward = targetState != SettingsPage.Menu
                        (slideInHorizontally(tween(240)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(240)))
                            .togetherWith(slideOutHorizontally(tween(240)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(160)))
                    },
                    label = "réglages",
                ) { current ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                    ) {
                        Box(
                            Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp, bottom = 6.dp)
                                .size(36.dp, 4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)),
                        )
                        when (current) {
                            SettingsPage.Menu -> MenuPage(audioOnly)
                            SettingsPage.Qualite -> QualityPage(::back)
                            SettingsPage.Vitesse -> SpeedPage(::back)
                            SettingsPage.SousTitres -> CaptionsPage(::back)
                            SettingsPage.Minuteur -> TimerPage(::back)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- pages

@Composable
private fun MenuPage(audioOnly: Boolean) {
    val context = LocalContext.current
    val now by PlayerHolder.nowPlaying.collectAsState()
    val qualities by PlayerHolder.qualities.collectAsState()
    val quality by PlayerHolder.quality.collectAsState()
    val playing by PlayerHolder.playingQuality.collectAsState()
    val speed by PlayerHolder.speed.collectAsState()
    val tracks by PlayerHolder.captionTracks.collectAsState()
    val caption by PlayerHolder.caption.collectAsState()
    val loop by PlayerHolder.loop.collectAsState()
    val ambient by PlayerHolder.ambient.collectAsState()
    val stable by PlayerHolder.stableVolume.collectAsState()
    val live = now?.live == true
    val video = !audioOnly && now?.audioOnly == false

    fun go(page: SettingsPage) {
        PlayerHolder.settingsPage.value = page
    }

    if (video && qualities.isNotEmpty()) {
        NavRow(MkIcons.Quality, stringResource(R.string.quality), qualityLabel(quality, playing)) { go(SettingsPage.Qualite) }
    }
    if (!live) {
        NavRow(MkIcons.Speed, stringResource(R.string.playback_speed), speedLabel(speed)) { go(SettingsPage.Vitesse) }
    }
    if (video && !live) {
        NavRow(
            MkIcons.Captions,
            stringResource(R.string.subtitles),
            when {
                caption != null -> caption!!.label
                tracks.isEmpty() -> stringResource(R.string.subtitles_none_video)
                else -> stringResource(R.string.off_plural)
            },
            enabled = tracks.isNotEmpty(),
        ) { go(SettingsPage.SousTitres) }
    }
    if (!live) {
        ToggleRow(MkIcons.Loop, stringResource(R.string.loop), null, loop) { PlayerHolder.setLoop(it) }
    }
    if (video && !live) {
        val autoplay by PlayerHolder.autoplay.collectAsState()
        ToggleRow(MkIcons.Next, stringResource(R.string.autoplay), stringResource(R.string.autoplay_text), autoplay) { PlayerHolder.setAutoplay(it) }
    }
    if (video && ambientSupported) {
        ToggleRow(MkIcons.Ambient, stringResource(R.string.ambient_mode), stringResource(R.string.ambient_mode_text), ambient) { PlayerHolder.setAmbient(it) }
    }
    if (StableVolume.supported && now?.audioOnly != true) { // pas pour la musique (voir PlayerHolder)
        ToggleRow(MkIcons.Waves, stringResource(R.string.stable_volume), stringResource(R.string.stable_volume_text), stable) { on ->
            if (!PlayerHolder.setStableVolume(on)) {
                Toast.makeText(context, context.getString(R.string.stable_volume_refused), Toast.LENGTH_SHORT).show()
            }
        }
    }
    NavRow(MkIcons.Moon, stringResource(R.string.sleep_timer), timerLabel()) { go(SettingsPage.Minuteur) }
}

@Composable
private fun QualityPage(onBack: () -> Unit) {
    val qualities by PlayerHolder.qualities.collectAsState()
    val quality by PlayerHolder.quality.collectAsState()
    val playing by PlayerHolder.playingQuality.collectAsState()
    PageHeader(stringResource(R.string.quality), onBack)
    OptionRow(if (playing > 0) stringResource(R.string.quality_auto_with, playing) else stringResource(R.string.quality_auto), null, quality == null) { PlayerHolder.setQuality(null) }
    qualities.forEach { q ->
        OptionRow("${q}p", when { q >= 4320 -> "8K"; q >= 2160 -> "4K"; q >= 1440 -> "QHD"; q >= 1080 -> "FHD"; q >= 720 -> "HD"; else -> null }, quality == q || (quality != null && quality !in qualities && playing == q)) { PlayerHolder.setQuality(q) }
    }
}

@Composable
private fun SpeedPage(onBack: () -> Unit) {
    val speed by PlayerHolder.speed.collectAsState()
    PageHeader(stringResource(R.string.playback_speed), onBack)
    SPEEDS.forEach { s -> OptionRow(speedLabel(s), null, speed == s) { PlayerHolder.setSpeed(s) } }
}

@Composable
private fun CaptionsPage(onBack: () -> Unit) {
    val tracks by PlayerHolder.captionTracks.collectAsState()
    val caption by PlayerHolder.caption.collectAsState()
    PageHeader(stringResource(R.string.subtitles), onBack)
    OptionRow(stringResource(R.string.off_plural), null, caption == null) { PlayerHolder.setCaption(null) }
    tracks.forEach { t -> OptionRow(t.label, null, caption?.id == t.id) { PlayerHolder.setCaption(t) } }
}

@Composable
private fun TimerPage(onBack: () -> Unit) {
    val timer by PlayerHolder.sleepTimer.collectAsState()
    val now by PlayerHolder.nowPlaying.collectAsState()
    PageHeader(stringResource(R.string.sleep_timer), onBack)
    if (timer is SleepTimer.At) {
        Text(
            stringResource(R.string.sleep_stops_in, timerLabel()),
            Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
            color = Pelage.RouxClair,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
    OptionRow(stringResource(R.string.off), null, timer == SleepTimer.Off) { PlayerHolder.setSleepTimer(null) }
    TIMER_MINUTES.forEach { m ->
        OptionRow(stringResource(R.string.minutes_n, m), null, (timer as? SleepTimer.At)?.minutes == m) { PlayerHolder.setSleepTimer(m) }
    }
    if (now?.live != true) {
        OptionRow(stringResource(R.string.end_of_video), null, timer == SleepTimer.EndOfVideo) { PlayerHolder.setSleepAtEndOfVideo() }
    }
}

// ---------------------------------------------------------------- libellés

@Composable
private fun qualityLabel(quality: Int?, playing: Int): String = when {
    // Une qualité voulue plus haute que ce que la vidéo propose : on montre celle qui joue vraiment (2.21.0)
    quality != null -> "${if (playing > 0) playing else quality}p"
    playing > 0 -> stringResource(R.string.quality_auto_with, playing)
    else -> stringResource(R.string.quality_auto)
}

@Composable
private fun speedLabel(speed: Float): String =
    if (speed == 1f) stringResource(R.string.speed_normal)
    else speed.toString().removeSuffix(".0").let { if (AppLocale.language() == "fr") it.replace('.', ',') else it } + "×"

/** « 12 min », « 45 s », « Fin de la vidéo » ou « Désactivé », rafraîchi chaque seconde. */
@Composable
private fun timerLabel(): String {
    val timer by PlayerHolder.sleepTimer.collectAsState()
    var clock by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(timer) {
        while (timer is SleepTimer.At) {
            clock = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    return when (val t = timer) {
        SleepTimer.Off -> stringResource(R.string.off)
        SleepTimer.EndOfVideo -> stringResource(R.string.end_of_video)
        is SleepTimer.At -> {
            val left = ((t.elapsedRealtime - clock) / 1000).coerceAtLeast(0)
            if (left >= 60) "${(left + 59) / 60} min" else "$left s"
        }
    }
}

// ---------------------------------------------------------------- lignes

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = stringResource(R.string.back), onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(MkIcons.ChevronLeft, stringResource(R.string.back), tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Text(
            title,
            Modifier.padding(start = 4.dp).semantics { heading() },
            color = Pelage.Texte,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, value: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (enabled) Color.White else Secondary.copy(alpha = 0.5f), modifier = Modifier.size(22.dp))
        Text(
            title,
            Modifier.padding(start = 16.dp).weight(1f),
            color = if (enabled) Pelage.Texte else Secondary.copy(alpha = 0.6f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(value, Modifier.padding(start = 12.dp).widthIn(max = 180.dp), color = Secondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (enabled) Icon(MkIcons.ChevronRight, null, tint = Secondary, modifier = Modifier.padding(start = 4.dp).size(18.dp))
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 22.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Column(Modifier.padding(start = 16.dp, end = 12.dp).weight(1f)) {
            Text(title, color = Pelage.Texte, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, color = Secondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = null, // toute la ligne est cliquable
            colors = SwitchDefaults.colors(
                checkedTrackColor = Pelage.Roux,
                checkedThumbColor = Pelage.Creme,
                checkedBorderColor = Pelage.Roux,
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                uncheckedThumbColor = Color.White.copy(alpha = 0.75f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.3f),
            ),
        )
    }
}

@Composable
private fun OptionRow(title: String, badge: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
            if (selected) Icon(MkIcons.Check, stringResource(R.string.selected), tint = Pelage.RouxClair, modifier = Modifier.size(20.dp))
        }
        Text(
            title,
            Modifier.padding(start = 16.dp),
            color = if (selected) Pelage.RouxClair else Pelage.Texte,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
        )
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                badge,
                Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp),
                color = Secondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}
