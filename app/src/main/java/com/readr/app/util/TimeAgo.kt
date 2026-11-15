package com.readr.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Friendly relative timestamps for the library ("2 hours ago", "Yesterday"). */
object TimeAgo {

    private val dayFormat = SimpleDateFormat("MMM d", Locale.getDefault())

    fun format(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        if (timestamp <= 0) return "—"
        val diff = now - timestamp
        val minute = 60_000L
        val hour = 60 * minute
        val day = 24 * hour

        return when {
            diff < minute -> "Just now"
            diff < hour -> "${diff / minute} min ago"
            diff < day -> "${diff / hour} h ago"
            diff < 2 * day -> "Yesterday"
            diff < 7 * day -> "${diff / day} days ago"
            else -> dayFormat.format(Date(timestamp))
        }
    }
}
