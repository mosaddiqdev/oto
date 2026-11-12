package com.oto.stats.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oto.stats.stats.ActivityBar
import com.oto.stats.stats.BarState
import com.oto.stats.stats.BarUnit
import com.oto.stats.stats.StatsData
import com.oto.stats.ui.theme.OtoColors
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateStyle
import java.util.Locale

/*
 * Your days. Spec: docs/STATS_DAYS.md.
 * One chart: bars for each day (or month, or year). The busiest is lit; touch or drag to read any other.
 * A dashed line marks the average. No axis, no gridlines: the readout above says the number.
 */

private val BarGrey = Color(0xFF2B2B2B)

private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
private val yearFmt = DateTimeFormatter.ofPattern("yyyy", Locale.getDefault())

private fun name(b: ActivityBar): String = when (b.unit) {
    BarUnit.DAY -> dayFmt.format(b.start)
    BarUnit.MONTH -> monthFmt.format(b.start)
    BarUnit.YEAR -> yearFmt.format(b.start)
}

private fun unitWord(u: BarUnit) = when (u) { BarUnit.DAY -> "day"; BarUnit.MONTH -> "month"; BarUnit.YEAR -> "year" }

private fun peakIndex(bars: List<ActivityBar>): Int =
    bars.indices.filter { bars[it].state == BarState.TRACKED && bars[it].playedMs > 0 }.maxByOrNull { bars[it].playedMs } ?: -1

/** The sentence above the card. Null when there is nothing to say. */
fun daysSentence(bars: List<ActivityBar>): String? {
    val i = peakIndex(bars)
    if (i < 0) return null
    val b = bars[i]
    val active = bars.count { it.state == BarState.TRACKED && it.playedMs > 0 }
    if (active < 2) return "Just one ${unitWord(b.unit)} of listening so far."
    return when (b.unit) {
        BarUnit.DAY -> "Your busiest day was ${DateTimeFormatter.ofPattern("EEE d", Locale.getDefault()).format(b.start)}, with ${formatDuration(b.playedMs)}."
        BarUnit.MONTH -> "${b.start.month.getDisplayName(DateStyle.FULL, Locale.getDefault())} was your biggest month, with ${formatDuration(b.playedMs)}."
        BarUnit.YEAR -> "${b.start.year} was your biggest year, with ${formatDuration(b.playedMs)}."
    }
}

private fun axisLabel(bars: List<ActivityBar>, i: Int): String? {
    val b = bars[i]
    return when (b.unit) {
        BarUnit.DAY ->
            if (bars.size <= 7) b.start.dayOfWeek.getDisplayName(DateStyle.NARROW, Locale.getDefault())
            else if (i % 7 == 0) b.start.dayOfMonth.toString() else null
        BarUnit.MONTH ->
            if (bars.size <= 12) b.start.month.getDisplayName(DateStyle.NARROW, Locale.getDefault())
            else if (b.start.monthValue == 1 || i == 0) "'" + (b.start.year % 100).toString().padStart(2, '0') else null
        BarUnit.YEAR -> if (bars.size <= 8 || i % 2 == 0) b.start.year.toString() else null
    }
}

