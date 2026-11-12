package com.oto.stats.ui.stats

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oto.stats.data.ArtStore
import com.oto.stats.stats.StatsData
import com.oto.stats.ui.components.TrackCover
import com.oto.stats.ui.theme.OtoColors
import com.oto.stats.ui.theme.OtoMotion

/*
 * Most played. Spec: docs/STATS_MOST_PLAYED.md.
 * One card, one list at a time. #1 is featured; 2 to 5 are compact rows. Bars share one scale (the #1 value).
 */

/** One row of any of the three lists, reduced to what the card draws. */
private data class Entry(val title: String, val subtitle: String, val coverKey: String?, val plays: Int, val ms: Long)

private enum class Tab(val label: String) { Artists("Artists"), Tracks("Tracks"), Albums("Albums") }

private fun entries(d: StatsData, tab: Tab): List<Entry> = when (tab) {
    Tab.Artists -> d.topArtists.map { Entry(it.name, "", it.identityKey, it.plays, it.playedMs) }
    Tab.Tracks -> d.topTracks.map { Entry(it.title, it.artist, it.identityKey, it.plays, it.playedMs) }
    Tab.Albums -> d.topAlbums.map { Entry(it.album, it.artist, it.identityKey, it.plays, it.playedMs) }
}

private fun value(e: Entry, byTime: Boolean) = if (byTime) e.ms else e.plays.toLong()

private fun playsText(n: Int) = "${formatCount(n)} ${if (n == 1) "play" else "plays"}"

/** The one sentence above the card: what the leader is worth, as a share or a count. */
fun mostPlayedSentence(d: StatsData, tabIndex: Int, byTime: Boolean): String? {
    val tab = Tab.entries[tabIndex]
    val top = entries(d, tab).firstOrNull() ?: return null
    return when (tab) {
        Tab.Artists -> {
            val total = if (byTime) d.headline.playedMs else d.headline.plays.toLong()
            val share = if (total > 0) Math.round(value(top, byTime) * 100.0 / total).toInt() else 0
            if (share >= 5) "${top.title} was $share% of your ${if (byTime) "listening time" else "plays"}." else "${top.title} led the way."
        }
        Tab.Tracks -> if (byTime) "${top.title} got ${formatDuration(top.ms)} of your ears." else "${top.title} played ${playsText(top.plays)}."
        Tab.Albums -> "${top.title} led with ${if (byTime) formatDuration(top.ms) else playsText(top.plays)}."
    }
}

@Composable
fun MostPlayedCard(
    d: StatsData,
    byTime: Boolean,
    onByTime: (Boolean) -> Unit,
    tabIndex: Int,
    onTab: (Int) -> Unit,
    artStore: ArtStore,
    key: String,
    modifier: Modifier = Modifier,
) {
    StatsCard(modifier.animateContentSize(tween(260, easing = OtoMotion.Decelerate))) {
        Segmented(
            options = Tab.entries.map { it.label },
            selectedIndex = tabIndex,
            onSelect = onTab,
            modifier = Modifier.fillMaxWidth(),
            height = 40.dp,
        )
        MetricToggle(byTime, onByTime, Modifier.padding(top = 12.dp).align(Alignment.End))
        Crossfade(targetState = tabIndex to byTime, animationSpec = tween(180), label = "mostPlayed") { (t, time) ->
            val tab = Tab.entries[t]
            val list = entries(d, tab)
            if (list.isEmpty()) {
                Text(
                    if (tab == Tab.Albums) "No album information for these listens." else "Nothing here yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OtoColors.TextTertiary,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                Column(Modifier.padding(top = 16.dp)) {
                    val top = value(list.first(), time).coerceAtLeast(1L).toFloat()
                    Featured(list.first(), tab, time, artStore, "$key/$t/$time")
                    list.drop(1).forEachIndexed { i, e ->
                        ListRow(e, rank = i + 2, tab = tab, byTime = time, fraction = (value(e, time) / top).coerceIn(0f, 1f), artStore = artStore, key = "$key/$t/$time", index = i + 1)
                    }
                }
            }
        }
    }
}

