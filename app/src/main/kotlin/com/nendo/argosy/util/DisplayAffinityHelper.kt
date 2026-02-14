package com.nendo.argosy.util

import android.app.ActivityOptions
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class SecondaryDisplayType { NONE, BUILT_IN, EXTERNAL }

@Singleton
class DisplayAffinityHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    val hasSecondaryDisplay: Boolean
        get() = displayManager.displays.size > 1

    val secondaryDisplayType: SecondaryDisplayType
        get() {
            val secondary = displayManager.displays.getOrNull(1) ?: return SecondaryDisplayType.NONE
            val type = try {
                Display::class.java.getMethod("getType").invoke(secondary) as? Int
            } catch (_: Exception) { null }
            return when {
                type == 2 -> SecondaryDisplayType.EXTERNAL
                type == 1 -> SecondaryDisplayType.BUILT_IN
                secondary.flags and Display.FLAG_PRESENTATION != 0 -> SecondaryDisplayType.EXTERNAL
                else -> SecondaryDisplayType.BUILT_IN
            }
        }

    private val secondaryDisplayId: Int?
        get() = displayManager.displays.getOrNull(1)?.displayId

    fun getActivityOptions(forEmulator: Boolean, rolesSwapped: Boolean = false): Bundle? {
        if (!hasSecondaryDisplay) return null

        val targetDisplayId = if (forEmulator) {
            if (rolesSwapped) secondaryDisplayId ?: return null
            else Display.DEFAULT_DISPLAY
        } else {
            secondaryDisplayId ?: return null
        }

        return ActivityOptions.makeBasic()
            .setLaunchDisplayId(targetDisplayId)
            .toBundle()
    }
}
