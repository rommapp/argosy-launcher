package com.nendo.argosy.util

import android.content.Context
import android.content.pm.ApplicationInfo
import com.nendo.argosy.data.model.RootScript
import java.io.File

enum class SystemAppState { ORDINARY, SYSTEMIZED, PART_OF_SYSTEM }

object SystemAppStatus {
    private const val SYSTEMIZED_DIR = "/system/app/Argosy"

    fun state(context: Context): SystemAppState = when {
        context.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0 -> SystemAppState.ORDINARY
        File(SYSTEMIZED_DIR).isDirectory -> SystemAppState.SYSTEMIZED
        else -> SystemAppState.PART_OF_SYSTEM
    }

    /**
     * The root script the system-app row runs, or null when Argosy ships inside this device's
     * system image and neither script applies.
     */
    fun systemAppScript(context: Context): RootScript? = when (state(context)) {
        SystemAppState.ORDINARY -> RootScript.SYSTEMIZE
        SystemAppState.SYSTEMIZED -> RootScript.UNSYSTEMIZE
        SystemAppState.PART_OF_SYSTEM -> null
    }
}
