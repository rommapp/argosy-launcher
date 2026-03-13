package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object ProsystemManifest : CoreOptionManifest {
    override val coreId = "prosystem"
    override val options = listOf(
        CoreOptionDef(
            key = "prosystem_color_depth",
            displayName = "Color Depth (Restart)",
            values = listOf("16bit", "24bit"),
            defaultValue = "16bit",
            description = "Sets the color depth used for rendering"
        ),
        CoreOptionDef(
            key = "prosystem_low_pass_filter",
            displayName = "Audio Filter",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Applies a low-pass filter to soften audio output"
        ),
        CoreOptionDef(
            key = "prosystem_low_pass_range",
            displayName = "Audio Filter Level",
            values = listOf(
                "5", "10", "15", "20", "25", "30", "35", "40", "45",
                "50", "55", "60", "65", "70", "75", "80", "85", "90", "95"
            ),
            defaultValue = "60",
            description = "Controls the strength of the low-pass audio filter"
        ),
        CoreOptionDef(
            key = "prosystem_gamepad_dual_stick_hack",
            displayName = "Dual Stick Controller",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Maps both analog sticks to simulate two-joystick controllers"
        ),
    )
}
