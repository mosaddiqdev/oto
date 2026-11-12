package com.oto.stats.ui.stats

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oto.stats.ui.theme.OtoColors
import com.oto.stats.ui.theme.OtoMotion
import com.oto.stats.ui.theme.OtoSpace
import java.util.Locale

/** True when the user turned animations off in system settings; we then jump to final states. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Exception) {
            false
        }
    }
}

/** Counts from 0 to [target] once per [key]. Snaps to the end when motion is reduced or already played. */
@Composable
fun rememberCountUp(target: Long, key: Any?, hero: Boolean = false): Long {
    val reduced = rememberReducedMotion()
    var played by rememberSaveable(key) { mutableStateOf(false) }
    val progress = remember(key) { Animatable(if (reduced || played) 1f else 0f) }
    LaunchedEffect(key, target) {
        if (!reduced && !played) {
            progress.animateTo(1f, OtoMotion.countUp(hero))
            played = true
        }
    }
    return (target * progress.value).toLong()
}

/** Cards arrive: 24dp up and fading in, staggered by [index]. Plays once per [key], not on every scroll back. */
@Composable
fun Modifier.cardEnter(index: Int, key: Any?): Modifier {
    val reduced = rememberReducedMotion()
    var played by rememberSaveable(key) { mutableStateOf(false) }
    val progress = remember(key) { Animatable(if (reduced || played) 1f else 0f) }
    LaunchedEffect(key) {
        if (!reduced && !played) {
            progress.animateTo(1f, tween350(delay = (index * OtoMotion.StaggerMs).coerceAtMost(OtoMotion.MaxStaggerMs)))
            played = true
        }
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 24.dp.toPx()
    }
}

private fun tween350(delay: Int) = androidx.compose.animation.core.tween<Float>(350, delayMillis = delay, easing = OtoMotion.Decelerate)

/** A section: a Bricolage title, an optional one-sentence takeaway, then the content. */
@Composable
fun StatsSection(
    title: String,
    modifier: Modifier = Modifier,
    sentence: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = OtoColors.TextPrimary)
        if (sentence != null) {
            Text(
                sentence,
                style = MaterialTheme.typography.bodyLarge,
                color = OtoColors.TextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Column(Modifier.padding(top = OtoSpace.CardGap), content = content)
    }
}

/** The standard surface: raised charcoal, hairline edge, 28dp corners, no shadow. */
@Composable
fun StatsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(OtoColors.Surface1, RoundedCornerShape(OtoSpace.CardRadius))
            .border(BorderStroke(1.dp, OtoColors.Hairline), RoundedCornerShape(OtoSpace.CardRadius))
            .padding(OtoSpace.CardPadding),
        content = content,
    )
}

/** 12h 05m with the units quieter than the digits. */
@Composable
fun durationText(ms: Long, digits: TextStyle, unitSize: Int, color: androidx.compose.ui.graphics.Color = OtoColors.TextPrimary, unitColor: androidx.compose.ui.graphics.Color = OtoColors.TextTertiary) =
    buildAnnotatedString {
        val minutes = (ms / 60_000).coerceAtLeast(0)
        val h = minutes / 60
        val m = minutes % 60
        val unit = SpanStyle(fontSize = unitSize.sp, color = unitColor)
        withStyle(SpanStyle(color = color)) {
            if (h > 0) {
                append(h.toString()); withStyle(unit) { append("h ") }
                append(String.format(Locale.US, "%02d", m)); withStyle(unit) { append("m") }
            } else {
                append(m.toString()); withStyle(unit) { append("m") }
            }
        }
    }

fun formatDuration(ms: Long): String {
    val minutes = (ms / 60_000).coerceAtLeast(0)
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${String.format(Locale.US, "%02d", m)}m" else "${m}m"
}

fun formatCount(n: Int): String = String.format(Locale.US, "%,d", n)
