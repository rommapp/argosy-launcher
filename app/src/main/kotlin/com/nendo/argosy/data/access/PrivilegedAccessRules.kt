package com.nendo.argosy.data.access

import com.nendo.argosy.data.model.PrivilegedReach
import com.nendo.argosy.data.model.PrivilegedRoute
import com.nendo.argosy.util.RootShell

internal object PrivilegedAccessRules {

    const val REACHED = "PRIVILEGED:reached"
    private const val ROOT_UID = 0
    private const val OWN_PACKAGE_PREFIX = "com.nendo.argosy"

    fun offeredRoute(vendorRoot: Boolean, magiskInstalled: Boolean, shizukuRunning: Boolean): PrivilegedRoute? = when {
        vendorRoot -> null
        magiskInstalled -> PrivilegedRoute.MAGISK
        shizukuRunning -> PrivilegedRoute.SHIZUKU
        else -> null
    }

    fun promptRoute(
        firstRunComplete: Boolean,
        answered: Boolean,
        enabled: PrivilegedRoute?,
        detected: PrivilegedRoute?
    ): PrivilegedRoute? = detected.takeIf { firstRunComplete && !answered && enabled == null }

    fun checkScript(userId: Int): String = """
        u=${'$'}(id -u)
        if [ "${'$'}u" = "$ROOT_UID" ]; then d=/data/media/$userId/Android/data; else d=/storage/emulated/$userId/Android/data; fi
        echo "${'$'}u"
        [ -d "${'$'}d" ] || exit 3
        ls "${'$'}d" > /dev/null || exit 4
        for p in "${'$'}d"/*; do
          case "${'$'}p" in */$OWN_PACKAGE_PREFIX*) continue ;; esac
          [ -d "${'$'}p" ] || continue
          ls "${'$'}p" > /dev/null || exit 5
          break
        done
        echo $REACHED
    """.trimIndent()

    fun reachOf(route: PrivilegedRoute, result: RootShell.Result?): PrivilegedReach {
        if (result == null) return PrivilegedReach.REFUSED
        val uid = result.output.firstOrNull()?.trim()?.toIntOrNull()
        val reached = result.succeeded && result.output.any { it.trim() == REACHED }
        return when {
            uid == null || !reached -> PrivilegedReach.UNREACHABLE
            uid == ROOT_UID -> PrivilegedReach.ROOT
            route == PrivilegedRoute.SHIZUKU -> PrivilegedReach.APP_DATA
            else -> PrivilegedReach.UNREACHABLE
        }
    }
}
