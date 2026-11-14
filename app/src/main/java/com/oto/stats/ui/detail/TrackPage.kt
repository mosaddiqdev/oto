package com.oto.stats.ui.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oto.stats.AppContainer
import com.oto.stats.data.TrackListen
import com.oto.stats.stats.Skyline
import com.oto.stats.stats.TrackDetail
import com.oto.stats.tracking.MusicApps
import com.oto.stats.ui.components.TrackCover
import com.oto.stats.ui.components.timeAgo
import com.oto.stats.ui.nav.LocalNavigator
import com.oto.stats.ui.nav.Route
import com.oto.stats.ui.stats.LitDigits
import com.oto.stats.ui.stats.StatsCard
import com.oto.stats.ui.stats.StatsSection
import com.oto.stats.ui.stats.formatCount
import com.oto.stats.ui.stats.formatDuration
import com.oto.stats.ui.stats.giant
import com.oto.stats.ui.stats.rememberCountUp
import com.oto.stats.ui.stats.rememberReducedMotion
import com.oto.stats.ui.theme.OtoColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The Track page. Plan and skeleton: docs/DETAIL_PAGES.md.
 * Back bar, hero numeral (plays), three facts, your history with the song as a skyline, a few habits, the last ten listens.
 * Complete from local data alone.
 */

private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val fullFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
private val shortFmt = DateTimeFormatter.ofPattern("d MMM yy", Locale.getDefault())

@Composable
fun TrackPage(route: Route.Track, container: AppContainer, onBack: () -> Unit) {
    val window = route.window
    var allTime by rememberSaveable(route.encode()) { mutableStateOf(window == null) }
    val start = if (allTime || window == null) 0L else window.startMs
    val end = if (allTime || window == null) Long.MAX_VALUE else window.endMs
    val detail by produceState<TrackDetail?>(null, route.id, start, end) {
        container.details.track(route.id, start, end).collect { value = it }
    }
    val is24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)

    Column(Modifier.fillMaxSize().background(OtoColors.Canvas).windowInsetsPadding(WindowInsets.statusBars)) {
        DetailTopBar(onBack, window, allTime) { allTime = it }
        val d = detail
        if (d != null) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                content(d, container, linkWindow = if (allTime) null else window, key = "${route.id}/$start/$end", windowLabel = if (allTime) null else window?.label, is24 = is24, onShowAll = { allTime = true }, allTime = allTime)
                item { Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(1.dp)) }
            }
        }
    }
}

private fun LazyListScope.content(d: TrackDetail, container: AppContainer, linkWindow: Route.Window?, key: String, windowLabel: String?, is24: Boolean, onShowAll: () -> Unit, allTime: Boolean) {
    val zone = ZoneId.systemDefault()
    val s = d.stats
    item(key = "hero") { Hero(d, container, key, windowLabel, linkWindow) }
    if (s.plays == 0) {
        item(key = "none") {
            StatsCard {
                Text("Not played in ${windowLabel ?: "this window"}", style = MaterialTheme.typography.titleMedium, color = OtoColors.TextPrimary)
                if (!allTime) {
                    Text(
                        "See all time",
                        style = MaterialTheme.typography.labelLarge,
                        color = OtoColors.Accent,
                        modifier = Modifier.padding(top = 10.dp).clickable(onClick = onShowAll),
                    )
                }
            }
        }
        return
    }
    item(key = "facts") { Facts(d, zone) }
    item(key = "history") {
        val peak = d.insights.peakDay
        val first = Instant.ofEpochMilli(s.firstAt).atZone(zone).toLocalDate()
        val sentence = buildString {
            append("First played ${fullFmt.format(first)}.")
            if (peak != null && peak.plays > 1) append(" Busiest day: ${peak.plays} plays on ${dayFmt.format(LocalDate.ofEpochDay(peak.day))}.")
        }
        StatsSection("Your history with it", sentence = sentence) { History(d, zone, key) }
    }
    item(key = "habits") { StatsSection("Habits") { Habits(d, is24) } }
    if (d.listens.isNotEmpty()) item(key = "listens") {
        StatsSection("Recent listens") { Listens(d.listens, is24) }
    }
}

