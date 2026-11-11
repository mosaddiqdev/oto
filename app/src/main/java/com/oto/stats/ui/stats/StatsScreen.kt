package com.oto.stats.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oto.stats.stats.ActivityBar
import com.oto.stats.stats.BarState
import com.oto.stats.stats.PeriodKind
import com.oto.stats.stats.StatsData
import com.oto.stats.stats.StatsMath
import com.oto.stats.stats.StatsModel
import com.oto.stats.stats.StatsUiState
import com.oto.stats.tracking.MusicApps
import com.oto.stats.ui.components.OtoCard
import com.oto.stats.ui.components.ScreenColumn
import com.oto.stats.ui.components.SectionLabel
import com.oto.stats.ui.components.hoursMinutes
import com.oto.stats.ui.theme.OtoColors
import com.oto.stats.ui.theme.OtoNumbers
import java.time.format.TextStyle
import java.util.Locale

/*
 * Structure-first version of the Stats tab: every section from docs/STATS_PLAN.md is here and
 * wired to real data, with plain styling. The visual design pass comes later.
 */

@Composable
fun StatsScreen(model: StatsModel, headerProgress: MutableFloatState) {
    val ui by model.state.collectAsState()

    ScreenColumn(headerProgress) {
        item { PeriodBar(ui, model) }
        when (val c = ui.content) {
            StatsUiState.Content.Loading -> Unit
            StatsUiState.Content.NoHistory -> item {
                Note("Nothing tracked yet", "Stats appear after your first counted listen. Play something in any music app.")
            }
            StatsUiState.Content.EmptyPeriod -> item {
                Note("No listening in ${ui.periodLabel}", "Step back to an earlier period, or pick another range.")
            }
            is StatsUiState.Content.Ready -> readySections(c.data, ui, model)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.readySections(
    d: StatsData,
    ui: StatsUiState,
    model: StatsModel,
) {
    val byTime = ui.selection.byTime
    item { Headline(d) }
    item { Activity(d, ui.selection.kind) }
    item { MetricToggle(byTime) { model.setByTime(it) } }
    item {
        TopList("Top artists", d.topArtists.map { Row3(it.name, null, it.plays, it.playedMs) }, byTime)
    }
    item {
        TopList("Top tracks", d.topTracks.map { Row3(it.title, it.artist, it.plays, it.playedMs) }, byTime)
    }
    if (d.topAlbums.isNotEmpty()) item {
        TopList("Top albums", d.topAlbums.map { Row3(it.album, it.artist, it.plays, it.playedMs) }, byTime)
    }
    if (d.hours != null && d.weekdays != null) {
        item { Patterns(d.hours, d.weekdays) }
    } else item {
        Note("Patterns need a little more listening", "Hour and weekday patterns appear after about 3 days and 10 plays.")
    }
    item { Habits(d) }
    if (d.apps.size > 1) item { Apps(d) }
}

@Composable
private fun PeriodBar(ui: StatsUiState, model: StatsModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (kind in PeriodKind.entries) {
                Chip(
                    text = kind.name.lowercase().replaceFirstChar { it.uppercase() },
                    selected = ui.selection.kind == kind,
                ) { model.selectKind(kind) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Arrow("‹", enabled = ui.canGoPrevious) { model.previous() }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ui.periodLabel, style = MaterialTheme.typography.titleMedium, color = OtoColors.TextPrimary)
                if (ui.resolvedDates.isNotEmpty()) {
                    Text(ui.resolvedDates, style = MaterialTheme.typography.bodySmall, color = OtoColors.TextTertiary)
                }
            }
            Arrow("›", enabled = ui.canGoNext) { model.next() }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) OtoColors.Accent else OtoColors.Surface1,
        border = BorderStroke(1.dp, if (selected) OtoColors.Accent else OtoColors.Hairline),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) OtoColors.OnAccent else OtoColors.TextSecondary,
        )
    }
}

