package com.oto.stats.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlinx.coroutines.launch
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.util.lerp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.oto.stats.tracking.TrackingStatus
import com.oto.stats.tracking.engine.PlayState
import com.oto.stats.ui.components.Equalizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.colorControls
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.oto.stats.AppContainer
import com.oto.stats.ui.components.ComingSoon
import com.oto.stats.ui.components.NavGlyph
import com.oto.stats.ui.components.OVERSCAN_DP
import com.oto.stats.ui.components.NavIcon
import com.oto.stats.ui.debug.DebugActions
import com.oto.stats.ui.debug.DebugScreen
import com.oto.stats.ui.debug.DebugState
import com.oto.stats.ui.now.NowScreen
import com.oto.stats.ui.stats.StatsScreen
import com.oto.stats.ui.theme.OtoColors

/*
 * Glass recipe. Numbers measured from the open-source Spotify client the user pointed at
 * (kota-zo/myspoti, which in turn builds on Kyant's backdrop): a dark tint, saturation boosted
 * 1.6x, a SMALL blur so the refraction does the work, and lens depth plus chromatic aberration.
 * The page colour is painted far beyond the recording first, so the blur never samples
 * transparency (where it does, the sharp page shows through and the top edge looks like it
 * vanished). Only the numbers are borrowed, no code.
 */
private val GlassTint = Color(0xFF1C1C22).copy(alpha = 0.42f)
private val GlassHighlight = Highlight(width = 0.75.dp, alpha = 0.9f)
private val GlassShadow = Shadow(radius = 18.dp, color = Color.Black.copy(alpha = 0.22f))
private val PageFill: DrawScope.(DrawScope.() -> Unit) -> Unit = { drawPage ->
    drawRect(OtoColors.Canvas, topLeft = Offset(-20_000f, -20_000f), size = Size(40_000f, 40_000f))
    drawPage()
}

private enum class Tab(val label: String, val icon: NavIcon) {
    Now("Now", NavIcon.Now),
    Stats("Stats", NavIcon.Stats),
    History("History", NavIcon.History),
    Library("Library", NavIcon.Library),
}

/** App frame: content that scrolls under a floating Liquid Glass tab bar. */
@Composable
fun OtoShell(container: AppContainer, state: DebugState, actions: DebugActions) {
    var tabName by rememberSaveable { mutableStateOf(Tab.Now.name) }
    var showSetup by rememberSaveable { mutableStateOf(false) }
    val tab = Tab.valueOf(tabName)

    if (showSetup) {
        BackHandler { showSetup = false }
        Column(Modifier.fillMaxSize().background(OtoColors.Canvas)) {
            Box(Modifier.weight(1f)) { DebugScreen(container = container, state = state, actions = actions) }
            Text(
                "Back to Oto",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSetup = false }
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(vertical = 18.dp, horizontal = 20.dp),
                style = MaterialTheme.typography.titleSmall,
                color = OtoColors.Accent,
            )
        }
        return
    }

    val backdrop = rememberLayerBackdrop()
    val headerProgress = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(tab) { headerProgress.floatValue = 0f }
    val snapshot by TrackingStatus.nowPlaying.collectAsState()
    val needsAttention = !state.accessGranted || !state.notificationsGranted || !state.batteryExempt

    // Scrolling down shrinks the bottom bars to give the content room; scrolling up (or being at
    // the top of the page) brings them back.
    var minimized by remember { mutableStateOf(false) }
    LaunchedEffect(tab) { minimized = false }
    val chromeProgress = animateFloatAsState(
        targetValue = if (minimized) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "chrome",
    )
    // Clamped: the spring overshoots 1 slightly, and a negative size in layout crashes the app.
    val chrome: () -> Float = { chromeProgress.value.coerceIn(0f, 1f) }
    val scrollWatcher = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                when {
                    headerProgress.floatValue < 0.02f -> minimized = false
                    available.y < -6f -> minimized = true
                    available.y > 6f -> minimized = false
                }
                return Offset.Zero
            }
        }
    }

    Box(Modifier.fillMaxSize().background(OtoColors.Canvas)) {
        // Everything inside this box is what the glass pieces bend and blur.
        Box(Modifier.fillMaxSize().nestedScroll(scrollWatcher).extendUp(OVERSCAN_DP.dp).layerBackdrop(backdrop).background(OtoColors.Canvas)) {
            when (tab) {
                Tab.Now -> NowScreen(container = container, state = state, headerProgress = headerProgress, onGrantAccess = actions.openAccessSettings)
                Tab.Stats -> StatsScreen(model = container.stats, artStore = container.artStore, headerProgress = headerProgress)
                Tab.History -> ComingSoon(headerProgress, "Every listen, grouped by day, searchable.")
                Tab.Library -> ComingSoon(headerProgress, "Artists, albums and tracks you have played, with their numbers.")
            }
        }

        GlassHeader(
            title = tab.label,
            progress = { headerProgress.floatValue },
            needsAttention = needsAttention,
            onSetup = { showSetup = true },
            backdrop = backdrop,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        ) {
            if (tab != Tab.Now && snapshot != null) {
                MiniPlayer(backdrop = backdrop, chrome = chrome, onClick = { tabName = Tab.Now.name })
                Spacer(Modifier.height(10.dp))
            }
            GlassTabBar(selected = tab, onSelect = { tabName = it.name }, backdrop = backdrop, chrome = chrome)
        }
    }
}

