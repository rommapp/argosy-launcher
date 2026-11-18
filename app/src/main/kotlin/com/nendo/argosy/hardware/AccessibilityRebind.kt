package com.nendo.argosy.hardware

import android.content.ComponentName
import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.RootShell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brings Argosy's accessibility service back when it is switched on but the system has not bound
 * it. Some firmware leaves the service unbound after an update or a long uptime, which leaves hold
 * Home, the quick panel and the system key filter dead. The root route switches it off and on in
 * the secure settings, which makes the system bind it again; without root the player is told to do
 * the same by hand.
 */
@Singleton
class AccessibilityRebind @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager
) {
    private val checking = AtomicBoolean(false)
    private val noticePosted = AtomicBoolean(false)

    suspend fun recoverAfterUpdate() = recover(tellPlayer = false)

    /**
     * Checks the service when Argosy comes to the foreground, and posts one notice per process
     * when it stays unbound with no root route to rebind it.
     */
    suspend fun recoverIfUnbound() = recover(tellPlayer = true)

    private suspend fun recover(tellPlayer: Boolean) {
        if (!checking.compareAndSet(false, true)) return
        try {
            if (!FocusAccessibilityService.isEnabled(context)) return
            repeat(BIND_CHECKS) {
                if (FocusAccessibilityService.instance != null) return
                delay(BIND_CHECK_INTERVAL_MS)
            }
            if (FocusAccessibilityService.instance != null) return
            if (!RootShell.isAvailable) {
                Logger.info(TAG, "accessibility service switched on but unbound; no root route to rebind it")
                if (tellPlayer && noticePosted.compareAndSet(false, true)) postUnboundNotice()
                return
            }
            val component = ComponentName(context, FocusAccessibilityService::class.java).flattenToString()
            val result = RootShell.run(context, rebindScript(component))
            Logger.info(TAG, "accessibility service rebind: ${if (result?.succeeded == true) "done" else "failed"}")
        } finally {
            checking.set(false)
        }
    }

    private fun postUnboundNotice() {
        notificationManager.show(
            title = NotificationText.Res(R.string.notif_accessibility_unbound_title),
            subtitle = NotificationText.Res(R.string.notif_accessibility_unbound_body),
            type = NotificationType.WARNING,
            duration = NotificationDuration.LONG,
            key = KEY_UNBOUND,
            immediate = true
        )
    }

    private fun rebindScript(component: String): String {
        val quoted = RootShell.quote(component)
        return "key=enabled_accessibility_services\n" +
            "all=$(settings get secure \$key)\n" +
            "rest=$(echo \"\$all\" | tr ':' '\\n' | grep -vxF $quoted | tr '\\n' ':')\n" +
            "rest=\${rest%:}\n" +
            "if [ -z \"\$rest\" ]; then settings delete secure \$key; else settings put secure \$key \"\$rest\"; fi\n" +
            "sleep 1\n" +
            "settings put secure \$key \"\$all\"\n"
    }

    private companion object {
        const val TAG = "AccessibilityRebind"
        const val KEY_UNBOUND = "accessibility_unbound"
        const val BIND_CHECKS = 8
        const val BIND_CHECK_INTERVAL_MS = 1_000L
    }
}
