package com.oto.stats.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oto.stats.AppContainer
import com.oto.stats.data.DemoPreset
import com.oto.stats.data.EventRow
import com.oto.stats.data.Totals
import com.oto.stats.diag.DiagLog
import com.oto.stats.tracking.MusicApps
import com.oto.stats.tracking.OemHelper
import com.oto.stats.tracking.OemShortcut
import com.oto.stats.tracking.TrackingStatus
import com.oto.stats.tracking.engine.PlayState
import com.oto.stats.ui.theme.OtoColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/** What the phone currently allows. Re-read every time the app comes back to the screen. */
class DebugState(
    val accessGranted: Boolean,
    val notificationsGranted: Boolean,
    val batteryExempt: Boolean,
)

class DebugActions(
    val openAccessSettings: () -> Unit,
    val requestNotifications: () -> Unit,
    val requestBatteryExemption: () -> Unit,
    val openAppSettings: () -> Unit,
    val openOemShortcut: (OemShortcut) -> Unit,
    val copyLog: () -> Unit,
)

/** Temporary developer screen for checking tracking on a real phone. */
@Composable
fun DebugScreen(
    container: AppContainer,
    state: DebugState,
    actions: DebugActions,
) {
    val context = LocalContext.current
    val connected by TrackingStatus.listenerConnected.collectAsState()
    val foreground by TrackingStatus.foreground.collectAsState()
    val nowPlaying by TrackingStatus.nowPlaying.collectAsState()
    val trackAll by container.prefs.trackAllApps.collectAsState()
    val fetchArt by container.prefs.fetchArt.collectAsState()
    val totals by remember(container) { container.repository.totals() }
        .collectAsState(initial = Totals(0, 0L, 0))
    val events by remember(container) { container.repository.recentEvents(50) }
        .collectAsState(initial = emptyList())
    val log by DiagLog.lines.collectAsState()
    val guide = remember { OemHelper.guide(context) }
    val scope = rememberCoroutineScope()

    val insets = WindowInsets.safeDrawing.asPaddingValues()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = insets.calculateTopPadding() + 16.dp,
            bottom = insets.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("oto", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
                Text("Tracking engine · debug screen", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            DebugCard {
                StatusRow("Notification access", if (state.accessGranted) "On" else "Off", if (state.accessGranted) OtoColors.Success else OtoColors.Warning)
                StatusRow("Listener service", if (connected) "Connected" else "Not connected", if (connected) OtoColors.Success else OtoColors.TextTertiary)
                StatusRow("Keep-alive mode", if (foreground) "Active" else "Not active", if (foreground) OtoColors.Success else OtoColors.Warning)
                StatusRow("Notifications", if (state.notificationsGranted) "Allowed" else "Blocked", if (state.notificationsGranted) OtoColors.Success else OtoColors.Warning)
                if (!state.accessGranted) {
                    Button(onClick = actions.openAccessSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("Grant notification access")
                    }
                    Text(
                        "If the switch is greyed out: App info, then the three-dot menu, then \"Allow restricted settings\". Android blocks this for apps installed outside the Play Store.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OtoColors.TextTertiary,
                    )
                } else if (!foreground) {
                    Text(
                        "Keep-alive mode starts when this screen is open. If it stays off, copy the log below and send it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OtoColors.TextTertiary,
                    )
                }
                if (!state.notificationsGranted) {
                    Button(onClick = actions.requestNotifications, modifier = Modifier.fillMaxWidth()) {
                        Text("Allow notifications")
                    }
                    Text(
                        "Oto shows one quiet notification while it listens. Phones are much more likely to keep an app alive when they can see it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OtoColors.TextTertiary,
                    )
                }
            }
        }

        item {
            DebugCard {
                Text("Background protection", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusRow(
                    "Battery restrictions",
                    if (state.batteryExempt) "Unrestricted" else "Restricted",
                    if (state.batteryExempt) OtoColors.Success else OtoColors.Warning,
                )
                if (!state.batteryExempt) {
                    Button(onClick = actions.requestBatteryExemption, modifier = Modifier.fillMaxWidth()) {
                        Text("Allow background activity")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(guide.oem.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Your phone has its own battery manager on top of Android. Do these too, or it can stop Oto when the screen is off or when you swipe it away:",
                    style = MaterialTheme.typography.bodySmall,
                    color = OtoColors.TextTertiary,
                )
                guide.steps.forEachIndexed { index, step ->
                    Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                guide.shortcuts.forEach { shortcut ->
                    OutlinedButton(onClick = { actions.openOemShortcut(shortcut) }, modifier = Modifier.fillMaxWidth()) {
                        Text(shortcut.label)
                    }
                }
                OutlinedButton(onClick = actions.openAppSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Open app settings")
                }
                Text(
                    "Menu names differ a little between versions. If a button does not open the right page, follow the steps by hand.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OtoColors.TextTertiary,
                )
            }
        }

        item {
            DebugCard {
                Text("Now playing", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val np = nowPlaying
                if (np == null) {
                    Text("Nothing playing", style = MaterialTheme.typography.bodyLarge, color = OtoColors.TextTertiary)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(if (np.state == PlayState.PLAYING) OtoColors.Accent else OtoColors.TextTertiary, CircleShape),
                        )
                        Text(
                            np.meta.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(np.meta.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${MusicApps.labelFor(np.packageName)} · ${np.state.name.lowercase()} · counted ${formatClock(np.countedMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OtoColors.TextTertiary,
                    )
                    if (!np.countable) {
                        Text("Filtered out (ad or not music), will not be counted", style = MaterialTheme.typography.bodySmall, color = OtoColors.TextTertiary)
                    }
                }
            }
        }

        item {
            DebugCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Track all apps", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "Turn this on if your player is not on the built-in list (for example YouTube or a browser). Messaging apps stay blocked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OtoColors.TextTertiary,
                        )
                    }
                    Switch(checked = trackAll, onCheckedChange = { container.prefs.setTrackAllApps(it) })
                }
            }
        }

        item {
            DebugCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Fetch artist photos", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "Looks up artist photos on Deezer for the artists you see in Stats. Only the artist name is sent and nothing else leaves your phone. Off keeps Oto fully offline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OtoColors.TextTertiary,
                        )
                    }
                    Switch(checked = fetchArt, onCheckedChange = { container.prefs.setFetchArt(it) })
                }
            }
        }

        item {
            DebugCard {
                Text("Totals", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("Plays", totals.plays.toString())
                    Stat("Unique tracks", totals.uniqueTracks.toString())
                    Stat("Time", formatClock(totals.totalPlayedMs))
                }
            }
        }

        item {
            Text("Recent plays", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        }

        if (events.isEmpty()) {
            item {
                Text(
                    "No plays saved yet. Play a song for 25+ seconds. It appears here within about 15 seconds and keeps updating while it plays.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OtoColors.TextTertiary,
                )
            }
        } else {
            items(events, key = { it.id }) { row -> EventItem(row) }
        }

        item {
            DebugCard {
                Text("Diagnostics", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "What the app saw and did, newest first. If tracking fails, copy this and send it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OtoColors.TextTertiary,
                )
                val shown = log.takeLast(40).asReversed()
                Text(
                    if (shown.isEmpty()) "Nothing logged yet." else shown.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = actions.copyLog, modifier = Modifier.weight(1f)) { Text("Copy log") }
                    OutlinedButton(onClick = { DiagLog.clear() }, modifier = Modifier.weight(1f)) { Text("Clear log") }
                }
            }
        }

        item {
            val demoCount by remember(container) { container.repository.demoEventCount() }
                .collectAsState(initial = 0)
            var loading by remember { mutableStateOf(false) }
            DebugCard {
                Text("Demo history", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (demoCount > 0) "$demoCount demo listens loaded. Your real listens are not touched."
                    else "Fills Oto with made-up listening so Stats can be tried without waiting. Easy to remove.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (preset in DemoPreset.values()) {
                        OutlinedButton(
                            enabled = !loading,
                            onClick = {
                                loading = true
                                scope.launch {
                                    try { container.repository.loadDemo(preset) } finally { loading = false }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) { Text(preset.label, maxLines = 1) }
                    }
                }
                OutlinedButton(
                    enabled = !loading && demoCount > 0,
                    onClick = { scope.launch { container.repository.clearDemo() } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (loading) "Working…" else "Remove demo history") }
            }
        }

        item {
            OutlinedButton(
                onClick = { scope.launch { container.repository.clearAll() } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear all data")
            }
        }
    }
}

@Composable
private fun DebugCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun StatusRow(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.bodySmall, color = OtoColors.TextTertiary)
    }
}

@Composable
private fun EventItem(row: EventRow) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(row.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(row.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val tag = if (row.skipped) " · skipped" else ""
        Text(
            "${formatWhen(row.startedAt)} · ${MusicApps.labelFor(row.sourcePackage)} · ${formatClock(row.playedMs)} · ${row.completionPct}%$tag",
            style = MaterialTheme.typography.bodySmall,
            color = OtoColors.TextTertiary,
        )
        HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private fun formatClock(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

private fun formatWhen(epochMs: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(epochMs))
