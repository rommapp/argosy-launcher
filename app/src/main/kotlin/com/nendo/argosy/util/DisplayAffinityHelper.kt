package com.nendo.argosy.util

import android.app.ActivityOptions
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
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

    private val physicalDisplays: Array<Display>
        get() = displayManager.displays.filter { it.isPhysicalDisplay() }.toTypedArray()

    val hasPhysicalSecondaryDisplay: Boolean
        get() = physicalDisplays.size > 1

    var dualScreenEnabled: Boolean = false

    /**
     * False once the companion has been proven unable to initialize on the secondary display,
     * which happens on OS builds that do not let a home activity run there. Gates every
     * dual-screen entry point until a display change or an explicit user re-enable re-probes it.
     */
    var secondaryDisplayUsable: Boolean = true

    val hasSecondaryDisplay: Boolean
        get() = dualScreenEnabled && secondaryDisplayUsable && hasPhysicalSecondaryDisplay

    val secondaryDisplayType: SecondaryDisplayType
        get() {
            val secondary = physicalDisplays.getOrNull(1) ?: return SecondaryDisplayType.NONE
            val type = secondary.displayType()
            return when {
                type == DISPLAY_TYPE_EXTERNAL -> SecondaryDisplayType.EXTERNAL
                type == DISPLAY_TYPE_BUILT_IN -> SecondaryDisplayType.BUILT_IN
                secondary.flags and Display.FLAG_PRESENTATION != 0 -> SecondaryDisplayType.EXTERNAL
                else -> SecondaryDisplayType.BUILT_IN
            }
        }

    private val secondaryDisplayId: Int?
        get() = physicalDisplays.getOrNull(1)?.displayId

    /**
     * The roomiest physical display, by pixel area.
     *
     * Measured rather than assumed to be the default one, because which panel is larger is a fact
     * about the hardware; a handheld whose second screen is the bigger of the two would otherwise
     * send video to the smaller.
     */
    fun largestDisplayId(): Int? = physicalDisplays
        .maxByOrNull { display ->
            val metrics = android.graphics.Point()
            @Suppress("DEPRECATION")
            display.getRealSize(metrics)
            metrics.x.toLong() * metrics.y.toLong()
        }
        ?.displayId

    fun registerDisplayListener(
        listener: DisplayManager.DisplayListener,
        handler: android.os.Handler? = null
    ) {
        displayManager.registerDisplayListener(listener, handler)
    }

    fun unregisterDisplayListener(listener: DisplayManager.DisplayListener) {
        displayManager.unregisterDisplayListener(listener)
    }

    fun getCompanionLaunchOptions(): Bundle? {
        val displayId = secondaryDisplayId ?: return null
        return ActivityOptions.makeBasic()
            .setLaunchDisplayId(displayId)
            .toBundle()
    }

    fun getEmulatorDisplayId(rolesSwapped: Boolean): Int {
        return if (rolesSwapped) secondaryDisplayId ?: Display.DEFAULT_DISPLAY
        else Display.DEFAULT_DISPLAY
    }

    /**
     * Which physical display holds each role: the one the viewer is driving, then the one
     * describing what that screen has focused. Null on a single-screen device, where there are no
     * roles to hold.
     *
     * The interactive display carries Home, Library and Media. A role swap is the only thing that
     * moves them, so a caller asks which display holds its role instead of naming a display id,
     * and keeps landing correctly after a swap.
     */
    fun getRoleDisplayIds(rolesSwapped: Boolean): Pair<Int, Int>? {
        val secondary = secondaryDisplayId ?: return null
        return if (rolesSwapped) {
            Display.DEFAULT_DISPLAY to secondary
        } else {
            secondary to Display.DEFAULT_DISPLAY
        }
    }

    /**
     * Where the video player belongs once a game has claimed [emulatorDisplayId]: the other physical
     * display. Null when there is no second display, which is the single-screen answer - nothing
     * moves and the player stays where it is.
     */
    fun getMediaPlayerDisplayId(emulatorDisplayId: Int?): Int? {
        if (!hasSecondaryDisplay) return null
        val secondary = secondaryDisplayId ?: return null
        return if (emulatorDisplayId == secondary) Display.DEFAULT_DISPLAY else secondary
    }

    fun getActivityOptions(
        forEmulator: Boolean,
        rolesSwapped: Boolean = false,
        overrideDisplayId: Int? = null
    ): Bundle? {
        if (overrideDisplayId == null && !hasSecondaryDisplay) return null

        val targetDisplayId = overrideDisplayId ?: if (forEmulator) {
            if (rolesSwapped) secondaryDisplayId ?: return null
            else Display.DEFAULT_DISPLAY
        } else {
            secondaryDisplayId ?: return null
        }

        return ActivityOptions.makeBasic()
            .setLaunchDisplayId(targetDisplayId)
            .toBundle()
    }

    fun isPhysicalDisplay(displayId: Int): Boolean {
        val display = displayManager.getDisplay(displayId) ?: return false
        return display.isPhysicalDisplay()
    }

    companion object {
        private const val DISPLAY_TYPE_BUILT_IN = 1
        private const val DISPLAY_TYPE_EXTERNAL = 2

        private val KNOWN_DUAL_SCREEN_DEVICES = listOf("thor")

        fun isKnownDualScreenDevice(): Boolean =
            KNOWN_DUAL_SCREEN_DEVICES.any { Build.MODEL.contains(it, ignoreCase = true) }

        private fun Display.displayType(): Int? = try {
            Display::class.java.getMethod("getType").invoke(this) as? Int
        } catch (_: Exception) { null }

        private fun Display.isPhysicalDisplay(): Boolean {
            if (state == Display.STATE_OFF) return false
            val type = displayType()
            if (type != null) return type == DISPLAY_TYPE_BUILT_IN || type == DISPLAY_TYPE_EXTERNAL
            return flags and Display.FLAG_PRIVATE == 0
        }
    }
}