/**
 * The top bar. At rest it is just a title over the content. As the page scrolls it turns into a
 * pane of Liquid Glass: the bottom corners round off and the content passing under it is bent
 * and blurred at the edge. [progress] is 0 (clear) to 1 (full glass).
 */
@Composable
private fun GlassHeader(
    title: String,
    progress: () -> Float,
    needsAttention: Boolean,
    onSetup: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val glassOn by remember { derivedStateOf { progress() > 0.02f } }
    Box(modifier.fillMaxWidth()) {
        if (glassOn) {
            // The pane is taller than the header and reaches above the screen, so the glass edge
            // (its highlight and shadow) is never visible along the top of the display.
            Box(
                Modifier
                    .matchParentSize()
                    .extendUp(48.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = {
                            val r = lerp(0f, 36f, progress()).dp
                            RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomEnd = r, bottomStart = r)
                        },
                        effects = {
                            val p = progress()
                            colorControls(brightness = -0.03f, saturation = 1f + 0.6f * p)
                            blur(10.dp.toPx() * p)
                            lens(26.dp.toPx() * p, 36.dp.toPx() * p, depthEffect = true, chromaticAberration = true)
                        },
                        highlight = { GlassHighlight.copy(alpha = GlassHighlight.alpha * progress()) },
                        shadow = { GlassShadow.copy(alpha = progress()) },
                        onDrawBackdrop = PageFill,
                        onDrawSurface = { drawRect(GlassTint.copy(alpha = GlassTint.alpha * progress())) },
                    ),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                // The bar gets a little shorter as it turns to glass: 64dp down to 52dp.
                .layout { measurable, constraints ->
                    val h = lerp(64.dp.toPx(), 52.dp.toPx(), progress()).roundToInt()
                    val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                    layout(placeable.width, h) { placeable.place(0, 0) }
                }
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                title,
                modifier = Modifier.graphicsLayer {
                    val scale = lerp(1f, 0.86f, progress())
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                },
                style = MaterialTheme.typography.headlineLarge,
                color = OtoColors.TextPrimary,
            )
            val setupSource = remember { MutableInteractionSource() }
            val setupPress = rememberPress(setupSource)
            Surface(
                onClick = onSetup,
                interactionSource = setupSource,
                modifier = Modifier.graphicsLayer {
                    val scale = lerp(1f, 0.92f, progress()) * (1f - 0.04f * setupPress.value)
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(1f, 0.5f)
                },
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.38f),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (needsAttention) {
                        Box(Modifier.size(7.dp).background(OtoColors.Warning, CircleShape))
                        Box(Modifier.size(6.dp))
                    }
                    Text("Setup", style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            }
        }
    }
}

/** Makes the element [by] taller upward without moving its bottom edge or changing the layout. */
private fun Modifier.extendUp(by: Dp): Modifier = layout { measurable, constraints ->
    val extra = by.roundToPx()
    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, constraints.maxHeight + extra))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, -extra) }
}

/** 0..1: how pressed [source] is, on a springy curve so release overshoots a touch. */
@Composable
private fun rememberPress(source: InteractionSource): State<Float> {
    val pressed by source.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
}

/** Glass shrinks a little under the finger and springs back. */
private fun Modifier.pressScale(press: State<Float>, amount: Float = 0.04f): Modifier = graphicsLayer {
    val s = 1f - amount * press.value
    scaleX = s
    scaleY = s
}

/** Height that eases from [expanded] to [minimized] as [chrome] goes 0..1 (read at layout time). */
private fun Modifier.chromeHeight(expanded: Dp, minimized: Dp, chrome: () -> Float): Modifier =
    layout { measurable, constraints ->
        val h = lerp(expanded.toPx(), minimized.toPx(), chrome().coerceIn(0f, 1f)).roundToInt().coerceAtLeast(0)
        val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
        layout(placeable.width, h) { placeable.place(0, 0) }
    }

