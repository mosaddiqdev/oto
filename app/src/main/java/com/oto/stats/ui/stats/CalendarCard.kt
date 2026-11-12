package com.oto.stats.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oto.stats.stats.PeriodKind
import com.oto.stats.stats.StatsData
import com.oto.stats.ui.theme.OtoColors
import com.oto.stats.ui.theme.OtoNumbers
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private fun level(ms: Long, max: Long): Int {
    if (ms <= 0) return 0
    val r = ms.toFloat() / max.coerceAtLeast(1L)
    return when {
        r <= 0.25f -> 1
        r <= 0.5f -> 2
        r <= 0.75f -> 3
        else -> 4
    }
}

private fun heat(level: Int): Color = when (level) {
    -1 -> Color.White.copy(alpha = 0.03f)
    0 -> Color.White.copy(alpha = 0.08f)
    1 -> OtoColors.Accent.copy(alpha = 0.30f)
    2 -> OtoColors.Accent.copy(alpha = 0.52f)
    3 -> OtoColors.Accent.copy(alpha = 0.78f)
    else -> OtoColors.Accent
}

internal fun calendarSentence(d: StatsData, kind: PeriodKind): String? {
    if (kind == PeriodKind.WEEK) return null
    val shown = (ChronoUnit.DAYS.between(d.heatStart, minOf(d.heatEnd, d.today)) + 1).toInt().coerceAtLeast(1)
    val span = if (kind == PeriodKind.ALL) "the last year" else "$shown ${if (shown == 1) "day" else "days"}"
    return if (kind == PeriodKind.ALL) "You listened on ${d.activeDaysInPeriod} days in total." else "You listened on ${d.activeDaysInPeriod} of $span."
}

/** The month as a calendar, or a year as a 12-row mosaic. Brighter yellow is a bigger day. */
@Composable
fun CalendarCard(d: StatsData, kind: PeriodKind) {
    if (kind == PeriodKind.WEEK) return
    val dayMs = remember(d.daily) { d.daily.associate { it.day to it.playedMs } }
    val max = remember(d.daily) { (d.daily.maxOfOrNull { it.playedMs } ?: 0L).coerceAtLeast(1L) }
    val longest = d.moments.longestDay?.takeIf { it.playedMs > 0 }
    val longestDate = longest?.let { LocalDate.ofEpochDay(it.day) }

    StatsCard(Modifier.semantics { contentDescription = calendarSentence(d, kind).orEmpty() }) {
        if (kind == PeriodKind.MONTH) MonthGrid(d, dayMs, max) else YearMosaic(d, dayMs, max)
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            if (longest != null && longestDate != null) {
                Fact(formatDuration(longest.playedMs), "Longest day, ${longestDate.month.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault())} ${longestDate.dayOfMonth}")
            }
            if (d.streaks.current >= 2) Fact("${d.streaks.current}", "Day streak")
            else if (d.streaks.longest >= 2) Fact("${d.streaks.longest}", "Best streak")
        }
    }
}

@Composable
private fun Fact(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = OtoColors.TextPrimary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = OtoColors.TextTertiary)
    }
}

@Composable
private fun MonthGrid(d: StatsData, dayMs: Map<Long, Long>, max: Long) {
    val first = d.heatStart
    val lead = first.dayOfWeek.value - 1 // blanks before the 1st, Monday first
    val total = d.heatEnd.dayOfMonth
    val rows = (lead + total + 6) / 7
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (w in 1..7) {
                Text(
                    DayOfWeek.of(w).getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = OtoColors.TextTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..total) {
                            val date = first.withDayOfMonth(dayNum)
                            val lvl = if (date > d.today) -1 else level(dayMs[date.toEpochDay()] ?: 0L, max)
                            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(heat(lvl)))
                            Text(
                                dayNum.toString(),
                                style = OtoNumbers.Small,
                                color = if (lvl >= 2) OtoColors.OnAccent else OtoColors.TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearMosaic(d: StatsData, dayMs: Map<Long, Long>, max: Long) {
    val months = remember(d.heatStart) { (0 until 12).map { d.heatStart.withDayOfMonth(1).plusMonths(it.toLong()) } }
    val measurer = rememberTextMeasurer()
    val labelStyle = ComposeTextStyle(color = OtoColors.TextTertiary, fontSize = 10.sp)
    Canvas(Modifier.fillMaxWidth().height(172.dp)) {
        val labelW = 18.dp.toPx()
        val gap = 2.dp.toPx()
        val cell = (size.width - labelW - gap * 30) / 31f
        val rowH = (size.height - gap * 11) / 12f
        val corner = CornerRadius(2.dp.toPx())
        months.forEachIndexed { row, monthStart ->
            val y = row * (rowH + gap)
            val name = monthStart.month.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault())
            drawText(measurer, name, Offset(0f, y + (rowH - 12.sp.toPx()) / 2), labelStyle)
            if (monthStart > d.heatEnd && monthStart > d.today) return@forEachIndexed
            for (day in 1..monthStart.lengthOfMonth()) {
                val date = monthStart.withDayOfMonth(day)
                if (date < d.heatStart || date > d.heatEnd) continue
                val lvl = if (date > d.today) -1 else level(dayMs[date.toEpochDay()] ?: 0L, max)
                drawRoundRect(
                    color = heat(lvl),
                    topLeft = Offset(labelW + (day - 1) * (cell + gap), y),
                    size = Size(cell, rowH),
                    cornerRadius = corner,
                )
            }
        }
    }
}
