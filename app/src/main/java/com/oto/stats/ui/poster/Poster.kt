package com.oto.stats.ui.poster

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.*
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oto.stats.ui.theme.Display
import java.util.Random
import kotlinx.coroutines.launch

/*
 * Posters: the share-worthy cards. Each says one thing, with one number set very large.
 * Colour comes from the listener's own cover art, so no two people's posters look the same.
 * The share button sits outside the captured layer, so it never ends up in the image.
 */

object PosterType {
    val Giant = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 132.sp, lineHeight = 120.sp, letterSpacing = (-5).sp)
    val Big = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 96.sp, lineHeight = 90.sp, letterSpacing = (-3.5).sp)
    val Mid = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 52.sp, letterSpacing = (-1.5).sp)
    val Unit = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp)
}

val PosterInk = Color(0xFFFFFFFF)
val PosterInkSoft = Color(0xB3FFFFFF)

/** Fine film grain, made once. Takes the digital flatness out of big gradients. */
private object Grain {
    val brush: ShaderBrush by lazy {
        val size = 96
        val rnd = Random(7)
        val px = IntArray(size * size) {
            val g = rnd.nextInt(256)
            (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        val bmp = Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
        ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
}

@Composable
fun PosterBackdrop(palette: PosterPalette, modifier: Modifier = Modifier) {
    Box(
        modifier.drawBehind {
            drawRect(palette.base)
            drawRect(
                Brush.radialGradient(
                    listOf(palette.glow1.copy(alpha = 0.9f), Color.Transparent),
                    center = Offset(size.width * 0.9f, size.height * 0.08f),
                    radius = size.maxDimension * 0.8f,
                ),
            )
            drawRect(
                Brush.radialGradient(
                    listOf(palette.glow2.copy(alpha = 0.7f), Color.Transparent),
                    center = Offset(size.width * 0.05f, size.height * 0.75f),
                    radius = size.maxDimension * 0.7f,
                ),
            )
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                    startY = size.height * 0.4f,
                    endY = size.height,
                ),
            )
            drawRect(Grain.brush, alpha = 0.07f)
        },
    )
}

/**
 * The poster shell: backdrop, a small label, the content pinned to the bottom, and a footer.
 * [art] is drawn as a tilted print bleeding off the top right.
 */
@Composable
fun PosterFrame(
    palette: PosterPalette,
    label: String,
    footer: String,
    shareName: String,
    modifier: Modifier = Modifier,
    art: ImageBitmap? = null,
    aspect: Float = 0.8f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Box(modifier.fillMaxWidth().aspectRatio(aspect).clip(RoundedCornerShape(28.dp))) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
        ) {
            PosterBackdrop(palette, Modifier.fillMaxSize())
            if (art != null) {
                Image(
                    bitmap = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 36.dp, y = (-6).dp)
                        .size(210.dp)
                        .graphicsLayer { rotationZ = 8f }
                        .shadow(24.dp, RoundedCornerShape(18.dp), clip = false, ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(RoundedCornerShape(18.dp)),
                )
            }
            Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 20.dp)) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.6.sp),
                    color = PosterInkSoft,
                )
                Spacer(Modifier.weight(1f))
                content()
                Row(Modifier.padding(top = 18.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(footer, style = MaterialTheme.typography.labelMedium, color = PosterInkSoft)
                    Text("oto", style = MaterialTheme.typography.labelLarge.copy(fontFamily = Display, fontWeight = FontWeight.Bold), color = PosterInk)
                }
            }
        }
        ShareButton(
            Modifier.align(Alignment.TopEnd).padding(14.dp),
        ) {
            scope.launch {
                val bitmap = layer.toImageBitmap().asAndroidBitmap()
                ShareImage.share(context, bitmap, shareName)
            }
        }
    }
}

@Composable
private fun ShareButton(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val w = size.width
            val h = size.height
            val stroke = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round)
            // an arrow leaving a tray
            drawLine(Color.White, Offset(w / 2, h * 0.62f), Offset(w / 2, h * 0.08f), stroke.width, StrokeCap.Round)
            drawLine(Color.White, Offset(w / 2, h * 0.08f), Offset(w * 0.28f, h * 0.3f), stroke.width, StrokeCap.Round)
            drawLine(Color.White, Offset(w / 2, h * 0.08f), Offset(w * 0.72f, h * 0.3f), stroke.width, StrokeCap.Round)
            drawLine(Color.White, Offset(w * 0.1f, h * 0.5f), Offset(w * 0.1f, h * 0.92f), stroke.width, StrokeCap.Round)
            drawLine(Color.White, Offset(w * 0.9f, h * 0.5f), Offset(w * 0.9f, h * 0.92f), stroke.width, StrokeCap.Round)
            drawLine(Color.White, Offset(w * 0.1f, h * 0.92f), Offset(w * 0.9f, h * 0.92f), stroke.width, StrokeCap.Round)
        }
    }
}

/** Counts up from zero to [target] once per [key], so the number arrives instead of appearing. */
@Composable
fun rememberCountUp(target: Long, key: Any?): Long {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key, target) { progress.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    return (target * progress.value).toLong()
}

/** A hero duration: hours set huge, minutes smaller, units quiet, all on one baseline. */
@Composable
fun GiantDuration(ms: Long, modifier: Modifier = Modifier) {
    val minutes = (ms / 60_000).coerceAtLeast(0)
    val h = minutes / 60
    val m = minutes % 60
    val lead = if (h > 0) h.toString() else m.toString()
    val leadStyle = when {
        lead.length <= 2 -> PosterType.Giant
        lead.length == 3 -> PosterType.Big
        else -> PosterType.Mid
    }
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(lead, style = leadStyle, color = PosterInk, modifier = Modifier.alignByBaseline())
        Text(if (h > 0) "h" else "m", style = PosterType.Unit, color = PosterInkSoft, modifier = Modifier.alignByBaseline().padding(start = 4.dp, end = 10.dp))
        if (h > 0) {
            Text(String.format(java.util.Locale.US, "%02d", m), style = PosterType.Mid, color = PosterInk, modifier = Modifier.alignByBaseline())
            Text("m", style = PosterType.Unit, color = PosterInkSoft, modifier = Modifier.alignByBaseline().padding(start = 3.dp))
        }
    }
}
