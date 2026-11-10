package com.oto.stats.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.oto.stats.AppContainer
import com.oto.stats.ui.components.ComingSoon
import com.oto.stats.ui.debug.DebugActions
import com.oto.stats.ui.debug.DebugScreen
import com.oto.stats.ui.debug.DebugState
import com.oto.stats.ui.now.NowScreen
import com.oto.stats.ui.theme.OtoColors

private enum class Tab(val label: String) { Now("Now"), Stats("Stats"), History("History"), Library("Library") }

/** App frame: the four tabs and their bottom bar. */
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
                    .padding(vertical = 18.dp),
                style = MaterialTheme.typography.titleSmall,
                color = OtoColors.Accent,
            )
        }
        return
    }

    Column(Modifier.fillMaxSize().background(OtoColors.Canvas)) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.Now -> NowScreen(container = container, state = state, onOpenSetup = { showSetup = true }, onGrantAccess = actions.openAccessSettings)
                Tab.Stats -> ComingSoon("Stats", "Your top artists, tracks and listening hours over any period.")
                Tab.History -> ComingSoon("History", "Every listen, grouped by day, searchable.")
                Tab.Library -> ComingSoon("Library", "Artists, albums and tracks you have played, with their numbers.")
            }
        }
        NavBar(selected = tab, onSelect = { tabName = it.name })
    }
}

@Composable
private fun NavBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.background(OtoColors.Canvas)) {
        HorizontalDivider(color = OtoColors.Hairline)
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Tab.entries.forEach { tab ->
                val active = tab == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                        .selectable(selected = active, role = Role.Tab, onClick = { onSelect(tab) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .width(20.dp)
                            .height(3.dp)
                            .background(if (active) OtoColors.Accent else OtoColors.Canvas, RoundedCornerShape(2.dp)),
                    )
                    Box(Modifier.size(8.dp))
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) OtoColors.TextPrimary else OtoColors.TextTertiary,
                    )
                }
            }
        }
    }
}
