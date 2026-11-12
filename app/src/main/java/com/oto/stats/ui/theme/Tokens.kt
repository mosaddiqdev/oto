package com.oto.stats.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp

/** Spacing and shape tokens. One place, so the design spec is code and not folklore. */
object OtoSpace {
    val Gutter = 20.dp
    val CardGap = 12.dp
    val SectionGap = 32.dp
    val CardPadding = 20.dp
    val CardRadius = 28.dp
    val TileRadius = 16.dp
    val MinTouch = 48.dp
}

/** Motion tokens. Springs move things; tweens change colour and opacity and never overshoot. */
object OtoMotion {
    /** Strong ease-out: things arrive quickly and settle. */
    val Decelerate = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    fun <T> enter() = tween<T>(350, easing = Decelerate)
    fun <T> fade() = tween<T>(200)
    fun <T> countUp(hero: Boolean = false) = tween<T>(if (hero) 1200 else 900, delayMillis = 150, easing = Decelerate)
    fun <T> slide() = tween<T>(260, easing = Decelerate)

    /** Bars rise from the baseline with a touch of life, under 4% overshoot. */
    fun <T> bar() = spring<T>(dampingRatio = 0.75f, stiffness = 300f)

    fun <T> pressDown() = spring<T>(dampingRatio = 0.6f, stiffness = 800f)
    fun <T> pressUp() = spring<T>(dampingRatio = 0.5f, stiffness = 400f)

    const val StaggerMs = 50
    const val MaxStaggerMs = 400
    const val BarStaggerMs = 30
}
