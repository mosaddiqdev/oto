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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oto.stats.stats.Daypart
import com.oto.stats.stats.Insights
import com.oto.stats.stats.StatsData
import com.oto.stats.ui.theme.Display
import com.oto.stats.ui.theme.OtoColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * How you listen. Spec: docs/STATS_LISTENER.md.
 * A label with its rule, and four spectrums that show where you sit between two ends.
 * Every position is a real percentage from your own listening. No predictions.
 */

private val Tile = Color(0xFF222222)

/** One spectrum: two ends, your position between them, and the number behind it. */
private data class Axis(val left: String, val right: String, val percent: Int, val caption: String)

private fun axes(d: StatsData): List<Axis> {
    val out = ArrayList<Axis>()
    val hours = d.hours
    if (hours != null && hours.sum() > 0) {
        val shares = Insights.daypartShares(hours).toMap()
        val dark = (shares[Daypart.EVENING] ?: 0) + (shares[Daypart.NIGHT] ?: 0)
        out.add(Axis("Daytime", "After dark", dark.coerceIn(0, 100), "$dark% after dark"))
    }
    val plays = d.headline.plays
    if (plays > 0) {
        val skipped = d.headline.skips * 100 / plays
        out.add(Axis("Finisher", "Skipper", skipped, "$skipped% skipped"))
    }
    d.moments.explorer?.let { out.add(Axis("Familiar", "Explorer", it.percent.coerceIn(0, 100), "${it.percent}% new to you")) }
    d.moments.artist?.let { out.add(Axis("Wide taste", "Loyal", it.percent.coerceIn(0, 100), "${it.percent}% to ${it.name}")) }
    return out
}

/** The sentence above the card. */
fun listenerSentence(d: StatsData): String? =
    if (d.headline.plays <= 0) null else "${d.moments.personality.name}. ${d.moments.personality.line}"

@Composable
fun ListenerCard(d: StatsData, key: String, modifier: Modifier = Modifier) {
    val me = d.moments.personality
    val list = remember(d) { axes(d) }
    val reduced = rememberReducedMotion()
    var played by rememberSaveable(key) { mutableStateOf(false) }
    val intro = remember(key) { Animatable(if (reduced || played) 1f else 0f) }
    LaunchedEffect(key) {
        if (!reduced && !played) {
            intro.animateTo(1f, tween(900, delayMillis = 200))
            played = true
        }
    }
    val summary = "Listener type: ${me.name}. ${me.line} " + list.joinToString(" ") { "${it.left} to ${it.right}: ${it.caption}." }

    StatsCard(modifier.semantics(mergeDescendants = true) { contentDescription = summary }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(me.name, Modifier.size(64.dp).clip(CircleShape).background(Tile))
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    me.name,
                    style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Display, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
                    color = OtoColors.TextPrimary,
                    maxLines = 2,
                )
                Text(me.line, style = MaterialTheme.typography.bodyMedium, color = OtoColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (list.isNotEmpty()) {
            Canvas(Modifier.padding(top = 20.dp).fillMaxWidth().height(1.dp)) { drawRect(Color.White.copy(alpha = 0.08f)) }
            Column(Modifier.padding(top = 4.dp)) {
                list.forEachIndexed { i, axis -> AxisRow(axis, intro.value, i) }
            }
        }
    }
}

@Composable
private fun AxisRow(a: Axis, progress: Float, index: Int) {
    Column(Modifier.padding(top = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(a.left, style = MaterialTheme.typography.bodySmall, color = OtoColors.TextTertiary)
            Text(a.caption, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), color = OtoColors.TextPrimary)
            Text(a.right, style = MaterialTheme.typography.bodySmall, color = OtoColors.TextTertiary)
        }
        Canvas(Modifier.padding(top = 8.dp).fillMaxWidth().height(20.dp)) {
            val trackH = 4.dp.toPx()
            val y = size.height / 2
            val r = 7.dp.toPx()
            drawRoundRect(Color.White.copy(alpha = 0.07f), Offset(0f, y - trackH / 2), Size(size.width, trackH), CornerRadius(trackH / 2))
            drawLine(Color.White.copy(alpha = 0.18f), Offset(size.width / 2, y - 5.dp.toPx()), Offset(size.width / 2, y + 5.dp.toPx()), 1.dp.toPx())
            // The dot glides from the middle to its place, a little after the one above it, ease-out, no overshoot.
            val local = ((progress * 1.4f - index * 0.12f)).coerceIn(0f, 1f)
            val eased = 1f - (1f - local) * (1f - local) * (1f - local)
            val target = (a.percent / 100f).coerceIn(0f, 1f)
            val pos = 0.5f + (target - 0.5f) * eased
            val x = r + (size.width - 2 * r) * pos
            val from = minOf(size.width / 2, x)
            val to = maxOf(size.width / 2, x)
            drawRoundRect(
                Brush.horizontalGradient(listOf(Color(0xFFDBBE2C), OtoColors.Accent), startX = from, endX = to.coerceAtLeast(from + 1f)),
                Offset(from, y - trackH / 2), Size(to - from, trackH), CornerRadius(trackH / 2),
            )
            drawCircle(OtoColors.Accent, r, Offset(x, y))
            drawCircle(OtoColors.Surface1, 2.5.dp.toPx(), Offset(x, y))
        }
    }
}

