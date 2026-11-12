package com.oto.stats.ui.poster

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import com.oto.stats.data.ArtStore
import com.oto.stats.ui.theme.OtoColors
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Three colours for a poster: a deep base and two glows. All of them come from the user's own cover art. */
data class PosterPalette(val base: Color, val glow1: Color, val glow2: Color) {
    companion object {
        /** No art available: the Oto accent on a warm dark base. */
        val Fallback = PosterPalette(
            base = Color(0xFF15130A),
            glow1 = OtoColors.Accent,
            glow2 = Color(0xFFB89B1C),
        )
    }
}

object ArtPalette {
    private const val SAMPLE = 24
    private const val BUCKETS = 24

    private class Bucket {
        var weight = 0f
        var h = 0f
        var s = 0f
        var v = 0f
    }

    /** Picks the two most characterful hues of a cover, then tunes them so text on top stays readable. */
    fun from(image: ImageBitmap): PosterPalette {
        val src = image.asAndroidBitmap()
        val small = Bitmap.createScaledBitmap(src, SAMPLE, SAMPLE, true)
        val px = IntArray(SAMPLE * SAMPLE)
        small.getPixels(px, 0, SAMPLE, 0, 0, SAMPLE, SAMPLE)
        if (small !== src) small.recycle()

        val buckets = Array(BUCKETS) { Bucket() }
        val hsv = FloatArray(3)
        var total = 0f
        for (c in px) {
            android.graphics.Color.colorToHSV(c, hsv)
            val (h, s, v) = Triple(hsv[0], hsv[1], hsv[2])
            if (v < 0.18f || s < 0.12f) continue
            val w = s * v + 0.02f
            val b = buckets[((h / 360f) * BUCKETS).toInt().coerceIn(0, BUCKETS - 1)]
            b.weight += w; b.h += h * w; b.s += s * w; b.v += v * w
            total += w
        }
        if (total < 1.5f) return PosterPalette.Fallback // mostly grey or black art

        fun mean(b: Bucket) = Triple(b.h / b.weight, b.s / b.weight, b.v / b.weight)
        val first = buckets.maxByOrNull { it.weight }!!
        val (h1, s1, v1) = mean(first)
        val second = buckets
            .filter { it.weight > total * 0.06f && hueGap(mean(it).first, h1) >= 40f }
            .maxByOrNull { it.weight }
        val (h2, s2, v2) = second?.let { mean(it) } ?: Triple((h1 + 28f) % 360f, s1, v1)

        fun tone(h: Float, s: Float, v: Float, vMin: Float) =
            Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s.coerceIn(0.45f, 0.92f), v.coerceIn(vMin, 1f))))
        return PosterPalette(
            base = Color(android.graphics.Color.HSVToColor(floatArrayOf(h1, (s1 * 0.7f).coerceIn(0.25f, 0.6f), 0.13f))),
            glow1 = tone(h1, s1, v1, 0.72f),
            glow2 = tone(h2, s2, v2, 0.6f),
        )
    }

    private fun hueGap(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }
}

/** The palette of the first cover in [keys] that has saved art, computed off the main thread. */
@Composable
fun rememberPalette(keys: List<String>, artStore: ArtStore): State<PosterPalette> =
    produceState(PosterPalette.Fallback, keys) {
        for (key in keys) {
            val art = artStore.load(key) ?: continue
            value = withContext(Dispatchers.Default) { ArtPalette.from(art) }
            return@produceState
        }
        value = PosterPalette.Fallback
    }
