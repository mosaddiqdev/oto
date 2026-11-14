package com.oto.stats.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * Records. Spec: docs/STATS_RECORDS.md (v3).
 * One card, all three bests at once, no tabs. The biggest day is the hero numeral. Your own days form a skyline:
 * the record day is lit, the streak is a bracket under the baseline, the most-plays day wears a ring.
 * Below, the streak and the most-plays record sit side by side, each keyed by its own mark.
 */

private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val shortFmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/** The sentence above the card. */
fun recordsSentence(d: StatsData): String? {
    val r = d.records ?: return null
    if (r.biggestDay == null) return null
    if (r.anyAllTimeBest) {
        val which = when {
            r.biggestDay.allTimeBest -> "your biggest day ever"
            r.longestRun?.allTimeBest == true -> "your longest streak ever"
            else -> "your most plays in a day"
        }
        return "A personal best: $which is in this window."
    }
    return "Your biggest day was ${dayFmt.format(r.biggestDay.day)}, with ${formatDuration(r.biggestDay.value)}."
}

@Composable
fun RecordsCard(d: StatsData, kind: PeriodKind, firstTracked: LocalDate?, key: String, modifier: Modifier = Modifier) {
    val r = d.records ?: return
    val big = r.biggestDay ?: return
    val run = r.longestRun
    val plays = r.mostPlaysDay
    val reduced = rememberReducedMotion()

    val start = (if (kind == PeriodKind.ALL) firstTracked else null) ?: d.heatStart
    val end = if (d.heatEnd.isAfter(d.today)) d.today else d.heatEnd
    val cols = remember(d.daily, start, end, big.day) { Skyline.build(d.daily, start, end, 100, { it.playedMs }, { it == big.day }) }
    val runCols = remember(d.daily, start, end, run) {
        if (run == null) emptyList() else Skyline.build(d.daily, start, end, 100, { it.playedMs }, { !it.isBefore(run.start) && !it.isAfter(run.end) })
    }
    val playCols = remember(d.daily, start, end, plays) {
        if (plays == null) emptyList() else Skyline.build(d.daily, start, end, 100, { it.playedMs }, { it == plays.day })
    }
    val showRef = !big.allTimeBest && r.allTimeDayMs > 0L
    val refText = "All-time best ${formatDuration(r.allTimeDayMs)}"

    val grow = remember(key) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(key) {
        if (reduced) grow.snapTo(1f) else { grow.snapTo(0f); grow.animateTo(1f, tween(900, delayMillis = 80)) }
    }
    val summary = buildString {
        append("Biggest day: ${formatDuration(big.value)}, ${dayFmt.format(big.day)}.")
        if (big.allTimeBest) append(" All-time best.")
        run?.let { append(" Longest streak: ${it.length} days, ${shortFmt.format(it.start)} to ${shortFmt.format(it.end)}.") }
        plays?.let { append(" Most plays in a day: ${it.value}, ${dayFmt.format(it.day)}.") }
    }

    StatsCard(modifier.semantics { contentDescription = summary }) {
        Row(verticalAlignment = Alignment.Bottom) {
            val lead = formatDuration(big.value)
            Text(lead, style = giant(if (lead.length > 5) 54f else 64f, -2.4).copy(brush = LitDigits, fontFeatureSettings = "tnum"), modifier = Modifier.alignByBaseline(), maxLines = 1)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Biggest day \u00b7 ${dayFmt.format(big.day)}", style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = OtoColors.TextSecondary, maxLines = 1)
            if (big.allTimeBest) Tag("ALL-TIME BEST")
        }

        val measurer = rememberTextMeasurer()
        val refStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = OtoColors.TextTertiary)
        val streakTint = OtoColors.Accent.copy(alpha = 0.34f)
        Canvas(Modifier.padding(top = 20.dp).fillMaxWidth().height(if (run != null) 156.dp else 140.dp)) {
            if (cols.isEmpty()) return@Canvas
            val bracketRoom = if (run != null) 20.dp.toPx() else 0f
            val scaleMax = maxOf(cols.maxOf { it.value }, if (showRef) r.allTimeDayMs else 0L).coerceAtLeast(1L)
            val top = 20.dp.toPx() // room for the ring and the all-time label
            val base = size.height - bracketRoom
            val usable = base - top
            val n = cols.size
            val slot = size.width / n
            val barW = (slot * 0.68f).coerceIn(1.5.dp.toPx(), 14.dp.toPx())
            val rad = CornerRadius(minOf(barW / 2f, 3.dp.toPx()))
            val g = grow.value
            fun eased(i: Int): Float {
                val local = (g * (1f + n * 0.01f) - i * 0.01f).coerceIn(0f, 1f)
                return 1f - (1f - local) * (1f - local) * (1f - local)
            }
            cols.forEachIndexed { i, c ->
                if (c.value <= 0L) return@forEachIndexed
                val h = (usable * c.value / scaleMax * eased(i)).coerceAtLeast(2.dp.toPx())
                val x = i * slot + (slot - barW) / 2f
                when {
                    c.lit -> drawRoundRect(Brush.verticalGradient(listOf(OtoColors.Accent, Color(0xFFC9A91C)), startY = base - h, endY = base), Offset(x, base - h), Size(barW, h), rad)
                    runCols.getOrNull(i)?.lit == true -> drawRoundRect(streakTint, Offset(x, base - h), Size(barW, h), rad)
                    else -> drawRoundRect(Color(0xFF2B2B2B), Offset(x, base - h), Size(barW, h), rad)
                }
            }
            drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, base), Offset(size.width, base), 1.dp.toPx())

            if (showRef) {
                val y = base - usable * r.allTimeDayMs / scaleMax
                drawLine(Color.White.copy(alpha = 0.40f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())))
                val l = measurer.measure(refText, refStyle)
                drawText(l, topLeft = Offset(size.width - l.size.width, y - l.size.height - 3.dp.toPx()))
            }

            // Most plays: a ring above that day's column.
            val pi = playCols.indexOfFirst { it.lit }
            if (pi >= 0) {
                val a = ((g - 0.7f) / 0.3f).coerceIn(0f, 1f)
                val cx = pi * slot + slot / 2f
                val h = usable * cols[pi].value / scaleMax
                val cy = (base - h - 9.dp.toPx()).coerceAtLeast(5.dp.toPx())
                drawCircle(Color.White.copy(alpha = 0.9f * a), 4.dp.toPx(), Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
            }

            // Streak: a bracket under the baseline across the days.
            val first = runCols.indexOfFirst { it.lit }
            val last = runCols.indexOfLast { it.lit }
            if (first >= 0) {
                val x0 = first * slot + (slot - barW) / 2f
                val x1 = (last * slot + (slot + barW) / 2f).coerceAtLeast(x0 + 8.dp.toPx())
                val a = ((g - 0.5f) / 0.5f).coerceIn(0f, 1f)
                val w = (x1 - x0) * a
                val y = base + 12.dp.toPx()
                val sw = 2.dp.toPx()
                drawLine(OtoColors.Accent, Offset(x0, y), Offset(x0 + w, y), sw, StrokeCap.Round)
                drawLine(OtoColors.Accent, Offset(x0, y - 5.dp.toPx()), Offset(x0, y), sw, StrokeCap.Round)
                if (a >= 1f) drawLine(OtoColors.Accent, Offset(x1, y - 5.dp.toPx()), Offset(x1, y), sw, StrokeCap.Round)
            }
        }

        if (run != null || plays != null) {
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                if (run != null) {
                    Stat(
                        Modifier.weight(1f), label = "STREAK", key = { StreakKey() },
                        value = run.length.toString(), unit = if (run.length == 1) "day" else "days",
                        sub = "${shortFmt.format(run.start)} \u2013 ${shortFmt.format(run.end)}",
                        best = run.allTimeBest, ref = "Longest ever ${r.allTimeRun}",
                    )
                }
                if (run != null && plays != null) {
                    Box(Modifier.padding(horizontal = 16.dp).width(1.dp).fillMaxHeight().background(OtoColors.Hairline))
                }
                if (plays != null) {
                    Stat(
                        Modifier.weight(1f), label = "MOST PLAYS", key = { PlaysKey() },
                        value = formatCount(plays.value.toInt()), unit = if (plays.value == 1L) "play" else "plays",
                        sub = dayFmt.format(plays.day),
                        best = plays.allTimeBest, ref = "Best ever ${formatCount(r.allTimePlays)}",
                    )
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
        color = OtoColors.Accent,
        modifier = Modifier.clip(CircleShape).background(OtoColors.Accent.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun Stat(
    modifier: Modifier, label: String, key: @Composable () -> Unit, value: String, unit: String,
    sub: String, best: Boolean, ref: String,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            key()
            Text(label, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Medium), color = OtoColors.TextTertiary, maxLines = 1)
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text(value, style = giant(30f, -0.9).copy(fontFeatureSettings = "tnum"), color = OtoColors.TextPrimary, modifier = Modifier.alignByBaseline(), maxLines = 1)
            Text(unit, style = MaterialTheme.typography.bodyMedium, color = OtoColors.TextTertiary, modifier = Modifier.alignByBaseline().padding(start = 6.dp), maxLines = 1)
        }
        Text(sub, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = OtoColors.TextSecondary, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
        if (best) {
            Text("All-time best", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = OtoColors.Accent, modifier = Modifier.padding(top = 2.dp))
        } else {
            Text(ref, style = MaterialTheme.typography.labelSmall, color = OtoColors.TextTertiary, modifier = Modifier.padding(top = 2.dp), maxLines = 1)
        }
    }
}

@Composable
private fun StreakKey() {
    Canvas(Modifier.size(width = 18.dp, height = 10.dp)) {
        val sw = 2.dp.toPx(); val y = size.height - 1.dp.toPx()
        drawLine(OtoColors.Accent, Offset(1.dp.toPx(), y), Offset(size.width - 1.dp.toPx(), y), sw, StrokeCap.Round)
        drawLine(OtoColors.Accent, Offset(1.dp.toPx(), y - 5.dp.toPx()), Offset(1.dp.toPx(), y), sw, StrokeCap.Round)
        drawLine(OtoColors.Accent, Offset(size.width - 1.dp.toPx(), y - 5.dp.toPx()), Offset(size.width - 1.dp.toPx(), y), sw, StrokeCap.Round)
    }
}

@Composable
private fun PlaysKey() {
    Canvas(Modifier.size(10.dp)) {
        drawCircle(Color.White.copy(alpha = 0.9f), 4.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
    }
}
