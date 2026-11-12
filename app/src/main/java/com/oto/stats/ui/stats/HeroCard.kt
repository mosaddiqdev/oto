package com.oto.stats.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oto.stats.stats.PeriodKind
import com.oto.stats.stats.PeriodRank
import com.oto.stats.stats.StatsData
import com.oto.stats.stats.StatsMath
import com.oto.stats.ui.poster.Grain
import com.oto.stats.ui.theme.Display
import com.oto.stats.ui.theme.OtoColors
import com.oto.stats.ui.theme.OtoMotion
import com.oto.stats.ui.theme.OtoSpace
import kotlin.math.abs

private val HeroBase = Color(0xFF151515)
/** Light from the top edge of the digits down to a warm, deeper yellow. Gives the numerals weight without a flat fill. */
private val LitDigits = Brush.verticalGradient(listOf(Color(0xFFFFF4A8), OtoColors.Accent, Color(0xFFC9A91C)))

private fun giant(sp: Float, track: Double) = TextStyle(
    fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = sp.sp, lineHeight = (sp * 0.86f).sp, letterSpacing = track.sp,
)

/**
 * The hero. A dark card with a warm glow behind yellow-lit numerals, and a two-bar comparison instead of a badge.
 * The yellow is light, not paint: gradient digits, a soft glow and fine grain.
 */
@Composable
fun HeroCard(d: StatsData, periodLabel: String, kind: PeriodKind, modifier: Modifier = Modifier) {
    val key = periodLabel + kind.name
    val ms = rememberCountUp(d.headline.playedMs, key, hero = true)
    val delta = StatsMath.delta(d.headline.playedMs, d.previous?.playedMs ?: 0)
    val sentence = heroSentence(d, kind, delta)
    val spoken = "Listening time, $periodLabel: ${formatDuration(d.headline.playedMs)}. $sentence"
    val shape = RoundedCornerShape(OtoSpace.CardRadius)

    // The numerals are fixed art, so they ignore very large font scales rather than wrapping.
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = density.fontScale.coerceAtMost(1.1f))) {
        Column(
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(HeroBase)
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            listOf(OtoColors.Accent.copy(alpha = 0.20f), Color.Transparent),
                            center = Offset(size.width * 0.22f, size.height * 0.40f),
                            radius = size.width * 0.85f,
                        ),
                    )
                    drawRect(Grain.brush, alpha = 0.05f)
                }
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), shape)
                .semantics(mergeDescendants = true) { contentDescription = spoken }
                .padding(24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("LISTENING TIME", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp), color = OtoColors.TextTertiary)
                Text(periodLabel.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp), color = OtoColors.TextTertiary)
            }
            HeroNumerals(ms, Modifier.padding(top = 36.dp))
            if (delta != null && d.previous != null && d.comparisonName != null) {
                Comparison(
                    delta = delta,
                    nowLabel = periodLabel,
                    nowMs = d.headline.playedMs,
                    beforeLabel = beforeLabel(d.comparisonName, d.comparisonPartial),
                    beforeMs = d.previous.playedMs,
                    key = key,
                    modifier = Modifier.padding(top = 28.dp),
                )
            }
            Text(
                sentence,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = OtoColors.TextSecondary,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }
}

/**
 * Lead number sized from the room available, so it takes at most about half the card whatever the digit count.
 * Hours and minutes sit beside it, smaller, which keeps the composition the same shape from 5 minutes to 5,000 hours.
 */
@Composable
private fun HeroNumerals(ms: Long, modifier: Modifier = Modifier) {
    val minutes = (ms / 60_000).coerceAtLeast(0)
    val h = minutes / 60
    val m = minutes % 60
    val lead = if (h > 0) h.toString() else m.toString()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val scale = LocalDensity.current.fontScale
        // About 0.53em per digit with the tight tracking; allow the lead number half the width.
        val leadSp = (maxWidth.value * 0.5f / (0.53f * lead.length) / scale).coerceIn(48f, 132f)
        val midSp = (leadSp * 0.40f).coerceAtLeast(30f)
        val unitSp = (leadSp * 0.2f).coerceIn(20f, 30f)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(lead, style = giant(leadSp, -leadSp * 0.045).copy(brush = LitDigits), modifier = Modifier.alignByBaseline())
            Text(if (h > 0) "h" else "m", style = giant(unitSp, -0.3), color = OtoColors.TextTertiary, modifier = Modifier.alignByBaseline().padding(start = 4.dp, end = 14.dp))
            if (h > 0) {
                Text(String.format(java.util.Locale.US, "%02d", m), style = giant(midSp, -midSp * 0.04), color = OtoColors.TextPrimary, modifier = Modifier.alignByBaseline())
                Text("m", style = giant(unitSp, -0.3), color = OtoColors.TextTertiary, modifier = Modifier.alignByBaseline().padding(start = 3.dp))
            }
        }
    }
}

/** This period against the same stretch of the one before, as two bars on one scale. */
@Composable
private fun Comparison(
    delta: StatsMath.Delta, nowLabel: String, nowMs: Long, beforeLabel: String, beforeMs: Long, key: String, modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val top = maxOf(nowMs, beforeMs, 1L).toFloat()
    val nowTarget = (nowMs / top).coerceIn(0f, 1f)
    val beforeTarget = (beforeMs / top).coerceIn(0f, 1f)
    val nowFill = remember(key) { Animatable(if (reduced) nowTarget else 0f) }
    val beforeFill = remember(key) { Animatable(if (reduced) beforeTarget else 0f) }
    LaunchedEffect(key, nowTarget, beforeTarget) {
        if (reduced) {
            nowFill.snapTo(nowTarget); beforeFill.snapTo(beforeTarget)
        } else {
            nowFill.animateTo(nowTarget, OtoMotion.countUp(hero = true))
            beforeFill.animateTo(beforeTarget, OtoMotion.countUp(hero = true))
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BarRow(nowLabel, formatDuration(nowMs) + deltaText(delta), nowFill.value.coerceIn(0f, 1f), OtoColors.Accent, strong = true)
        BarRow(beforeLabel, formatDuration(beforeMs), beforeFill.value.coerceIn(0f, 1f), OtoColors.Surface3, strong = false)
    }
}

@Composable
private fun BarRow(label: String, value: String, fraction: Float, color: Color, strong: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = if (strong) OtoColors.TextPrimary else OtoColors.TextTertiary, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal), color = if (strong) OtoColors.TextPrimary else OtoColors.TextTertiary, maxLines = 1, modifier = Modifier.padding(start = 12.dp))
        }
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            drawRoundRect(Color.White.copy(alpha = 0.05f), size = size, cornerRadius = CornerRadius(size.height / 2))
            if (fraction > 0f) drawRoundRect(color, size = Size(size.width * fraction, size.height).let { if (it.width < size.height) Size(size.height, size.height) else it }, cornerRadius = CornerRadius(size.height / 2))
        }
    }
}

private fun deltaText(delta: StatsMath.Delta) = when (delta.direction) {
    StatsMath.Direction.UP -> "  \u25B2 ${delta.percent}%"
    StatsMath.Direction.DOWN -> "  \u25BC ${delta.percent}%"
    StatsMath.Direction.FLAT -> "  = same"
}

private fun beforeLabel(name: String, partial: Boolean): String {
    val base = if (name == "the week before") "The week before" else name.replaceFirstChar { it.uppercase() }
    return if (partial) (if (name == "the week before") "Same days the week before" else "Same days of $name") else base
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
