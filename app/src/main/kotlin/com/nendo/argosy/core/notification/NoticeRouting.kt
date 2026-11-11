package com.nendo.argosy.core.notification

import com.nendo.argosy.data.preferences.AppPreferences

enum class NoticeRoute {
    IN_APP,
    HOLD,
    BUILT_IN_GAME,
    OVER_APP,
    DROP
}

/**
 * The notice settings as one read. A category in [disabledCategories] is dropped even when
 * [enabled] is true.
 */
data class NoticePreferences(
    val enabled: Boolean = true,
    val disabledCategories: Set<NotificationCategory> = emptySet()
) {
    companion object {
        fun from(prefs: AppPreferences) = NoticePreferences(
            enabled = prefs.notificationsEnabled,
            disabledCategories = buildSet {
                if (!prefs.notifyDownloadsFinished) add(NotificationCategory.DOWNLOAD)
                if (!prefs.notifyBattery) add(NotificationCategory.BATTERY)
                if (!prefs.notifyCharging) add(NotificationCategory.CHARGING)
                if (!prefs.notifyControllers) add(NotificationCategory.CONTROLLER)
            }
        )
    }
}

/**
 * Where Argosy stands when a notice arrives. [argosyVisible] is true while a launcher surface that
 * draws notices is started; [builtInGameInFront] is true while a built-in game is resumed.
 */
data class NoticeStage(
    val argosyVisible: Boolean,
    val builtInGameInFront: Boolean
)

fun routeNotice(
    type: NotificationType,
    category: NotificationCategory,
    preferences: NoticePreferences,
    stage: NoticeStage
): NoticeRoute {
    if (category in preferences.disabledCategories) return NoticeRoute.DROP
    if (!preferences.enabled && !category.bypassesGlobalOff) return NoticeRoute.DROP
    if (stage.argosyVisible) return NoticeRoute.IN_APP
    val showsOverGames = category.showsOverGames ||
        type == NotificationType.WARNING ||
        type == NotificationType.ERROR
    if (!showsOverGames) return NoticeRoute.HOLD
    return if (stage.builtInGameInFront) NoticeRoute.BUILT_IN_GAME else NoticeRoute.OVER_APP
}
