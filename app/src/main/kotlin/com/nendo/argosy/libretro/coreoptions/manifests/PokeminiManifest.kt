package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object PokeminiManifest : CoreOptionManifest {
    override val coreId = "pokemini"
    override val options = listOf(
        CoreOptionDef(
            key = "pokemini_video_scale",
            displayName = "Video Scale",
            values = listOf("1x", "2x", "3x", "4x", "5x", "6x"),
            defaultValue = "4x",
            description = "Multiplies the internal resolution for a larger display"
        ),
        CoreOptionDef(
            key = "pokemini_lcdfilter",
            displayName = "LCD Filter",
            values = listOf("dotmatrix", "scanline", "none"),
            defaultValue = "dotmatrix",
            description = "Applies a visual filter to simulate the original LCD screen"
        ),
        CoreOptionDef(
            key = "pokemini_lcdmode",
            displayName = "LCD Mode",
            values = listOf("analog", "3shades", "2shades"),
            defaultValue = "analog",
            description = "Sets how many shades the LCD simulation uses"
        ),
        CoreOptionDef(
            key = "pokemini_lcdcontrast",
            displayName = "LCD Contrast",
            values = listOf("0", "16", "32", "48", "64", "80", "96"),
            defaultValue = "64"
        ),
        CoreOptionDef(
            key = "pokemini_lcdbright",
            displayName = "LCD Brightness",
            values = listOf("-80", "-60", "-40", "-20", "0", "20", "40", "60", "80"),
            defaultValue = "0"
        ),
        CoreOptionDef(
            key = "pokemini_palette",
            displayName = "Palette",
            values = listOf(
                "Default", "Old", "Monochrome", "Green", "Green Vector",
                "Red", "Red Vector", "Blue LCD", "LEDBacklight", "Girl Power",
                "Blue", "Blue Vector", "Sepia", "Monochrome Vector"
            ),
            defaultValue = "Default",
            description = "Selects the color scheme used to render the display"
        ),
        CoreOptionDef(
            key = "pokemini_piezofilter",
            displayName = "Piezo Filter",
            values = listOf("ON", "OFF"),
            defaultValue = "ON",
            description = "Filters the piezo speaker audio to reduce harshness"
        ),
        CoreOptionDef(
            key = "pokemini_rumblelvl",
            displayName = "Rumble Level",
            values = listOf("0", "1", "2", "3"),
            defaultValue = "3",
            description = "Sets the intensity of the rumble feedback"
        ),
        CoreOptionDef(
            key = "pokemini_controller_rumble",
            displayName = "Controller Rumble",
            values = listOf("ON", "OFF"),
            defaultValue = "ON",
            description = "Sends rumble events to the physical controller"
        ),
        CoreOptionDef(
            key = "pokemini_screen_shake",
            displayName = "Screen Shake",
            values = listOf("ON", "OFF"),
            defaultValue = "ON",
            description = "Shakes the screen to simulate the Pokemon Mini's rumble motor"
        ),
    )
}
