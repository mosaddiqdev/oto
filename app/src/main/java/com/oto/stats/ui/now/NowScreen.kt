package com.oto.stats.ui.now

import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.oto.stats.data.Totals
import com.oto.stats.tracking.MusicApps
import com.oto.stats.tracking.TrackingStatus
import com.oto.stats.tracking.engine.NowPlayingSnapshot
import com.oto.stats.tracking.engine.PlayState
import com.oto.stats.ui.components.Equalizer
import com.oto.stats.ui.components.OtoCard
import com.oto.stats.ui.components.SectionLabel
import com.oto.stats.ui.components.clock
import com.oto.stats.ui.components.hoursMinutes
import com.oto.stats.ui.debug.DebugState
import com.oto.stats.ui.theme.OtoColors
import com.oto.stats.ui.theme.OtoNumbers
import kotlinx.coroutines.delay

@Composable
fun NowScreen(
    container: AppContainer,
    state: DebugState,
    onOpenSetup: () -> Unit,
    onGrantAccess: () -> Unit,
) {
    val snapshot by TrackingStatus.nowPlaying.collectAsState()
    val art by TrackingStatus.artwork.collectAsState()
    val totals by remember(container) { container.repository.totals() }
        .collectAsState(initial = Totals(0, 0L, 0))
    val recent by remember(container) { container.repository.recentEvents(5) }
        .collectAsState(initial = emptyList())
    val artBitmap = remember(art) { art?.asImageBitmap() }
    val countedMs = rememberLiveCountedMs(snapshot)
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        // The cover, blurred into a glow that fades into the charcoal canvas.
        if (artBitmap != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Image(
                bitmap = artBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp)
                    .blur(90.dp)
                    .alpha(0.45f),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(520.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, OtoColors.Canvas))),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = top + 12.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Now", style = MaterialTheme.typography.headlineLarge, color = OtoColors.TextPrimary)
                    SetupPill(needsAttention = !state.accessGranted || !state.notificationsGranted || !state.batteryExempt, onClick = onOpenSetup)
                }
            }

            if (!state.accessGranted) {
                item { AccessCard(onGrantAccess) }
            }

            item { Cover(snapshot, artBitmap) }
            item { TrackInfo(snapshot) }
            if (snapshot != null) item { ListenMeter(snapshot!!, countedMs) }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile("Listens", totals.plays.toString(), Modifier.weight(1f))
                    Tile("Time", hoursMinutes(totals.totalPlayedMs), Modifier.weight(1f))
                    Tile("Tracks", totals.uniqueTracks.toString(), Modifier.weight(1f))
                }
            }

            if (recent.isNotEmpty()) {
                item { SectionLabel("Recent") }
                item {
                    OtoCard {
                        recent.forEachIndexed { i, row ->
                            if (i > 0) HorizontalDivider(color = OtoColors.Hairline, modifier = Modifier.padding(vertical = 12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(row.title, style = MaterialTheme.typography.titleSmall, color = OtoColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(row.artist, style = MaterialTheme.typography.bodySmall, color = OtoColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(clock(row.playedMs), style = OtoNumbers.Body, color = OtoColors.TextSecondary, modifier = Modifier.padding(start = 12.dp))
                            }
                        }
                    }
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
private fun SetupPill(needsAttention: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.08f),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (needsAttention) {
                Box(Modifier.size(7.dp).background(OtoColors.Warning, CircleShape))
                Box(Modifier.size(6.dp))
            }
            Text("Setup", style = MaterialTheme.typography.labelLarge, color = OtoColors.TextSecondary)
        }
    }
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

/** The cover, large and square. Without art it shows a calm placeholder instead of a broken image. */
@Composable
private fun Cover(snapshot: NowPlayingSnapshot?, art: androidx.compose.ui.graphics.ImageBitmap?) {
    val shape = RoundedCornerShape(32.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .shadow(28.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Brush.linearGradient(listOf(OtoColors.Surface3, OtoColors.Surface1))),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = "Cover of ${snapshot?.meta?.title.orEmpty()}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val rings = 5
                for (i in 1..rings) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.045f),
                        radius = size.minDimension * 0.1f * i,
                        center = center,
                        style = Stroke(1.dp.toPx()),
                    )
                }
            }
            val initial = snapshot?.meta?.title?.firstOrNull { it.isLetterOrDigit() }?.uppercase()
            if (initial != null) {
                Text(initial, style = MaterialTheme.typography.displayLarge.copy(fontSize = 120.sp, lineHeight = 120.sp), color = OtoColors.TextTertiary)
            } else {
                Equalizer(active = false, color = OtoColors.TextTertiary, modifier = Modifier.size(34.dp))
            }
        }
    }
}

@Composable
private fun TrackInfo(snapshot: NowPlayingSnapshot?) {
    if (snapshot == null) {
        Column {
            Text("Nothing playing", style = MaterialTheme.typography.headlineMedium, color = OtoColors.TextPrimary)
            Text(
                "Play something in any music app. Oto picks it up on its own.",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = OtoColors.TextSecondary,
            )
        }
        return
    }
    val playing = snapshot.state == PlayState.PLAYING
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.headlineMedium,
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

/** How long you have listened to this track, and how much of it that is. */
@Composable
private fun ListenMeter(snapshot: NowPlayingSnapshot, countedMs: Long) {
    if (!snapshot.countable) {
        Text(
            "This one isn't counted: the app or the content is filtered out.",
            style = MaterialTheme.typography.bodyMedium,
            color = OtoColors.Warning,
        )
        return
    }
    val duration = snapshot.meta.durationMs?.takeIf { it > 0 }
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(clock(countedMs), style = OtoNumbers.Hero, color = OtoColors.TextPrimary)
            Text(
                "listened",
                modifier = Modifier.padding(start = 10.dp, bottom = 10.dp),
                style = MaterialTheme.typography.labelMedium,
                color = OtoColors.TextTertiary,
            )
            if (duration != null) {
                Box(Modifier.weight(1f))
                Text(
                    clock(duration),
                    modifier = Modifier.padding(bottom = 10.dp),
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

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.05f)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = OtoColors.TextPrimary, maxLines = 1)
            Text(label, modifier = Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelMedium, color = OtoColors.TextTertiary)
        }
    }
}
