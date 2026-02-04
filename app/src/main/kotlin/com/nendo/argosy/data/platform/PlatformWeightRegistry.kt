package com.nendo.argosy.data.platform

import com.nendo.argosy.ui.screens.settings.libretro.LibretroSettingDef

object PlatformWeightRegistry {
    enum class Weight { LIGHT, MEDIUM, HEAVY }

    private val heavyPlatforms = setOf(
        "n64", "n64dd",
        "gc",
        "psx", "ps2",
        "saturn",
        "dreamcast",
        "psp",
        "nds", "dsi",
        "3do",
        "jaguar", "jaguarcd"
    )

    fun getWeight(platformSlug: String): Weight {
        val canonical = PlatformDefinitions.getCanonicalSlug(platformSlug)
        return if (canonical in heavyPlatforms) Weight.HEAVY else Weight.LIGHT
    }

    private val platformsWithAnalog = setOf(
        "n64", "n64dd",
        "psx", "ps2", "psp", "vita",
        "gc", "wii",
        "dreamcast", "saturn",
        "nds", "dsi", "3ds"
    )

    private val platformsWithRumble = setOf(
        "n64", "n64dd",
        "psx", "ps2",
        "gc", "wii",
        "dreamcast"
    )

    fun hasAnalogStick(platformSlug: String): Boolean =
        PlatformDefinitions.getCanonicalSlug(platformSlug) in platformsWithAnalog

    fun hasRumble(platformSlug: String): Boolean =
        PlatformDefinitions.getCanonicalSlug(platformSlug) in platformsWithRumble

    fun isSettingVisible(
        setting: LibretroSettingDef,
        platformSlug: String?,
        canEnableBFI: Boolean = true
    ): Boolean {
        if (setting == LibretroSettingDef.BlackFrameInsertion && !canEnableBFI) {
            return false
        }

        if (setting == LibretroSettingDef.RewindEnabled &&
            platformSlug != null &&
            getWeight(platformSlug) == Weight.HEAVY
        ) {
            return false
        }

        return true
    }
}
