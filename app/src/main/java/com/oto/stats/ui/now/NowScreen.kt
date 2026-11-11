package com.oto.stats.ui.now

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.MutableFloatState
import com.oto.stats.ui.components.TrackHeaderProgress
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oto.stats.AppContainer
import com.oto.stats.data.EventRow
import com.oto.stats.tracking.MusicApps
import com.oto.stats.tracking.TrackingStatus
import com.oto.stats.tracking.engine.NowPlayingSnapshot
import com.oto.stats.tracking.engine.PlayState
import com.oto.stats.ui.components.Equalizer
import com.oto.stats.ui.components.OtoCard
import com.oto.stats.ui.components.TrackCover
import com.oto.stats.ui.components.timeAgo
import com.oto.stats.ui.components.SectionLabel
import com.oto.stats.ui.components.clock
import com.oto.stats.ui.components.hoursMinutes
import com.oto.stats.ui.debug.DebugState
import com.oto.stats.ui.theme.OtoColors
import com.oto.stats.ui.theme.OtoNumbers
import java.util.TimeZone
import kotlinx.coroutines.delay

@Composable
fun NowScreen(
    container: AppContainer,
    state: DebugState,
    headerProgress: MutableFloatState,
    onGrantAccess: () -> Unit,
) {
    val snapshot by TrackingStatus.nowPlaying.collectAsState()
    val art by TrackingStatus.artwork.collectAsState()
    val artBitmap = remember(art) { art?.asImageBitmap() }

    // The week window follows the calendar day, so it rolls over at midnight.
    val tzOffsetMs = TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()
    val today = (System.currentTimeMillis() + tzOffsetMs) / DAY_MS
    val week by remember(container, today) {
        container.repository.dailyTotals((today - 6) * DAY_MS - tzOffsetMs, tzOffsetMs)
    }.collectAsState(initial = emptyList())
    val finished by remember(container) { container.repository.recentEvents(8) }
        .collectAsState(initial = emptyList())
    // Re-read the clock so "5m ago" keeps ageing while the screen stays open.
    val clockNow by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    // The listen in progress is saved too, but it belongs in the hero, not in the history.
    val recent = finished.filter { it.sessionId != snapshot?.sessionId }.take(6)

    val listState = rememberLazyListState()
    TrackHeaderProgress(listState, headerProgress, fadeDp = 160.dp)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Hero(
                snapshot = snapshot,
                art = artBitmap,
            )
        }

        if (!state.accessGranted) {
            item { Box(Modifier.padding(horizontal = 24.dp)) { AccessCard(onGrantAccess) } }
        }

        snapshot?.let { current ->
            item { ListenMeter(current, Modifier.padding(horizontal = 24.dp)) }
        }

        item { Box(Modifier.padding(horizontal = 24.dp)) { WeekCard(week, today) } }

        if (recent.isEmpty() && snapshot == null) {
            item {
                Text(
                    "Your listens will show up here, with their covers.",
                    modifier = Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OtoColors.TextTertiary,
                )
            }
        }

        if (recent.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text("Recent", style = MaterialTheme.typography.headlineSmall, color = OtoColors.TextPrimary)
                    Column(Modifier.padding(top = 8.dp)) {
                        recent.forEachIndexed { i, row ->
                            if (i > 0) HorizontalDivider(color = OtoColors.Hairline, modifier = Modifier.padding(start = 64.dp))
                            RecentRow(row, container, clockNow)
                        }
                    }
                }
            }
        }
    }
}

private const val DAY_MS = 86_400_000L

@Composable
private fun RecentRow(row: EventRow, container: AppContainer, nowMs: Long) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        TrackCover(row.identityKey, row.title, container.artStore, 48.dp)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(row.title, style = MaterialTheme.typography.titleSmall, color = OtoColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row {
                Text(
                    row.artist,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodySmall,
                    color = OtoColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.skipped) {
                    Text(" · skipped", style = MaterialTheme.typography.bodySmall, color = OtoColors.TextTertiary, maxLines = 1)
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(clock(row.playedMs), style = OtoNumbers.Body, color = OtoColors.TextPrimary)
            Text(timeAgo(row.startedAt + row.playedMs, nowMs), style = MaterialTheme.typography.labelSmall, color = OtoColors.TextTertiary)
        }
    }
}

/**
 * Edge-to-edge cover that fades into the canvas, with the track written over its lower part.
 * Controls float on top as dark glass pills so they stay readable on any artwork.
 */