/** A small mark for each label, in line weight, so the label has a face. */
@Composable
private fun Glyph(name: String, modifier: Modifier) {
    Canvas(modifier) {
        val c = center
        val u = size.minDimension / 64f
        val stroke = Stroke(2.5f * u, cap = StrokeCap.Round)
        val ink = OtoColors.Accent
        when (name) {
            "Night Owl" -> {
                drawCircle(ink, 14 * u, c)
                drawCircle(Tile, 12 * u, c + Offset(7 * u, -5 * u))
            }
            "Early Bird" -> {
                drawCircle(ink, 9 * u, c, style = stroke)
                for (k in 0 until 8) {
                    val a = k * PI / 4
                    drawLine(ink, c + Offset((cos(a) * 15 * u).toFloat(), (sin(a) * 15 * u).toFloat()), c + Offset((cos(a) * 21 * u).toFloat(), (sin(a) * 21 * u).toFloat()), 2.5f * u, StrokeCap.Round)
                }
            }
            "Loyalist" -> {
                drawCircle(ink.copy(alpha = 0.4f), 18 * u, c, style = Stroke(1.5f * u))
                drawCircle(ink, 6 * u, c)
                val a = -PI / 4
                drawCircle(ink, 3.5f * u, c + Offset((cos(a) * 18 * u).toFloat(), (sin(a) * 18 * u).toFloat()))
            }
            "On Repeat" -> {
                val r = 14 * u
                drawArc(ink, 30f, 295f, false, c - Offset(r, r), Size(2 * r, 2 * r), style = stroke)
                val a = (30 + 295) * PI / 180
                val p = c + Offset((cos(a) * r).toFloat(), (sin(a) * r).toFloat())
                val t = Offset((-sin(a)).toFloat(), cos(a).toFloat())
                val n = Offset(cos(a).toFloat(), sin(a).toFloat())
                val tri = Path().apply {
                    moveTo((p + t * (6 * u)).x, (p + t * (6 * u)).y)
                    lineTo((p + n * (5 * u)).x, (p + n * (5 * u)).y)
                    lineTo((p - n * (5 * u)).x, (p - n * (5 * u)).y)
                    close()
                }
                drawPath(tri, ink)
            }
            "Explorer" -> {
                val o = 20 * u
                val i = 5 * u
                val star = Path().apply {
                    moveTo(c.x, c.y - o); lineTo(c.x + i, c.y - i); lineTo(c.x + o, c.y); lineTo(c.x + i, c.y + i)
                    lineTo(c.x, c.y + o); lineTo(c.x - i, c.y + i); lineTo(c.x - o, c.y); lineTo(c.x - i, c.y - i); close()
                }
                drawPath(star, ink)
            }
            "Picky" -> drawSkip(c, u, ink)
            "Just Getting Started" -> {
                drawCircle(ink, 5 * u, c)
                drawCircle(ink.copy(alpha = 0.55f), 12 * u, c, style = Stroke(2f * u))
                drawCircle(ink.copy(alpha = 0.25f), 19 * u, c, style = Stroke(2f * u))
            }
            else -> { // Steady Listener: five bars of one height, an even level
                for (k in -2..2) {
                    drawRoundRect(ink, Offset(c.x + k * 8 * u - 2.5f * u, c.y - 10 * u), Size(5 * u, 20 * u), CornerRadius(2.5f * u))
                }
            }
        }
    }
}

private fun DrawScope.drawSkip(c: Offset, u: Float, ink: Color) {
    fun tri(x: Float) = Path().apply {
        moveTo(x, c.y - 11 * u); lineTo(x + 14 * u, c.y); lineTo(x, c.y + 11 * u); close()
    }
    drawPath(tri(c.x - 20 * u), ink)
    drawPath(tri(c.x - 4 * u), ink)
    drawRoundRect(ink, Offset(c.x + 13 * u, c.y - 11 * u), Size(4 * u, 22 * u), CornerRadius(2 * u))
}