/** What is playing, as a glass pill above the tab bar, on every tab except Now. Tap to go to Now. */
@Composable
private fun MiniPlayer(backdrop: Backdrop, chrome: () -> Float, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val snapshot by TrackingStatus.nowPlaying.collectAsState()
    val art by TrackingStatus.artwork.collectAsState()
    val current = snapshot ?: return
    val bitmap = remember(art) { art?.asImageBitmap() }
    val playing = current.state == PlayState.PLAYING
    val shape = RoundedCornerShape(32.dp)
    val source = remember { MutableInteractionSource() }
    val press = rememberPress(source)
    Row(
        modifier
            .fillMaxWidth()
            .pressScale(press)
            .chromeHeight(64.dp, 52.dp, chrome)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    colorControls(brightness = -0.03f, saturation = 1.6f)
                    blur(5.dp.toPx())
                    val bulge = 1f + 0.35f * press.value
                    lens(22.dp.toPx() * bulge, 36.dp.toPx() * bulge, depthEffect = true, chromaticAberration = true)
                },
                highlight = { GlassHighlight },
                shadow = { GlassShadow },
                onDrawBackdrop = PageFill,
                onDrawSurface = { drawRect(GlassTint) },
            )
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(OtoColors.Surface2),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp))
            } else {
                Text(
                    current.meta.title.firstOrNull { it.isLetterOrDigit() }?.uppercase().orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = OtoColors.TextTertiary,
                )
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(current.meta.title, style = MaterialTheme.typography.titleSmall, color = OtoColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(current.meta.artist, style = MaterialTheme.typography.bodySmall, color = OtoColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Equalizer(
            active = playing,
            color = if (playing) OtoColors.Accent else OtoColors.TextTertiary,
            modifier = Modifier.padding(end = 10.dp).size(width = 16.dp, height = 14.dp),
        )
    }
}

/**
 * Floating capsule of Liquid Glass. The selected tab sits in a soft pill that slides to the new
 * tab on a spring; its icon turns yellow and pops with a small bounce. Scrolling down shrinks the
 * bar ([chrome] 0..1) and folds the labels away.
 */
@Composable
private fun GlassTabBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    backdrop: Backdrop,
    chrome: () -> Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(36.dp)
    val pad = 6.dp
    val haptic = LocalHapticFeedback.current
    val count = Tab.entries.size
    val position by animateFloatAsState(
        targetValue = selected.ordinal.toFloat(),
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "pill",
    )

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .chromeHeight(68.dp, 54.dp, chrome)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    colorControls(brightness = -0.03f, saturation = 1.6f)
                    blur(5.dp.toPx())
                    lens(22.dp.toPx(), 36.dp.toPx(), depthEffect = true, chromaticAberration = true)
                },
                highlight = { GlassHighlight },
                shadow = { GlassShadow },
                onDrawBackdrop = PageFill,
                onDrawSurface = { drawRect(GlassTint) },
            ),
    ) {
        val tabWidth = (maxWidth - pad * 2) / count
        val pillShape = RoundedCornerShape(30.dp)
        // The sliding pill under the selected tab.
        Box(
            Modifier
                .offset { IntOffset((pad.toPx() + position * tabWidth.toPx()).roundToInt(), pad.roundToPx()) }
                .size(tabWidth, maxHeight - pad * 2)
                .background(Color.White.copy(alpha = 0.10f), pillShape)
                .border(1.dp, Color.White.copy(alpha = 0.14f), pillShape),
        )

        Row(Modifier.fillMaxSize().padding(horizontal = pad)) {
            Tab.entries.forEach { tab ->
                val active = tab == selected
                val tint = if (active) OtoColors.Accent else OtoColors.TextSecondary
                val pop by animateFloatAsState(
                    targetValue = if (active) 1.12f else 1f,
                    animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
                    label = "pop",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .selectable(
                            selected = active,
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (!active) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(tab)
                            },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    NavGlyph(
                        tab.icon,
                        tint,
                        Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                scaleX = pop
                                scaleY = pop
                            },
                    )
                    // The label folds away when the bar shrinks, and the icon settles to the middle.
                    Box(
                        Modifier
                            .layout { measurable, constraints ->
                                val c = chrome().coerceIn(0f, 1f)
                                val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                                val gap = (3.dp.toPx() * (1f - c)).roundToInt().coerceAtLeast(0)
                                val h = ((placeable.height * (1f - c)).roundToInt() + gap).coerceAtLeast(0)
                                layout(placeable.width, h) { placeable.place(0, gap) }
                            }
                            .graphicsLayer { alpha = (1f - chrome().coerceIn(0f, 1f) * 1.6f).coerceIn(0f, 1f) },
                    ) {
                        Text(tab.label, style = MaterialTheme.typography.labelSmall, color = tint)
                    }
                }
            }
        }
    }
}
