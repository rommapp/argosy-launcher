package com.nendo.argosy.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.nendo.argosy.R

object DownloadNotificationChannel {
    const val CHANNEL_ID = "download_service_channel"

    fun create(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.sync_download_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.sync_download_channel_description)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
