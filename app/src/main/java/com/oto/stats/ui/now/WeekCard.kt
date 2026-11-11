package com.oto.stats.ui.now

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.oto.stats.data.DayTotal
import com.oto.stats.ui.components.OtoCard
import com.oto.stats.ui.components.hoursMinutes
import com.oto.stats.ui.theme.OtoColors

private val DayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
private val DayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private const val MAX_BAR_DP = 64

/**
 * One number leads, the last seven days give it context. Tap a bar to read that day; the number
 * and the yellow mark follow your finger. Starts on today.
 */
@Composable
fun WeekCard(days: List<DayTotal>, today: Long) {
    val byDay = days.associateBy { it.day }
    val week = (today - 6..today).map { byDay[it] ?: DayTotal(it, 0L, 0) }
    var selected by rememberSaveable(today) { mutableIntStateOf(week.lastIndex) }
    val chosen = week[selected]
    val before = week.getOrNull(selected - 1)
    val peak = week.maxOf { it.playedMs }.coerceAtLeast(1L)

    // Bars grow in the first time the card appears.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    OtoCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                dayLabel(selected, week.lastIndex, chosen.day).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = OtoColors.TextTertiary,
            )
            Text("LAST 7 DAYS", style = MaterialTheme.typography.labelMedium, color = OtoColors.TextTertiary)
        }
        Text(
            if (chosen.playedMs > 0) hoursMinutes(chosen.playedMs) else "0m",
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.displaySmall,
            color = OtoColors.TextPrimary,
        )
        Text(
            comparison(chosen, before, isToday = selected == week.lastIndex),
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = OtoColors.TextSecondary,
        )

        Row(
            Modifier
                .padding(top = 22.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            week.forEachIndexed { i, day ->
                val isSelected = i == selected
                val target = when {
                    !shown || day.playedMs <= 0 -> 0f
                    else -> (day.playedMs.toFloat() / peak).coerceIn(0.1f, 1f)
                }
                val fraction by animateFloatAsState(target, tween(650, delayMillis = i * 45), label = "bar")
                val name = DayNames[((day.day + 3) % 7).toInt()]
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                        ) { selected = i }
                        .semantics {
                            contentDescription = if (day.playedMs > 0) "$name, ${hoursMinutes(day.playedMs)}" else "$name, nothing played"
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.height(MAX_BAR_DP.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier
                                .width(26.dp)
                                .height((MAX_BAR_DP * fraction).dp.coerceAtLeast(6.dp))
                                .background(
                                    when {
                                        day.playedMs <= 0 -> Color.White.copy(alpha = 0.06f)
                                        isSelected -> OtoColors.Accent
                                        else -> OtoColors.Surface3
                                    },
                                    RoundedCornerShape(8.dp),
                                ),
                        )
                    }
                    Text(
                        DayLetters[((day.day + 3) % 7).toInt()],
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) OtoColors.TextPrimary else OtoColors.TextTertiary,
                    )
                }
            }
        }
    }
}

private fun dayLabel(index: Int, lastIndex: Int, day: Long): String = when (index) {
    lastIndex -> "Today"
    lastIndex - 1 -> "Yesterday"
    else -> DayNames[((day + 3) % 7).toInt()]
}

private fun comparison(day: DayTotal, before: DayTotal?, isToday: Boolean): String {
    if (day.plays == 0) return if (isToday) "Nothing yet today" else "Nothing played"
    val plays = if (day.plays == 1) "1 listen" else "${day.plays} listens"
    if (before == null || before.playedMs <= 0) return plays
    val than = if (isToday) "yesterday" else "the day before"
    val diffMin = (day.playedMs - before.playedMs) / 60_000
    return plays + when {
        diffMin >= 1 -> " · ${hoursMinutes(diffMin * 60_000)} more than $than"
        diffMin <= -1 -> " · ${hoursMinutes(-diffMin * 60_000)} less than $than"
        else -> " · same as $than"
    }
}
