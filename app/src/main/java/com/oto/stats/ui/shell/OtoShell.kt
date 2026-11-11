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
import androidx.compose.runtime.Composable
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
    Box(Modifier.fillMaxSize().background(OtoColors.Canvas)) {
        // Everything inside this box is what the glass bar bends and blurs.
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(OtoColors.Canvas)) {
            when (tab) {
                Tab.Now -> NowScreen(container = container, state = state, onOpenSetup = { showSetup = true }, onGrantAccess = actions.openAccessSettings)
                Tab.Stats -> ComingSoon("Stats", "Your top artists, tracks and listening hours over any period.")
                Tab.History -> ComingSoon("History", "Every listen, grouped by day, searchable.")
                Tab.Library -> ComingSoon("Library", "Artists, albums and tracks you have played, with their numbers.")
            }
        }
        GlassTabBar(
            selected = tab,
            onSelect = { tabName = it.name },
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
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
