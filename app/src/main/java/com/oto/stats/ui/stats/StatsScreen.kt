package com.oto.stats.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oto.stats.data.ArtStore
import com.oto.stats.stats.StatsData
import com.oto.stats.stats.StatsModel
import com.oto.stats.stats.StatsUiState
import com.oto.stats.ui.components.ScreenColumn
import com.oto.stats.ui.theme.OtoColors
import com.oto.stats.ui.theme.OtoSpace
import java.time.format.TextStyle
import java.util.Locale

/*
 * The Stats tab. Structure and design: docs/STATS_DESIGN.md.
 * One yellow hero, then one question per section, then the trust line.
 */

@Composable
fun StatsScreen(model: StatsModel, artStore: ArtStore, headerProgress: MutableFloatState) {
    val ui by model.state.collectAsState()
    // Entrances play once when the tab first shows data, not on every scroll back.
    val loaded = ui.content is StatsUiState.Content.Ready

    ScreenColumn(headerProgress, spacing = OtoSpace.SectionGap) {
        item(key = "period") { PeriodControl(ui, model) }
        when (val c = ui.content) {
            StatsUiState.Content.Loading -> Unit
            StatsUiState.Content.NoHistory -> item {
                Note("Nothing tracked yet", "Stats appear after your first counted listen. Play something in any music app and come back.")
            }
            StatsUiState.Content.EmptyPeriod -> item {
                Note("No listening in ${ui.periodLabel}", "Step back to an earlier period, or pick another range.")
            }
            is StatsUiState.Content.Ready -> ready(c.data, ui, model, artStore, loaded)
        }
        item(key = "trust") { TrustLine(ui) }
    }
}

private fun LazyListScope.ready(d: StatsData, ui: StatsUiState, model: StatsModel, artStore: ArtStore, loaded: Boolean) {
    val kind = ui.selection.kind
    val key = ui.periodLabel + kind.name

    item(key = "hero") {
        HeroCard(d, ui.periodLabel, kind, artStore, Modifier.cardEnter(0, loaded))
    }
    item(key = "days") {
        StatsSection("Your days", sentence = daysSentence(d.bars), modifier = Modifier.cardEnter(1, loaded)) {
            DaysCard(d, key, kind)
        }
    }
    item(key = "most") {
        StatsSection("Most played", modifier = Modifier.cardEnter(2, loaded)) {
            MostPlayedCard(d, ui.selection.byTime, model::setByTime, artStore, key)
        }
    }
    if (d.hours != null) {
        item(key = "hour") {
            StatsSection("Your hour", sentence = hourSentence(d), modifier = Modifier.cardEnter(3, loaded)) {
                HourCard(d, key)
            }
        }
    }
    calendarSentence(d, kind)?.let { sentence ->
        item(key = "calendar") {
            StatsSection("Showing up", sentence = sentence, modifier = Modifier.cardEnter(4, loaded)) {
                CalendarCard(d, kind)
            }
        }
    }
    item(key = "listener") {
        StatsSection("How you listen", modifier = Modifier.cardEnter(5, loaded)) {
            ListenerCard(d, key)
        }
    }
    if (d.hours == null) {
        item(key = "lowdata") {
            Note("More patterns soon", "Your hour and listening habits appear after about 3 days and 10 plays.")
        }
    }
}

@Composable
private fun Note(title: String, body: String) {
    StatsCard {
        Text(title, style = MaterialTheme.typography.titleMedium, color = OtoColors.TextPrimary)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = OtoColors.TextSecondary, modifier = Modifier.padding(top = 6.dp))
    }
}

/** What counts, since when, and that it is live. Visible rules are what make the numbers trustworthy. */
@Composable
private fun TrustLine(ui: StatsUiState) {
    val since = ui.trackedSince?.let {
        "Tracking since ${it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${it.dayOfMonth}, ${it.year}. "
    }.orEmpty()
    Column(Modifier.fillMaxWidth()) {
        Text(
            "A listen counts after 25 seconds, or half of a very short track. Skipped means under 30% played. ${since}Updated live.",
            style = MaterialTheme.typography.bodySmall,
            color = OtoColors.TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
    }
}
