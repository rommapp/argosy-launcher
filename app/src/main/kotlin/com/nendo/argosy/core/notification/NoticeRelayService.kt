package com.nendo.argosy.core.notification

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.nendo.argosy.core.service.startServiceSafely
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Argosy's main process entry for notices raised in a game slot process, so they reach the one
 * [NotificationManager] and its routing.
 */
@AndroidEntryPoint
class NoticeRelayService : Service() {

    @Inject lateinit var notificationManager: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ResolvedNotice.fromBundle(intent?.extras)?.let { notice ->
            notificationManager.show(
                title = NotificationText.Raw(notice.title),
                subtitle = notice.body?.let { NotificationText.Raw(it) },
                type = notice.type,
                imagePath = notice.imagePath,
                duration = notice.duration,
                key = notice.key,
                accentColor = notice.accentColor,
                category = notice.category
            )
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    companion object {
        fun relay(context: Context, notice: ResolvedNotice) {
            val intent = Intent(context, NoticeRelayService::class.java).putExtras(notice.toBundle())
            context.startServiceSafely(intent)
        }
    }
}
