package com.oto.stats.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oto.stats.data.ArtStore
import com.oto.stats.stats.PeriodKind
import com.oto.stats.stats.PeriodRank
import com.oto.stats.stats.StatsData
import com.oto.stats.stats.StatsMath
import com.oto.stats.ui.theme.Display
import com.oto.stats.ui.theme.OtoColors
import com.oto.stats.ui.theme.OtoSpace
import kotlin.math.abs

private val Ink = OtoColors.OnAccent
private val InkSoft = OtoColors.OnAccent.copy(alpha = 0.62f)

private fun giant(size: Int, line: Int, track: Double) = TextStyle(
    fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = size.sp, lineHeight = line.sp, letterSpacing = track.sp,
)

/** The label: flat Tape Yellow, giant black numerals. One accent field per screen, and this is it. */
@Composable
fun HeroCard(d: StatsData, periodLabel: String, kind: PeriodKind, artStore: ArtStore, modifier: Modifier = Modifier) {
    val key = periodLabel + kind.name
    val ms = rememberCountUp(d.headline.playedMs, key, hero = true)
    val coverKey = d.moments.heroCovers.firstOrNull()
    val cover by produceState<ImageBitmap?>(coverKey?.let { artStore.peek(it) }, coverKey) {
        value = coverKey?.let { artStore.load(it) }
    }
    val delta = StatsMath.delta(d.headline.playedMs, d.previous?.playedMs ?: 0)
    val sentence = heroSentence(d, kind, delta)
    val spoken = "Listening time, $periodLabel: ${formatDuration(d.headline.playedMs)}. $sentence"

    // The numerals are fixed art, so they ignore very large font scales rather than wrapping.
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = density.fontScale.coerceAtMost(1.1f))) {
        Box(
            modifier
                .fillMaxWidth()
                .heightIn(min = 336.dp)
                .clip(RoundedCornerShape(OtoSpace.CardRadius))
                .background(OtoColors.Accent)
                .semantics(mergeDescendants = true) { contentDescription = spoken },
        ) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text("LISTENING TIME", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp), color = InkSoft)
                Text(periodLabel, style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp), color = InkSoft)
                Spacer(Modifier.size(if (cover != null) 76.dp else 40.dp))
                HeroNumerals(ms)
                if (delta != null && d.comparisonName != null) {
                    DeltaChip(delta, deltaLabel(d, kind), Modifier.padding(top = 14.dp))
                }
                Text(
                    sentence,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                    color = Ink,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
            cover?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 20.dp, end = 24.dp)
                        .size(84.dp)
                        .graphicsLayer { rotationZ = 8f }
                        .shadow(16.dp, RoundedCornerShape(14.dp), clip = false, ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(RoundedCornerShape(14.dp)),
                )
            }
        }
    }
}

@Composable
private fun HeroNumerals(ms: Long) {
    val minutes = (ms / 60_000).coerceAtLeast(0)
    val h = minutes / 60
    val m = minutes % 60
    val lead = if (h > 0) h.toString() else m.toString()
    val (leadStyle, midStyle, unitStyle) = when {
        lead.length <= 2 -> Triple(giant(156, 132, -7.0), giant(72, 64, -3.0), giant(30, 30, -0.3))
        lead.length == 3 -> Triple(giant(112, 96, -5.0), giant(56, 52, -2.5), giant(26, 26, -0.3))
        else -> Triple(giant(80, 72, -3.5), giant(44, 42, -2.0), giant(22, 22, -0.3))
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(lead, style = leadStyle, color = Ink, modifier = Modifier.alignByBaseline())
        Text(if (h > 0) "h" else "m", style = unitStyle, color = InkSoft, modifier = Modifier.alignByBaseline().padding(start = 4.dp, end = 12.dp))
        if (h > 0) {
            Text(String.format(java.util.Locale.US, "%02d", m), style = midStyle, color = Ink, modifier = Modifier.alignByBaseline())
            Text("m", style = unitStyle, color = InkSoft, modifier = Modifier.alignByBaseline().padding(start = 3.dp))
        }
    }
}

@Composable
private fun DeltaChip(delta: StatsMath.Delta, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (delta.direction != StatsMath.Direction.FLAT) {
            Canvas(Modifier.size(10.dp)) {
                val up = delta.direction == StatsMath.Direction.UP
                val p = Path().apply {
                    if (up) { moveTo(size.width / 2, 0f); lineTo(size.width, size.height); lineTo(0f, size.height) }
                    else { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height) }
                    close()
                }
                drawPath(p, Ink)
            }
        } else {
            Text("=", style = MaterialTheme.typography.labelLarge, color = Ink)
        }
        Text(
            "  ${delta.percent}% $label",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = Ink,
        )
    }
}

private fun deltaLabel(d: StatsData, kind: PeriodKind): String {
    val name = d.comparisonName ?: return ""
    return if (d.comparisonPartial) "vs same days of $name".replace("same days of the week before", "same days the week before") else "vs $name"
}

/** One or two honest sentences, each backed by a computed number. */
internal fun heroSentence(d: StatsData, kind: PeriodKind, delta: StatsMath.Delta?): String {
    val parts = ArrayList<String>(2)
    val prev = d.previous?.playedMs
    val name = d.comparisonName
    if (delta != null && prev != null && name != null && delta.direction != StatsMath.Direction.FLAT) {
        val diff = formatDuration(abs(d.headline.playedMs - prev))
        val more = if (d.headline.playedMs >= prev) "more" else "less"
        val tail = if (d.comparisonPartial) " so far" else ""
        val than = if (name == "the week before") "the week before" else name
        parts.add("$diff $more than $than$tail.")
    }
    d.rank?.let { PeriodRank.sentence(it, kind).takeIf { s -> s.isNotEmpty() }?.let(parts::add) }
    if (parts.isEmpty()) parts.add(d.moments.equivalent)
    return parts.joinToString(" ")
}
