package com.nendo.argosy.data.sync

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.util.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenshotUploadNotices @Inject constructor(
    private val notificationManager: NotificationManager
) {
    fun drainFinished(uploaded: Int, rejected: Int) {
        if (uploaded <= 0 && rejected <= 0) return
        try {
            val uploadedLine = NotificationText.Plural(R.plurals.sync_screenshot_uploaded, uploaded, listOf(uploaded))
            val rejectedLine = NotificationText.Plural(R.plurals.sync_screenshot_rejected, rejected, listOf(rejected))
            when {
                uploaded > 0 && rejected > 0 -> notificationManager.show(
                    title = uploadedLine,
                    subtitle = rejectedLine,
                    type = NotificationType.WARNING
                )
                uploaded > 0 -> notificationManager.show(title = uploadedLine, type = NotificationType.SUCCESS)
                else -> notificationManager.show(title = rejectedLine, type = NotificationType.WARNING)
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "Could not show the screenshot upload notice", e)
        }
    }

    private companion object {
        const val TAG = "ScreenshotUploadNotices"
    }
}
