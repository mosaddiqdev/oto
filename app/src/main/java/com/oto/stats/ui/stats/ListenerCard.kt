package com.oto.stats.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oto.stats.stats.StatsData
import com.oto.stats.ui.theme.Display
import com.oto.stats.ui.theme.OtoColors
import com.oto.stats.ui.theme.OtoMotion
import com.oto.stats.ui.theme.OtoNumbers
import com.oto.stats.ui.theme.OtoSpace

private val Archetype = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 40.sp, letterSpacing = (-1.2).sp)

/** Who you are as a listener: a named archetype with the rule behind it, and four plain facts. */
@Composable
fun ListenerCard(d: StatsData, periodKey: Any) {
    val p = d.moments.personality
    val h = d.headline
    val plays = h.plays.coerceAtLeast(1)
    val finished = h.fullListens * 100 / plays
    val skipped = h.skips * 100 / plays

    StatsCard(Modifier.semantics(mergeDescendants = true) { contentDescription = "${p.name}. ${p.line}" }) {
        Text(p.name, style = Archetype, color = OtoColors.Accent)
        Text(p.line, style = MaterialTheme.typography.bodyLarge, color = OtoColors.TextSecondary, modifier = Modifier.padding(top = 8.dp))
        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RingTile("Finished", finished, periodKey, Modifier.weight(1f))
                RingTile("Skipped", skipped, periodKey, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberTile(d.replays.toString(), "Replays", Modifier.weight(1f))
                NumberTile(if (d.streaks.longest == 1) "1 day" else "${d.streaks.longest} days", "Longest streak", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RingTile(label: String, percent: Int, key: Any, modifier: Modifier) {
    val reduced = rememberReducedMotion()
    val sweep = remember(key) { Animatable(if (reduced) percent / 100f else 0f) }
    LaunchedEffect(key, percent) { sweep.animateTo(percent / 100f, OtoMotion.countUp()) }
    Row(
        modifier
            .background(OtoColors.Surface2, RoundedCornerShape(OtoSpace.TileRadius))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(44.dp)) {
            val stroke = 5.dp.toPx()
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(OtoColors.Surface3, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(OtoColors.Accent, -90f, 360f * sweep.value.coerceIn(0f, 1f), false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text("$percent%", style = OtoNumbers.Title, color = OtoColors.TextPrimary)
            Text(label, style = MaterialTheme.typography.labelMedium, color = OtoColors.TextTertiary, maxLines = 1)
        }
    }
}

@Composable
private fun NumberTile(value: String, label: String, modifier: Modifier) {
    Column(
        modifier
            .background(OtoColors.Surface2, RoundedCornerShape(OtoSpace.TileRadius))
            .padding(14.dp),
    ) {
        Text(value, style = OtoNumbers.Title, color = OtoColors.TextPrimary, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = OtoColors.TextTertiary, maxLines = 1)
    }
}
