package com.nendo.argosy.ui.notification

fun NotificationManager.showError(message: String) {
    show(
        title = "Error",
        subtitle = message,
        type = NotificationType.ERROR,
        duration = NotificationDuration.LONG
    )
}

fun NotificationManager.showSuccess(message: String) {
    show(
        title = message,
        type = NotificationType.SUCCESS,
        duration = NotificationDuration.SHORT
    )
}
