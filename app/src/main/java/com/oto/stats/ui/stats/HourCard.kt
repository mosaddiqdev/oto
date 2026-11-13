package com.oto.stats.ui.stats

import android.text.format.DateFormat
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oto.stats.stats.Daypart
import com.oto.stats.stats.Insights
import com.oto.stats.stats.StatsData
import com.oto.stats.ui.theme.OtoColors
import com.oto.stats.ui.theme.OtoSpace

/*
 * Your hour. Spec: docs/STATS_HOUR.md.
 * Not a clock. Your day drawn as a ribbon from midnight to midnight, with the hour as the headline numeral.
 * Drag along the ribbon and the numeral becomes that hour. Underneath, the day in four parts.
 */

private fun hourParts(h: Int, is24: Boolean): Pair<String, String> =
    if (is24) h.toString().padStart(2, '0') to ":00"
    else (if (h % 12 == 0) 12 else h % 12).toString() to (if (h < 12) "AM" else "PM")

private fun hourText(h: Int, is24: Boolean): String = hourParts(h, is24).let { if (is24) it.first + it.second else "${it.first} ${it.second}" }

private fun partName(p: Daypart) = when (p) {
    Daypart.NIGHT -> "late nights"
    Daypart.MORNING -> "mornings"
    Daypart.AFTERNOON -> "afternoons"
    Daypart.EVENING -> "evenings"
}

private fun peakHour(hours: List<Long>): Int = hours.indices.maxByOrNull { hours[it] } ?: 0

/** The sentence above the card. Null when there is nothing to say. */
fun hourSentence(hours: List<Long>?, is24: Boolean): String? {
    if (hours == null || hours.sum() <= 0L) return null
    val shares = Insights.daypartShares(hours)
    val top = shares.maxByOrNull { it.second }!!
    val peak = hourText(peakHour(hours), is24)
    return if (top.second >= 35) "Mostly ${partName(top.first)}, peaking at $peak." else "You listen all through the day, peaking at $peak."
}

