package com.oto.stats.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
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
import com.kyant.backdrop.effects.vibrancy
import com.oto.stats.AppContainer
import com.oto.stats.ui.components.ComingSoon
import com.oto.stats.ui.components.NavGlyph
import com.oto.stats.ui.components.OVERSCAN_DP
import com.oto.stats.ui.components.NavIcon
import com.oto.stats.ui.debug.DebugActions
import com.oto.stats.ui.debug.DebugScreen
import com.oto.stats.ui.debug.DebugState
import com.oto.stats.ui.now.NowScreen
import com.oto.stats.ui.theme.OtoColors

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

    Box(Modifier.fillMaxSize().background(OtoColors.Canvas)) {
        // Everything inside this box is what the glass pieces bend and blur.
        Box(Modifier.fillMaxSize().extendUp(OVERSCAN_DP.dp).layerBackdrop(backdrop).background(OtoColors.Canvas)) {
            when (tab) {
                Tab.Now -> NowScreen(container = container, state = state, headerProgress = headerProgress, onGrantAccess = actions.openAccessSettings)
                Tab.Stats -> ComingSoon(headerProgress, "Your top artists, tracks and listening hours over any period.")
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
                MiniPlayer(backdrop = backdrop, onClick = { tabName = Tab.Now.name })
                Spacer(Modifier.height(10.dp))
            }
            GlassTabBar(selected = tab, onSelect = { tabName = it.name }, backdrop = backdrop)
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
                            blur(12.dp.toPx() * p)
                            lens(22.dp.toPx() * p, 28.dp.toPx() * p)
                        },
                        highlight = { Highlight.Default.copy(alpha = progress()) },
                        shadow = { Shadow(alpha = progress()) },
                        onDrawSurface = { drawRect(OtoColors.Canvas.copy(alpha = 0.40f * progress())) },
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
            Surface(
                onClick = onSetup,
                modifier = Modifier.graphicsLayer {
                    val scale = lerp(1f, 0.92f, progress())
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

/** What is playing, as a glass pill above the tab bar, on every tab except Now. Tap to go to Now. */
@Composable
private fun MiniPlayer(backdrop: Backdrop, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val snapshot by TrackingStatus.nowPlaying.collectAsState()
    val art by TrackingStatus.artwork.collectAsState()
    val current = snapshot ?: return
    val bitmap = remember(art) { art?.asImageBitmap() }
    val playing = current.state == PlayState.PLAYING
    val shape = RoundedCornerShape(32.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(10.dp.toPx())
                    lens(16.dp.toPx(), 20.dp.toPx())
                },
                onDrawSurface = { drawRect(OtoColors.Canvas.copy(alpha = 0.42f)) },
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(OtoColors.Surface2),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp))
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
 * Floating capsule. Real Liquid Glass (refraction + blur + specular edge) on Android 13+,
 * blur-only on 12, and a plain translucent bar below that. The effects are drawn by the Backdrop
 * library, which skips whatever the phone cannot do.
 */
@Composable
private fun GlassTabBar(selected: Tab, onSelect: (Tab) -> Unit, backdrop: Backdrop, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(36.dp)
    val pad = 6.dp
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(68.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(10.dp.toPx())
                    lens(18.dp.toPx(), 22.dp.toPx())
                },
                onDrawSurface = { drawRect(OtoColors.Canvas.copy(alpha = 0.42f)) },
            ),
    ) {
        val tabWidth = (maxWidth - pad * 2) / Tab.entries.size
        val x by animateDpAsState(
            targetValue = tabWidth * selected.ordinal,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
            label = "indicator",
        )
        // The sliding pill under the selected tab.
        Box(
            Modifier
                .offset(x = pad + x, y = pad)
                .width(tabWidth)
                .height(68.dp - pad * 2)
                .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(30.dp))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(30.dp)),
        )
        Row(Modifier.fillMaxSize().padding(horizontal = pad)) {
            Tab.entries.forEach { tab ->
                val active = tab == selected
                val tint = if (active) OtoColors.Accent else OtoColors.TextSecondary
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .selectable(
                            selected = active,
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(tab) },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    NavGlyph(tab.icon, tint, Modifier.size(24.dp))
                    Box(Modifier.size(3.dp))
                    Text(tab.label, style = MaterialTheme.typography.labelSmall, color = tint)
                }
            }
        }
    }
}
