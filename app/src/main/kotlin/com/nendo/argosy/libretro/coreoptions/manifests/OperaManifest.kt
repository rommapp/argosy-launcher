package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object OperaManifest : CoreOptionManifest {
    override val coreId = "opera"
    override val options = listOf(
        CoreOptionDef(
            key = "opera_cpu_overclock",
            displayName = "CPU Overclock",
            values = listOf(
                "1.0x (12.50Mhz)", "1.1x (13.75Mhz)", "1.2x (15.00Mhz)",
                "1.5x (18.75Mhz)", "1.6x (20.00Mhz)", "1.8x (22.50Mhz)",
                "2.0x (25.00Mhz)"
            ),
            defaultValue = "1.0x (12.50Mhz)",
            description = "Increases the emulated CPU speed to reduce slowdown in games"
        ),
        CoreOptionDef(
            key = "opera_high_resolution",
            displayName = "High Resolution",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Doubles the rendering resolution from 320x240 to 640x480"
        ),
        CoreOptionDef(
            key = "opera_nvram_storage",
            displayName = "NVRAM Storage",
            values = listOf("per game", "shared"),
            defaultValue = "per game",
            description = "Stores save data separately per game or in a single shared file"
        ),
        CoreOptionDef(
            key = "opera_active_devices",
            displayName = "Active Devices",
            values = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8"),
            defaultValue = "1",
            description = "Sets the number of connected controller devices"
        ),
        CoreOptionDef(
            key = "opera_hack_timing_1",
            displayName = "Timing Hack 1 (Crash 'n Burn)",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Applies a timing fix for Crash 'n Burn"
        ),
        CoreOptionDef(
            key = "opera_hack_timing_3",
            displayName = "Timing Hack 3 (Dinopark Tycoon)",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Applies a timing fix for Dinopark Tycoon"
        ),
        CoreOptionDef(
            key = "opera_hack_timing_5",
            displayName = "Timing Hack 5 (Microcosm)",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Applies a timing fix for Microcosm"
        ),
        CoreOptionDef(
            key = "opera_hack_timing_6",
            displayName = "Timing Hack 6 (Alone in the Dark)",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Applies a timing fix for Alone in the Dark"
        ),
        CoreOptionDef(
            key = "opera_hack_graphics_step_y",
            displayName = "Graphics Step Y Hack (Samurai Shodown)",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Applies a graphics fix for Samurai Shodown"
        )
    )
}
