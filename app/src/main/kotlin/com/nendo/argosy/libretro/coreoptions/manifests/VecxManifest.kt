package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object VecxManifest : CoreOptionManifest {
    override val coreId = "vecx"
    override val options = listOf(
        CoreOptionDef(
            key = "vecx_render",
            displayName = "Use Hardware Rendering",
            values = listOf("Hardware", "Software"),
            defaultValue = "Hardware",
            description = "Switches between GPU-accelerated and software rendering"
        ),
        CoreOptionDef(
            key = "vecx_renderresolution",
            displayName = "Hardware Rendering Resolution",
            values = listOf(
                "434x540", "515x640", "580x720", "618x768", "824x1024",
                "845x1050", "869x1080", "966x1200", "1159x1440", "1648x2048"
            ),
            defaultValue = "824x1024",
            description = "Sets the internal resolution when using hardware rendering"
        ),
        CoreOptionDef(
            key = "vecx_linebrightness",
            displayName = "Line Brightness",
            values = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9"),
            defaultValue = "4",
            description = "Controls the brightness of vector lines"
        ),
        CoreOptionDef(
            key = "vecx_linewidth",
            displayName = "Line Width",
            values = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"),
            defaultValue = "4",
            description = "Controls the thickness of vector lines"
        ),
        CoreOptionDef(
            key = "vecx_bloombrightness",
            displayName = "Bloom Brightness",
            values = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9"),
            defaultValue = "4",
            description = "Controls the brightness of the glow effect around vector lines"
        ),
        CoreOptionDef(
            key = "vecx_bloomwidth",
            displayName = "Bloom Width",
            values = listOf("2x", "3x", "4x", "6x", "8x", "10x", "12x", "14x", "16x"),
            defaultValue = "8x",
            description = "Controls the spread of the glow effect around vector lines"
        ),
    )
}
