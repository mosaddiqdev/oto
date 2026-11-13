package com.oto.stats.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oto.stats.data.ArtStore
import com.oto.stats.stats.DiscoveryMoment
import com.oto.stats.stats.StatsData
import com.oto.stats.ui.components.TrackCover
import com.oto.stats.ui.theme.OtoColors

/*
 * Discovery. Spec: docs/STATS_DISCOVERY.md.
 * Did you find new music, or loop old favourites? One proportion, then the actual finds.
 */

/** The sentence above the card; null when new-to-you is not meaningful for this window. */
fun discoverySentence(d: StatsData): String? {
    val x = d.moments.discovery ?: return null
    if (x.uniqueTracks == 0) return null
    return when {
        x.newTracks == 0 -> "Nothing new this time. You stayed with familiar favourites."
        x.newTracks == x.uniqueTracks -> "Every track was new to you."
        else -> "${x.newTracks} of ${x.uniqueTracks} tracks were new to you."
    }
}

@Composable
fun DiscoveryCard(d: StatsData, artStore: ArtStore, key: String, modifier: Modifier = Modifier) {
    val x: DiscoveryMoment = d.moments.discovery ?: return
    val reduced = rememberReducedMotion()
    var played by rememberSaveable(key) { mutableStateOf(false) }
    val intro = remember(key) { Animatable(if (reduced || played) 1f else 0f) }
    LaunchedEffect(key) {
        if (!reduced && !played) {
            intro.animateTo(1f, tween(900, delayMillis = 200))
            played = true
        }
    }
    val share = if (x.uniqueTracks > 0) x.newTracks.toFloat() / x.uniqueTracks else 0f
    val summary = "Discovery. ${x.newTracks} of ${x.uniqueTracks} tracks were new to you, ${x.newArtists} new artists." +
        x.finds.joinToString(" ") { " Best find: ${it.title} by ${it.artist}." }.take(200)

    StatsCard(modifier.semantics(mergeDescendants = true) { contentDescription = summary }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("NEW TO YOU", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp), color = OtoColors.TextTertiary)
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 10.dp)) {
                    Text(x.newTracks.toString(), style = giant(64f, -2.8).copy(brush = LitDigits, fontFeatureSettings = "tnum"), modifier = Modifier.alignByBaseline())
                    Text("of ${x.uniqueTracks} tracks", style = giant(20f, -0.3), color = OtoColors.TextTertiary, modifier = Modifier.alignByBaseline().padding(start = 8.dp))
                }
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 4.dp)) {
                Text(x.newArtists.toString(), style = giant(34f, -1.0).copy(fontFeatureSettings = "tnum"), color = OtoColors.TextPrimary)
                Text(if (x.newArtists == 1) "new artist" else "new artists", style = MaterialTheme.typography.bodySmall, color = OtoColors.TextTertiary)
            }
        }

        // One bar: the new share lit, the familiar rest quiet.
        Canvas(Modifier.padding(top = 20.dp).fillMaxWidth().height(10.dp)) {
            val h = size.height
            drawRoundRect(Color.White.copy(alpha = 0.06f), size = size, cornerRadius = CornerRadius(h / 2))
            val w = (size.width * share * intro.value.coerceIn(0f, 1f)).let { if (x.newTracks > 0) it.coerceAtLeast(h) else 0f }
            if (w > 0f) {
                drawRoundRect(
                    Brush.horizontalGradient(listOf(Color(0xFFDBBE2C), OtoColors.Accent), endX = w.coerceAtLeast(1f)),
                    Offset.Zero, Size(w, h), CornerRadius(h / 2),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("New", style = MaterialTheme.typography.bodySmall, color = OtoColors.TextSecondary)
            Text("Familiar · ${x.uniqueTracks - x.newTracks}", style = MaterialTheme.typography.bodySmall, color = OtoColors.TextTertiary)
        }

        if (x.finds.isNotEmpty()) {
            Canvas(Modifier.padding(top = 18.dp).fillMaxWidth().height(1.dp)) { drawRect(Color.White.copy(alpha = 0.08f)) }
            Text("BEST FINDS", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp), color = OtoColors.TextTertiary, modifier = Modifier.padding(top = 16.dp))
            x.finds.forEach { t ->
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    TrackCover(identityKey = t.identityKey, title = t.title, artStore = artStore, size = 44.dp)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.titleSmall, color = OtoColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, style = MaterialTheme.typography.bodySmall, color = OtoColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        playsText(t.plays),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Medium),
                        color = OtoColors.TextSecondary,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
        if (x.replays > 0) {
            Text(
                "You also replayed songs ${formatCount(x.replays)} ${if (x.replays == 1) "time" else "times"}, coming back the same day.",
                style = MaterialTheme.typography.bodySmall,
                color = OtoColors.TextTertiary,
                modifier = Modifier.padding(top = 18.dp),
            )
        }
    }
}
