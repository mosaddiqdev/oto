package com.oto.stats.ui.now

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oto.stats.data.DayTotal
import com.oto.stats.ui.components.OtoCard
import com.oto.stats.ui.components.hoursMinutes
import com.oto.stats.ui.theme.OtoColors

private val DayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
private const val MAX_BAR_DP = 64

/**
 * Today's listening and the last seven days as bars. One number leads; the bars give it context.
 * Only today is yellow.
 */
@Composable
fun WeekCard(days: List<DayTotal>, today: Long) {
    val byDay = days.associateBy { it.day }
    val week = (today - 6..today).map { byDay[it] ?: DayTotal(it, 0L, 0) }
    val todayTotal = week.last()
    val yesterday = week[week.size - 2]
    val peak = week.maxOf { it.playedMs }.coerceAtLeast(1L)

    OtoCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("TODAY", style = MaterialTheme.typography.labelMedium, color = OtoColors.TextTertiary)
            Text("LAST 7 DAYS", style = MaterialTheme.typography.labelMedium, color = OtoColors.TextTertiary)
        }
        Text(
            if (todayTotal.playedMs > 0) hoursMinutes(todayTotal.playedMs) else "0m",
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.displaySmall,
            color = OtoColors.TextPrimary,
        )
        Text(
            comparison(todayTotal, yesterday),
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = OtoColors.TextSecondary,
        )

        Row(
            Modifier
                .padding(top = 22.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            week.forEachIndexed { i, day ->
                val isToday = i == week.lastIndex
                val target = if (day.playedMs <= 0) 0f else (day.playedMs.toFloat() / peak).coerceIn(0.08f, 1f)
                val fraction by animateFloatAsState(target, tween(600), label = "bar")
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.height(MAX_BAR_DP.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((MAX_BAR_DP * fraction).dp.coerceAtLeast(5.dp))
                                .background(
                                    when {
                                        isToday && day.playedMs > 0 -> OtoColors.Accent
                                        day.playedMs > 0 -> OtoColors.Surface3
                                        else -> OtoColors.Surface2
                                    },
                                    RoundedCornerShape(7.dp),
                                ),
                        )
                    }
                    Text(
                        DayLetters[((day.day + 3) % 7).toInt()],
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) OtoColors.TextPrimary else OtoColors.TextTertiary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun comparison(today: DayTotal, yesterday: DayTotal): String {
    val plays = if (today.plays == 1) "1 listen" else "${today.plays} listens"
    if (today.plays == 0) return "Nothing yet today"
    val diffMin = (today.playedMs - yesterday.playedMs) / 60_000
    val vs = when {
        yesterday.playedMs <= 0 -> ""
        diffMin >= 1 -> " · ${hoursMinutes(diffMin * 60_000)} more than yesterday"
        diffMin <= -1 -> " · ${hoursMinutes(-diffMin * 60_000)} less than yesterday"
        else -> " · same as yesterday"
    }
    return plays + vs
}
