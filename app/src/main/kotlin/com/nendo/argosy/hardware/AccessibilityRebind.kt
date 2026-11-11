package com.nendo.argosy.hardware

import android.content.ComponentName
import android.content.Context
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.RootShell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brings Argosy's accessibility service back after Argosy itself is updated. Some firmware leaves
 * an updated app's accessibility service unbound until the app is opened again, which leaves hold
 * Home and the system key filter dead. When the service stays unbound, the root route switches it
 * off and on in the secure settings, which makes the system bind it again.
 */
@Singleton
class AccessibilityRebind @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun recoverAfterUpdate() {
        if (!FocusAccessibilityService.isEnabled(context)) return
        repeat(BIND_CHECKS) {
            if (FocusAccessibilityService.instance != null) return
            delay(BIND_CHECK_INTERVAL_MS)
        }
        if (FocusAccessibilityService.instance != null) return
        if (!RootShell.isAvailable) {
            Logger.info(TAG, "accessibility service still unbound after update; no root route to rebind it")
            return
        }
        val component = ComponentName(context, FocusAccessibilityService::class.java).flattenToString()
        val result = RootShell.run(context, rebindScript(component))
        Logger.info(TAG, "accessibility service rebind after update: ${if (result?.succeeded == true) "done" else "failed"}")
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
        const val BIND_CHECKS = 8
        const val BIND_CHECK_INTERVAL_MS = 1_000L
    }
}