@Composable
private fun Hero(d: TrackDetail, container: AppContainer, key: String, windowLabel: String?, linkWindow: Route.Window?) {
    val nav = LocalNavigator.current
    val t = d.track
    val plays = rememberCountUp(d.stats.plays.toLong(), key, hero = true)
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TrackCover(t.identityKey, t.title, container.artStore, 96.dp)
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(t.title, style = MaterialTheme.typography.headlineSmall, color = OtoColors.TextPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                // Every credited artist is a door to their page.
                val names = buildAnnotatedString {
                    if (d.artists.isEmpty()) append(t.artistText)
                    d.artists.forEachIndexed { i, a ->
                        if (i > 0) append(", ")
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = "artist:${a.artistId}",
                                styles = TextLinkStyles(SpanStyle(color = OtoColors.TextSecondary)),
                                linkInteractionListener = { nav?.open(Route.Artist(a.artistId, linkWindow)) },
                            ),
                        ) { append(a.name) }
                    }
                }
                Text(names, style = MaterialTheme.typography.bodyLarge, color = OtoColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                if (!t.album.isNullOrBlank()) {
                    Text(t.album, style = MaterialTheme.typography.bodyMedium, color = OtoColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(Modifier.padding(top = 28.dp), verticalAlignment = Alignment.Bottom) {
            val text = formatCount(plays.toInt())
            Text(text, style = giant(if (text.length > 5) 64f else 80f, -3.0).copy(brush = LitDigits, fontFeatureSettings = "tnum"), modifier = Modifier.alignByBaseline(), maxLines = 1)
            Text(
                if (d.stats.plays == 1) "play" else "plays",
                style = giant(22f, -0.3), color = OtoColors.TextTertiary,
                modifier = Modifier.alignByBaseline().padding(start = 10.dp),
            )
        }
        val rank = when {
            d.stats.plays == 0 -> null
            d.rank == 1 -> "Your most played track" + (windowLabel?.let { " in $it" } ?: "")
            else -> "#${d.rank} of ${formatCount(d.tracksPlayed)} tracks" + (windowLabel?.let { " in $it" } ?: "")
        }
        Text(
            listOfNotNull(if (d.stats.plays > 0) "${formatDuration(d.stats.playedMs)} listened" else null, rank).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = OtoColors.TextSecondary,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun Facts(d: TrackDetail, zone: ZoneId) {
    val s = d.stats
    val first = Instant.ofEpochMilli(s.firstAt).atZone(zone).toLocalDate()
    val skipPct = Math.round(s.skips * 100.0 / s.plays).toInt()
    val finished = Math.round(s.avgCompletion).toInt()
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "First heard ${fullFmt.format(first)}. Skipped $skipPct percent. Finished $finished percent on average." }) {
        Fact("FIRST HEARD", shortFmt.format(first), Modifier.weight(1f))
        FactDivider()
        Fact("SKIPPED", "$skipPct%", Modifier.weight(1f))
        FactDivider()
        Fact("FINISHED", "$finished%", Modifier.weight(1f))
    }
}

@Composable
internal fun FactDivider() = Box(Modifier.padding(horizontal = 12.dp).width(1.dp).height(40.dp).background(OtoColors.Hairline))

@Composable
internal fun Fact(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Medium), color = OtoColors.TextTertiary, maxLines = 1)
        Text(value, style = giant(24f, -0.6).copy(fontFeatureSettings = "tnum"), color = OtoColors.TextPrimary, maxLines = 1, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun History(d: TrackDetail, zone: ZoneId, key: String) =
    HistoryCard(d.daily, Instant.ofEpochMilli(d.stats.firstAt).atZone(zone).toLocalDate(), d.insights.peakDay?.day, zone, key)

/** Plays per day from the first play (or the window start) to today, with the busiest day lit. Shared by the Track and Artist pages. */
@Composable
internal fun HistoryCard(daily: List<com.oto.stats.data.DayTotal>, first: LocalDate, peakDay: Long?, zone: ZoneId, key: String) {
    val today = LocalDate.now(zone)
    val startDay = LocalDate.ofEpochDay(daily.first().day).let { if (it.isBefore(first)) it else first }
    val endDay = LocalDate.ofEpochDay(daily.last().day).let { if (it.isAfter(today)) it else today }
    val columns = remember(daily, startDay, endDay) {
        Skyline.build(daily, startDay, endDay, 100, { it.plays.toLong() }, { it.toEpochDay() == peakDay })
    }
    val reduced = rememberReducedMotion()
    val grow = remember(key) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(key) { if (reduced) grow.snapTo(1f) else { grow.snapTo(0f); grow.animateTo(1f, tween(800, delayMillis = 100)) } }

    StatsCard {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            if (columns.isEmpty()) return@Canvas
            val maxV = columns.maxOf { it.value }.coerceAtLeast(1L)
            val n = columns.size
            val slot = size.width / n
            val barW = (slot * 0.68f).coerceIn(1.5.dp.toPx(), 14.dp.toPx())
            val r = CornerRadius(minOf(barW / 2f, 3.dp.toPx()))
            val base = size.height
            val usable = base - 4.dp.toPx()
            columns.forEachIndexed { i, c ->
                if (c.value <= 0L) return@forEachIndexed
                val local = (grow.value * (1f + n * 0.01f) - i * 0.01f).coerceIn(0f, 1f)
                val e = 1f - (1f - local) * (1f - local) * (1f - local)
                val h = (usable * c.value / maxV * e).coerceAtLeast(2.dp.toPx())
                val x = i * slot + (slot - barW) / 2f
                if (c.lit) drawRoundRect(Brush.verticalGradient(listOf(OtoColors.Accent, Color(0xFFC9A91C)), startY = base - h, endY = base), Offset(x, base - h), Size(barW, h), r)
                else drawRoundRect(Color(0xFF2B2B2B), Offset(x, base - h), Size(barW, h), r)
            }
            drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, base), Offset(size.width, base), 1.dp.toPx())
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(shortFmt.format(startDay), style = MaterialTheme.typography.labelSmall, color = OtoColors.TextTertiary)
            Text(if (endDay == today) "Today" else shortFmt.format(endDay), style = MaterialTheme.typography.labelSmall, color = OtoColors.TextTertiary)
        }
    }
}

