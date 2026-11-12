package com.oto.stats.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oto.stats.stats.ActivityBar
import com.oto.stats.stats.BarState
import com.oto.stats.stats.BarUnit
import com.oto.stats.stats.PeriodKind
import com.oto.stats.stats.StatsData
import com.oto.stats.ui.theme.OtoColors
import com.oto.stats.ui.theme.OtoMotion
import com.oto.stats.ui.theme.OtoNumbers
import kotlinx.coroutines.delay
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.min

private fun barName(bar: ActivityBar, locale: Locale = Locale.getDefault()): String = when (bar.unit) {
    BarUnit.DAY -> "${bar.start.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)}, ${bar.start.month.getDisplayName(TextStyle.SHORT, locale)} ${bar.start.dayOfMonth}"
    BarUnit.MONTH -> "${bar.start.month.getDisplayName(TextStyle.FULL, locale)} ${bar.start.year}"
    BarUnit.YEAR -> bar.start.year.toString()
}

private fun axisLabel(bar: ActivityBar, index: Int, count: Int, locale: Locale = Locale.getDefault()): String? = when (bar.unit) {
    BarUnit.DAY -> if (count <= 7) bar.start.dayOfWeek.getDisplayName(TextStyle.NARROW, locale)
    else if (index % 7 == 0) bar.start.dayOfMonth.toString() else null
    BarUnit.MONTH -> if (count <= 12) bar.start.month.getDisplayName(TextStyle.NARROW, locale)
    else if (bar.start.monthValue == 1) bar.start.year.toString() else null
    BarUnit.YEAR -> if (count <= 8 || index % 2 == 0) bar.start.year.toString() else null
}

/** "Your busiest day was Saturday the 8th." The takeaway goes above the chart, as a sentence. */
internal fun daysSentence(bars: List<ActivityBar>): String? {
    val peak = bars.filter { it.state == BarState.TRACKED }.maxByOrNull { it.playedMs }?.takeIf { it.playedMs > 0 } ?: return null
    val locale = Locale.getDefault()
    return when (peak.unit) {
        BarUnit.DAY -> "Your busiest day was ${peak.start.dayOfWeek.getDisplayName(TextStyle.FULL, locale)} the ${peak.start.dayOfMonth}${ordinalSuffix(peak.start.dayOfMonth)}."
        BarUnit.MONTH -> "${peak.start.month.getDisplayName(TextStyle.FULL, locale)} was your busiest month."
        BarUnit.YEAR -> "${peak.start.year} was your busiest year."
    }
}

private fun ordinalSuffix(n: Int) = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }

/** The days of the period as rounded bars. The busiest is yellow; tap or drag to read any other. */
@Composable
fun DaysCard(d: StatsData, periodKey: Any, kind: PeriodKind) {
    val bars = d.bars
    val peak = remember(bars) { bars.indices.filter { bars[it].state == BarState.TRACKED }.maxByOrNull { bars[it].playedMs } ?: 0 }
    var picked by remember(periodKey, bars.size) { mutableIntStateOf(-1) }
    val shown = if (picked in bars.indices) picked else peak
    val bar = bars.getOrNull(shown)
    val max = (bars.maxOfOrNull { it.playedMs } ?: 0L).coerceAtLeast(1L)
    var widthPx by remember { mutableIntStateOf(1) }
    val haptic = LocalHapticFeedback.current
    val gap = if (bars.size > 20) 3.dp else 8.dp
    val spoken = daysSentence(bars) ?: "No listening in this period."

    fun pick(x: Float) {
        if (bars.isEmpty()) return
        val i = ((x / widthPx) * bars.size).toInt().coerceIn(0, bars.size - 1)
        if (i != picked) {
            picked = i
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    StatsCard(Modifier.semantics { contentDescription = spoken }) {
        if (bar != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        (if (picked == -1) "BUSIEST  ·  " else "") + barName(bar).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = OtoColors.TextTertiary,
                    )
                    Text(
                        durationText(bar.playedMs, OtoNumbers.Large.copy(fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold), unitSize = 16),
                        modifier = Modifier.padding(top = 4.dp),
                        style = OtoNumbers.Large.copy(fontSize = 34.sp, lineHeight = 38.sp),
                    )
                }
                if (bar.state == BarState.BEFORE_TRACKING) {
                    Text("Before tracking began", style = MaterialTheme.typography.labelMedium, color = OtoColors.TextTertiary)
                } else if (bar.plays > 0) {
                    Text("${bar.plays} plays", style = OtoNumbers.Small, color = OtoColors.TextTertiary)
                }
            }
        }
        Row(
            Modifier
                .padding(top = 20.dp)
                .fillMaxWidth()
                .height(120.dp)
                .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
                .pointerInput(bars) { detectTapGestures { pick(it.x) } }
                .pointerInput(bars) { detectHorizontalDragGestures(onDragStart = { pick(it.x) }) { change, _ -> pick(change.position.x) } },
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEachIndexed { i, b -> ChartBar(b, max, selected = i == shown, index = i, key = periodKey) }
        }
        Row(Modifier.padding(top = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            bars.forEachIndexed { i, b ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        axisLabel(b, i, bars.size).orEmpty(),
                        modifier = Modifier.wrapContentWidth(unbounded = true),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (i == shown) OtoColors.TextPrimary else OtoColors.TextTertiary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                }
            }
        }
        d.averagePerDay?.let {
            Text(
                "Average ${formatDuration(it.perDayMs)} a day over ${it.overDays} ${if (it.overDays == 1) "day" else "days"}",
                style = MaterialTheme.typography.bodySmall,
                color = OtoColors.TextTertiary,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun RowScope.ChartBar(bar: ActivityBar, max: Long, selected: Boolean, index: Int, key: Any) {
    val reduced = rememberReducedMotion()
    val target = when {
        bar.state != BarState.TRACKED -> 0.02f
        bar.playedMs <= 0 -> 0.03f
        else -> (bar.playedMs.toFloat() / max).coerceIn(0.06f, 1f)
    }
    val height = remember(key) { Animatable(if (reduced) target else 0f) }
    LaunchedEffect(key, target) {
        if (!reduced) delay(min(index * OtoMotion.BarStaggerMs, 600).toLong())
        height.animateTo(target, OtoMotion.bar())
    }
    val color = when {
        bar.state != BarState.TRACKED -> OtoColors.Hairline
        bar.playedMs <= 0 -> OtoColors.Hairline
        selected -> OtoColors.Accent
        else -> OtoColors.Surface3
    }
    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .fillMaxWidth()
                // A spring can overshoot; heights outside 0..1 would crash the layout.
                .fillMaxHeight(height.value.coerceIn(0f, 1f))
                .background(color, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp)),
        )
    }
}
