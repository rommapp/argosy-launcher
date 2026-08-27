package com.nendo.argosy.core.notification

import com.nendo.argosy.R

fun NotificationManager.showError(message: NotificationText) {
    show(
        title = NotificationText.Res(R.string.notif_generic_error_title),
        subtitle = message,
        type = NotificationType.ERROR,
        duration = NotificationDuration.LONG
    )
}

fun NotificationManager.showSuccess(message: NotificationText) {
    show(
        title = message,
        type = NotificationType.SUCCESS,
        duration = NotificationDuration.SHORT
    )
}
