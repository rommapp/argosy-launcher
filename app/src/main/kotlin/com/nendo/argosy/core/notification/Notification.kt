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

/**
 * What a notice is about. [GENERAL] is held while a game is in front unless it is a warning or an
 * error; every other category shows over games. [BATTERY] keeps showing with notifications turned
 * off.
 */
enum class NotificationCategory(val showsOverGames: Boolean, val bypassesGlobalOff: Boolean = false) {
    GENERAL(showsOverGames = false),
    DOWNLOAD(showsOverGames = true),
    SAVE(showsOverGames = true),
    SOCIAL(showsOverGames = true),
    BATTERY(showsOverGames = true, bypassesGlobalOff = true),
    CHARGING(showsOverGames = true),
    CONTROLLER(showsOverGames = true)
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
    val accentColor: Int? = null,
    val category: NotificationCategory = NotificationCategory.GENERAL
) {
    /**
     * An error or a battery notice: the notices that still show where a screen asks for only the
     * ones that cannot wait.
     */
    val isCritical: Boolean
        get() = type == NotificationType.ERROR || category == NotificationCategory.BATTERY
}
