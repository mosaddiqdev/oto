package com.oto.stats.ui.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oto.stats.data.ArtStore
import com.oto.stats.stats.StatsData
import com.oto.stats.tracking.MusicApps
import com.oto.stats.ui.components.OtoCard
import com.oto.stats.ui.components.SectionLabel
import com.oto.stats.ui.components.TrackCover
import com.oto.stats.ui.components.hoursMinutes
import com.oto.stats.ui.theme.OtoColors
import com.oto.stats.ui.theme.OtoNumbers
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

class RankedItem(
    val title: String,
    val subtitle: String?,
    val plays: Int,
    val playedMs: Long,
    /** A track whose cover to show; null draws a round monogram (used for artists). */
    val coverKey: String?,
)

/** A ranked list. The leader is larger and yellow; each row's line is its share of the leader. */
@Composable
fun RankedCard(title: String, items: List<RankedItem>, byTime: Boolean, artStore: ArtStore) {
    OtoCard {
        SectionLabel(title)
        if (items.isEmpty()) {
            Text("Nothing yet", style = MaterialTheme.typography.bodyMedium, color = OtoColors.TextTertiary, modifier = Modifier.padding(top = 10.dp))
            return@OtoCard
        }
        fun metric(i: RankedItem) = if (byTime) i.playedMs else i.plays.toLong()
        val lead = items.maxOf { metric(it) }.coerceAtLeast(1L)
        items.forEachIndexed { index, item ->
            RankedRow(index, item, metric(item).toFloat() / lead, byTime, artStore)
        }
    }
}

@Composable
private fun RankedRow(index: Int, item: RankedItem, share: Float, byTime: Boolean, artStore: ArtStore) {
    val first = index == 0
    val size = if (first) 56.dp else 44.dp
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val animated by animateFloatAsState(if (shown) share else 0f, tween(600, delayMillis = index * 60), label = "share")

    Row(Modifier.fillMaxWidth().padding(top = if (first) 14.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${index + 1}",
            modifier = Modifier.width(22.dp),
            style = OtoNumbers.Body,
            color = if (first) OtoColors.Accent else OtoColors.TextTertiary,
        )
        Cover(item, size, artStore)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                item.title,
                style = if (first) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                color = OtoColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle != null) {
                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = OtoColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(3.dp).background(OtoColors.Surface2, CircleShape)) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animated.coerceIn(0f, 1f))
                        .background(if (first) OtoColors.Accent else OtoColors.TextTertiary.copy(alpha = 0.55f), CircleShape),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(if (byTime) hoursMinutes(item.playedMs) else "${item.plays}", style = OtoNumbers.Body, color = OtoColors.TextPrimary)
            Text(if (byTime) "${item.plays} plays" else hoursMinutes(item.playedMs), style = OtoNumbers.Small, color = OtoColors.TextTertiary)
        }
    }
}

@Composable
private fun Cover(item: RankedItem, size: Dp, artStore: ArtStore) {
    if (item.coverKey != null) {
        TrackCover(item.coverKey, item.title, artStore, size)
    } else {
        Box(Modifier.size(size).background(OtoColors.Surface2, CircleShape), contentAlignment = Alignment.Center) {
            Text(
                item.title.firstOrNull { it.isLetterOrDigit() }?.uppercase().orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = OtoColors.TextTertiary,
            )
        }
    }
}

private val Dayparts = listOf("Night" to 0..5, "Morning" to 6..11, "Afternoon" to 12..17, "Evening" to 18..23)