@Composable
private fun Hero(
    snapshot: NowPlayingSnapshot?,
    art: androidx.compose.ui.graphics.ImageBitmap?,
) {
    val playing = snapshot?.state == PlayState.PLAYING
    // With nothing playing the hero shrinks, so today's numbers are visible without scrolling.
    val ratio by animateFloatAsState(if (snapshot == null) 1.5f else 0.92f, tween(450), label = "hero")
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ratio),
    ) {
        if (art != null) {
            Crossfade(targetState = art, animationSpec = tween(450), label = "cover") { cover ->
                if (cover != null) {
                    Image(
                        bitmap = cover,
                        contentDescription = "Cover of ${snapshot?.meta?.title.orEmpty()}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(OtoColors.Surface3, OtoColors.Canvas))),
            )
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height * 0.4f)
                for (i in 1..6) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.04f),
                        radius = size.width * 0.12f * i,
                        center = center,
                        style = Stroke(1.dp.toPx()),
                    )
                }
            }
            val initial = snapshot?.meta?.title?.firstOrNull { it.isLetterOrDigit() }?.uppercase()
            Box(Modifier.fillMaxSize().padding(bottom = 90.dp), contentAlignment = Alignment.Center) {
                if (initial != null) {
                    Text(initial, style = MaterialTheme.typography.displayLarge.copy(fontSize = 120.sp, lineHeight = 120.sp), color = OtoColors.TextTertiary)
                } else {
                    Equalizer(active = false, color = OtoColors.TextTertiary, modifier = Modifier.size(36.dp))
                }
            }
        }

        // Darkens the top for the status bar and fades the bottom into the canvas.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.40f),
                        0.22f to Color.Transparent,
                        0.50f to Color.Transparent,
                        0.80f to OtoColors.Canvas.copy(alpha = 0.80f),
                        1f to OtoColors.Canvas,
                    ),
                ),
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        ) {
            if (snapshot == null) {
                Text("Nothing playing", style = MaterialTheme.typography.headlineLarge, color = OtoColors.TextPrimary)
                Text(
                    "Play something in any music app. Oto picks it up on its own.",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = OtoColors.TextSecondary,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                    Equalizer(active = playing, color = if (playing) OtoColors.Accent else OtoColors.TextTertiary, modifier = Modifier.size(width = 14.dp, height = 12.dp))
                    Text(
                        (if (playing) "Playing" else "Paused") + " · " + MusicApps.labelFor(snapshot.packageName),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = OtoColors.TextSecondary,
                    )
                }
                Text(
                    snapshot.meta.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = OtoColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (snapshot.meta.artist.isNotBlank()) {
                    Text(
                        snapshot.meta.artist,
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = OtoColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Time listened to the current track, moving smoothly. The service reports every 4 seconds, so
 * between reports this adds the time that has passed. It stops adding after a few missed
 * reports, so a dead player cannot make the number run on.
 */
@Composable
private fun rememberLiveCountedMs(snapshot: NowPlayingSnapshot?): Long {
    var nowMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val playing = snapshot?.state == PlayState.PLAYING
    LaunchedEffect(playing, snapshot?.atElapsedMs) {
        nowMs = SystemClock.elapsedRealtime()
        while (playing) {
            delay(200)
            nowMs = SystemClock.elapsedRealtime()
        }
    }
    if (snapshot == null) return 0L
    if (!playing || snapshot.atElapsedMs == 0L) return snapshot.countedMs
    val extra = (nowMs - snapshot.atElapsedMs).coerceIn(0L, 12_000L)
    return snapshot.countedMs + extra
}

@Composable
private fun AccessCard(onGrant: () -> Unit) {
    OtoCard {
        Text("Oto can't see your music yet", style = MaterialTheme.typography.titleMedium, color = OtoColors.TextPrimary)
        Text(
            "Allow notification access so Oto can notice what plays in any music app. Nothing leaves your phone.",
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = OtoColors.TextSecondary,
        )
        Button(
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = OtoColors.Accent, contentColor = OtoColors.OnAccent),
        ) { Text("Allow access", style = MaterialTheme.typography.labelLarge) }
    }
}

/** How long you have listened to this track, and how much of it that is. */
@Composable
private fun ListenMeter(snapshot: NowPlayingSnapshot, modifier: Modifier = Modifier) {
    val countedMs = rememberLiveCountedMs(snapshot)
    if (!snapshot.countable) {
        Text(
            "This one isn't counted: the app or the content is filtered out.",
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium,
            color = OtoColors.Warning,
        )
        return
    }
    val duration = snapshot.meta.durationMs?.takeIf { it > 0 }
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(clock(countedMs), style = OtoNumbers.Large, color = OtoColors.TextPrimary)
            Text(
                "listened",
                modifier = Modifier.padding(start = 10.dp, bottom = 7.dp),
                style = MaterialTheme.typography.labelMedium,
                color = OtoColors.TextTertiary,
            )
            if (duration != null) {
                Box(Modifier.weight(1f))
                Text(
                    clock(duration),
                    modifier = Modifier.padding(bottom = 7.dp),
                    style = OtoNumbers.Small,
                    color = OtoColors.TextTertiary,
                )
            }
        }
        if (duration != null) {
            val fraction = (countedMs.toFloat() / duration).coerceIn(0f, 1f)
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(OtoColors.Surface3),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(OtoColors.TextPrimary),
                )
            }
        }
    }
}
