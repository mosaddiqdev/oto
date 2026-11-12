package com.oto.stats.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oto.stats.data.ArtStore
import com.oto.stats.stats.PeriodKind
import com.oto.stats.stats.StatsData
import com.oto.stats.stats.StatsModel
import com.oto.stats.stats.StatsUiState
import com.oto.stats.ui.components.OtoCard
import com.oto.stats.ui.components.ScreenColumn
import com.oto.stats.ui.theme.OtoColors

private val KindLabels = listOf("Week", "Month", "Year", "All")

@Composable
fun StatsScreen(model: StatsModel, artStore: ArtStore, headerProgress: MutableFloatState) {
    val ui by model.state.collectAsState()

    ScreenColumn(headerProgress) {
        item { PeriodHeader(ui, model) }
        when (val c = ui.content) {
            StatsUiState.Content.Loading -> Unit
            StatsUiState.Content.NoHistory -> item {
                Note("Nothing tracked yet", "Stats appear after your first counted listen. Play something in any music app.")
            }
            StatsUiState.Content.EmptyPeriod -> item {
                Note("No listening in ${ui.periodLabel}", "Step back to an earlier period, or pick another range.")
            }
            is StatsUiState.Content.Ready -> readySections(c.data, ui, model, artStore)
        }
    }
}

private fun LazyListScope.readySections(d: StatsData, ui: StatsUiState, model: StatsModel, artStore: ArtStore) {
    val byTime = ui.selection.byTime
    val label = ui.periodLabel
    item { HeroPoster(d, label, artStore) }
    item { HeroCard(d) }
    item { ActivityCard(d, periodKey = label) }
    item { ArtistPoster(d, label, artStore) }
    item { RepeatPoster(d, label, artStore) }
    item { ClockPoster(d, label, artStore) }
    item { CalendarPoster(d, ui.selection.kind, label, artStore) }
    item { ExplorerPoster(d, label, artStore) }
    item {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Most played", style = MaterialTheme.typography.headlineSmall, color = OtoColors.TextPrimary, modifier = Modifier.weight(1f))
            Segmented(
                options = listOf("Time", "Plays"),
                selectedIndex = if (byTime) 0 else 1,
                onSelect = { model.setByTime(it == 0) },
                modifier = Modifier.width(156.dp),
                height = 36.dp,
            )
        }
    }
    item { RankedCard("Artists", d.topArtists.map { RankedItem(it.name, null, it.plays, it.playedMs, null) }, byTime, artStore) }
    item { RankedCard("Tracks", d.topTracks.map { RankedItem(it.title, it.artist, it.plays, it.playedMs, it.identityKey) }, byTime, artStore) }
    if (d.topAlbums.isNotEmpty()) {
        item { RankedCard("Albums", d.topAlbums.map { RankedItem(it.album, it.artist, it.plays, it.playedMs, it.identityKey) }, byTime, artStore) }
    }
    if (d.hours == null) {
        item { Note("Patterns need a little more listening", "Your hour and weekday patterns appear after about 3 days and 10 plays.") }
    }
    item { HabitsCard(d) }
    if (d.apps.size > 1) item { AppsCard(d) }
}

@Composable
private fun PeriodHeader(ui: StatsUiState, model: StatsModel) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Segmented(
            options = KindLabels,
            selectedIndex = ui.selection.kind.ordinal,
            onSelect = { model.selectKind(PeriodKind.entries[it]) },
            modifier = Modifier.fillMaxWidth(),
            height = 44.dp,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Chevron(left = true, enabled = ui.canGoPrevious, onClick = model::previous, description = "Previous period")
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    ui.periodLabel,
                    style = MaterialTheme.typography.headlineMedium,
                    color = OtoColors.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                if (ui.resolvedDates.isNotEmpty()) {
                    Text(ui.resolvedDates, style = MaterialTheme.typography.bodySmall, color = OtoColors.TextTertiary, textAlign = TextAlign.Center)
                }
            }
            Chevron(left = false, enabled = ui.canGoNext, onClick = model::next, description = "Next period")
        }
    }
}

@Composable
private fun Note(title: String, body: String) {
    OtoCard {
        Text(title, style = MaterialTheme.typography.titleMedium, color = OtoColors.TextPrimary)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = OtoColors.TextSecondary, modifier = Modifier.padding(top = 6.dp))
    }
}
