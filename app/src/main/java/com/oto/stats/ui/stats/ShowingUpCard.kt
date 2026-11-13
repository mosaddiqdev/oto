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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oto.stats.stats.BarState
import com.oto.stats.stats.HeatDay
import com.oto.stats.stats.PeriodKind
import com.oto.stats.stats.ShowingUp
import com.oto.stats.stats.StatsData
import com.oto.stats.ui.theme.OtoColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

/*
 * Showing up. Spec: docs/STATS_SHOWING_UP.md.
 * Not a heatmap of identical squares. Each day is a bead; days in a row are strung together, so a streak
 * is a visible chain. Week and month are a calendar of big beads, year is twelve strings of small ones.
 */

private val Levels = floatArrayOf(0f, 0.34f, 0.56f, 0.78f, 1f)
private val Ink = Color(0xFF111111)

private class Geo(val calendar: Boolean, val rows: Int, val lead: Int, val monthStarts: List<LocalDate>, val w: Float, private val d: Density) {
    val header = if (calendar) with(d) { 22.dp.toPx() } else 0f
    val label = if (calendar) 0f else with(d) { 16.dp.toPx() }
    val cols = if (calendar) 7 else 31
    val pitchX = (w - label) / cols
    val pitchY = with(d) { (if (calendar) 44.dp else 20.dp).toPx() }
    val dot = with(d) { (if (calendar) 30.dp else 7.dp).toPx() }
    val pill = with(d) { (if (calendar) 38.dp else 11.dp).toPx() }
    fun cx(col: Int) = label + (col + 0.5f) * pitchX
    fun cy(row: Int) = header + (row + 0.5f) * pitchY
    val height get() = header + rows * pitchY + (if (calendar) 0f else with(d) { 4.dp.toPx() })
}

private fun monthStarts(from: LocalDate, to: LocalDate): List<LocalDate> {
    val out = ArrayList<LocalDate>()
    var m = from.withDayOfMonth(1)
    while (!m.isAfter(to)) { out.add(m); m = m.plusMonths(1) }
    return out
}

private fun geoFor(kind: PeriodKind, days: List<HeatDay>, w: Float, d: Density): Geo {
    val calendar = kind == PeriodKind.WEEK || kind == PeriodKind.MONTH
    val first = days.first().date
    return if (calendar) {
        val firstDow = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val lead = ((first.dayOfWeek.value - firstDow.value) + 7) % 7
        Geo(true, (lead + days.size + 6) / 7, lead, emptyList(), w, d)
    } else {
        val starts = monthStarts(first, days.last().date)
        Geo(false, starts.size, 0, starts, w, d)
    }
}

/** (col, row) of day number [i] in the list. */
private fun place(g: Geo, days: List<HeatDay>, i: Int): Pair<Int, Int> =
    if (g.calendar) (g.lead + i) % 7 to (g.lead + i) / 7
    else days[i].date.dayOfMonth - 1 to ChronoUnit.MONTHS.between(g.monthStarts.first(), days[i].date.withDayOfMonth(1)).toInt()

private fun hit(g: Geo, days: List<HeatDay>, x: Float, y: Float): Int? {
    val col = ((x - g.label) / g.pitchX).toInt().coerceIn(0, g.cols - 1)
    val row = ((y - g.header) / g.pitchY).toInt().coerceIn(0, g.rows - 1)
    val i = if (g.calendar) row * 7 + col - g.lead
    else {
        val d = g.monthStarts[row].plusDays(col.toLong())
        if (d.month != g.monthStarts[row].month) -1 else ChronoUnit.DAYS.between(days.first().date, d).toInt()
    }
    return i.takeIf { it in days.indices }
}

fun showingUpDays(d: StatsData, firstTracked: LocalDate?): List<HeatDay> =
    ShowingUp.build(d.heatStart, d.heatEnd, d.today, firstTracked, d.daily)

/** The sentence above the card. */
fun showingUpSentence(days: List<HeatDay>, kind: PeriodKind): String? {
    val elapsed = ShowingUp.elapsed(days)
    val active = ShowingUp.active(days)
    if (elapsed == 0 || active == 0) return null
    val span = if (kind == PeriodKind.ALL) " in the last 12 months" else ""
    val run = ShowingUp.longestRun(days)
    val tail = if (run != null && run.length >= 2) " Longest run: ${run.length} days." else ""
    return "You listened on $active of $elapsed days$span.$tail"
}

