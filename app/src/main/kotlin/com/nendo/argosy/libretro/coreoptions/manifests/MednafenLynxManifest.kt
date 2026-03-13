package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object MednafenLynxManifest : CoreOptionManifest {
    override val coreId = "mednafen_lynx"
    override val options = listOf(
        CoreOptionDef(
            key = "lynx_rot_screen",
            displayName = "Auto-Rotate Screen",
            values = listOf("auto", "manual", "0", "90", "180", "270"),
            defaultValue = "auto",
            description = "Automatically rotates the display to match the game's orientation"
        ),
        CoreOptionDef(
            key = "lynx_pix_format",
            displayName = "Color Format (Restart Required)",
            values = listOf("16", "32"),
            defaultValue = "16",
            description = "Sets the color depth in bits per pixel"
        ),
        CoreOptionDef(
            key = "lynx_force_60hz",
            displayName = "Force 60Hz",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Forces 60Hz output instead of the Lynx's native 75Hz refresh rate"
        ),
    )
}
