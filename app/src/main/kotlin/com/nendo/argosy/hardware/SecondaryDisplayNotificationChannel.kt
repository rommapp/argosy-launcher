package com.nendo.argosy.hardware

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.nendo.argosy.R

/**
 * The channel's name and description are resolved on every [create] call rather than held in a
 * constant. Android keeps whatever wording a channel was created with, so a cached string would
 * leave the system's notification settings in the language of first launch.
 */
object SecondaryDisplayNotificationChannel {
    const val CHANNEL_ID = "secondary_display_channel"
    const val NOTIFICATION_ID = 0x4001

    fun create(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.dual_channel_secondary_display_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description =
                context.getString(R.string.dual_channel_secondary_display_description)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
