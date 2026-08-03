package com.nendo.argosy.data.repository

/**
 * Which button profile a core's port device speaks.
 *
 * A libretro device id is core-local and its numeric value carries no meaning across cores: FBNeo's
 * 6-Button Panel and Beetle Saturn's 3D Control Pad are both 261. Worse, a single core can reuse an
 * id across platforms - Dolphin's GameCube pad and its bare Wiimote are both RETRO_DEVICE_JOYPAD -
 * so a pairing is only sound when scoped to the platform profile it was read against.
 *
 * Every entry is transcribed from the core's own device table and the code that reads it, never
 * inferred from a device's name. A core or device absent from this map keeps the platform's default
 * profile.
 */
object CoreDeviceProfiles {

    private const val JOYPAD = 1
    private const val ANALOG = 5

    private fun subclass(shift: Int, base: Int): Int = (shift shl 8) or base

    private class DevicePairings(
        val appliesTo: MappingPlatform,
        val byDeviceId: Map<Int, String>
    )

    /**
     * Dolphin, `Source/Core/DolphinLibretro/Input.cpp`: the bare Wiimote is RETRO_DEVICE_JOYPAD and
     * the variants are raw shifts 2 to 5. Sideways reports the upright Wiimote's buttons, so both
     * take the same profile. Scoped to Wii because GameCube reuses RETRO_DEVICE_JOYPAD.
     */
    private val DOLPHIN_WII = DevicePairings(
        appliesTo = MappingPlatforms.WII,
        byDeviceId = mapOf(
            JOYPAD to MappingPlatforms.WII.id,
            subclass(2, JOYPAD) to MappingPlatforms.WII.id,
            subclass(3, JOYPAD) to MappingPlatforms.WII_NUNCHUK.id,
            subclass(4, JOYPAD) to MappingPlatforms.WII_CLASSIC.id,
            subclass(5, JOYPAD) to MappingPlatforms.WII_CLASSIC_PRO.id
        )
    )

    /**
     * FBNeo, `src/burner/libretro/retro_input.h`: its pad devices subclass RETRO_DEVICE_ANALOG
     * rather than JOYPAD. Classic is the default the core falls back to in SetDefaultDeviceTypes.
     */
    private val FBNEO_ARCADE = DevicePairings(
        appliesTo = MappingPlatforms.ARCADE6,
        byDeviceId = mapOf(
            ANALOG to MappingPlatforms.ARCADE6.id,
            subclass(1, ANALOG) to MappingPlatforms.ARCADE_6PANEL.id,
            subclass(2, ANALOG) to MappingPlatforms.ARCADE_MODERN.id
        )
    )

    /**
     * mame2003-plus, `src/mame2003/mame2003.c`: RetroPad is plain JOYPAD, with fightstick, 8-button
     * and 6-button as its three subclasses.
     */
    private val MAME_ARCADE = DevicePairings(
        appliesTo = MappingPlatforms.ARCADE_MAME,
        byDeviceId = mapOf(
            JOYPAD to MappingPlatforms.ARCADE_MAME.id,
            subclass(1, JOYPAD) to MappingPlatforms.ARCADE_MAME_FIGHTSTICK.id,
            subclass(2, JOYPAD) to MappingPlatforms.ARCADE_MAME_8BUTTON.id,
            subclass(3, JOYPAD) to MappingPlatforms.ARCADE_MAME_6BUTTON.id
        )
    )

    /**
     * Beetle Saturn, `input.c`: the Control Pad is advertised as plain RETRO_DEVICE_JOYPAD while the
     * 3D Control Pad subclasses ANALOG. The remaining devices - wheel, mission sticks, twin-stick,
     * gun, mouse, keyboard - are not pads and have no button profile.
     */
    private val SATURN_DEVICES = DevicePairings(
        appliesTo = MappingPlatforms.SATURN,
        byDeviceId = mapOf(
            JOYPAD to MappingPlatforms.SATURN.id,
            subclass(1, ANALOG) to MappingPlatforms.SATURN_3D.id
        )
    )

    private val BY_CORE: Map<String, List<DevicePairings>> = mapOf(
        "dolphin" to listOf(DOLPHIN_WII),
        "fbneo" to listOf(FBNEO_ARCADE),
        "mame2003_plus" to listOf(MAME_ARCADE),
        "mednafen_saturn" to listOf(SATURN_DEVICES)
    )

    /**
     * The profile id for a port running [deviceId] under [coreId], or null when the pairing is not
     * recorded and the caller should fall back to the platform's own profile.
     */
    fun profileIdFor(coreId: String?, platformSlug: String, deviceId: Int?): String? {
        if (coreId == null || deviceId == null) return null
        val platformProfile = MappingPlatforms.profileForSlug(platformSlug)
        return BY_CORE[coreId]
            ?.firstOrNull { it.appliesTo == platformProfile }
            ?.byDeviceId
            ?.get(deviceId)
    }

    fun hasDeviceProfiles(coreId: String?): Boolean = coreId != null && coreId in BY_CORE
}