@Composable
fun DaysCard(d: StatsData, key: String, modifier: Modifier = Modifier) {
    val bars = d.bars
    val haptic = LocalHapticFeedback.current
    val reduced = rememberReducedMotion()
    val peak = remember(bars) { peakIndex(bars) }
    var picked by rememberSaveable(key) { mutableIntStateOf(-1) }
    val selected = if (picked in bars.indices && bars[picked].state == BarState.TRACKED) picked else peak

    val tracked = remember(bars) { bars.filter { it.state == BarState.TRACKED } }
    val maxMs = remember(bars) { (tracked.maxOfOrNull { it.playedMs } ?: 0L).coerceAtLeast(1L) }
    val avgMs = if (tracked.size >= 3) tracked.sumOf { it.playedMs } / tracked.size else 0L

    var played by rememberSaveable(key) { mutableStateOf(false) }
    val intro = remember(key) { Animatable(if (reduced || played) 1f else 0f) }
    LaunchedEffect(key) {
        if (!reduced && !played) {
            intro.animateTo(1f, tween(900, delayMillis = 150))
            played = true
        }
    }

    val summary = buildString {
        append("Listening by ${unitWord(bars.firstOrNull()?.unit ?: BarUnit.DAY)}. ")
        if (peak >= 0) append("Highest: ${name(bars[peak])}, ${formatDuration(bars[peak].playedMs)}. ")
        if (avgMs > 0) append("Average ${formatDuration(avgMs)}.")
    }

    StatsCard(modifier.semantics { contentDescription = summary }) {
        val s = selected.takeIf { it >= 0 }?.let { bars[it] }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    s?.let { formatDuration(it.playedMs) } ?: "–",
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    color = OtoColors.TextPrimary,
                )
                Text(
                    s?.let { name(it) + if (it.plays > 0) " · ${formatCount(it.plays)} ${if (it.plays == 1) "play" else "plays"}" else "" } ?: "No listening yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = OtoColors.TextTertiary,
                )
            }
            if (avgMs > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Canvas(Modifier.padding(end = 6.dp).height(2.dp).width(14.dp)) {
                        drawLine(Color.White.copy(alpha = 0.45f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
                    }
                    Text("avg ${formatDuration(avgMs)}", style = MaterialTheme.typography.bodySmall, color = OtoColors.TextTertiary)
                }
            }
        }

        val measurer = rememberTextMeasurer()
        val labelStyle = TextStyle(fontSize = 11.sp, color = OtoColors.TextTertiary, fontWeight = FontWeight.Medium)
        val labelLit = labelStyle.copy(color = OtoColors.TextPrimary, fontWeight = FontWeight.SemiBold)
        Canvas(
            Modifier
                .padding(top = 20.dp)
                .fillMaxWidth()
                .height(156.dp)
                .pointerInput(bars) {
                    fun pick(x: Float) {
                        if (bars.isEmpty()) return
                        val i = (x / (size.width / bars.size)).toInt().coerceIn(0, bars.size - 1)
                        if (i != picked && bars[i].state == BarState.TRACKED) {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            picked = i
                        }
                    }
                    detectHorizontalDragGestures(onDragStart = { pick(it.x) }) { change, _ -> pick(change.position.x) }
                }
                .pointerInput(bars) {
                    detectTapGestures { offset ->
                        if (bars.isEmpty()) return@detectTapGestures
                        val i = (offset.x / (size.width / bars.size)).toInt().coerceIn(0, bars.size - 1)
                        if (i != picked && bars[i].state == BarState.TRACKED) {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            picked = i
                        }
                    }
                },
        ) {
            val n = bars.size
            if (n == 0) return@Canvas
            val labelsH = 22.dp.toPx()
            val chartH = size.height - labelsH
            val slot = size.width / n
            val barW = (slot * 0.62f).coerceAtMost(28.dp.toPx()).coerceAtLeast(2.dp.toPx())
            val radius = CornerRadius(minOf(barW / 2f, 6.dp.toPx()))
            val stub = 3.dp.toPx()
            val p = intro.value

            for (i in 0 until n) {
                val b = bars[i]
                val x = i * slot + (slot - barW) / 2f
                when (b.state) {
                    BarState.BEFORE_TRACKING, BarState.FUTURE -> {
                        val a = if (b.state == BarState.FUTURE) 0.05f else 0.09f
                        drawCircle(Color.White.copy(alpha = a), radius = 1.5.dp.toPx(), center = Offset(x + barW / 2f, chartH - 2.dp.toPx()))
                    }
                    BarState.TRACKED -> {
                        // Each bar rises on its own clock, a little after the one before, with an ease-out and no overshoot.
                        val local = ((p * (1f + n * 0.03f) - i * 0.03f)).coerceIn(0f, 1f)
                        val eased = 1f - (1f - local) * (1f - local) * (1f - local)
                        val full = if (b.playedMs <= 0L) stub else (chartH * (b.playedMs.toFloat() / maxMs)).coerceAtLeast(stub + 1.dp.toPx())
                        val h = full * eased
                        if (b.playedMs <= 0L) {
                            drawRoundRect(Color.White.copy(alpha = 0.06f * eased), Offset(x, chartH - stub), Size(barW, stub), radius)
                        } else if (i == selected) {
                            drawRoundRect(Brush.verticalGradient(listOf(OtoColors.Accent, Color(0xFFC9A91C)), startY = chartH - h, endY = chartH), Offset(x, chartH - h), Size(barW, h), radius)
                        } else {
                            drawRoundRect(BarGrey, Offset(x, chartH - h), Size(barW, h), radius)
                        }
                    }
                }
            }
            if (avgMs > 0) {
                val y = chartH - chartH * (avgMs.toFloat() / maxMs) * intro.value
                drawLine(
                    Color.White.copy(alpha = 0.40f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())),
                )
            }
            for (i in 0 until n) {
                val text = axisLabel(bars, i) ?: continue
                val layout = measurer.measure(text, if (i == selected) labelLit else labelStyle)
                val cx = i * slot + slot / 2f
                val x = (cx - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(x, chartH + 8.dp.toPx()))
            }
        }
    }
}

