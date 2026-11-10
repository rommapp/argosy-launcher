package com.nendo.argosy.libretro.slot

import android.app.Application
import android.os.Build
import com.nendo.argosy.data.platform.PlatformDefinitions
import java.io.File

/**
 * The separate processes built-in games run in, so several games stay open at once. Each slot is
 * its own activity class declared with its own `android:process`; slot 0 is the plain
 * [com.nendo.argosy.libretro.LibretroActivity] in Argosy's main process.
 */
object GameSlots {

    const val PROCESS_PREFIX = ":game"

    val activities: List<Class<out com.nendo.argosy.libretro.LibretroActivity>> = listOf(
        LibretroSlot1Activity::class.java,
        LibretroSlot2Activity::class.java,
        LibretroSlot3Activity::class.java,
        LibretroSlot4Activity::class.java,
        LibretroSlot5Activity::class.java
    )

    /**
     * The room the open games share. A game takes [weightOf] its platform: the platforms whose
     * built-in cores hold the most memory take two, the rest one.
     */
    const val CAPACITY_UNITS = 5

    private val heavyPlatforms = setOf("gc", "wii", "ps2", "dreamcast", "psp", "3ds")

    fun weightOf(platformSlug: String?): Int =
        if (platformSlug != null && PlatformDefinitions.getCanonicalSlug(platformSlug) in heavyPlatforms) 2 else 1

    val isSlotProcess: Boolean by lazy { currentProcessName().contains(PROCESS_PREFIX) }

    fun isSlotActivity(className: String?): Boolean =
        className != null && activities.any { it.name == className }

    private fun currentProcessName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            runCatching { File("/proc/self/cmdline").readText().trimEnd(Char(0)) }.getOrDefault("")
        }
}
