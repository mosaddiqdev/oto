package com.oto.stats.ui.stats

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oto.stats.stats.PeriodKind
import com.oto.stats.stats.StatsModel
import com.oto.stats.stats.StatsUiState
import com.oto.stats.ui.theme.OtoColors
import com.oto.stats.ui.theme.OtoMotion
import com.oto.stats.ui.theme.OtoSpace

/** A single capsule with a sliding yellow pill. The same language as the tab bar. */
@Composable
fun Segmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(OtoColors.Surface1)
            .border(BorderStroke(1.dp, OtoColors.Hairline), CircleShape),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(3.dp)) {
            val segment = maxWidth / options.size
            val x by animateDpAsState(segment * selectedIndex, OtoMotion.slide(), label = "segment")
            Box(
                Modifier
                    .offset(x = x)
                    .width(segment)
                    .fillMaxHeight()
                    .background(OtoColors.Accent, CircleShape),
            )
            Row(Modifier.fillMaxSize()) {
                options.forEachIndexed { i, label ->
                    val selected = i == selectedIndex
                    val color by animateColorAsState(
                        if (selected) OtoColors.OnAccent else OtoColors.TextSecondary,
                        tween(180),
                        label = "segmentText",
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                            ) {
                                if (!selected) {
                                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onSelect(i)
                                }
                            }
                            .semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = if (selected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
                            color = color,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Chevron(left: Boolean, enabled: Boolean, description: String, onClick: () -> Unit) {
    val tint = if (enabled) OtoColors.TextPrimary else OtoColors.Hairline
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier
            .size(OtoSpace.MinTouch)
            .clip(CircleShape)
            .then(
                if (enabled) {
                    Modifier.clickable(role = Role.Button) {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onClick()
                    }
                } else {
                    Modifier
                },
            )
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

/** Week / Month / Year / All, then the period's title and dates between two chevrons. */
@Composable
fun PeriodControl(ui: StatsUiState, model: StatsModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Segmented(
            options = listOf("Week", "Month", "Year", "All"),
            selectedIndex = ui.selection.kind.ordinal,
            onSelect = { model.selectKind(PeriodKind.entries[it]) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Chevron(left = true, enabled = ui.canGoPrevious, description = "Previous period", onClick = model::previous)
            Crossfade(
                targetState = ui.periodLabel to ui.resolvedDates,
                animationSpec = tween(180),
                modifier = Modifier.weight(1f),
                label = "periodTitle",
            ) { (title, dates) ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, style = MaterialTheme.typography.headlineMedium, color = OtoColors.TextPrimary, textAlign = TextAlign.Center, maxLines = 1)
                    if (dates.isNotEmpty()) {
                        Text(dates, style = MaterialTheme.typography.bodySmall, color = OtoColors.TextTertiary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
            Chevron(left = false, enabled = ui.canGoNext, description = "Next period", onClick = model::next)
        }
    }
}
