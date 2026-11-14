package com.oto.stats.ui.nav

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import com.oto.stats.ui.theme.OtoMotion

/**
 * Marks something as a door to another page: it dips to 0.97 under the finger and springs back,
 * with a light tick on release. With a null [onClick] it does nothing, so a row can opt in later.
 */
fun Modifier.doorway(onClick: (() -> Unit)?): Modifier {
    if (onClick == null) return this
    return composed {
        val source = remember { MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        val scale by animateFloatAsState(
            if (pressed) 0.97f else 1f,
            if (pressed) OtoMotion.pressDown() else OtoMotion.pressUp(),
            label = "doorway",
        )
        val haptic = LocalHapticFeedback.current
        this
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            }
    }
}
