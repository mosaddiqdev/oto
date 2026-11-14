package com.oto.stats.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oto.stats.AppContainer
import com.oto.stats.stats.ArtistDetail
import com.oto.stats.ui.components.TrackCover
import com.oto.stats.ui.nav.LocalNavigator
import com.oto.stats.ui.nav.Route
import com.oto.stats.ui.nav.doorway
import com.oto.stats.ui.stats.LitDigits
import com.oto.stats.ui.stats.StatsCard
import com.oto.stats.ui.stats.StatsSection
import com.oto.stats.ui.stats.formatCount
import com.oto.stats.ui.stats.formatDuration
import com.oto.stats.ui.stats.giant
import com.oto.stats.ui.stats.rememberCountUp
import com.oto.stats.ui.theme.OtoColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The Artist page. Same skeleton as the Track page (docs/DETAIL_PAGES.md):
 * back bar, hero numeral (plays), three facts, your history with them, their tracks and albums, habits.
 */

private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val fullFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
private val shortFmt = DateTimeFormatter.ofPattern("d MMM yy", Locale.getDefault())

@Composable
fun ArtistPage(route: Route.Artist, container: AppContainer, onBack: () -> Unit) {
    val window = route.window
    var allTime by rememberSaveable(route.encode()) { mutableStateOf(window == null) }
    val start = if (allTime || window == null) 0L else window.startMs
    val end = if (allTime || window == null) Long.MAX_VALUE else window.endMs
    val detail by produceState<ArtistDetail?>(null, route.id, start, end) {
        container.details.artist(route.id, start, end).collect { value = it }
    }
    val is24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val zone = ZoneId.systemDefault()
    val linkWindow = if (allTime) null else window
    val key = "${route.id}/$start/$end"

    Column(Modifier.fillMaxSize().background(OtoColors.Canvas).windowInsetsPadding(WindowInsets.statusBars)) {
        DetailTopBar(onBack, window, allTime) { allTime = it }
        val d = detail ?: return@Column
        val s = d.stats
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item(key = "hero") { Hero(d, container, key, if (allTime) null else window?.label) }
            if (s.plays == 0) {
                item(key = "none") {
                    StatsCard {
                        Text("Not played in ${window?.label ?: "this window"}", style = MaterialTheme.typography.titleMedium, color = OtoColors.TextPrimary)
                        if (!allTime) {
                            Text("See all time", style = MaterialTheme.typography.labelLarge, color = OtoColors.Accent, modifier = Modifier.padding(top = 10.dp).clickable { allTime = true })
                        }
                    }
                }
            } else {
                val first = Instant.ofEpochMilli(s.firstAt).atZone(zone).toLocalDate()
                item(key = "facts") {
                    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "First heard ${fullFmt.format(first)}. ${s.tracks} tracks. ${d.insights.daysPlayed} days." }) {
                        Fact("FIRST HEARD", shortFmt.format(first), Modifier.weight(1f))
                        FactDivider()
                        Fact("TRACKS", formatCount(s.tracks), Modifier.weight(1f))
                        FactDivider()
                        Fact("DAYS", formatCount(d.insights.daysPlayed), Modifier.weight(1f))
                    }
                }
                item(key = "history") {
                    val peak = d.insights.peakDay
                    val sentence = buildString {
                        append("First played ${fullFmt.format(first)}.")
                        if (peak != null && peak.plays > 1) append(" Busiest day: ${peak.plays} plays on ${dayFmt.format(LocalDate.ofEpochDay(peak.day))}.")
                    }
                    StatsSection("Your history with them", sentence = sentence) { HistoryCard(d.daily, first, peak?.day, zone, key) }
                }
                if (d.topTracks.isNotEmpty()) item(key = "tracks") {
                    StatsSection("Top tracks") { TopTracks(d, container, linkWindow) }
                }
                if (d.albums.isNotEmpty()) item(key = "albums") {
                    StatsSection("Albums") { Albums(d, container) }
                }
                item(key = "habits") { StatsSection("Habits") { HabitsCard(d.insights, s.lastAt, is24) } }
            }
            item { Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(1.dp)) }
        }
    }
}