@Composable
private fun Habits(d: TrackDetail, is24: Boolean) = HabitsCard(d.insights, d.stats.lastAt, is24)

@Composable
internal fun HabitsCard(i: com.oto.stats.stats.TrackInsights, lastAt: Long, is24: Boolean) {
    val rows = buildList {
        i.favouriteHour?.let { add("Favourite hour" to hourText(it, is24)) }
        add("Days played" to "${formatCount(i.daysPlayed)} ${if (i.daysPlayed == 1) "day" else "days"}")
        i.run?.let { add("Longest run" to "${it.first} days in a row") }
        add("Last heard" to timeAgo(lastAt))
    }
    StatsCard {
        rows.forEachIndexed { idx, (label, value) ->
            if (idx > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(OtoColors.Hairline))
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = OtoColors.TextTertiary)
                Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = OtoColors.TextPrimary)
            }
        }
    }
}

internal fun hourText(h: Int, is24: Boolean): String =
    if (is24) String.format(Locale.US, "%02d:00", h) else "${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "am" else "pm"}"

@Composable
private fun Listens(listens: List<TrackListen>, is24: Boolean) {
    val timeFmt = remember(is24) { DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", Locale.getDefault()) }
    StatsCard {
        listens.forEachIndexed { idx, l ->
            if (idx > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(OtoColors.Hairline))
            val at = Instant.ofEpochMilli(l.startedAt).atOffset(ZoneOffset.ofTotalSeconds(l.tzOffsetMinutes * 60))
            val app = MusicApps.known[l.sourcePackage] ?: l.sourcePackage.substringAfterLast('.')
            val result = when {
                l.skipped -> "Skipped"
                l.completionPct >= 90 -> "Finished"
                else -> "${l.completionPct}%"
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(dayFmt.format(at), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = OtoColors.TextPrimary)
                    Text("${timeFmt.format(at)} · $app", style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = OtoColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(result, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = if (l.skipped) OtoColors.TextTertiary else OtoColors.TextSecondary)
                    Canvas(Modifier.padding(top = 6.dp).width(56.dp).height(3.dp)) {
                        val rr = CornerRadius(size.height / 2)
                        drawRoundRect(Color.White.copy(alpha = 0.06f), size = size, cornerRadius = rr)
                        val w = size.width * (l.completionPct.coerceIn(0, 100) / 100f)
                        if (w > 0f) drawRoundRect(if (l.skipped) Color(0xFF3A3A3A) else Color(0xFF6A6A6A), size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = rr)
                    }
                }
            }
        }
    }
}
