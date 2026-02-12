package com.nendo.argosy.hardware

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object SecondaryDisplayNotificationChannel {
    const val CHANNEL_ID = "secondary_display_channel"
    const val NOTIFICATION_ID = 0x4001
    private const val CHANNEL_NAME = "Secondary Display"
    private const val CHANNEL_DESCRIPTION = "Shows game library on secondary display"

    fun create(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = CHANNEL_DESCRIPTION
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
