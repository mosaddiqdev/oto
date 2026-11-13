package com.oto.stats.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oto.stats.stats.PeriodKind
import com.oto.stats.stats.Skyline
import com.oto.stats.stats.StatsData
import com.oto.stats.ui.theme.OtoColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Records. Spec: docs/STATS_RECORDS.md.
 * One record at a time, as a giant numeral. Under it, your own days as a skyline with the record lit,
 * so you see the peak in the context of everything else. No icons. If it is not your all-time best,
 * a dashed line shows where your best sits above it.
 */

private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val shortFmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

private enum class Rec(val tab: String) { Day("Biggest day"), Streak("Streak"), Plays("Most plays") }

private class Plaque(
    val rec: Rec, val lead: String, val unit: String, val sub: String, val best: Boolean,
    val reference: String?, // text for the all-time line, null when this IS the all-time best
    val refValue: Long, // all-time level in the same units as the series, for the dashed line
    val series: (com.oto.stats.data.DayTotal) -> Long,
    val lit: (LocalDate) -> Boolean,
)

private fun plaques(d: StatsData): List<Plaque> {
    val r = d.records ?: return emptyList()
    val out = ArrayList<Plaque>(3)
    r.biggestDay?.let { b ->
        out.add(
            Plaque(
                Rec.Day, formatDuration(b.value), "", dayFmt.format(b.day), b.allTimeBest,
                if (b.allTimeBest) null else "All-time best ${formatDuration(r.allTimeDayMs)}", r.allTimeDayMs,
                { it.playedMs }, { it == b.day },
            ),
        )
    }
    r.longestRun?.let { run ->
        out.add(
            Plaque(
                Rec.Streak, run.length.toString(), "days in a row", "${shortFmt.format(run.start)} – ${shortFmt.format(run.end)}", run.allTimeBest,
                if (run.allTimeBest) null else "Your longest ever is ${r.allTimeRun} days", 0L,
                { it.playedMs }, { !it.isBefore(run.start) && !it.isAfter(run.end) },
            ),
        )
    }
    r.mostPlaysDay?.let { p ->
        out.add(
            Plaque(
                Rec.Plays, formatCount(p.value.toInt()), if (p.value == 1L) "play in a day" else "plays in a day", dayFmt.format(p.day), p.allTimeBest,
                if (p.allTimeBest) null else "All-time best ${formatCount(r.allTimePlays)}", r.allTimePlays.toLong(),
                { it.plays.toLong() }, { it == p.day },
            ),
        )
    }
    return out
}

/** The sentence above the card. */
fun recordsSentence(d: StatsData): String? {
    val r = d.records ?: return null
    if (plaques(d).isEmpty()) return null
    if (r.anyAllTimeBest) {
        val which = when {
            r.biggestDay?.allTimeBest == true -> "your biggest day ever"
            r.longestRun?.allTimeBest == true -> "your longest streak ever"
            else -> "your most plays in a day"
        }
        return "A personal best: $which is in this window."
    }
    return r.biggestDay?.let { "Your biggest day was ${dayFmt.format(it.day)}, with ${formatDuration(it.value)}." }
}

