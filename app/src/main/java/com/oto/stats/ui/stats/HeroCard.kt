package com.oto.stats.ui.stats

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.StrokeCap
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
internal val LitDigits = Brush.verticalGradient(listOf(Color(0xFFF6E479), OtoColors.Accent, Color(0xFFDBBE2C)))
private val Rim = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.04f)))

internal fun giant(sp: Float, track: Double) = TextStyle(
    fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = sp.sp, lineHeight = (sp * 0.86f).sp, letterSpacing = track.sp,
)

@Composable
fun HeroCard(d: StatsData, periodLabel: String, kind: PeriodKind, modifier: Modifier = Modifier) {
    val key = periodLabel + kind.name
    val finalMs = d.headline.playedMs
    val ms = rememberCountUp(finalMs, key, hero = true)
    val delta = StatsMath.delta(finalMs, d.previous?.playedMs ?: 0)
    val story = heroStory(d, kind, delta)
    val spoken = buildString {
        append("Listening time, $periodLabel: ${spokenDuration(finalMs)}. ")
        if (delta != null && d.comparisonName != null) append("${deltaSpoken(delta)} ${beforeLabel(d.comparisonName, d.comparisonPartial)}. ")
        append(story)
    }
    val shape = RoundedCornerShape(OtoSpace.CardRadius)
    // The glow warms up with the count, so the card seems to switch on.
    val warmth = if (finalMs > 0) (ms.toFloat() / finalMs).coerceIn(0f, 1f) else 1f

    // The numerals are fixed art, so they ignore very large font scales rather than wrapping.
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = density.fontScale.coerceAtMost(1.1f))) {
        Column(
            modifier
                .fillMaxWidth()
                // Before clip and padding, so a change between layouts (comparison, facts, one or two lines) eases instead of jumping.
                .animateContentSize(tween(260, easing = OtoMotion.Decelerate))
                .clip(shape)
                .background(HeroBase)
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            listOf(OtoColors.Accent.copy(alpha = 0.08f + 0.12f * warmth), Color.Transparent),
                            center = Offset(size.width * 0.22f, size.height * 0.42f),
                            radius = size.width * 0.85f,
                        ),
                    )
                    drawRect(Grain.brush, alpha = 0.05f)
                }
                .border(BorderStroke(1.dp, Rim), shape)
                .semantics(mergeDescendants = true) { contentDescription = spoken }
                .padding(24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "LISTENING TIME \u00B7 ${periodLabel.uppercase()}",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
                    color = OtoColors.TextTertiary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (delta != null && d.previous != null && d.comparisonName != null) DeltaPill(delta, Modifier.padding(start = 12.dp))
            }
            HeroNumerals(shown = ms, final = finalMs, modifier = Modifier.padding(top = 26.dp))
            if (delta != null && d.previous != null && d.comparisonName != null) {
                Comparison(
                    nowLabel = periodLabel,
                    nowMs = finalMs,
                    beforeLabel = beforeLabel(d.comparisonName, d.comparisonPartial).removePrefix("Same days of ").removePrefix("Same days "),
                    beforeMs = d.previous.playedMs,
                    key = key,
                    modifier = Modifier.padding(top = 28.dp),
                )
            } else {
                Facts(d, Modifier.padding(top = 28.dp))
            }
            Text(
                story,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = OtoColors.TextSecondary,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }
}

