package com.oto.stats.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oto.stats.data.ArtStore
import com.oto.stats.stats.Daypart
import com.oto.stats.stats.Insights
import com.oto.stats.stats.PeriodKind
import com.oto.stats.stats.StatsData
import com.oto.stats.stats.StatsMath
import com.oto.stats.ui.components.hoursMinutes
import com.oto.stats.ui.poster.PosterFrame
import com.oto.stats.ui.poster.PosterInk
import com.oto.stats.ui.poster.PosterInkSoft
import com.oto.stats.ui.poster.PosterPalette
import com.oto.stats.ui.poster.PosterType
import com.oto.stats.ui.poster.GiantDuration
import com.oto.stats.ui.poster.rememberCountUp
import com.oto.stats.ui.poster.rememberPalette
import com.oto.stats.ui.theme.Display
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/*
 * The Moments: one poster per idea. Each says one thing, sets one number very large, takes its
 * colour from the music it is about, and can be shared as an image.
 */

@Composable
private fun rememberCover(key: String?, artStore: ArtStore): State<ImageBitmap?> =
    produceState(key?.let { artStore.peek(it) }, key) { value = key?.let { artStore.load(it) } }

private val Headline = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = (-0.8).sp)

private fun numberStyle(digits: Int) = when {
    digits <= 2 -> PosterType.Giant
    digits == 3 -> PosterType.Big
    else -> PosterType.Mid
}

@Composable
private fun Pill(text: String) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp), color = PosterInk)
    }
}

@Composable
private fun BigNumber(value: String, unit: String?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(value, style = numberStyle(value.length), color = PosterInk, modifier = Modifier.alignByBaseline())
        if (unit != null) {
            Text(unit, style = PosterType.Unit, color = PosterInkSoft, modifier = Modifier.alignByBaseline().padding(start = 6.dp))
        }
    }
}

