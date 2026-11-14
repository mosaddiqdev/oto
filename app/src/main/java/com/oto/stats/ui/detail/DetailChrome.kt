package com.oto.stats.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.oto.stats.ui.nav.Route
import com.oto.stats.ui.stats.Segmented
import com.oto.stats.ui.theme.OtoColors

/** The bar every detail page starts with: a back button, and, when a window came with the page, the This period / All time switch. */
@Composable
fun DetailTopBar(onBack: () -> Unit, window: Route.Window?, allTime: Boolean, onAllTime: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(OtoColors.Surface1)
                .border(BorderStroke(1.dp, OtoColors.Hairline), CircleShape)
                .semantics { contentDescription = "Back" }
                .clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(20.dp)) {
                val p = Path().apply {
                    moveTo(size.width * 0.62f, size.height * 0.2f)
                    lineTo(size.width * 0.32f, size.height * 0.5f)
                    lineTo(size.width * 0.62f, size.height * 0.8f)
                }
                drawPath(p, OtoColors.TextPrimary, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        if (window != null) {
            Segmented(
                options = listOf(window.label, "All time"),
                selectedIndex = if (allTime) 1 else 0,
                onSelect = { onAllTime(it == 1) },
                modifier = Modifier.width(236.dp),
                height = 40.dp,
            )
        }
    }
}
