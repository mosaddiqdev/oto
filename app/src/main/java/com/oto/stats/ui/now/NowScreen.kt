package com.oto.stats.ui.now

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oto.stats.AppContainer
import com.oto.stats.data.Totals
import com.oto.stats.tracking.MusicApps
import com.oto.stats.tracking.TrackingStatus
import com.oto.stats.tracking.engine.NowPlayingSnapshot
import com.oto.stats.tracking.engine.PlayState
import com.oto.stats.ui.components.OtoCard
import com.oto.stats.ui.components.ScreenColumn
import com.oto.stats.ui.components.SectionLabel
import com.oto.stats.ui.components.clock
import com.oto.stats.ui.components.hoursMinutes
import com.oto.stats.ui.debug.DebugState
import com.oto.stats.ui.theme.OtoColors
import com.oto.stats.ui.theme.OtoNumbers

@Composable
fun NowScreen(
    container: AppContainer,
    state: DebugState,
    onOpenSetup: () -> Unit,
    onGrantAccess: () -> Unit,
) {
    val nowPlaying by TrackingStatus.nowPlaying.collectAsState()
    val totals by remember(container) { container.repository.totals() }
        .collectAsState(initial = Totals(0, 0L, 0))
    val recent by remember(container) { container.repository.recentEvents(5) }
        .collectAsState(initial = emptyList())

    ScreenColumn(
        title = "Now",
        action = {
            TextButton(onClick = onOpenSetup) {
                if (!state.accessGranted || !state.notificationsGranted || !state.batteryExempt) {
                    Box(Modifier.size(8.dp).background(OtoColors.Warning, CircleShape))
                    Box(Modifier.size(6.dp))
                }
                Text("Setup", style = MaterialTheme.typography.labelLarge, color = OtoColors.TextSecondary)
            }
        },
    ) {
        if (!state.accessGranted) {
            item {
                OtoCard {
                    Text("Oto can't see your music yet", style = MaterialTheme.typography.titleMedium, color = OtoColors.TextPrimary)
                    Text(
                        "Allow notification access so Oto can notice what plays in any music app. Nothing leaves your phone.",
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OtoColors.TextSecondary,
                    )
                    Button(
                        onClick = onGrantAccess,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OtoColors.Accent,
                            contentColor = OtoColors.OnAccent,
                        ),
                    ) { Text("Allow access", style = MaterialTheme.typography.labelLarge) }
                }
            }
        }

        item { PlayingCard(nowPlaying) }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Listens", totals.plays.toString(), Modifier.weight(1f))
                Tile("Time", hoursMinutes(totals.totalPlayedMs), Modifier.weight(1f))
                Tile("Tracks", totals.uniqueTracks.toString(), Modifier.weight(1f))
            }
        }

        if (recent.isNotEmpty()) {
            item { SectionLabel("Recent") }
            item {
                OtoCard {
                    recent.forEachIndexed { i, row ->
                        if (i > 0) HorizontalDivider(color = OtoColors.Hairline, modifier = Modifier.padding(vertical = 12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(row.title, style = MaterialTheme.typography.titleSmall, color = OtoColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(row.artist, style = MaterialTheme.typography.bodySmall, color = OtoColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(clock(row.playedMs), style = OtoNumbers.Body, color = OtoColors.TextSecondary, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayingCard(snapshot: NowPlayingSnapshot?) {
    OtoCard {
        if (snapshot == null) {
            Text("Nothing playing", style = MaterialTheme.typography.headlineMedium, color = OtoColors.TextPrimary)
            Text(
                "Play something in any music app. Oto picks it up on its own.",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = OtoColors.TextSecondary,
            )
            return@OtoCard
        }

        val playing = snapshot.state == PlayState.PLAYING
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(if (playing) OtoColors.Accent else OtoColors.TextTertiary, CircleShape))
            Text(
                (if (playing) "Playing" else "Paused") + " · " + MusicApps.labelFor(snapshot.packageName),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = OtoColors.TextSecondary,
            )
        }
        Text(
            snapshot.meta.title,
            modifier = Modifier.padding(top = 14.dp),
            style = MaterialTheme.typography.headlineMedium,
            color = OtoColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (snapshot.meta.artist.isNotBlank()) {
            Text(
                snapshot.meta.artist,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = OtoColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider(color = OtoColors.Hairline, modifier = Modifier.padding(vertical = 18.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(clock(snapshot.countedMs), style = MaterialTheme.typography.displayMedium, color = OtoColors.TextPrimary)
            Text(
                if (snapshot.countable) "listened" else "not counted",
                modifier = Modifier.padding(start = 10.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = if (snapshot.countable) OtoColors.TextTertiary else OtoColors.Warning,
            )
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = OtoColors.Surface1,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = OtoColors.TextPrimary, maxLines = 1)
            Text(label, modifier = Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelMedium, color = OtoColors.TextTertiary)
        }
    }
}