@Composable
fun HeroPoster(d: StatsData, periodLabel: String, artStore: ArtStore) {
    val keys = d.moments.heroCovers
    val palette by rememberPalette(keys, artStore)
    val cover by rememberCover(keys.firstOrNull(), artStore)
    val ms = rememberCountUp(d.headline.playedMs, periodLabel)
    PosterFrame(
        palette = palette,
        label = "Time with music",
        footer = periodLabel,
        shareName = "time-$periodLabel",
        art = cover,
    ) {
        Pill(d.moments.personality.name)
        GiantDuration(ms, Modifier.padding(top = 10.dp))
        Text(d.moments.personality.line, style = MaterialTheme.typography.titleMedium, color = PosterInk, modifier = Modifier.padding(top = 10.dp))
        Text(d.moments.equivalent, style = MaterialTheme.typography.bodyMedium, color = PosterInkSoft, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun ArtistPoster(d: StatsData, periodLabel: String, artStore: ArtStore) {
    val a = d.moments.artist ?: return
    val key = a.topTrack?.identityKey
    val palette by rememberPalette(listOfNotNull(key), artStore)
    val cover by rememberCover(key, artStore)
    val pct = rememberCountUp(a.percent.toLong(), periodLabel)
    PosterFrame(
        palette = palette,
        label = "Top artist",
        footer = periodLabel,
        shareName = "artist-${a.name}-$periodLabel",
        art = cover,
    ) {
        BigNumber(pct.toString(), "%")
        Text(
            "of your listening went to",
            style = MaterialTheme.typography.bodyMedium,
            color = PosterInkSoft,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(a.name, style = Headline, color = PosterInk, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        val oneIn = Insights.oneIn(a.percent)?.let { "$it minutes. " }.orEmpty()
        Text(
            "$oneIn${a.trackCount} ${if (a.trackCount == 1) "track" else "tracks"}, ${a.plays} plays.",
            style = MaterialTheme.typography.bodyMedium,
            color = PosterInkSoft,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
fun RepeatPoster(d: StatsData, periodLabel: String, artStore: ArtStore) {
    val r = d.moments.onRepeat ?: return
    val palette by rememberPalette(listOf(r.track.identityKey), artStore)
    val cover by rememberCover(r.track.identityKey, artStore)
    val plays = rememberCountUp(r.track.plays.toLong(), periodLabel)
    PosterFrame(
        palette = palette,
        label = "On repeat",
        footer = periodLabel,
        shareName = "repeat-${r.track.title}-$periodLabel",
        art = cover,
    ) {
        BigNumber(plays.toString(), "plays")
        Text(r.track.title, style = Headline, color = PosterInk, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(
            "${r.track.artist}  ·  across ${r.days} ${if (r.days == 1) "day" else "days"}",
            style = MaterialTheme.typography.bodyMedium,
            color = PosterInkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun hourLabel(h: Int): String = "${(h + 11) % 12 + 1} ${if (h < 12) "AM" else "PM"}"

@Composable
fun ClockPoster(d: StatsData, periodLabel: String, artStore: ArtStore) {
    val hours = d.hours ?: return
    val weekdays = d.weekdays
    val keys = d.moments.heroCovers
    val palette by rememberPalette(keys.drop(1).ifEmpty { keys }, artStore)
    val peak = hours.indices.maxByOrNull { hours[it] } ?: 0
    val shares = Insights.daypartShares(hours)
    val top = shares.maxByOrNull { it.second }
    val peakDay = weekdays?.indices?.maxByOrNull { weekdays[it] }
    val dayName = peakDay?.let { DayOfWeek.of(it + 1).getDisplayName(JavaTextStyle.FULL, Locale.getDefault()) }
    val max = (hours.maxOrNull() ?: 0L).coerceAtLeast(1L)

    PosterFrame(
        palette = palette,
        label = "Your hour",
        footer = periodLabel,
        shareName = "hour-$periodLabel",
        aspect = 0.72f,
    ) {
        Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().height(210.dp)) {
                val c = Offset(size.width / 2, size.height / 2)
                val outer = size.minDimension / 2
                val inner = outer * 0.46f
                for (h in 0 until 24) {
                    val f = hours[h].toFloat() / max
                    val angle = Math.toRadians((h / 24.0) * 360.0 - 90.0)
                    val len = inner + (outer - inner) * f.coerceAtLeast(0.06f)
                    val dx = cos(angle).toFloat()
                    val dy = sin(angle).toFloat()
                    drawLine(
                        color = if (h == peak) Color.White else Color.White.copy(alpha = 0.3f),
                        start = Offset(c.x + dx * inner, c.y + dy * inner),
                        end = Offset(c.x + dx * len, c.y + dy * len),
                        strokeWidth = 5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(hourLabel(peak), style = PosterType.Mid.copy(fontSize = 34.sp, lineHeight = 36.sp), color = PosterInk)
                Text("peak hour", style = MaterialTheme.typography.labelMedium, color = PosterInkSoft)
            }
        }
        if (top != null) {
            val phrase = when (top.first) {
                Daypart.NIGHT -> "Mostly at night"
                else -> "Mostly ${top.first.label.lowercase()}s"
            }
            Text(phrase, style = Headline, color = PosterInk, modifier = Modifier.padding(top = 14.dp))
            Text(
                "${top.second}% of your listening falls in the ${top.first.label.lowercase()}." + (dayName?.let { " Busiest on ${it}s." } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = PosterInkSoft,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun level(ms: Long, max: Long): Int = when {
    ms <= 0 -> 0
    else -> {
        val r = ms.toFloat() / max.coerceAtLeast(1L)
        when {
            r <= 0.25f -> 1
            r <= 0.5f -> 2
            r <= 0.75f -> 3
            else -> 4
        }
    }
}

private fun heatColor(level: Int, glow: Color): Color = when (level) {
    -1 -> Color.White.copy(alpha = 0.03f)
    0 -> Color.White.copy(alpha = 0.10f)
    1 -> glow.copy(alpha = 0.35f)
    2 -> glow.copy(alpha = 0.55f)
    3 -> glow.copy(alpha = 0.8f)
    else -> glow
}

@Composable
fun CalendarPoster(d: StatsData, kind: PeriodKind, periodLabel: String, artStore: ArtStore) {
    if (kind == PeriodKind.WEEK) return
    val keys = d.moments.heroCovers
    val palette by rememberPalette(keys, artStore)
    val dayMs = remember(d.daily) { d.daily.associate { it.day to it.playedMs } }
    val max = remember(d.daily) { (d.daily.maxOfOrNull { it.playedMs } ?: 0L).coerceAtLeast(1L) }
    val activeDays = d.activeDaysInPeriod
    val shownDays = (java.time.temporal.ChronoUnit.DAYS.between(d.heatStart, minOf(d.heatEnd, d.today)) + 1).toInt().coerceAtLeast(1)
    val longest = d.moments.longestDay
    val longestDate = longest?.let { LocalDate.ofEpochDay(it.day) }
    val count = rememberCountUp(activeDays.toLong(), periodLabel)

    PosterFrame(
        palette = palette,
        label = "Showing up",
        footer = periodLabel,
        shareName = "days-$periodLabel",
        aspect = 0.62f,
    ) {
        BigNumber(count.toString(), if (activeDays == 1) "day" else "days")
        Text(
            if (kind == PeriodKind.ALL) "of the last year you pressed play" else "of $shownDays you pressed play",
            style = MaterialTheme.typography.bodyMedium,
            color = PosterInkSoft,
        )
        Box(Modifier.padding(top = 16.dp)) {
            if (kind == PeriodKind.MONTH) {
                MonthGrid(d, dayMs, max, palette.glow1)
            } else {
                YearMosaic(d, dayMs, max, palette.glow1)
            }
        }
        val line = buildList {
            if (longest != null && longestDate != null && longest.playedMs > 0) {
                val name = longestDate.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault()) + ", " +
                    longestDate.month.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault()) + " " + longestDate.dayOfMonth
                add("Longest day: $name · ${hoursMinutes(longest.playedMs)}")
            }
            if (d.streaks.current >= 2) add("Streak: ${d.streaks.current} days")
        }.joinToString("   ")
        if (line.isNotEmpty()) {
            Text(line, style = MaterialTheme.typography.bodySmall, color = PosterInkSoft, modifier = Modifier.padding(top = 14.dp))
        }
    }
}

@Composable
private fun MonthGrid(d: StatsData, dayMs: Map<Long, Long>, max: Long, glow: Color) {
    val first = d.heatStart
    val lead = first.dayOfWeek.value - 1 // blanks before the 1st, Monday first
    val total = d.heatEnd.dayOfMonth
    val rows = (lead + total + 6) / 7
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (w in 1..7) {
                Text(
                    DayOfWeek.of(w).getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = PosterInkSoft,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..total) {
                            val date = first.withDayOfMonth(dayNum)
                            val lvl = if (date > d.today) -1 else level(dayMs[date.toEpochDay()] ?: 0L, max)
                            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(heatColor(lvl, glow)))
                            Text(
                                dayNum.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (lvl >= 3) Color.Black.copy(alpha = 0.8f) else PosterInkSoft,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearMosaic(d: StatsData, dayMs: Map<Long, Long>, max: Long, glow: Color) {
    val months = remember(d.heatStart) { (0 until 12).map { d.heatStart.withDayOfMonth(1).plusMonths(it.toLong()) } }
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val labelW = 18.dp.toPx()
        val gap = 2.dp.toPx()
        val cell = ((size.width - labelW - gap * 30) / 31f)
        val rowH = (size.height - gap * 11) / 12f
        val corner = CornerRadius(1.5.dp.toPx())
        months.forEachIndexed { row, monthStart ->
            if (monthStart > d.heatEnd && monthStart > d.today) return@forEachIndexed
            val y = row * (rowH + gap)
            val len = monthStart.lengthOfMonth()
            for (day in 1..len) {
                val date = monthStart.withDayOfMonth(day)
                if (date < d.heatStart || date > d.heatEnd) continue
                val lvl = if (date > d.today) -1 else level(dayMs[date.toEpochDay()] ?: 0L, max)
                drawRoundRect(
                    color = heatColor(lvl, glow),
                    topLeft = Offset(labelW + (day - 1) * (cell + gap), y),
                    size = Size(cell, rowH),
                    cornerRadius = corner,
                )
            }
        }
    }
}

@Composable
fun ExplorerPoster(d: StatsData, periodLabel: String, artStore: ArtStore) {
    val e = d.moments.explorer ?: return
    if (e.uniqueTracks < 5) return
    val keys = d.moments.heroCovers
    val palette by rememberPalette(keys.drop(2).ifEmpty { keys }, artStore)
    val pct = rememberCountUp(e.percent.toLong(), periodLabel)

    PosterFrame(
        palette = palette,
        label = "Discovery",
        footer = periodLabel,
        shareName = "new-$periodLabel",
    ) {
        Canvas(Modifier.fillMaxWidth().height(78.dp)) {
            val cols = 20
            val step = size.width / cols
            val r = step * 0.32f
            for (i in 0 until 100) {
                val col = i % cols
                val row = i / cols
                val filled = i < e.percent
                drawCircle(
                    color = if (filled) palette.glow1 else Color.White.copy(alpha = 0.14f),
                    radius = r,
                    center = Offset(step * (col + 0.5f), step * (row + 0.5f)),
                )
            }
        }
        BigNumber(pct.toString(), "%", Modifier.padding(top = 18.dp))
        Text("new to you", style = Headline, color = PosterInk)
        Text(
            "${e.newTracks} of the ${e.uniqueTracks} tracks you played were first heard in $periodLabel.",
            style = MaterialTheme.typography.bodyMedium,
            color = PosterInkSoft,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