/** The change at a glance. Yellow when up; quiet grey when down, because listening less is not an error. */
@Composable
private fun DeltaPill(delta: StatsMath.Delta, modifier: Modifier = Modifier) {
    val up = delta.direction == StatsMath.Direction.UP
    Text(
        when (delta.direction) {
            StatsMath.Direction.UP -> "\u25B2 ${delta.percent}%"
            StatsMath.Direction.DOWN -> "\u25BC ${delta.percent}%"
            StatsMath.Direction.FLAT -> "= same"
        },
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = if (up) OtoColors.Accent else OtoColors.TextSecondary,
        modifier = modifier
            .clip(CircleShape)
            .background(if (up) OtoColors.Accent.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
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
                style = giant(leadSp, -leadSp * 0.045.toDouble()).copy(brush = LitDigits),
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

/**
 * One track. The yellow fill is this period, the white tick is where the comparison window ended up, on the same scale.
 * Fill past the tick means ahead of it. Under it a two-part legend names both with their values.
 */
@Composable
private fun Comparison(nowLabel: String, nowMs: Long, beforeLabel: String, beforeMs: Long, key: String, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    // 8% headroom so the longer of the two never touches the end of the track.
    val top = maxOf(nowMs, beforeMs, 1L) * 1.08f
    val nowTarget = (nowMs / top).coerceIn(0f, 1f)
    val beforeTarget = (beforeMs / top).coerceIn(0f, 1f)
    val fill = remember(key) { Animatable(if (reduced) nowTarget else 0f) }
    val tick = remember(key) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(key, nowTarget) {
        if (reduced) {
            fill.snapTo(nowTarget); tick.snapTo(1f)
        } else {
            fill.animateTo(nowTarget, OtoMotion.countUp(hero = true))
            tick.animateTo(1f, tween(220))
        }
    }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(20.dp)) {
            val h = 10.dp.toPx()
            val y = (size.height - h) / 2
            drawRoundRect(Color.White.copy(alpha = 0.06f), topLeft = Offset(0f, y), size = Size(size.width, h), cornerRadius = CornerRadius(h / 2))
            val w = (size.width * fill.value.coerceIn(0f, 1f)).coerceAtLeast(if (nowMs > 0) h else 0f)
            if (w > 0f) {
                drawRoundRect(
                    Brush.horizontalGradient(listOf(Color(0xFFC9A91C), OtoColors.Accent), endX = w.coerceAtLeast(1f)),
                    topLeft = Offset(0f, y), size = Size(w, h), cornerRadius = CornerRadius(h / 2),
                )
            }
            val x = size.width * beforeTarget
            drawLine(Color.White.copy(alpha = 0.78f * tick.value.coerceIn(0f, 1f)), Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(OtoColors.Accent, RoundedCornerShape(2.dp)))
                Text(
                    formatDuration(nowMs), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = OtoColors.TextPrimary, maxLines = 1, modifier = Modifier.padding(start = 6.dp),
                )
                Text(nowLabel, style = MaterialTheme.typography.bodySmall, color = OtoColors.TextTertiary, maxLines = 1, modifier = Modifier.padding(start = 5.dp, end = 12.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(2.dp).height(10.dp).background(Color.White.copy(alpha = 0.78f)))
                Text(
                    formatDuration(beforeMs), style = MaterialTheme.typography.bodySmall,
                    color = OtoColors.TextSecondary, maxLines = 1, modifier = Modifier.padding(start = 6.dp),
                )
                Text(beforeLabel, style = MaterialTheme.typography.bodySmall, color = OtoColors.TextTertiary, maxLines = 1, modifier = Modifier.padding(start = 5.dp))
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

private fun deltaSpoken(delta: StatsMath.Delta) = when (delta.direction) {
    StatsMath.Direction.UP -> "Up ${delta.percent} percent on"
    StatsMath.Direction.DOWN -> "Down ${delta.percent} percent on"
    StatsMath.Direction.FLAT -> "Same as"
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

/**
 * The one line under the card. The percentage and both values are already on the card, so this adds meaning:
 * where the period ranks in your own history, else the plain difference, else a human-scale equivalent.
 */
internal fun heroStory(d: StatsData, kind: PeriodKind, delta: StatsMath.Delta?): String {
    d.rank?.let { r -> PeriodRank.sentence(r, kind).takeIf { it.isNotEmpty() }?.let { return it } }
    val prev = d.previous?.playedMs
    val name = d.comparisonName
    if (delta != null && prev != null && name != null && delta.direction != StatsMath.Direction.FLAT) {
        val diff = formatDuration(abs(d.headline.playedMs - prev))
        val more = if (d.headline.playedMs >= prev) "more" else "less"
        return "$diff $more than $name${if (d.comparisonPartial) " so far" else ""}."
    }
    return d.moments.equivalent
}
