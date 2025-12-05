package com.nendo.argosy.ui.notification

import java.util.UUID

enum class NotificationType {
    SUCCESS,
    INFO,
    WARNING,
    ERROR
}

enum class NotificationDuration(val ms: Long) {
    SHORT(2000),
    MEDIUM(4000),
    LONG(6000)
}

data class NotificationAction(
    val label: String,
    val onClick: () -> Unit
)

data class NotificationProgress(
    val current: Int,
    val total: Int
) {
    val fraction: Float get() = if (total > 0) current.toFloat() / total else 0f
    val displayText: String get() = "$current / $total"
}

data class Notification(
    val id: String = UUID.randomUUID().toString(),
    val key: String? = null,
    val type: NotificationType = NotificationType.INFO,
    val title: String,
    val subtitle: String? = null,
    val imagePath: String? = null,
    val duration: NotificationDuration = NotificationDuration.SHORT,
    val action: NotificationAction? = null,
    val immediate: Boolean = false,
    val progress: NotificationProgress? = null
)
