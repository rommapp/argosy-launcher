package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object HandyManifest : CoreOptionManifest {
    override val coreId = "handy"
    override val options = listOf(
        CoreOptionDef(
            key = "handy_refresh_rate",
            displayName = "Video Refresh Rate",
            values = listOf("50", "60", "75", "100", "120"),
            defaultValue = "60",
            description = "Sets the target video refresh rate in Hz"
        ),
        CoreOptionDef(
            key = "handy_rot",
            displayName = "Display Rotation",
            values = listOf("Auto", "None", "270", "180", "90"),
            defaultValue = "Auto",
            description = "Rotates the display to match the game's intended orientation"
        ),
        CoreOptionDef(
            key = "handy_gfx_colors",
            displayName = "Color Depth (Restart Required)",
            values = listOf("16bit", "24bit"),
            defaultValue = "16bit",
            description = "Sets the color depth used for rendering"
        ),
        CoreOptionDef(
            key = "handy_lcd_ghosting",
            displayName = "LCD Ghosting Filter",
            values = listOf("disabled", "2frames", "3frames", "4frames"),
            defaultValue = "disabled",
            description = "Simulates the motion blur of the original Lynx LCD screen"
        ),
        CoreOptionDef(
            key = "handy_overclock",
            displayName = "CPU Overclock Multiplier",
            values = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "20", "30", "40", "50"),
            defaultValue = "1",
            description = "Multiplies the emulated CPU speed to reduce slowdown"
        ),
        CoreOptionDef(
            key = "handy_frameskip",
            displayName = "Frameskip",
            values = listOf("disabled", "auto", "manual"),
            defaultValue = "disabled",
            description = "Skips rendering some frames to improve performance"
        ),
        CoreOptionDef(
            key = "handy_frameskip_threshold",
            displayName = "Frameskip Threshold (%)",
            values = listOf("15", "18", "21", "24", "27", "30", "33", "36", "39", "42", "45", "48", "51", "54", "57", "60"),
            defaultValue = "33",
            description = "Sets the audio buffer occupancy below which frames are skipped"
        ),
    )
}
