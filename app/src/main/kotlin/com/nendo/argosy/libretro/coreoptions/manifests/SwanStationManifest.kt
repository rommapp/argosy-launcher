package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object SwanStationManifest : CoreOptionManifest {
    override val coreId = "swanstation"
    override val options = listOf(
        CoreOptionDef(
            key = "swanstation_GPU_Renderer",
            displayName = "GPU Renderer",
            values = listOf("Auto", "OpenGL", "Vulkan", "Software"),
            defaultValue = "Auto",
            description = "Which renderer to use to emulate the GPU",
            valueLabels = mapOf(
                "Auto" to "Hardware (Auto)",
                "OpenGL" to "Hardware (OpenGL)",
                "Vulkan" to "Hardware (Vulkan)"
            )
        ),
        CoreOptionDef(
            key = "swanstation_GPU_ResolutionScale",
            displayName = "Internal Resolution Scale",
            values = (1..16).map { it.toString() },
            defaultValue = "1",
            description = "Scales internal VRAM resolution for the hardware renderer; larger values are slower",
            valueLabels = (1..16).associate { it.toString() to "${it}x" } + mapOf(
                "3" to "3x (for 720p)",
                "5" to "5x (for 1080p)",
                "6" to "6x (for 1440p)",
                "9" to "9x (for 4K)"
            )
        ),
        CoreOptionDef(
            key = "swanstation_GPU_PGXPEnable",
            displayName = "PGXP Geometry Correction",
            values = listOf("true", "false"),
            defaultValue = "false",
            description = "Reduces wobbly polygons by preserving fractional vertex positions",
            valueLabels = mapOf("true" to "Enabled", "false" to "Disabled")
        ),
        CoreOptionDef(
            key = "swanstation_GPU_TrueColor",
            displayName = "True Color Rendering",
            values = listOf("true", "false"),
            defaultValue = "false",
            description = "Renders in 24-bit color instead of the console's 15-bit with dithering",
            valueLabels = mapOf("true" to "Enabled", "false" to "Disabled")
        ),
        CoreOptionDef(
            key = "swanstation_BIOS_PatchFastBoot",
            displayName = "Fast Boot",
            values = listOf("true", "false"),
            defaultValue = "false",
            description = "Skips the PlayStation BIOS startup animation",
            valueLabels = mapOf("true" to "Enabled", "false" to "Disabled")
        ),
        CoreOptionDef(
            key = "swanstation_Console_Region",
            displayName = "Console Region",
            values = listOf("Auto", "NTSC-J", "NTSC-U", "PAL"),
            defaultValue = "Auto",
            description = "Sets the region when content cannot be auto-detected",
            valueLabels = mapOf(
                "Auto" to "Auto-Detect",
                "NTSC-J" to "NTSC-J (Japan)",
                "NTSC-U" to "NTSC-U (US, Canada)",
                "PAL" to "PAL (Europe, Australia)"
            )
        )
    )
}
