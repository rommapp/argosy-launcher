package com.nendo.argosy.core.notification

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

data class NotificationProgress(
    val current: Int,
    val total: Int
) {
    val fraction: Float get() = if (total > 0) current.toFloat() / total else 0f
}

data class Notification(
    val id: String = UUID.randomUUID().toString(),
    val key: String? = null,
    val type: NotificationType = NotificationType.INFO,
    val title: NotificationText,
    val subtitle: NotificationText? = null,
    val imagePath: String? = null,
    /**
     * Draws this platform's own icon in place of the launcher's mark. Carried as a slug rather
     * than a path because the asset it resolves to is a UI concern the notification does not have.
     */
    val platformSlug: String? = null,
    val duration: NotificationDuration = NotificationDuration.SHORT,
    val immediate: Boolean = false,
    val progress: NotificationProgress? = null,
    val accentColor: Int? = null
)

data class StatusNotification(
    val title: NotificationText,
    val subtitle: NotificationText? = null,
    val progress: Float? = null,
    val isActive: Boolean = true
)
