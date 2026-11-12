package com.oto.stats.ui.stats

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oto.stats.data.ArtStore
import com.oto.stats.stats.StatsData
import com.oto.stats.ui.components.TrackCover
import com.oto.stats.ui.theme.OtoColors
import com.oto.stats.ui.theme.OtoMotion
import com.oto.stats.ui.theme.OtoNumbers
import kotlin.math.min

private class Ranked(
    val title: String,
    val subtitle: String?,
    val plays: Int,
    val playedMs: Long,
    /** A track whose cover to show; null draws a round monogram (used for artists). */
    val coverKey: String?,
)

private enum class Kind(val label: String) { ARTISTS("Artists"), TRACKS("Tracks"), ALBUMS("Albums") }

/** One ranked list at a time. The leader is large with a yellow line; the rest share a grey scale. */
@Composable
fun MostPlayedCard(d: StatsData, byTime: Boolean, onByTime: (Boolean) -> Unit, artStore: ArtStore, periodKey: Any) {
    var tabIndex by rememberSaveable { mutableIntStateOf(1) }
    val tabs = remember(d.topAlbums.isNotEmpty()) {
        if (d.topAlbums.isNotEmpty()) Kind.entries else listOf(Kind.ARTISTS, Kind.TRACKS)
    }
    val tab = tabs[tabIndex.coerceIn(0, tabs.size - 1)]
    val items = remember(d, tab) {
        when (tab) {
            Kind.ARTISTS -> d.topArtists.map { Ranked(it.name, null, it.plays, it.playedMs, null) }
            Kind.TRACKS -> d.topTracks.map { Ranked(it.title, it.artist, it.plays, it.playedMs, it.identityKey) }
            Kind.ALBUMS -> d.topAlbums.map { Ranked(it.album, it.artist, it.plays, it.playedMs, it.identityKey) }
        }
    }

    StatsCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Segmented(
                options = tabs.map { it.label },
                selectedIndex = tabs.indexOf(tab),
                onSelect = { tabIndex = it },
                modifier = Modifier.weight(1f),
                height = 40.dp,
            )
            MetricToggle(byTime, onByTime)
        }
        Crossfade(targetState = tab to items, animationSpec = tween(180), label = "rankedTab") { (_, rows) ->
            Column {
                if (rows.isEmpty()) {
                    Text("Nothing yet", style = MaterialTheme.typography.bodyMedium, color = OtoColors.TextTertiary, modifier = Modifier.padding(top = 20.dp))
                }
                val lead = rows.maxOfOrNull { if (byTime) it.playedMs else it.plays.toLong() }?.coerceAtLeast(1L) ?: 1L
                rows.forEachIndexed { i, r ->
                    RankRow(i, r, (if (byTime) r.playedMs else r.plays.toLong()).toFloat() / lead, byTime, artStore, periodKey)
                }
            }
        }
    }
}

@Composable
private fun MetricToggle(byTime: Boolean, onChange: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        for ((label, isTime) in listOf("Time" to true, "Plays" to false)) {
            val on = byTime == isTime
            Text(
                label,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                    ) {
                        if (!on) {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onChange(isTime)
                        }
                    }
                    .padding(horizontal = 6.dp)
                    .wrapCenter(),
                style = if (on) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
                color = if (on) OtoColors.TextPrimary else OtoColors.TextTertiary,
            )
        }
    }
}

private fun Modifier.wrapCenter() = this.then(Modifier.padding(vertical = 14.dp))

@Composable
private fun RankRow(index: Int, item: Ranked, share: Float, byTime: Boolean, artStore: ArtStore, periodKey: Any) {
    val first = index == 0
    val reduced = rememberReducedMotion()
    val line = remember(periodKey, item.title) { Animatable(if (reduced) share else 0f) }
    LaunchedEffect(periodKey, share, byTime) {
        line.animateTo(share, tween(600, delayMillis = if (reduced) 0 else min(index * 60, 300)))
    }
    val cover = if (first) 72.dp else 48.dp

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = if (first) 22.dp else 16.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Number ${index + 1}, ${item.title}${item.subtitle?.let { ", $it" } ?: ""}. ${formatDuration(item.playedMs)}, ${item.plays} plays."
            },
        verticalAlignment = if (first) Alignment.Top else Alignment.CenterVertically,
    ) {
        Text(
            "${index + 1}",
            modifier = Modifier.width(24.dp).padding(top = if (first) 2.dp else 0.dp),
            style = OtoNumbers.Body,
            color = if (first) OtoColors.Accent else OtoColors.TextTertiary,
        )
        Cover(item, cover, artStore)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                item.title,
                style = if (first) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                color = OtoColors.TextPrimary,
                maxLines = if (first) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle != null) {
                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = OtoColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(3.dp).background(OtoColors.Surface2, CircleShape)) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(line.value.coerceIn(0f, 1f))
                        .background(if (first) OtoColors.Accent else OtoColors.TextTertiary.copy(alpha = 0.6f), CircleShape),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(if (byTime) formatDuration(item.playedMs) else "${item.plays}", style = OtoNumbers.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = OtoColors.TextPrimary)
            Text(if (byTime) "${item.plays} plays" else formatDuration(item.playedMs), style = OtoNumbers.Small, color = OtoColors.TextTertiary)
        }
    }
}

@Composable
private fun Cover(item: Ranked, size: Dp, artStore: ArtStore) {
    if (item.coverKey != null) {
        TrackCover(item.coverKey, item.title, artStore, size)
    } else {
        Box(Modifier.size(size).background(OtoColors.Surface2, CircleShape), contentAlignment = Alignment.Center) {
            Text(
                item.title.firstOrNull { it.isLetterOrDigit() }?.uppercase().orEmpty(),
                style = if (size > 56.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                color = OtoColors.TextTertiary,
            )
        }
    }
}
