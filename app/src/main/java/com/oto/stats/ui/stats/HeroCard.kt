package com.oto.stats.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Shadow
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
import java.util.Locale
import kotlin.math.abs

/*
 * Hero card. Spec: docs/STATS.md and docs/STATS_HERO.md.
 * A dark card where the yellow is light: lit numerals, a warm glow behind them, fine grain, a rim of light on the top edge.
 * Under the numerals: a two-bar comparison with the previous window, or three quiet facts when there is nothing to compare with.
 */

private val HeroBase = Color(0xFF151515)
private val LitDigits = Brush.verticalGradient(listOf(Color(0xFFFFF4A8), OtoColors.Accent, Color(0xFFC9A91C)))
private val DigitGlow = Shadow(OtoColors.Accent.copy(alpha = 0.30f), Offset.Zero, 38f)
private val Rim = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.04f)))

private fun giant(sp: Float, track: Double) = TextStyle(
    fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = sp.sp, lineHeight = (sp * 0.86f).sp, letterSpacing = track.sp,
)

@Composable
fun HeroCard(d: StatsData, periodLabel: String, kind: PeriodKind, modifier: Modifier = Modifier) {
    val key = periodLabel + kind.name
    val finalMs = d.headline.playedMs
    val ms = rememberCountUp(finalMs, key, hero = true)
    val delta = StatsMath.delta(finalMs, d.previous?.playedMs ?: 0)
    val sentence = heroSentence(d, kind, delta)
    val spoken = "Listening time, $periodLabel: ${spokenDuration(finalMs)}. $sentence"
    val shape = RoundedCornerShape(OtoSpace.CardRadius)
    // The glow warms up with the count, so the card seems to switch on.
    val warmth = if (finalMs > 0) (ms.toFloat() / finalMs).coerceIn(0f, 1f) else 1f

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
                            listOf(OtoColors.Accent.copy(alpha = 0.08f + 0.12f * warmth), Color.Transparent),
                            center = Offset(size.width * 0.22f, size.height * 0.40f),
                            radius = size.width * 0.85f,
                        ),
                    )
                    drawRect(Grain.brush, alpha = 0.05f)
                }
                .border(BorderStroke(1.dp, Rim), shape)
                .semantics(mergeDescendants = true) { contentDescription = spoken }
                .padding(24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("LISTENING TIME", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp), color = OtoColors.TextTertiary)
                Text(periodLabel.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp), color = OtoColors.TextTertiary, maxLines = 1)
            }
            HeroNumerals(shown = ms, final = finalMs, modifier = Modifier.padding(top = 30.dp))
            if (delta != null && d.previous != null && d.comparisonName != null) {
                Comparison(
                    delta = delta,
                    nowLabel = periodLabel,
                    nowMs = finalMs,
                    beforeLabel = beforeLabel(d.comparisonName, d.comparisonPartial),
                    beforeMs = d.previous.playedMs,
                    key = key,
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else {
                Facts(d, Modifier.padding(top = 24.dp))
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
 * The lead number is sized from the room available and from the FINAL value's length, never the counting value,
 * so it does not resize mid count-up. Hours and minutes sit beside it, smaller.
 *
 * Scale rules:
 *  - under an hour: minutes only, large
 *  - 1 to 999 hours: hours large, minutes beside it (up to about half the card for the lead)
 *  - 1,000 hours and up: hours only with thousands separators (minutes are noise), lead may use about 70% of the card
 */
@Composable
private fun HeroNumerals(shown: Long, final: Long, modifier: Modifier = Modifier) {
    val finalH = (final / 60_000).coerceAtLeast(0) / 60
    val minutes = (shown / 60_000).coerceAtLeast(0)
    val h = minutes / 60
    val m = minutes % 60
    val hoursMode = finalH > 0
    val bigHours = finalH >= 1000
    val finalLead = if (hoursMode) String.format(Locale.US, "%,d", finalH) else ((final / 60_000).coerceAtLeast(0)).toString()
    val lead = if (hoursMode) String.format(Locale.US, "%,d", h) else m.toString()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val scale = LocalDensity.current.fontScale
        val chars = finalLead.length - finalLead.count { it == ',' } * 0.5f
        val share = when { !hoursMode -> 0.55f; bigHours -> 0.72f; else -> 0.5f }
        // About 0.53em per digit at this tracking.
        val leadSp = (maxWidth.value * share / (0.53f * chars) / scale).coerceIn(48f, 132f)
        val midSp = (leadSp * 0.40f).coerceAtLeast(30f)
        val unitSp = (leadSp * 0.2f).coerceIn(20f, 30f)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                lead,
                style = giant(leadSp, -leadSp * 0.045.toDouble()).copy(brush = LitDigits, shadow = DigitGlow),
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                if (hoursMode) "h" else "m",
                style = giant(unitSp, -0.3),
                color = OtoColors.TextTertiary,
                modifier = Modifier.alignByBaseline().padding(start = 4.dp, end = 12.dp),
            )
            if (hoursMode && !bigHours) {
                Text(
                    String.format(Locale.US, "%02d", m),
                    style = giant(midSp, -midSp * 0.04.toDouble()).copy(fontFeatureSettings = "tnum"),
                    color = OtoColors.TextPrimary,
                    modifier = Modifier.alignByBaseline(),
                )
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BarRow(nowLabel, formatDuration(nowMs) + deltaText(delta), nowFill.value.coerceIn(0f, 1f), OtoColors.Accent, strong = true)
        BarRow(beforeLabel, formatDuration(beforeMs), beforeFill.value.coerceIn(0f, 1f), OtoColors.Surface3, strong = false)
    }
}

@Composable
private fun BarRow(label: String, value: String, fraction: Float, color: Color, strong: Boolean) {
    val text = if (strong) OtoColors.TextPrimary else OtoColors.TextTertiary
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = text, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            Text(
                value,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal),
                color = text,
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            drawRoundRect(Color.White.copy(alpha = 0.05f), size = size, cornerRadius = CornerRadius(size.height / 2))
            if (fraction > 0f) {
                val w = (size.width * fraction).coerceAtLeast(size.height)
                drawRoundRect(color, size = Size(w, size.height), cornerRadius = CornerRadius(size.height / 2))
            }
        }
    }
}

