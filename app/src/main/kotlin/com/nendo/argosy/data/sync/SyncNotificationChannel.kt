package com.nendo.argosy.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.nendo.argosy.R

object SyncNotificationChannel {
    const val CHANNEL_ID = "sync_service_channel"

    fun create(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.sync_service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.sync_service_channel_description)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