@Composable
private fun Hero(d: ArtistDetail, container: AppContainer, key: String, windowLabel: String?) {
    val plays = rememberCountUp(d.stats.plays.toLong(), key, hero = true)
    val topKey = d.topTracks.firstOrNull()?.identityKey
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(d, topKey, container, 96)
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(d.artist.name, style = MaterialTheme.typography.headlineMedium, color = OtoColors.TextPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.padding(top = 28.dp), verticalAlignment = Alignment.Bottom) {
            val text = formatCount(plays.toInt())
            Text(text, style = giant(if (text.length > 5) 64f else 80f, -3.0).copy(brush = LitDigits, fontFeatureSettings = "tnum"), modifier = Modifier.alignByBaseline(), maxLines = 1)
            Text(if (d.stats.plays == 1) "play" else "plays", style = giant(22f, -0.3), color = OtoColors.TextTertiary, modifier = Modifier.alignByBaseline().padding(start = 10.dp))
        }
        if (d.stats.plays > 0) {
            val where = if (d.rank == 1) "Your most played artist" else "#${d.rank} of ${formatCount(d.artistsPlayed)} artists"
            Text(
                "${formatDuration(d.stats.playedMs)} listened · $where" + (windowLabel?.let { " in $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = OtoColors.TextSecondary,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** The artist's photo when we have one (fetched on demand), meanwhile the cover of their top track. */
@Composable
private fun Avatar(d: ArtistDetail, topKey: String?, container: AppContainer, size: Int) {
    val photoKey = container.artistPhotos.keyFor(d.artist.id)
    val photo by produceState<ImageBitmap?>(container.artStore.peek(photoKey), photoKey) {
        value = container.artStore.load(photoKey) ?: if (container.artistPhotos.ensure(d.artist.id, d.artist.name)) container.artStore.load(photoKey) else null
    }
    val shown = photo
    Box(Modifier.size(size.dp)) {
        TrackCover(identityKey = topKey ?: d.artist.name, title = d.artist.name, artStore = container.artStore, size = size.dp, circle = true)
        if (shown != null) {
            androidx.compose.foundation.Image(bitmap = shown, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size.dp).clip(CircleShape))
        }
    }
}

@Composable
private fun TopTracks(d: ArtistDetail, container: AppContainer, window: Route.Window?) {
    val nav = LocalNavigator.current
    StatsCard {
        d.topTracks.forEachIndexed { i, t ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(OtoColors.Hairline))
            Row(
                Modifier.fillMaxWidth().doorway { nav?.open(Route.Track(t.trackId, window)) }.padding(vertical = 10.dp).semantics(mergeDescendants = true) { contentDescription = "${i + 1}. ${t.title}, ${t.plays} plays" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = OtoColors.TextTertiary, modifier = Modifier.width(22.dp))
                TrackCover(t.identityKey, t.title, container.artStore, 44.dp)
                Text(t.title, style = MaterialTheme.typography.bodyLarge, color = OtoColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp).weight(1f))
                Text(
                    "${formatCount(t.plays)} ${if (t.plays == 1) "play" else "plays"}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = OtoColors.TextSecondary,
                    modifier = Modifier.padding(start = 12.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun Albums(d: ArtistDetail, container: AppContainer) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 4.dp)) {
        items(d.albums, key = { it.album }) { a ->
            Column(Modifier.width(112.dp).semantics(mergeDescendants = true) { contentDescription = "${a.album}, ${a.plays} plays" }) {
                TrackCover(a.identityKey, a.album, container.artStore, 112.dp)
                Text(a.album, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = OtoColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                Text("${formatCount(a.plays)} ${if (a.plays == 1) "play" else "plays"}", style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = OtoColors.TextTertiary, maxLines = 1)
            }
        }
    }
}
