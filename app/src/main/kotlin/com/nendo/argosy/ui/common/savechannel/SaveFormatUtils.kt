package com.nendo.argosy.ui.common.savechannel

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val now = Date()
    val diffMs = now.time - timestamp
    val diffDays = diffMs / (1000 * 60 * 60 * 24)

    return when {
        diffDays == 0L -> {
            val format = SimpleDateFormat("h:mm a", Locale.getDefault())
            "Today ${format.format(date)}"
        }
        diffDays == 1L -> "Yesterday"
        diffDays < 7 -> "$diffDays days ago"
        else -> {
            val format = SimpleDateFormat("MMM d", Locale.getDefault())
            format.format(date)
        }
    }
}

fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}
