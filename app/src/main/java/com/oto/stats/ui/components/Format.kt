package com.oto.stats.ui.components

import java.util.Locale

/** 3:07 for a track, 1:03:07 past an hour. */
fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}

/** 12h 05m, or 7m when under an hour. For totals. */
fun hoursMinutes(ms: Long): String {
    val minutes = (ms / 60_000).coerceAtLeast(0)
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) String.format(Locale.US, "%dh %02dm", h, m) else "${m}m"
}