/** Time | Plays. Small, quiet, right aligned: it changes the ranking, not the layout. */
@Composable
private fun MetricToggle(byTime: Boolean, onByTime: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("Ranked by", style = MaterialTheme.typography.bodySmall, color = OtoColors.TextTertiary, modifier = Modifier.padding(end = 8.dp))
        listOf(true to "Time", false to "Plays").forEach { (isTime, label) ->
            val selected = byTime == isTime
            Text(
                label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                color = if (selected) OtoColors.OnAccent else OtoColors.TextSecondary,
                modifier = Modifier
                    .semantics { contentDescription = "Rank by $label" }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { if (!selected) onByTime(isTime) }
                    .background(if (selected) OtoColors.Accent else Color.Transparent, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun Featured(e: Entry, tab: Tab, byTime: Boolean, artStore: ArtStore, key: String) {
    val fraction = rememberBar(1f, key, 0)
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = spoken(e, 1, tab) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(e, tab, 88, artStore)
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text("No. 1", style = MaterialTheme.typography.labelMedium, color = OtoColors.Accent)
                Text(
                    e.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = OtoColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (e.subtitle.isNotEmpty()) {
                    Text(e.subtitle, style = MaterialTheme.typography.bodyMedium, color = OtoColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                if (byTime) formatDuration(e.ms) else playsText(e.plays),
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                color = OtoColors.TextPrimary,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (byTime) playsText(e.plays) else formatDuration(e.ms),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = OtoColors.TextTertiary,
                maxLines = 1,
            )
        }
        BarLine(fraction, accent = true, thickness = 6, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ListRow(e: Entry, rank: Int, tab: Tab, byTime: Boolean, fraction: Float, artStore: ArtStore, key: String, index: Int) {
    val f = rememberBar(fraction, key, index)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .semantics(mergeDescendants = true) { contentDescription = spoken(e, rank, tab) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                rank.toString(),
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = OtoColors.TextTertiary,
                modifier = Modifier.width(20.dp),
            )
            Cover(e, tab, 44, artStore)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.titleSmall, color = OtoColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (e.subtitle.isNotEmpty()) {
                    Text(e.subtitle, style = MaterialTheme.typography.bodySmall, color = OtoColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(
                if (byTime) formatDuration(e.ms) else playsText(e.plays),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = OtoColors.TextSecondary,
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        BarLine(f, accent = false, thickness = 3, modifier = Modifier.padding(start = 32.dp, top = 8.dp))
    }
}

@Composable
private fun Cover(e: Entry, tab: Tab, size: Int, artStore: ArtStore) {
    TrackCover(
        identityKey = e.coverKey ?: e.title,
        title = e.title,
        artStore = artStore,
        size = size.dp,
        circle = tab == Tab.Artists,
    )
}

/** Grows once from the left, staggered by row, clamped so a spring never paints past the track. */
@Composable
private fun rememberBar(target: Float, key: String, index: Int): Float {
    val reduced = rememberReducedMotion()
    var played by rememberSaveable(key) { mutableStateOf(false) }
    val fill = remember(key) { Animatable(if (reduced || played) target else 0f) }
    LaunchedEffect(key, target) {
        if (reduced || played) {
            fill.snapTo(target)
        } else {
            fill.animateTo(target, tween(600, delayMillis = 150 + index * OtoMotion.BarStaggerMs * 2, easing = OtoMotion.Decelerate))
            played = true
        }
    }
    return fill.value.coerceIn(0f, 1f)
}

@Composable
private fun BarLine(fraction: Float, accent: Boolean, thickness: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(thickness.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(Color.White.copy(alpha = 0.05f), size = size, cornerRadius = r)
        val w = (size.width * fraction).coerceAtLeast(if (fraction > 0f) size.height else 0f)
        if (w > 0f) {
            val brush = if (accent) Brush.horizontalGradient(listOf(Color(0xFFC9A91C), OtoColors.Accent), endX = w.coerceAtLeast(1f)) else Brush.horizontalGradient(listOf(Color(0xFF3A3A3A), Color(0xFF4A4A4A)), endX = w.coerceAtLeast(1f))
            drawRoundRect(brush, topLeft = Offset.Zero, size = Size(w, size.height), cornerRadius = r)
        }
    }
}

private fun spoken(e: Entry, rank: Int, tab: Tab) =
    "Number $rank, ${e.title}${if (e.subtitle.isNotEmpty()) ", ${e.subtitle}" else ""}. ${formatDuration(e.ms)}, ${playsText(e.plays)}."

