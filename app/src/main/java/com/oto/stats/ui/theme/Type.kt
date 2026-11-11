package com.oto.stats.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.oto.stats.R

/*
 * Two families, both bundled (the app works offline) and both under the SIL Open Font License:
 *  - Bricolage Grotesque: titles and the big hero numbers. Has character, is not the usual default.
 *  - IBM Plex Sans: everything else. Precise, and its numerals can be tabular so columns line up.
 */

@OptIn(ExperimentalTextApi::class)
private fun variable(resId: Int, weight: Int) = Font(
    resId = resId,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Display = FontFamily(
    variable(R.font.bricolage_grotesque, 500),
    variable(R.font.bricolage_grotesque, 600),
    variable(R.font.bricolage_grotesque, 700),
)

val Body = FontFamily(
    variable(R.font.ibm_plex_sans, 400),
    variable(R.font.ibm_plex_sans, 500),
    variable(R.font.ibm_plex_sans, 600),
)

private const val TABULAR = "tnum"

private fun display(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Display,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

private fun body(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Body,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

val OtoTypography = Typography(
    displayLarge = display(56, 56, 700, -1.5),
    displayMedium = display(44, 46, 700, -1.0),
    displaySmall = display(34, 38, 600, -0.5),
    headlineLarge = display(30, 36, 600, -0.4),
    headlineMedium = display(24, 30, 600, -0.2),
    headlineSmall = display(20, 26, 600),
    titleLarge = body(20, 26, 600),
    titleMedium = body(16, 22, 600),
    titleSmall = body(14, 20, 600),
    bodyLarge = body(16, 24, 400),
    bodyMedium = body(14, 20, 400),
    bodySmall = body(12, 16, 400, 0.1),
    labelLarge = body(14, 20, 500),
    labelMedium = body(12, 16, 500, 0.4),
    labelSmall = body(11, 14, 500, 0.5),
)

/** Numbers that sit in columns or change live (durations, counts). Digits keep a fixed width. */
object OtoNumbers {
    val Body = body(14, 20, 500).copy(fontFeatureSettings = TABULAR)
    val Small = body(12, 16, 500).copy(fontFeatureSettings = TABULAR)
    val Title = body(20, 26, 600).copy(fontFeatureSettings = TABULAR)
    val Hero = body(56, 60, 600, -1.0).copy(fontFeatureSettings = TABULAR)
}
