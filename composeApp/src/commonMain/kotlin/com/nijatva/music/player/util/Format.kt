package com.nijatva.music.player.util

/** 3:07, or 1:02:07 for anything an hour or longer. */
fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "--:--"
    val total = ms / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    val mm = if (hours > 0) minutes.toString().padStart(2, '0') else minutes.toString()
    val ss = seconds.toString().padStart(2, '0')
    return if (hours > 0) "$hours:$mm:$ss" else "$mm:$ss"
}

/** "42 songs · 3 hr 12 min" style summaries. */
fun formatSummary(count: Int, totalMs: Long): String {
    val songs = if (count == 1) "1 song" else "$count songs"
    if (totalMs <= 0L) return songs
    val minutes = totalMs / 60_000
    val hours = minutes / 60
    val rest = minutes % 60
    val length = when {
        hours > 0 && rest > 0 -> "$hours hr $rest min"
        hours > 0 -> "$hours hr"
        else -> "$rest min"
    }
    return "$songs · $length"
}