@Composable
fun RecordsCard(d: StatsData, kind: PeriodKind, firstTracked: LocalDate?, key: String, modifier: Modifier = Modifier) {
    val list = remember(d.records) { plaques(d) }
    if (list.isEmpty()) return
    val reduced = rememberReducedMotion()
    var tab by rememberSaveable(key) { mutableIntStateOf(0) }
    val p = list[tab.coerceIn(0, list.lastIndex)]

    // The skyline spans the window; for All time that is everything since tracking began.
    val start = (if (kind == PeriodKind.ALL) firstTracked else null) ?: d.heatStart
    val end = if (d.heatEnd.isAfter(d.today)) d.today else d.heatEnd
    val columns = remember(d.daily, p.rec, start, end) { Skyline.build(d.daily, start, end, 100, p.series, p.lit) }

    val grow = remember(key) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(key, p.rec) {
        if (reduced) grow.snapTo(1f) else { grow.snapTo(0f); grow.animateTo(1f, tween(800, delayMillis = 80)) }
    }
    val summary = "${p.rec.tab}: ${p.lead} ${p.unit}, ${p.sub}." + if (p.best) " All-time best." else (p.reference?.let { " $it." } ?: "")

    StatsCard(modifier.semantics { contentDescription = summary }) {
        Segmented(
            options = list.map { it.rec.tab },
            selectedIndex = tab.coerceIn(0, list.lastIndex),
            onSelect = { tab = it },
            modifier = Modifier.fillMaxWidth(),
            height = 40.dp,
        )
        Row(Modifier.padding(top = 22.dp), verticalAlignment = Alignment.Bottom) {
            Text(p.lead, style = giant(if (p.unit.isEmpty() && p.lead.length > 5) 52f else 64f, -2.4).copy(brush = LitDigits, fontFeatureSettings = "tnum"), modifier = Modifier.alignByBaseline(), maxLines = 1)
            if (p.unit.isNotEmpty()) {
                Text(p.unit, style = giant(20f, -0.3), color = OtoColors.TextTertiary, modifier = Modifier.alignByBaseline().padding(start = 8.dp), maxLines = 1)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(p.sub, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = OtoColors.TextSecondary, maxLines = 1)
            if (p.best) {
                Text(
                    "ALL-TIME BEST",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
                    color = OtoColors.Accent,
                    modifier = Modifier.clip(CircleShape).background(OtoColors.Accent.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        val measurer = rememberTextMeasurer()
        val refStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = OtoColors.TextTertiary)
        Canvas(Modifier.padding(top = 22.dp).fillMaxWidth().height(132.dp)) {
            if (columns.isEmpty()) return@Canvas
            val scaleMax = maxOf(columns.maxOf { it.value }, if (p.reference != null) p.refValue else 0L).coerceAtLeast(1L)
            val top = if (p.reference != null && p.refValue > 0) 16.dp.toPx() else 4.dp.toPx() // room for the label above a dashed line
            val base = size.height
            val usable = base - top
            val n = columns.size
            val slot = size.width / n
            val barW = (slot * 0.68f).coerceIn(1.5.dp.toPx(), 14.dp.toPx())
            val r = CornerRadius(minOf(barW / 2f, 3.dp.toPx()))
            val g = grow.value
            columns.forEachIndexed { i, c ->
                if (c.value <= 0L) return@forEachIndexed
                val local = ((g * (1f + n * 0.01f) - i * 0.01f)).coerceIn(0f, 1f)
                val eased = 1f - (1f - local) * (1f - local) * (1f - local)
                val h = (usable * c.value / scaleMax * eased).coerceAtLeast(2.dp.toPx())
                val x = i * slot + (slot - barW) / 2f
                if (c.lit) {
                    drawRoundRect(Brush.verticalGradient(listOf(OtoColors.Accent, Color(0xFFC9A91C)), startY = base - h, endY = base), Offset(x, base - h), Size(barW, h), r)
                } else {
                    drawRoundRect(Color(0xFF2B2B2B), Offset(x, base - h), Size(barW, h), r)
                }
            }
            drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, base), Offset(size.width, base), 1.dp.toPx())
            if (p.reference != null && p.refValue > 0L) {
                val y = base - usable * p.refValue / scaleMax
                drawLine(
                    Color.White.copy(alpha = 0.40f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())),
                )
                val l = measurer.measure(p.reference, refStyle)
                drawText(l, topLeft = Offset(size.width - l.size.width, y - l.size.height - 3.dp.toPx()))
            }
        }
        if (p.reference != null && p.refValue <= 0L) {
            Text(p.reference, style = MaterialTheme.typography.bodySmall, color = OtoColors.TextTertiary, modifier = Modifier.padding(top = 12.dp))
        }
    }
}
