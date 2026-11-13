package com.oto.stats.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oto.stats.data.ArtStore
import com.oto.stats.data.ArtistPhotos
import com.oto.stats.stats.StatsData
import com.oto.stats.stats.StatsModel
import com.oto.stats.stats.StatsUiState
import com.oto.stats.ui.components.ScreenColumn
import com.oto.stats.ui.theme.OtoColors
import com.oto.stats.ui.theme.OtoSpace
import java.time.format.TextStyle
import java.util.Locale

/*
 * The Stats tab. Structure and design: docs/STATS.md.
 * Period control, then one card per question, then the trust line.
 */

@Composable
fun StatsScreen(model: StatsModel, artStore: ArtStore, photos: ArtistPhotos, headerProgress: MutableFloatState, periodCollapse: MutableFloatState) {
    val ui by model.state.collectAsState()
    // Entrances play once when the tab first shows data, not on every scroll back.
    val loaded = ui.content is StatsUiState.Content.Ready

    val listState = rememberLazyListState()
    val is24 = android.text.format.DateFormat.is24HourFormat(androidx.compose.ui.platform.LocalContext.current)
    // The in-page period control scrolls away; as it goes, the glass header grows its own compact copy.
    val goneAt = with(LocalDensity.current) { 56.dp.toPx() }
    val fadeOver = with(LocalDensity.current) { 64.dp.toPx() }
    LaunchedEffect(listState, goneAt, fadeOver) {
        snapshotFlow {
            if (listState.firstVisibleItemIndex > 0) 1f
            else ((listState.firstVisibleItemScrollOffset - goneAt) / fadeOver).coerceIn(0f, 1f)
        }.collect { periodCollapse.floatValue = it }
    }
    ScreenColumn(headerProgress, spacing = OtoSpace.SectionGap, state = listState) {
        item(key = "period") { PeriodControl(ui, model) }
        when (val c = ui.content) {
            StatsUiState.Content.Loading -> Unit
            StatsUiState.Content.NoHistory -> item {
                Note("Nothing tracked yet", "Stats appear after your first counted listen. Play something in any music app and come back.")
            }
            StatsUiState.Content.EmptyPeriod -> item {
                Note("No listening in ${ui.periodLabel}", "Step back to an earlier period, or pick another range.")
            }
            is StatsUiState.Content.Ready -> ready(c.data, ui, model, artStore, photos, loaded, is24)
        }
        item(key = "trust") { TrustLine(ui) }
    }
}

/** Cards are rebuilt one at a time, in the order set out in docs/STATS.md. Nothing is drawn here until its turn. */
@Suppress("UNUSED_PARAMETER")
private fun LazyListScope.ready(d: StatsData, ui: StatsUiState, model: StatsModel, artStore: ArtStore, photos: ArtistPhotos, loaded: Boolean, is24: Boolean) {
    item(key = "hero") {
        HeroCard(d, ui.periodLabel, ui.selection.kind, Modifier.cardEnter(0, loaded))
    }
    item(key = "most") {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        StatsSection(
            "Most played",
            sentence = mostPlayedSentence(d, tab, ui.selection.byTime),
            modifier = Modifier.cardEnter(1, loaded),
        ) {
            MostPlayedCard(d, ui.selection.byTime, model::setByTime, tab, { tab = it }, artStore, photos, ui.periodLabel + ui.selection.kind.name)
        }
    }
    daysSentence(d.bars)?.let { sentence ->
        item(key = "days") {
            StatsSection("Your days", sentence = sentence, modifier = Modifier.cardEnter(2, loaded)) {
                DaysCard(d, ui.periodLabel + ui.selection.kind.name)
            }
        }
    }
    hourSentence(d.hours, is24)?.let { sentence ->
        item(key = "hour") {
            StatsSection("Your hour", sentence = sentence, modifier = Modifier.cardEnter(3, loaded)) {
                HourCard(d, ui.periodLabel + ui.selection.kind.name)
            }
        }
    }
    if (d.hours != null) showingUpSentence(showingUpDays(d, ui.trackedSince), ui.selection.kind)?.let { sentence ->
        item(key = "showing") {
            StatsSection("Showing up", sentence = sentence, modifier = Modifier.cardEnter(4, loaded)) {
                ShowingUpCard(d, ui.selection.kind, ui.trackedSince, ui.periodLabel + ui.selection.kind.name)
            }
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
