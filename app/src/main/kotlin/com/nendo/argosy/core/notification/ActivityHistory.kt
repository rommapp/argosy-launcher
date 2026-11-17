package com.nendo.argosy.core.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

enum class ActivityEntryKind { NOTICE, SAVES_SYNCED, DOWNLOADS_FINISHED }

/**
 * One line of the Activity page's recent list. A [ActivityEntryKind.NOTICE] is one notice as it
 * was posted; the other kinds are one line per category whose [count] grows and whose [latest]
 * is the most recent notice folded into it. [unseen] is set only on a NOTICE.
 */
data class ActivityEntry(
    val id: String,
    val kind: ActivityEntryKind,
    val latest: Notification,
    val count: Int,
    val atMillis: Long,
    val unseen: Boolean,
    val lastKey: String? = null
)

data class ActivityLog(val entries: List<ActivityEntry> = emptyList()) {
    val hasUnseen: Boolean get() = entries.any { it.unseen }
}

object ActivityHistoryRules {
    const val CAP = 20

    fun kindOf(notification: Notification): ActivityEntryKind? {
        val succeeded = notification.type == NotificationType.SUCCESS
        return when {
            notification.type == NotificationType.WARNING || notification.type == NotificationType.ERROR ->
                ActivityEntryKind.NOTICE
            notification.category == NotificationCategory.CONTROLLER ||
                notification.category == NotificationCategory.BATTERY -> ActivityEntryKind.NOTICE
            succeeded && notification.category == NotificationCategory.SAVE -> ActivityEntryKind.SAVES_SYNCED
            succeeded && notification.category == NotificationCategory.DOWNLOAD -> ActivityEntryKind.DOWNLOADS_FINISHED
            else -> null
        }
    }

    fun record(entries: List<ActivityEntry>, notification: Notification, nowMillis: Long): List<ActivityEntry> {
        val kind = kindOf(notification) ?: return entries
        val folded = if (kind == ActivityEntryKind.NOTICE) {
            val replaced = notification.key?.let { key -> entries.firstOrNull { it.kind == kind && it.lastKey == key } }
            ActivityEntry(
                id = replaced?.id ?: notification.id,
                kind = kind,
                latest = notification,
                count = 1,
                atMillis = nowMillis,
                unseen = true,
                lastKey = notification.key
            )
        } else {
            val line = entries.firstOrNull { it.kind == kind }
            ActivityEntry(
                id = line?.id ?: kind.name,
                kind = kind,
                latest = notification,
                count = (line?.count ?: 0) + 1,
                atMillis = nowMillis,
                unseen = false,
                lastKey = notification.key
            )
        }
        return (listOf(folded) + entries.filterNot { it.id == folded.id }).take(CAP)
    }

    fun markSeen(entries: List<ActivityEntry>): List<ActivityEntry> =
        if (entries.none { it.unseen }) entries else entries.map { if (it.unseen) it.copy(unseen = false) else it }
}

/**
 * The notices posted since the process started, in the Activity page's terms. Session only:
 * nothing is stored.
 */
@Singleton
class ActivityHistory @Inject constructor() {
    private val _log = MutableStateFlow(ActivityLog())
    val log: StateFlow<ActivityLog> = _log.asStateFlow()

    fun record(notification: Notification, nowMillis: Long = System.currentTimeMillis()) {
        _log.update { ActivityLog(ActivityHistoryRules.record(it.entries, notification, nowMillis)) }
    }

    fun markSeen() {
        _log.update { current ->
            val seen = ActivityHistoryRules.markSeen(current.entries)
            if (seen === current.entries) current else ActivityLog(seen)
        }
    }
}
