package com.oto.stats.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.oto.stats.data.ArtStore
import com.oto.stats.ui.theme.OtoColors

/** A saved cover, or a quiet tile with the first letter of the title when there is none. */
@Composable
fun TrackCover(
    identityKey: String,
    title: String,
    artStore: ArtStore,
    size: Dp,
    modifier: Modifier = Modifier,
    circle: Boolean = false,
) {
    val art by produceState<ImageBitmap?>(initialValue = artStore.peek(identityKey), identityKey) {
        value = artStore.load(identityKey)
    }
    val shape = if (circle) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(size * 0.22f)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(OtoColors.Surface2),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = art
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Text(
                title.firstOrNull { it.isLetterOrDigit() }?.uppercase().orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = OtoColors.TextTertiary,
            )
        }
    }
}