@Composable
private fun Arrow(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(44.dp).padding(horizontal = 12.dp).then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = MaterialTheme.typography.headlineMedium, color = if (enabled) OtoColors.TextPrimary else OtoColors.Hairline)
    }
}


@Composable
private fun Note(title: String, body: String) {
    OtoCard {
        Text(title, style = MaterialTheme.typography.titleMedium, color = OtoColors.TextPrimary)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = OtoColors.TextSecondary, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun Headline(d: StatsData) {
    OtoCard {
        SectionLabel("Listening time")
        Text(hoursMinutes(d.headline.playedMs), style = OtoNumbers.Hero, color = OtoColors.TextPrimary)
        DeltaText(StatsMath.delta(d.headline.playedMs, d.previous?.playedMs ?: 0), d.comparisonPartial)
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Figure("Plays", d.headline.plays.toString(), StatsMath.delta(d.headline.plays.toLong(), (d.previous?.plays ?: 0).toLong()))
            Figure("Tracks", d.headline.uniqueTracks.toString(), StatsMath.delta(d.headline.uniqueTracks.toLong(), (d.previous?.uniqueTracks ?: 0).toLong()))
            Figure("Artists", d.uniqueArtists.toString(), StatsMath.delta(d.uniqueArtists.toLong(), (d.previousUniqueArtists ?: 0).toLong()))
        }
    }
}

@Composable
private fun Figure(label: String, value: String, delta: StatsMath.Delta?) {
    Column {
        Text(value, style = OtoNumbers.Title, color = OtoColors.TextPrimary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = OtoColors.TextTertiary)
        DeltaText(delta, partial = false, small = true)
    }
}

@Composable
private fun DeltaText(delta: StatsMath.Delta?, partial: Boolean, small: Boolean = false) {
    if (delta == null) return
    val arrow = when (delta.direction) {
        StatsMath.Direction.UP -> "▲"
        StatsMath.Direction.DOWN -> "▼"
        StatsMath.Direction.FLAT -> "•"
    }
    val tail = if (partial) " vs same days before" else if (small) "" else " vs previous"
    Text(
        "$arrow ${delta.percent}%$tail",
        style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
        color = OtoColors.TextSecondary,
    )
}

@Composable
private fun Activity(d: StatsData, kind: PeriodKind) {
    OtoCard {
        SectionLabel("Activity")
        val max = (d.bars.maxOfOrNull { it.playedMs } ?: 0L).coerceAtLeast(1L)
        Row(
            Modifier.fillMaxWidth().height(96.dp).padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(if (d.bars.size > 20) 2.dp else 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (bar in d.bars) Bar(bar, max)
        }
        d.averagePerDay?.let {
            Text(
                "Average ${hoursMinutes(it.perDayMs)} a day, over ${it.overDays} ${if (it.activeOnly) "listening days" else "days"}",
                style = MaterialTheme.typography.bodySmall,
                color = OtoColors.TextTertiary,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Bar(bar: ActivityBar, max: Long) {
    val fraction = if (bar.state == BarState.TRACKED) bar.playedMs.toFloat() / max else 0f
    Box(Modifier.weight(1f).fillMaxHeightFraction(fraction.coerceIn(0.02f, 1f))
        .background(
            when {
                bar.state != BarState.TRACKED -> OtoColors.Hairline
                bar.playedMs == 0L -> OtoColors.Hairline
                else -> OtoColors.Accent
            },
            RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
        ),
    )
}

private fun Modifier.fillMaxHeightFraction(f: Float) = this.then(Modifier.fillMaxHeight(f))

@Composable
private fun MetricToggle(byTime: Boolean, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Rank by", style = MaterialTheme.typography.labelMedium, color = OtoColors.TextTertiary)
        Chip("Time", byTime) { onChange(true) }
        Chip("Plays", !byTime) { onChange(false) }
    }
}

private data class Row3(val title: String, val subtitle: String?, val plays: Int, val playedMs: Long)

@Composable
private fun TopList(title: String, rows: List<Row3>, byTime: Boolean) {
    OtoCard {
        SectionLabel(title)
        if (rows.isEmpty()) {
            Text("Nothing yet", style = MaterialTheme.typography.bodyMedium, color = OtoColors.TextTertiary, modifier = Modifier.padding(top = 8.dp))
        }
        rows.forEachIndexed { i, r ->
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = OtoNumbers.Body, color = OtoColors.TextTertiary, modifier = Modifier.padding(end = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.title, style = MaterialTheme.typography.bodyLarge, color = OtoColors.TextPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (r.subtitle != null) Text(r.subtitle, style = MaterialTheme.typography.bodySmall, color = OtoColors.TextTertiary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                val main = if (byTime) hoursMinutes(r.playedMs) else "${r.plays} plays"
                val sub = if (byTime) "${r.plays} plays" else hoursMinutes(r.playedMs)
                Column(horizontalAlignment = Alignment.End) {
                    Text(main, style = OtoNumbers.Body, color = OtoColors.TextPrimary)
                    Text(sub, style = OtoNumbers.Small, color = OtoColors.TextTertiary)
                }
            }
        }
    }
}

@Composable
private fun Patterns(hours: List<Long>, weekdays: List<Long>) {
    OtoCard {
        SectionLabel("When you listen")
        MiniBars(hours, Modifier.padding(top = 12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (h in listOf("0", "6", "12", "18", "23")) Text(h, style = MaterialTheme.typography.labelSmall, color = OtoColors.TextTertiary)
        }
        val peakHour = hours.indices.maxByOrNull { hours[it] } ?: 0
        val peakDay = weekdays.indices.maxByOrNull { weekdays[it] } ?: 0
        val dayName = java.time.DayOfWeek.of(peakDay + 1).getDisplayName(TextStyle.FULL, Locale.getDefault())
        Text("Most active: $dayName, around ${peakHour}:00", style = MaterialTheme.typography.bodyMedium, color = OtoColors.TextSecondary, modifier = Modifier.padding(top = 12.dp))
        MiniBars(weekdays, Modifier.padding(top = 16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (l in listOf("M", "T", "W", "T", "F", "S", "S")) Text(l, style = MaterialTheme.typography.labelSmall, color = OtoColors.TextTertiary)
        }
    }
}

@Composable
private fun MiniBars(values: List<Long>, modifier: Modifier = Modifier) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        for (v in values) {
            Box(Modifier.weight(1f).fillMaxHeightFraction((v.toFloat() / max).coerceIn(0.03f, 1f)).background(if (v == max) OtoColors.Accent else OtoColors.Surface3, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)))
        }
    }
}

@Composable
private fun Habits(d: StatsData) {
    val h = d.headline
    OtoCard {
        SectionLabel("Habits")
        val plays = h.plays.coerceAtLeast(1)
        FactRow("Full listens", "${h.fullListens * 100 / plays}%")
        FactRow("Skipped", "${h.skips * 100 / plays}%")
        FactRow("Replays", d.replays.toString())
        FactRow("Listening days", d.activeDaysInPeriod.toString())
        FactRow("Current streak", "${d.streaks.current} days")
        FactRow("Longest streak", "${d.streaks.longest} days")
        d.mostReplayed?.let { FactRow("Most replayed", "${it.title} (${it.total}×)") }
        d.mostSkipped?.let { FactRow("Most skipped", "${it.title} (${it.total}×)") }
    }
}

@Composable
private fun Apps(d: StatsData) {
    val total = d.apps.sumOf { it.playedMs }.coerceAtLeast(1L)
    OtoCard {
        SectionLabel("By app")
        for (a in d.apps) FactRow(MusicApps.labelFor(a.sourcePackage), "${a.playedMs * 100 / total}%  ·  ${hoursMinutes(a.playedMs)}")
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OtoColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, style = OtoNumbers.Body, color = OtoColors.TextPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