/** Plays, tracks and the daily average, for windows with nothing to compare against (All time, the first period). */
@Composable
private fun Facts(d: StatsData, modifier: Modifier = Modifier) {
    val avg = d.averagePerDay
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(1.dp)) { drawRect(Color.White.copy(alpha = 0.08f)) }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Fact(formatCount(d.headline.plays), "plays")
            Fact(formatCount(d.headline.uniqueTracks), "tracks")
            Fact(avg?.let { formatDuration(it.perDayMs) } ?: "–", if (avg?.activeOnly == true) "per active day" else "per day")
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Fact(value: String, label: String) {
    Column(Modifier.weight(1f, fill = false)) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), color = OtoColors.TextPrimary, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = OtoColors.TextTertiary, maxLines = 1)
    }
}

private fun deltaText(delta: StatsMath.Delta) = when (delta.direction) {
    StatsMath.Direction.UP -> "  ▲ ${delta.percent}%"
    StatsMath.Direction.DOWN -> "  ▼ ${delta.percent}%"
    StatsMath.Direction.FLAT -> "  = same"
}

private fun beforeLabel(name: String, partial: Boolean): String {
    val weekBefore = name == "the week before"
    val base = if (weekBefore) "The week before" else name.replaceFirstChar { it.uppercase() }
    return if (partial) (if (weekBefore) "Same days the week before" else "Same days of $name") else base
}

/** For TalkBack: "41 hours 12 minutes", not "41h 12m". */
internal fun spokenDuration(ms: Long): String {
    val minutes = (ms / 60_000).coerceAtLeast(0)
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h >= 1000 -> String.format(Locale.US, "%,d hours", h)
        h > 0 -> "$h ${if (h == 1L) "hour" else "hours"} $m ${if (m == 1L) "minute" else "minutes"}"
        else -> "$m ${if (m == 1L) "minute" else "minutes"}"
    }
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
        parts.add("$diff $more than $name$tail.")
    }
    d.rank?.let { PeriodRank.sentence(it, kind).takeIf { s -> s.isNotEmpty() }?.let(parts::add) }
    if (parts.isEmpty()) parts.add(d.moments.equivalent)
    return parts.joinToString(" ")
}
