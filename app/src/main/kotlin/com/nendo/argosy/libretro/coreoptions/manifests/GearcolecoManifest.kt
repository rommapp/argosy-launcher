package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object GearcolecoManifest : CoreOptionManifest {
    override val coreId = "gearcoleco"
    override val options = listOf(
        CoreOptionDef(
            key = "gearcoleco_timing",
            displayName = "Refresh Rate (Restart)",
            values = listOf("Auto", "NTSC (60 Hz)", "PAL (50 Hz)"),
            defaultValue = "Auto",
            description = "Sets the video timing standard for refresh rate"
        ),
        CoreOptionDef(
            key = "gearcoleco_aspect_ratio",
            displayName = "Aspect Ratio",
            values = listOf("1:1 PAR", "4:3 DAR", "16:9 DAR", "16:10 DAR"),
            defaultValue = "1:1 PAR"
        ),
        CoreOptionDef(
            key = "gearcoleco_overscan",
            displayName = "Overscan",
            values = listOf("Disabled", "Top+Bottom", "Full (284 width)", "Full (320 width)"),
            defaultValue = "Disabled",
            description = "Shows or hides the overscan border area around the visible picture"
        ),
        CoreOptionDef(
            key = "gearcoleco_up_down_allowed",
            displayName = "Allow Up+Down / Left+Right",
            values = listOf("Disabled", "Enabled"),
            defaultValue = "Disabled",
            description = "Allows pressing opposite directions simultaneously on the D-pad"
        ),
        CoreOptionDef(
            key = "gearcoleco_no_sprite_limit",
            displayName = "No Sprite Limit",
            values = listOf("Disabled", "Enabled"),
            defaultValue = "Disabled",
            description = "Removes the per-scanline sprite limit to reduce flickering"
        ),
        CoreOptionDef(
            key = "gearcoleco_spinners",
            displayName = "Spinner Support",
            values = listOf(
                "Disabled", "Super Action Controller",
                "Wheel Controller", "Roller Controller"
            ),
            defaultValue = "Disabled",
            description = "Selects which spinner/paddle controller type to emulate"
        ),
        CoreOptionDef(
            key = "gearcoleco_spinner_sensitivity",
            displayName = "Spinner Sensitivity",
            values = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"),
            defaultValue = "1",
            description = "Adjusts how responsive the emulated spinner is to input"
        ),
    )
}