@Composable
fun PatternsCard(hours: List<Long>, weekdays: List<Long>) {
    OtoCard {
        SectionLabel("When you listen")
        val total = hours.sum().coerceAtLeast(1L)
        val shares = Dayparts.map { (name, range) -> name to range.sumOf { hours[it] } * 100 / total }
        val top = shares.maxByOrNull { it.second }
        val peakDay = weekdays.indices.maxByOrNull { weekdays[it] } ?: 0
        val dayName = DayOfWeek.of(peakDay + 1).getDisplayName(TextStyle.FULL, Locale.getDefault())
        if (top != null) {
            Text(
                "Mostly ${top.first.lowercase()}s".replace("nights", "at night"),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.headlineSmall,
                color = OtoColors.TextPrimary,
            )
            Text("Busiest on ${dayName}s", style = MaterialTheme.typography.bodyMedium, color = OtoColors.TextSecondary)
        }
        Bars(hours, Modifier.padding(top = 18.dp).height(64.dp), gap = 3.dp)
        Row(Modifier.padding(top = 6.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (l in listOf("12a", "6a", "12p", "6p", "11p")) Text(l, style = MaterialTheme.typography.labelSmall, color = OtoColors.TextTertiary)
        }
        Row(Modifier.padding(top = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((name, pct) in shares) {
                val isTop = name == top?.first
                Column(
                    Modifier
                        .weight(1f)
                        .background(if (isTop) OtoColors.AccentSubtle else OtoColors.Surface2, RoundedCornerShape(12.dp))
                        .padding(vertical = 10.dp, horizontal = 10.dp),
                ) {
                    Text("$pct%", style = OtoNumbers.Body, color = if (isTop) OtoColors.Accent else OtoColors.TextPrimary)
                    Text(name, style = MaterialTheme.typography.labelSmall, color = OtoColors.TextTertiary, maxLines = 1)
                }
            }
        }
        Bars(weekdays, Modifier.padding(top = 22.dp).height(48.dp), gap = 8.dp)
        Row(Modifier.padding(top = 6.dp).fillMaxWidth()) {
            for (d in 1..7) {
                Text(
                    DayOfWeek.of(d).getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (d - 1 == peakDay) OtoColors.TextPrimary else OtoColors.TextTertiary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Bars(values: List<Long>, modifier: Modifier, gap: Dp) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Bottom) {
        for (v in values) {
            val f by animateFloatAsState((v.toFloat() / max).coerceIn(0.04f, 1f), tween(500), label = "pbar")
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(f)
                    .background(if (v == max) OtoColors.Accent else OtoColors.Surface3, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
            )
        }
    }
}

@Composable
fun HabitsCard(d: StatsData) {
    val h = d.headline
    val plays = h.plays.coerceAtLeast(1)
    OtoCard {
        SectionLabel("Habits")
        Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RingTile("Played through", h.fullListens * 100 / plays, Modifier.weight(1f))
            RingTile("Skipped", h.skips * 100 / plays, Modifier.weight(1f))
            Column(
                Modifier.weight(1f).background(OtoColors.Surface2, RoundedCornerShape(16.dp)).padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Text(d.replays.toString(), style = OtoNumbers.Title, color = OtoColors.TextPrimary)
                }
                Text("Replays", style = MaterialTheme.typography.labelMedium, color = OtoColors.TextTertiary, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Fact("Listening days", d.activeDaysInPeriod.toString())
        Fact("Current streak", plural(d.streaks.current, "day"))
        Fact("Longest streak", plural(d.streaks.longest, "day"))
        d.mostReplayed?.let { Fact("Most replayed", "${it.title}  ${it.total}×") }
        d.mostSkipped?.let { Fact("Most skipped", "${it.title}  ${it.total}×") }
    }
}

private fun plural(n: Int, unit: String) = if (n == 1) "1 $unit" else "$n ${unit}s"

@Composable
private fun RingTile(label: String, percent: Int, modifier: Modifier) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(percent) { shown = true }
    val sweep by animateFloatAsState(if (shown) percent / 100f else 0f, tween(700), label = "ring")
    Column(
        modifier.background(OtoColors.Surface2, RoundedCornerShape(16.dp)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(64.dp)) {
                val stroke = 6.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(OtoColors.Surface3, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(OtoColors.Accent, -90f, 360f * sweep.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Text("$percent%", style = OtoNumbers.Body, color = OtoColors.TextPrimary)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = OtoColors.TextTertiary, modifier = Modifier.padding(top = 8.dp), maxLines = 1)
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OtoColors.TextSecondary)
        Text(
            value,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
            style = OtoNumbers.Body,
            color = OtoColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
fun AppsCard(d: StatsData) {
    val total = d.apps.sumOf { it.playedMs }.coerceAtLeast(1L)
    OtoCard {
        SectionLabel("By app")
        Row(Modifier.padding(top = 14.dp).fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            d.apps.forEachIndexed { i, a ->
                Box(
                    Modifier
                        .weight(a.playedMs.coerceAtLeast(1L).toFloat())
                        .fillMaxHeight()
                        .background(appColor(i), RoundedCornerShape(5.dp)),
                )
            }
        }
        d.apps.forEachIndexed { i, a ->
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(appColor(i), CircleShape))
                Text(MusicApps.labelFor(a.sourcePackage), modifier = Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium, color = OtoColors.TextPrimary)
                Text("${a.playedMs * 100 / total}%", style = OtoNumbers.Body, color = OtoColors.TextPrimary)
                Text("  ${hoursMinutes(a.playedMs)}", style = OtoNumbers.Small, color = OtoColors.TextTertiary)
            }
        }
    }
}

/** One accent for the leader, then steps of grey. */
private fun appColor(index: Int) = when (index) {
    0 -> OtoColors.Accent
    1 -> OtoColors.TextSecondary
    2 -> OtoColors.TextTertiary
    else -> OtoColors.Surface3
}