@Composable
fun ShowingUpCard(d: StatsData, kind: PeriodKind, firstTracked: LocalDate?, key: String, modifier: Modifier = Modifier) {
    val days = remember(d.daily, d.heatStart, d.heatEnd, d.today, firstTracked) { showingUpDays(d, firstTracked) }
    if (days.isEmpty()) return
    val haptic = LocalHapticFeedback.current
    val reduced = rememberReducedMotion()
    val active = ShowingUp.active(days)
    val elapsed = ShowingUp.elapsed(days)
    val run = remember(days) { ShowingUp.longestRun(days) }
    var picked by rememberSaveable(key) { mutableLongStateOf(Long.MIN_VALUE) }
    val selected = days.firstOrNull { it.date.toEpochDay() == picked && it.state == BarState.TRACKED }

    var played by rememberSaveable(key) { mutableStateOf(false) }
    val intro = remember(key) { Animatable(if (reduced || played) 1f else 0f) }
    LaunchedEffect(key) {
        if (!reduced && !played) {
            intro.animateTo(1f, tween(1100, delayMillis = 150))
            played = true
        }
    }

    val includesToday = !d.heatEnd.isBefore(d.today) && !d.heatStart.isAfter(d.today)
    val streakNow = includesToday && d.streaks.current >= 2
    val streakValue = if (streakNow) d.streaks.current else run?.length ?: 0
    val summary = "Days with listening: $active of $elapsed. " + (if (streakValue >= 2) "${if (streakNow) "Current" else "Longest"} streak $streakValue days." else "")

    StatsCard(modifier.semantics { contentDescription = summary }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    if (kind == PeriodKind.ALL) "ACTIVE DAYS · LAST 12 MONTHS" else "DAYS WITH LISTENING",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
                    color = OtoColors.TextTertiary,
                )
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 10.dp)) {
                    Text(active.toString(), style = giant(64f, -2.8).copy(brush = LitDigits, fontFeatureSettings = "tnum"), modifier = Modifier.alignByBaseline())
                    Text("of $elapsed", style = giant(22f, -0.3), color = OtoColors.TextTertiary, modifier = Modifier.alignByBaseline().padding(start = 8.dp))
                }
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 4.dp)) {
                Text(if (streakNow) "STREAK" else "LONGEST RUN", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp), color = OtoColors.TextTertiary)
                Text(
                    if (streakValue >= 2) "$streakValue days" else "–",
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    color = if (streakNow) OtoColors.Accent else OtoColors.TextPrimary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        val measurer = rememberTextMeasurer()
        val numStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
        val axisStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = OtoColors.TextTertiary)
        val numbers = remember(days) { days.map { measurer.measure(it.date.dayOfMonth.toString(), numStyle) } }
        val density = androidx.compose.ui.platform.LocalDensity.current
        var width by remember { mutableStateOf(0f) }
        val geo = if (width > 0f) geoFor(kind, days, width, density) else null
        val heightDp = geo?.let { with(density) { it.height.toDp() } } ?: 220.dp
        val weekdayNames = remember {
            val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
            (0 until 7).map { first.plus(it.toLong()).getDisplayName(DateStyle.NARROW, Locale.getDefault()) }
        }
        val monthNames = remember(days) {
            monthStarts(days.first().date, days.last().date).map { it.month.getDisplayName(DateStyle.NARROW, Locale.getDefault()) }
        }

        Canvas(
            Modifier
                .padding(top = 20.dp)
                .fillMaxWidth()
                .onSizeChanged { if (width != it.width.toFloat()) width = it.width.toFloat() }
                .height(heightDp)
                .pointerInput(days, kind) {
                    fun pick(x: Float, y: Float) {
                        val g = geoFor(kind, days, size.width.toFloat(), this)
                        val i = hit(g, days, x, y) ?: return
                        val day = days[i]
                        if (day.state != BarState.TRACKED) return
                        val e = day.date.toEpochDay()
                        if (e != picked) {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            picked = e
                        }
                    }
                    detectTapGestures { pick(it.x, it.y) }
                }
                .pointerInput(days, kind) {
                    fun pick(x: Float, y: Float) {
                        val g = geoFor(kind, days, size.width.toFloat(), this)
                        val i = hit(g, days, x, y) ?: return
                        val day = days[i]
                        if (day.state != BarState.TRACKED) return
                        val e = day.date.toEpochDay()
                        if (e != picked) {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            picked = e
                        }
                    }
                    detectHorizontalDragGestures(onDragStart = { pick(it.x, it.y) }) { change, _ -> pick(change.position.x, change.position.y) }
                },
        ) {
            val g = geo ?: return@Canvas
            val p = intro.value
            val n = days.size

            // Labels: weekday initials across the top of a calendar, month initials down the side of the year.
            if (g.calendar) {
                weekdayNames.forEachIndexed { c, name ->
                    val l = measurer.measure(name, axisStyle)
                    drawText(l, topLeft = Offset(g.cx(c) - l.size.width / 2f, (g.header - l.size.height) / 2f))
                }
            } else {
                monthNames.forEachIndexed { r, name ->
                    val l = measurer.measure(name, axisStyle)
                    drawText(l, topLeft = Offset(0f, g.cy(r) - l.size.height / 2f))
                }
            }

            // Strings first: every run of two or more listening days in a row gets a soft capsule behind its beads.
            var runStart = -1
            fun flush(endIdx: Int) {
                if (runStart >= 0 && endIdx - runStart >= 1) {
                    val (c0, r0) = place(g, days, runStart)
                    val (c1, _) = place(g, days, endIdx)
                    val x0 = g.cx(c0) - g.pill / 2f
                    val x1 = g.cx(c1) + g.pill / 2f
                    val a = ((p * 1.5f - 0.6f)).coerceIn(0f, 1f)
                    drawRoundRect(
                        OtoColors.Accent.copy(alpha = (if (g.calendar) 0.15f else 0.22f) * a),
                        topLeft = Offset(x0, g.cy(r0) - g.pill / 2f), size = Size(x1 - x0, g.pill), cornerRadius = CornerRadius(g.pill / 2f),
                    )
                }
                runStart = -1
            }
            for (i in 0 until n) {
                val (c, r) = place(g, days, i)
                val live = days[i].level > 0
                val prevLive = i > 0 && days[i - 1].level > 0 && place(g, days, i - 1).second == r
                if (live) {
                    if (!prevLive) { flush(i - 1); runStart = i }
                } else {
                    flush(i - 1)
                }
                if (c == g.cols - 1 && g.calendar) flush(i) // a run does not wrap onto the next row
            }
            flush(n - 1)

            // Beads.
            for (i in 0 until n) {
                val day = days[i]
                val (c, r) = place(g, days, i)
                val center = Offset(g.cx(c), g.cy(r))
                val local = (p * 1.5f - (i.toFloat() / n) * 0.5f).coerceIn(0f, 1f)
                val eased = 1f - (1f - local) * (1f - local) * (1f - local)
                val isSel = selected != null && day.date == selected.date
                when {
                    day.state == BarState.FUTURE -> {
                        if (g.calendar) drawNumber(numbers[i], center, Color.White.copy(alpha = 0.22f))
                        else drawCircle(Color.White.copy(alpha = 0.05f), g.dot / 2f * 0.6f, center)
                    }
                    day.state == BarState.BEFORE_TRACKING -> {
                        if (!g.calendar) drawCircle(Color.White.copy(alpha = 0.05f), g.dot / 2f * 0.6f, center)
                    }
                    day.level == 0 -> {
                        if (g.calendar) drawNumber(numbers[i], center, OtoColors.TextTertiary.copy(alpha = 0.85f))
                        else drawCircle(Color.White.copy(alpha = 0.10f), g.dot / 2f * 0.7f, center)
                    }
                    else -> {
                        val color = OtoColors.Accent.copy(alpha = Levels[day.level] * eased)
                        drawCircle(color, g.dot / 2f * (0.7f + 0.3f * eased), center)
                        if (g.calendar) drawNumber(numbers[i], center, (if (day.level >= 3) Ink else OtoColors.TextPrimary).copy(alpha = eased))
                    }
                }
                if (day.date == d.today) {
                    drawCircle(Color.White.copy(alpha = 0.55f), g.dot / 2f + 1.dp.toPx(), center, style = Stroke(1.25.dp.toPx()))
                }
                if (isSel) {
                    drawCircle(Color.White, g.dot / 2f + 1.5.dp.toPx(), center, style = Stroke(1.75.dp.toPx()))
                }
            }
        }

        // One fixed line, so the card never changes height when a day is read.
        Text(
            selected?.let {
                DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).format(it.date) + " · " +
                    if (it.playedMs > 0) "${formatDuration(it.playedMs)} · ${formatCount(it.plays)} ${if (it.plays == 1) "play" else "plays"}" else "No listening"
            } ?: "Tap a day to read it",
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = if (selected != null) OtoColors.TextSecondary else OtoColors.TextTertiary,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNumber(layout: androidx.compose.ui.text.TextLayoutResult, center: Offset, color: Color) {
    drawText(layout, color = color, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
}

/** A small flame for streaks, drawn as one path so it stays crisp at any size. */
@Composable
internal fun Flame(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun x(v: Float) = v * w
        fun y(v: Float) = v * h
        val path = Path().apply {
            moveTo(x(0.52f), y(0.02f))
            cubicTo(x(0.55f), y(0.25f), x(0.85f), y(0.38f), x(0.85f), y(0.68f))
            cubicTo(x(0.85f), y(0.88f), x(0.70f), y(1.0f), x(0.50f), y(1.0f))
            cubicTo(x(0.30f), y(1.0f), x(0.15f), y(0.88f), x(0.15f), y(0.68f))
            cubicTo(x(0.15f), y(0.52f), x(0.25f), y(0.42f), x(0.32f), y(0.34f))
            cubicTo(x(0.34f), y(0.46f), x(0.40f), y(0.52f), x(0.45f), y(0.52f))
            cubicTo(x(0.42f), y(0.34f), x(0.45f), y(0.14f), x(0.52f), y(0.02f))
            close()
        }
        drawPath(path, color)
        drawCircle(OtoColors.Surface1, w * 0.12f, Offset(x(0.5f), y(0.78f)))
    }
}
