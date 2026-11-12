package com.oto.stats.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import com.oto.stats.data.ArtStore
import com.oto.stats.stats.StatsModel
import com.oto.stats.ui.components.ComingSoon

/** Placeholder while the Stats tab is redesigned from scratch. The data layer (StatsModel) is kept. */
@Composable
fun StatsScreen(model: StatsModel, artStore: ArtStore, headerProgress: MutableFloatState) {
    ComingSoon(headerProgress, "Stats are being redesigned.")
}
