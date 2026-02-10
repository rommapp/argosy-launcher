package com.nendo.argosy.util

import android.app.ActivityOptions
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DisplayAffinityHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    val hasSecondaryDisplay: Boolean
        get() = displayManager.displays.size > 1

    private val secondaryDisplayId: Int?
        get() = displayManager.displays.getOrNull(1)?.displayId

    fun getActivityOptions(forEmulator: Boolean): Bundle? {
        if (!hasSecondaryDisplay) return null

        val targetDisplayId = if (forEmulator) {
            Display.DEFAULT_DISPLAY
        } else {
            secondaryDisplayId ?: return null
        }

        return ActivityOptions.makeBasic()
            .setLaunchDisplayId(targetDisplayId)
            .toBundle()
    }
}
