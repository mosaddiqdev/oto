package com.oto.stats.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oto.stats.stats.Records
import com.oto.stats.stats.StatsData
import com.oto.stats.ui.theme.OtoColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Records. Spec: docs/STATS_RECORDS.md.
 * Plaques, not tiles: one personal best per row with its glyph, its value as the headline and its date.
 * A yellow ALL-TIME BEST tag appears only when nothing in your whole history beats it.
 */

private val Plate = Color(0xFF222222)
private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val shortFmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

private enum class Mark { Peak, Flame, Bars, Loop }

private data class Plaque(val label: String, val value: String, val sub: String, val best: Boolean, val mark: Mark)

private fun plaques(r: Records): List<Plaque> {
    val out = ArrayList<Plaque>(4)
    r.biggestDay?.let { out.add(Plaque("BIGGEST DAY", formatDuration(it.value), dayFmt.format(it.day), it.allTimeBest, Mark.Peak)) }
    r.longestRun?.let {
        out.add(Plaque("LONGEST STREAK", "${it.length} days", "${shortFmt.format(it.start)} – ${shortFmt.format(it.end)}", it.allTimeBest, Mark.Flame))
    }
    r.mostPlaysDay?.let { out.add(Plaque("MOST PLAYS IN A DAY", "${formatCount(it.value.toInt())} plays", dayFmt.format(it.day), it.allTimeBest, Mark.Bars)) }
    r.mostReplayed?.let { out.add(Plaque("MOST REPLAYED", it.title, "${it.total} plays", false, Mark.Loop)) }
    return out
}

/** The sentence above the card. */
fun recordsSentence(d: StatsData): String? {
    val r = d.records ?: return null
    if (plaques(r).isEmpty()) return null
    if (r.anyAllTimeBest && d.moments.personality.name != "") {
        val which = when {
            r.biggestDay?.allTimeBest == true -> "your biggest day ever"
            r.longestRun?.allTimeBest == true -> "your longest streak ever"
            else -> "your most plays in a day"
        }
        return "A personal best: $which is in this window."
    }
    return r.biggestDay?.let { "Your biggest day was ${dayFmt.format(it.day)}, with ${formatDuration(it.value)}." }
}

@Composable
fun RecordsCard(d: StatsData, key: String, modifier: Modifier = Modifier) {
    val r = d.records ?: return
    val list = remember(r) { plaques(r) }
    if (list.isEmpty()) return
    val reduced = rememberReducedMotion()
    var played by rememberSaveable(key) { mutableStateOf(false) }
    val intro = remember(key) { Animatable(if (reduced || played) 1f else 0f) }
    LaunchedEffect(key) {
        if (!reduced && !played) {
            intro.animateTo(1f, tween(700, delayMillis = 150))
            played = true
        }
    }
    val summary = list.joinToString(" ") { "${it.label.lowercase()}: ${it.value}, ${it.sub}${if (it.best) ", all-time best" else ""}." }

    StatsCard(modifier.semantics(mergeDescendants = true) { contentDescription = summary }) {
        list.forEachIndexed { i, p ->
            if (i > 0) Canvas(Modifier.fillMaxWidth().height(1.dp)) { drawRect(Color.White.copy(alpha = 0.07f)) }
            val local = ((intro.value * 1.6f - i * 0.18f)).coerceIn(0f, 1f)
            val eased = 1f - (1f - local) * (1f - local) * (1f - local)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .graphicsLayer { alpha = eased; translationY = (1f - eased) * 12.dp.toPx() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MarkTile(p.mark, Modifier.size(48.dp).clip(CircleShape).background(Plate))
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(p.label, style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp), color = OtoColors.TextTertiary, maxLines = 1)
                    Text(
                        p.value,
                        style = giant(30f, -0.8).copy(fontFeatureSettings = "tnum"),
                        color = OtoColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 12.dp)) {
                    Text(p.sub, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = OtoColors.TextSecondary, maxLines = 1)
                    if (p.best) {
                        Text(
                            "ALL-TIME BEST",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
                            color = OtoColors.Accent,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clip(CircleShape)
                                .background(OtoColors.Accent.copy(alpha = 0.14f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkTile(mark: Mark, modifier: Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 48f
        val c = center
        val ink = OtoColors.Accent
        when (mark) {
            Mark.Peak -> {
                val path = Path().apply {
                    moveTo(c.x - 15 * u, c.y + 10 * u)
                    lineTo(c.x - 4 * u, c.y - 10 * u)
                    lineTo(c.x + 2 * u, c.y - 1 * u)
                    lineTo(c.x + 6 * u, c.y - 6 * u)
                    lineTo(c.x + 15 * u, c.y + 10 * u)
                    close()
                }
                drawPath(path, ink)
            }
            Mark.Flame -> {
                val w = 22 * u
                val h = 26 * u
                val left = c.x - w / 2
                val top = c.y - h / 2
                fun x(v: Float) = left + v * w
                fun y(v: Float) = top + v * h
                val path = Path().apply {
                    moveTo(x(0.52f), y(0.02f))
                    cubicTo(x(0.55f), y(0.25f), x(0.85f), y(0.38f), x(0.85f), y(0.68f))
                    cubicTo(x(0.85f), y(0.88f), x(0.70f), y(1.0f), x(0.50f), y(1.0f))
                    cubicTo(x(0.30f), y(1.0f), x(0.15f), y(0.88f), x(0.15f), y(0.68f))
                    cubicTo(x(0.15f), y(0.52f), x(0.25f), y(0.42f), x(0.32f), y(0.34f))
                    cubicTo(x(0.34f), y(0.46f), x(0.40f), y(0.52f), x(0.45f), y(0.52f))
                    cubicTo(x(0.42f), y(0.34f), x(0.45f), y(0.14f), x(0.52f), y(0.02f))
                    close()
                }
                drawPath(path, ink)
                drawCircle(Plate, w * 0.12f, Offset(x(0.5f), y(0.78f)))
            }
            Mark.Bars -> {
                val hs = floatArrayOf(10f, 18f, 26f, 14f)
                hs.forEachIndexed { k, hh ->
                    val x = c.x + (k - 1.5f) * 9 * u - 2.5f * u
                    drawRoundRect(ink, Offset(x, c.y + 13 * u - hh * u), Size(5 * u, hh * u), CornerRadius(2.5f * u))
                }
            }
            Mark.Loop -> {
                val r = 11 * u
                drawArc(ink, 30f, 295f, false, c - Offset(r, r), Size(2 * r, 2 * r), style = Stroke(2.4f * u, cap = StrokeCap.Round))
                val a = (30 + 295) * PI / 180
                val p = c + Offset((cos(a) * r).toFloat(), (sin(a) * r).toFloat())
                val t = Offset((-sin(a)).toFloat(), cos(a).toFloat())
                val n = Offset(cos(a).toFloat(), sin(a).toFloat())
                val tip = p + t * (5 * u)
                val b1 = p + n * (4.5f * u)
                val b2 = p - n * (4.5f * u)
                drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(b1.x, b1.y); lineTo(b2.x, b2.y); close() }, ink)
            }
        }
    }
}
