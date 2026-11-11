package com.oto.stats.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Three bars that dance while music plays and rest low when it is paused. */
@Composable
fun Equalizer(active: Boolean, color: Color, modifier: Modifier = Modifier.size(14.dp)) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val a by transition.animateFloat(
        0.25f, 1f, infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a",
    )
    val b by transition.animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(740, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b",
    )
    val c by transition.animateFloat(
        0.2f, 0.95f, infiniteRepeatable(tween(610, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "c",
    )
    Canvas(modifier) {
        val fractions = if (active) listOf(a, b, c) else listOf(0.3f, 0.3f, 0.3f)
        val barWidth = size.width / 5f
        fractions.forEachIndexed { i, f ->
            val h = size.height * f
            drawRoundRect(
                color = color,
                topLeft = Offset(i * barWidth * 2f, size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

enum class NavIcon { Now, Stats, History, Library }

/** Tab icons drawn in code: one stroke weight, round caps, no icon library needed. */
@Composable
fun NavGlyph(icon: NavIcon, color: Color, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = 2.2.dp.toPx()
        val cap = StrokeCap.Round
        when (icon) {
            NavIcon.Now -> {
                val heights = listOf(0.30f, 0.62f, 0.95f, 0.5f, 0.28f)
                heights.forEachIndexed { i, f ->
                    val x = w * (0.1f + 0.2f * i)
                    val half = h * f / 2f
                    drawLine(color, Offset(x, h / 2f - half), Offset(x, h / 2f + half), sw, cap)
                }
            }
            NavIcon.Stats -> {
                val heights = listOf(0.42f, 0.9f, 0.62f)
                heights.forEachIndexed { i, f ->
                    val x = w * (0.2f + 0.3f * i)
                    drawLine(color, Offset(x, h * 0.94f), Offset(x, h * 0.94f - h * f), sw * 1.5f, cap)
                }
            }
            NavIcon.History -> {
                drawCircle(color, radius = w * 0.42f, style = Stroke(sw, cap = cap))
                drawLine(color, Offset(w / 2f, h / 2f), Offset(w / 2f, h * 0.27f), sw, cap)
                drawLine(color, Offset(w / 2f, h / 2f), Offset(w * 0.66f, h * 0.6f), sw, cap)
            }
            NavIcon.Library -> {
                drawCircle(color, radius = w * 0.42f, style = Stroke(sw, cap = cap))
                drawCircle(color, radius = w * 0.14f, style = Stroke(sw, cap = cap))
            }
        }
    }
}
