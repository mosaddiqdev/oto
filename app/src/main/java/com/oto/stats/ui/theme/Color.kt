package com.oto.stats.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Oto design tokens: charcoal canvas, a neutral monochrome ramp, one accent.
 * Semantic colors are for system status only (permissions, battery, errors), never for data.
 */
object OtoColors {
    // Surfaces
    val Canvas = Color(0xFF111111)
    val Surface1 = Color(0xFF1A1A1A)
    val Surface2 = Color(0xFF222222)
    val Surface3 = Color(0xFF2B2B2B)
    val Hairline = Color(0xFF2E2E2E)

    // Text
    val TextPrimary = Color(0xFFF2F2F0)
    val TextSecondary = Color(0xFFB0B0AD)
    val TextTertiary = Color(0xFF848482)

    // Accent: Tape Yellow (cassette tape, VU needle). About 12:1 on the canvas.
    val Accent = Color(0xFFEFD43B)
    val AccentPressed = Color(0xFFD6BC2C)
    val AccentSubtle = Color(0xFF25220E)
    val OnAccent = Color(0xFF141100)

    // Heatmap ramp: surface to accent
    val Heat0 = Color(0xFF222222)
    val Heat1 = Color(0xFF302D18)
    val Heat2 = Color(0xFF5E5617)
    val Heat3 = Color(0xFFA8951F)
    val Heat4 = Color(0xFFEFD43B)

    // Semantic (status only)
    val Success = Color(0xFF5BC48A)
    val Warning = Color(0xFFF08A4B) // orange, kept clear of the yellow accent
    val Error = Color(0xFFF2566B)
    val ErrorSubtle = Color(0xFF3A1A20)
    val Info = Color(0xFF6FA8FF)
}