@Composable
fun HourCard(d: StatsData, key: String, modifier: Modifier = Modifier) {
    val hours = d.hours ?: return
    val total = hours.sum()
    if (total <= 0L) return
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    val haptic = LocalHapticFeedback.current
    val reduced = rememberReducedMotion()
    val peak = remember(hours) { peakHour(hours) }
    var picked by rememberSaveable(key) { mutableIntStateOf(-1) }
    val selected = if (picked in 0..23) picked else peak
    val max = hours.max().coerceAtLeast(1L)
    val shares = remember(hours) { Insights.daypartShares(hours) }
    val topPart = shares.maxByOrNull { it.second }?.first

    var played by rememberSaveable(key) { mutableStateOf(false) }
    val intro = remember(key) { Animatable(if (reduced || played) 1f else 0f) }
    LaunchedEffect(key) {
        if (!reduced && !played) {
            intro.animateTo(1f, tween(1000, delayMillis = 150))
            played = true
        }
    }

    val summary = "Listening by hour. Peak ${hourText(peak, is24)}, ${hours[peak] * 100 / total} percent of listening. " +
        shares.joinToString(" ") { "${it.first.label} ${it.second} percent." }

    StatsCard(modifier.semantics { contentDescription = summary }) {
        val (lead, unit) = hourParts(selected, is24)
        val pct = hours[selected] * 100.0 / total
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    if (selected == peak && picked < 0) "YOUR PEAK HOUR" else "AT THIS HOUR",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
                    color = OtoColors.TextTertiary,
                )
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        lead,
                        style = giant(76f, -3.4).copy(brush = LitDigits, fontFeatureSettings = "tnum"),
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(unit, style = giant(26f, -0.3), color = OtoColors.TextTertiary, modifier = Modifier.alignByBaseline().padding(start = 6.dp))
                }
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    if (pct >= 1.0 || hours[selected] == 0L) "${Math.round(pct)}%" else "<1%",
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    color = OtoColors.TextPrimary,
                )
                Text("of your listening", style = MaterialTheme.typography.bodySmall, color = OtoColors.TextTertiary)
                Text(formatDuration(hours[selected]), style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = OtoColors.TextSecondary)
            }
        }

        val measurer = rememberTextMeasurer()
        val labelStyle = TextStyle(fontSize = 11.sp, color = OtoColors.TextTertiary, fontWeight = FontWeight.Medium)
        val axis = if (is24) listOf("00", "06", "12", "18") else listOf("12 AM", "6 AM", "12 PM", "6 PM")
        Canvas(
            Modifier
                .padding(top = 20.dp)
                .fillMaxWidth()
                .height(148.dp)
                .pointerInput(Unit) {
                    fun pick(x: Float) {
                        val h = (x / (size.width / 24f)).toInt().coerceIn(0, 23)
                        if (h != picked) {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            picked = h
                        }
                    }
                    detectHorizontalDragGestures(onDragStart = { pick(it.x) }) { change, _ -> pick(change.position.x) }
                }
                .pointerInput(Unit) {
                    detectTapGestures { o ->
                        val h = (o.x / (size.width / 24f)).toInt().coerceIn(0, 23)
                        if (h != picked) {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            picked = h
                        }
                    }
                },
        ) {
            val labelsH = 24.dp.toPx()
            val ribbonH = size.height - labelsH
            val w = size.width
            // Daylight: the ribbon is a touch lighter at midday and darkest at night. No new hue, only luminance.
            drawRoundRect(
                Brush.horizontalGradient(listOf(Color(0xFF131313), Color(0xFF1F1F1F), Color(0xFF131313)), startX = 0f, endX = w),
                size = Size(w, ribbonH), cornerRadius = CornerRadius(20.dp.toPx()),
            )
            val top = ribbonH * 0.16f
            val usable = ribbonH * 0.70f
            fun px(i: Int) = (i + 0.5f) * w / 24f
            fun py(i: Int) = top + usable * (1f - hours[i].toFloat() / max)
            // Smooth curve through every hour. Horizontal tangents at each point: it never overshoots the data.
            val line = Path().apply {
                moveTo(0f, py(0))
                lineTo(px(0), py(0))
                for (i in 0 until 23) {
                    val xm = (px(i) + px(i + 1)) / 2f
                    cubicTo(xm, py(i), xm, py(i + 1), px(i + 1), py(i + 1))
                }
                lineTo(w, py(23))
            }
            val area = Path().apply {
                addPath(line)
                lineTo(w, ribbonH)
                lineTo(0f, ribbonH)
                close()
            }
            val reveal = intro.value
            // Everything inside the band is clipped to the band's own rounded shape, so nothing pokes out of a corner.
            val band = Path().apply { addRoundRect(RoundRect(0f, 0f, w, ribbonH, CornerRadius(20.dp.toPx()))) }
            clipPath(band) { clipRect(left = 0f, top = 0f, right = w * reveal, bottom = ribbonH) {
                drawPath(area, Brush.verticalGradient(listOf(OtoColors.Accent.copy(alpha = 0.42f), OtoColors.Accent.copy(alpha = 0.0f)), startY = top, endY = ribbonH))
                drawPath(
                    line, Brush.horizontalGradient(listOf(Color(0xFFC9A91C), OtoColors.Accent, Color(0xFFFFF4A8)), startX = 0f, endX = w),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            } }
            // The selected hour: a hairline, a halo and a lit dot, on the curve.
            val sx = px(selected)
            val sy = py(selected)
            val a = ((reveal - 0.6f) / 0.4f).coerceIn(0f, 1f)
            if (a > 0f) {
                drawLine(Color.White.copy(alpha = 0.16f * a), Offset(sx, 10.dp.toPx()), Offset(sx, ribbonH - 10.dp.toPx()), 1.dp.toPx())
                drawCircle(OtoColors.Accent.copy(alpha = 0.22f * a), 11.dp.toPx(), Offset(sx, sy))
                drawCircle(OtoColors.Accent.copy(alpha = a), 5.5.dp.toPx(), Offset(sx, sy))
                drawCircle(Color(0xFF151515).copy(alpha = a), 2.dp.toPx(), Offset(sx, sy))
            }
            axis.forEachIndexed { k, text ->
                val layout = measurer.measure(text, labelStyle)
                val cx = px(k * 6) - w / 48f
                val x = if (k == 0) 4.dp.toPx() else (cx - layout.size.width / 2f).coerceAtMost(w - layout.size.width)
                drawText(layout, topLeft = Offset(x, ribbonH + 8.dp.toPx()))
            }
        }

        // The day in four parts: one bar for the proportions, one row for the numbers.
        Canvas(Modifier.padding(top = 20.dp).fillMaxWidth().height(8.dp)) {
            val gap = 3.dp.toPx()
            val usableW = size.width - gap * 3
            var x = 0f
            val sum = shares.sumOf { it.second }.coerceAtLeast(1)
            shares.forEach { (part, share) ->
                val seg = (usableW * share / sum).coerceAtLeast(if (share > 0) size.height else 0f)
                if (seg > 0f) {
                    val lit = part == topPart
                    drawRoundRect(
                        if (lit) Brush.horizontalGradient(listOf(Color(0xFFC9A91C), OtoColors.Accent), startX = x, endX = x + seg) else Brush.horizontalGradient(listOf(Color(0xFF2F2F2F), Color(0xFF3A3A3A)), startX = x, endX = x + seg),
                        topLeft = Offset(x, 0f), size = Size(seg, size.height), cornerRadius = CornerRadius(size.height / 2),
                    )
                }
                x += seg + gap
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            shares.forEach { (part, share) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$share%",
                        style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                        color = if (part == topPart) OtoColors.TextPrimary else OtoColors.TextSecondary,
                    )
                    Text(part.label, style = MaterialTheme.typography.bodySmall, color = OtoColors.TextTertiary)
                }
            }
        }
    }
}
