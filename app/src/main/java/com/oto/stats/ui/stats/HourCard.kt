package com.oto.stats.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.oto.stats.stats.Daypart
import com.oto.stats.stats.Insights
import com.oto.stats.stats.StatsData
import com.oto.stats.ui.theme.OtoColors
import com.oto.stats.ui.theme.OtoMotion
import com.oto.stats.ui.theme.OtoNumbers
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

internal fun hourLabel(h: Int): String = "${(h + 11) % 12 + 1} ${if (h < 12) "AM" else "PM"}"

internal fun hourSentence(d: StatsData): String? {
    val hours = d.hours ?: return null
    val peak = hours.indices.maxByOrNull { hours[it] } ?: return null
    val top = Insights.daypartShares(hours).maxByOrNull { it.second } ?: return null
    val phrase = if (top.first == Daypart.NIGHT) "Mostly at night" else "Mostly ${top.first.label.lowercase()}s"
    return "$phrase, peaking at ${hourLabel(peak)}."
}

/** A 24-hour dial: one rounded bar per hour around a ring, the peak hour in yellow. */
@Composable
fun HourCard(d: StatsData, periodKey: Any) {
    val hours = d.hours ?: return
    val peak = hours.indices.maxByOrNull { hours[it] } ?: 0
    val max = (hours.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val shares = remember(hours) { Insights.daypartShares(hours) }
    val topPart = shares.maxByOrNull { it.second }?.first
    val dayName = d.weekdays?.let { w ->
        w.indices.maxByOrNull { w[it] }?.let { DayOfWeek.of(it + 1).getDisplayName(TextStyle.FULL, Locale.getDefault()) }
    }
    val reduced = rememberReducedMotion()
    val grow = remember(periodKey) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(periodKey) { if (!reduced) grow.animateTo(1f, OtoMotion.enter()) }

    StatsCard(Modifier.semantics { contentDescription = (hourSentence(d) ?: "") + (dayName?.let { " Busiest on ${it}s." } ?: "") }) {
        Box(Modifier.fillMaxWidth().height(264.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().height(264.dp)) {
                val c = Offset(size.width / 2, size.height / 2)
                val outer = size.minDimension / 2 - 22.dp.toPx()
                val inner = outer * 0.6f
                val g = grow.value
                for (h in 0 until 24) {
                    val f = (hours[h].toFloat() / max).coerceAtLeast(0.07f)
                    val angle = Math.toRadians((h / 24.0) * 360.0 - 90.0)
                    val len = inner + (outer - inner) * f * g
                    val dx = cos(angle).toFloat()
                    val dy = sin(angle).toFloat()
                    drawLine(
                        color = if (h == peak) OtoColors.Accent else Color.White.copy(alpha = 0.28f),
                        start = Offset(c.x + dx * inner, c.y + dy * inner),
                        end = Offset(c.x + dx * len, c.y + dy * len),
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(hourLabel(peak), style = MaterialTheme.typography.headlineMedium, color = OtoColors.TextPrimary)
                Text("peak hour", style = MaterialTheme.typography.labelMedium, color = OtoColors.TextTertiary)
            }
            DialLabel("12a", Modifier.align(Alignment.TopCenter))
            DialLabel("6a", Modifier.align(Alignment.CenterEnd))
            DialLabel("12p", Modifier.align(Alignment.BottomCenter))
            DialLabel("6p", Modifier.align(Alignment.CenterStart))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((part, pct) in shares) {
                val on = part == topPart
                Column(
                    Modifier
                        .weight(1f)
                        .background(if (on) OtoColors.AccentSubtle else OtoColors.Surface2, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text("$pct%", style = OtoNumbers.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = if (on) OtoColors.Accent else OtoColors.TextPrimary)
                    Text(part.label, style = MaterialTheme.typography.labelSmall, color = OtoColors.TextTertiary, maxLines = 1)
                }
            }
        }
        if (dayName != null) {
            Text("Busiest on ${dayName}s.", style = MaterialTheme.typography.bodySmall, color = OtoColors.TextTertiary, modifier = Modifier.padding(top = 14.dp))
        }
    }
}

@Composable
private fun DialLabel(text: String, modifier: Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.labelSmall, color = OtoColors.TextTertiary)
}
