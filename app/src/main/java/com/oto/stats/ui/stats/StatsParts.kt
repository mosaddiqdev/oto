package com.oto.stats.ui.stats

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oto.stats.stats.ActivityBar
import com.oto.stats.stats.BarState
import com.oto.stats.stats.BarUnit
import com.oto.stats.stats.StatsData
import com.oto.stats.stats.StatsMath
import com.oto.stats.ui.components.OtoCard
import com.oto.stats.ui.components.SectionLabel
import com.oto.stats.ui.components.hoursMinutes
import com.oto.stats.ui.theme.OtoColors
import com.oto.stats.ui.theme.OtoNumbers
import java.time.format.TextStyle
import java.util.Locale

/** A single capsule with a sliding indicator, the same language as the tab bar. */
@Composable
fun Segmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
) {
    Box(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(OtoColors.Surface1)
            .border(BorderStroke(1.dp, OtoColors.Hairline), CircleShape),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(3.dp)) {
            val segment = maxWidth / options.size
            val x by animateDpAsState(segment * selectedIndex, tween(260, easing = FastOutSlowInEasing), label = "seg")
            Box(
                Modifier
                    .offset(x = x)
                    .width(segment)
                    .fillMaxHeight()
                    .background(OtoColors.Accent, CircleShape),
            )
            Row(Modifier.fillMaxSize()) {
                options.forEachIndexed { i, label ->
                    val color by animateColorAsState(
                        if (i == selectedIndex) OtoColors.OnAccent else OtoColors.TextSecondary,
                        tween(200),
                        label = "segText",
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                            ) { onSelect(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun Chevron(left: Boolean, enabled: Boolean, onClick: () -> Unit, description: String) {
    val tint = if (enabled) OtoColors.TextPrimary else OtoColors.Hairline
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(if (enabled) Modifier.clickable(onClick = onClick, role = Role.Button) else Modifier)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                if (left) {
                    moveTo(w * 0.65f, h * 0.15f); lineTo(w * 0.3f, h * 0.5f); lineTo(w * 0.65f, h * 0.85f)
                } else {
                    moveTo(w * 0.35f, h * 0.15f); lineTo(w * 0.7f, h * 0.5f); lineTo(w * 0.35f, h * 0.85f)
                }
            }
            drawPath(path, tint, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** 12h 05m with the units quieter than the digits. */
@Composable
fun DurationText(ms: Long, digits: androidx.compose.ui.text.TextStyle, unitSize: Int, modifier: Modifier = Modifier) {
    val minutes = (ms / 60_000).coerceAtLeast(0)
    val h = minutes / 60
    val m = minutes % 60
    val unit = SpanStyle(fontSize = unitSize.sp, color = OtoColors.TextTertiary)
    Text(
        buildAnnotatedString {
            if (h > 0) {
                append(h.toString()); withStyle(unit) { append("h ") }
                append(String.format(Locale.US, "%02d", m)); withStyle(unit) { append("m") }
            } else {
                append(m.toString()); withStyle(unit) { append("m") }
            }
        },
        modifier = modifier,
        style = digits,
        color = OtoColors.TextPrimary,
    )
}

@Composable
fun DeltaPill(delta: StatsMath.Delta?, suffix: String) {
    if (delta == null) return
    val glyph = when (delta.direction) {
        StatsMath.Direction.UP -> "▲"
        StatsMath.Direction.DOWN -> "▼"
        StatsMath.Direction.FLAT -> "="
    }
    Row(
        Modifier
            .clip(CircleShape)
            .background(OtoColors.Surface2)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(glyph, style = MaterialTheme.typography.labelSmall, color = OtoColors.TextSecondary)
        Text(
            " ${delta.percent}%$suffix",
            style = MaterialTheme.typography.labelMedium,
            color = OtoColors.TextSecondary,
        )
    }
}

@Composable
fun HeroCard(d: StatsData) {
    OtoCard {
        SectionLabel("The numbers")
        val timeDelta = StatsMath.delta(d.headline.playedMs, d.previous?.playedMs ?: 0)
        if (timeDelta != null) {
            Row(Modifier.padding(top = 10.dp)) {
                DeltaPill(timeDelta, if (d.comparisonPartial) " time vs same days before" else " time vs before")
            }
        }
        Box(Modifier.padding(vertical = 18.dp).fillMaxWidth().height(1.dp).background(OtoColors.Hairline))
        Row(Modifier.fillMaxWidth()) {
            Figure("Plays", d.headline.plays, d.previous?.plays, Modifier.weight(1f))
            Figure("Tracks", d.headline.uniqueTracks, d.previous?.uniqueTracks, Modifier.weight(1f))
            Figure("Artists", d.uniqueArtists, d.previousUniqueArtists, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Figure(label: String, value: Int, previous: Int?, modifier: Modifier) {
    Column(modifier) {
        Text(String.format(Locale.US, "%,d", value), style = OtoNumbers.Title, color = OtoColors.TextPrimary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = OtoColors.TextTertiary)
        val delta = StatsMath.delta(value.toLong(), (previous ?: 0).toLong())
        if (delta != null) {
            val g = when (delta.direction) {
                StatsMath.Direction.UP -> "▲"
                StatsMath.Direction.DOWN -> "▼"
                StatsMath.Direction.FLAT -> "="
            }
            Text("$g ${delta.percent}%", style = MaterialTheme.typography.labelSmall, color = OtoColors.TextTertiary, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

private fun barName(bar: ActivityBar, locale: Locale = Locale.getDefault()): String = when (bar.unit) {
    BarUnit.DAY -> "${bar.start.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)}, ${bar.start.month.getDisplayName(TextStyle.SHORT, locale)} ${bar.start.dayOfMonth}"
    BarUnit.MONTH -> "${bar.start.month.getDisplayName(TextStyle.FULL, locale)} ${bar.start.year}"
    BarUnit.YEAR -> bar.start.year.toString()
}

private fun axisLabel(bar: ActivityBar, index: Int, count: Int, locale: Locale = Locale.getDefault()): String? = when (bar.unit) {
    BarUnit.DAY -> if (count <= 7) bar.start.dayOfWeek.getDisplayName(TextStyle.NARROW, locale)
    else if (index % 7 == 0) bar.start.dayOfMonth.toString() else null
    BarUnit.MONTH -> if (count <= 12) bar.start.month.getDisplayName(TextStyle.NARROW, locale)
    else if (bar.start.monthValue == 1) bar.start.year.toString() else null
    BarUnit.YEAR -> if (count <= 8 || index % 2 == 0) bar.start.year.toString() else null
}

/**
 * Bars for the period. The busiest one is lit by default; tap or drag to read any other.
 * Empty bars say why: a real zero, before tracking began, or not yet.
 */
@Composable
fun ActivityCard(d: StatsData, periodKey: String) {
    val bars = d.bars
    val peak = remember(bars) { bars.indices.maxByOrNull { bars[it].playedMs } ?: 0 }
    var picked by remember(periodKey, bars.size) { mutableIntStateOf(-1) }
    val shown = if (picked in bars.indices) picked else peak
    val bar = bars.getOrNull(shown)
    val max = (bars.maxOfOrNull { it.playedMs } ?: 0L).coerceAtLeast(1L)
    var widthPx by remember { mutableIntStateOf(1) }
    val gap = if (bars.size > 20) 3.dp else 8.dp

    fun pick(x: Float) {
        if (bars.isEmpty()) return
        picked = ((x / widthPx) * bars.size).toInt().coerceIn(0, bars.size - 1)
    }

    OtoCard {
        SectionLabel("Activity")
        if (bar != null) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        (if (picked == -1) "Busiest · " else "") + barName(bar),
                        style = MaterialTheme.typography.labelMedium,
                        color = OtoColors.TextTertiary,
                    )
                    DurationText(bar.playedMs, OtoNumbers.Large.copy(fontSize = 32.sp, lineHeight = 36.sp), unitSize = 16)
                }
                if (bar.state == BarState.BEFORE_TRACKING) {
                    Text("Before tracking began", style = MaterialTheme.typography.labelMedium, color = OtoColors.TextTertiary)
                } else if (bar.plays > 0) {
                    Text("${bar.plays} plays", style = OtoNumbers.Small, color = OtoColors.TextTertiary)
                }
            }
        }
        Row(
            Modifier
                .padding(top = 18.dp)
                .fillMaxWidth()
                .height(112.dp)
                .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
                .pointerInput(bars) { detectTapGestures { pick(it.x) } }
                .pointerInput(bars) { detectHorizontalDragGestures(onDragStart = { pick(it.x) }) { change, _ -> pick(change.position.x) } },
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEachIndexed { i, b -> ChartBar(b, max, selected = i == shown, index = i) }
        }
        Row(Modifier.padding(top = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            bars.forEachIndexed { i, b ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        axisLabel(b, i, bars.size).orEmpty(),
                        modifier = Modifier.wrapContentWidth(unbounded = true),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (i == shown) OtoColors.TextPrimary else OtoColors.TextTertiary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        d.averagePerDay?.let {
            Text(
                "Average ${hoursMinutes(it.perDayMs)} a day over ${it.overDays} days",
                style = MaterialTheme.typography.bodySmall,
                color = OtoColors.TextTertiary,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun RowScope.ChartBar(bar: ActivityBar, max: Long, selected: Boolean, index: Int) {
    val target = if (bar.state == BarState.TRACKED && bar.playedMs > 0) (bar.playedMs.toFloat() / max).coerceIn(0.06f, 1f) else 0.04f
    val fraction by animateFloatAsState(target, tween(520, delayMillis = (index * 12).coerceAtMost(300)), label = "bar")
    val color = when {
        bar.state == BarState.FUTURE -> OtoColors.Hairline.copy(alpha = 0.5f)
        bar.state == BarState.BEFORE_TRACKING -> OtoColors.Hairline.copy(alpha = 0.5f)
        bar.playedMs <= 0 -> OtoColors.Hairline
        selected -> OtoColors.Accent
        else -> OtoColors.Surface3
    }
    val radius = 4.dp
    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction.coerceIn(0f, 1f))
                .background(color, RoundedCornerShape(topStart = radius, topEnd = radius, bottomStart = 2.dp, bottomEnd = 2.dp)),
        )
    }
}
