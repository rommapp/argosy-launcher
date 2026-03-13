package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object MednafenVbManifest : CoreOptionManifest {
    override val coreId = "mednafen_vb"
    override val options = listOf(
        CoreOptionDef(
            key = "vb_3dmode",
            displayName = "3D Mode",
            values = listOf("anaglyph", "cyberscope", "side-by-side", "vli", "hli"),
            defaultValue = "anaglyph",
            description = "Selects how the Virtual Boy's stereoscopic 3D is displayed"
        ),
        CoreOptionDef(
            key = "vb_anaglyph_preset",
            displayName = "Anaglyph Preset",
            values = listOf(
                "disabled", "red & blue", "red & cyan", "red & electric cyan",
                "green & magenta", "yellow & blue"
            ),
            defaultValue = "disabled",
            description = "Selects the color combination for anaglyph 3D glasses"
        ),
        CoreOptionDef(
            key = "vb_sidebyside_separation",
            displayName = "Side-by-Side Separation",
            values = (0..256 step 4).map { it.toString() },
            defaultValue = "0",
            description = "Sets the pixel gap between left and right eye images in side-by-side mode"
        ),
        CoreOptionDef(
            key = "vb_color_mode",
            displayName = "Palette",
            values = listOf(
                "black & red", "black & white", "black & blue", "black & cyan",
                "black & electric cyan", "black & green", "black & magenta", "black & yellow"
            ),
            defaultValue = "black & red",
            description = "Selects the display color scheme"
        ),
        CoreOptionDef(
            key = "vb_right_analog_to_digital",
            displayName = "Right Analog to Digital",
            values = listOf("disabled", "enabled", "invert x", "invert y", "invert both"),
            defaultValue = "disabled",
            description = "Maps the right analog stick to the Virtual Boy's right D-pad"
        ),
        CoreOptionDef(
            key = "vb_cpu_emulation",
            displayName = "CPU Emulation (Restart)",
            values = listOf("fast", "accurate"),
            defaultValue = "fast",
            description = "Trades accuracy for speed when set to fast"
        ),
    )
}
