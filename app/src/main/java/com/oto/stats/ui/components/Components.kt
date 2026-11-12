package com.oto.stats.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oto.stats.ui.theme.OtoColors

/** Height of the floating header's content row, below the status bar. */
const val HEADER_CONTENT_DP = 64

/**
 * The page is laid out this much taller than the screen, upward. Content that has just scrolled
 * off the top is still drawn there, so the glass header always has real pixels to blur at its
 * top edge instead of fading out over an empty strip.
 */
const val OVERSCAN_DP = 48

/**
 * Reports how far a list has scrolled as 0..1 so the glass header can morph in.
 * [fadeDp] is the scroll distance over which it goes from clear to full glass.
 */
@Composable
fun TrackHeaderProgress(state: LazyListState, target: MutableFloatState, fadeDp: Dp) {
    val fadePx = with(LocalDensity.current) { fadeDp.toPx() }
    LaunchedEffect(state, fadePx) {
        snapshotFlow {
            if (state.firstVisibleItemIndex > 0) 1f
            else (state.firstVisibleItemScrollOffset / fadePx).coerceIn(0f, 1f)
        }.collect { target.floatValue = it }
    }
}

/** A tab screen under the floating header, with room at the bottom for the glass bars. */
@Composable
fun ScreenColumn(
    headerProgress: MutableFloatState,
    modifier: Modifier = Modifier,
    spacing: Dp = 16.dp,
    content: LazyListScope.() -> Unit,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + HEADER_CONTENT_DP.dp
    val state = rememberLazyListState()
    TrackHeaderProgress(state, headerProgress, fadeDp = 32.dp)
    LazyColumn(
        state = state,
        modifier = modifier
            .fillMaxSize()
            .background(OtoColors.Canvas),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = OVERSCAN_DP.dp + top + 8.dp, bottom = 210.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** The one container style: a slightly raised surface with a hairline edge. */
@Composable
fun OtoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = OtoColors.Surface1,
        border = BorderStroke(1.dp, OtoColors.Hairline),
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = OtoColors.TextTertiary,
    )
}

@Composable
fun ComingSoon(headerProgress: MutableFloatState, line: String) {
    ScreenColumn(headerProgress) {
        item {
            OtoCard {
                Text("Coming soon", style = MaterialTheme.typography.titleMedium, color = OtoColors.Accent)
                Text(
                    line,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OtoColors.TextSecondary,
                )
            }
        }
    }
}
