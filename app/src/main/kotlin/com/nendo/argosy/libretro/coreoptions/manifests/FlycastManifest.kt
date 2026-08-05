package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object FlycastManifest : CoreOptionManifest {
    override val coreId = "flycast"
    override val options = listOf(
        CoreOptionDef(
            key = "reicast_hle_bios",
            displayName = "HLE BIOS",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Uses high-level emulation instead of a real BIOS file"
        ),
        CoreOptionDef(
            key = "reicast_internal_resolution",
            displayName = "Internal Resolution",
            values = listOf(
                "320x240", "640x480", "800x600", "960x720", "1024x768",
                "1280x960", "1440x1080", "1600x1200", "1920x1440", "2560x1920",
                "2880x2160", "3200x2400", "3840x2880", "4480x3360", "5120x3840",
                "5760x4320", "6400x4800", "7680x5760", "8960x6720", "10240x7680",
                "11520x8640", "12800x9600"
            ),
            defaultValue = "640x480",
            description = "Sets the 3D rendering resolution, higher values look sharper"
        ),
        CoreOptionDef(
            key = "reicast_screen_rotation",
            displayName = "Screen Orientation",
            values = listOf("horizontal", "vertical"),
            defaultValue = "horizontal",
            description = "Rotates the screen for vertically oriented arcade games",
            valueLabels = mapOf("horizontal" to "Horizontal", "vertical" to "Vertical")
        ),
        CoreOptionDef(
            key = "reicast_alpha_sorting",
            displayName = "Alpha Sorting",
            values = listOf(
                "per-strip (fast, least accurate)",
                "per-triangle (normal)",
                "per-pixel (accurate)"
            ),
            defaultValue = "per-triangle (normal)",
            description = "Sets the accuracy of transparent polygon sorting",
            valueLabels = mapOf(
                "per-strip (fast, least accurate)" to "Per-Strip (fast, least accurate)",
                "per-triangle (normal)" to "Per-Triangle (normal)",
                "per-pixel (accurate)" to "Per-Pixel (accurate, but slowest)"
            )
        ),
        CoreOptionDef(
            key = "reicast_gdrom_fast_loading",
            displayName = "GD-ROM Fast Loading",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Speeds up GD-ROM disc loading times"
        ),
        CoreOptionDef(
            key = "reicast_mipmapping",
            displayName = "Mipmapping",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Uses lower-resolution textures at distance for smoother visuals"
        ),
        CoreOptionDef(
            key = "reicast_fog",
            displayName = "Fog Effects",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled"
        ),
        CoreOptionDef(
            key = "reicast_volume_modifier_enable",
            displayName = "Volume Modifier",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Enables the Dreamcast GPU volume modifier for shadow effects"
        ),
        CoreOptionDef(
            key = "reicast_widescreen_hack",
            displayName = "Widescreen Hack",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Forces widescreen rendering by modifying the viewport"
        ),
        CoreOptionDef(
            key = "reicast_widescreen_cheats",
            displayName = "Widescreen Cheats",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables built-in widescreen patches for supported games"
        ),
        CoreOptionDef(
            key = "reicast_cable_type",
            displayName = "Cable Type",
            values = listOf("TV (RGB)", "TV (Composite)", "VGA"),
            defaultValue = "TV (Composite)",
            coreDefault = "TV (Composite)",
            description = "Selects the video output cable type, affects available display modes",
            valueLabels = mapOf("VGA" to "VGA (RGB)")
        ),
        CoreOptionDef(
            key = "reicast_broadcast",
            displayName = "Broadcast Standard",
            values = listOf("Default", "PAL_M", "PAL_N", "NTSC", "PAL"),
            defaultValue = "Default",
            coreDefault = "NTSC",
            description = "Default follows the region the game was released for",
            valueLabels = mapOf(
                "PAL_M" to "PAL-M (Brazil)",
                "PAL_N" to "PAL-N (Argentina, Paraguay, Uruguay)",
                "PAL" to "PAL (World)"
            )
        ),
        CoreOptionDef(
            key = "reicast_region",
            displayName = "Region",
            values = listOf("Default", "Japan", "USA", "Europe"),
            defaultValue = "Default",
            coreDefault = "USA"
        ),
        CoreOptionDef(
            key = "reicast_language",
            displayName = "Language",
            values = listOf("Default", "Japanese", "English", "German", "French", "Spanish", "Italian"),
            defaultValue = "Default",
            coreDefault = "English"
        ),
        CoreOptionDef(
            key = "reicast_force_wince",
            displayName = "Force Windows CE Mode",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Forces Windows CE compatibility mode for games that require it"
        ),
        CoreOptionDef(
            key = "reicast_analog_stick_deadzone",
            displayName = "Analog Stick Deadzone",
            values = listOf("0%", "5%", "10%", "15%", "20%", "25%", "30%"),
            defaultValue = "15%"
        ),
        CoreOptionDef(
            key = "reicast_trigger_deadzone",
            displayName = "Trigger Deadzone",
            values = listOf("0%", "5%", "10%", "15%", "20%", "25%", "30%"),
            defaultValue = "0%"
        ),
        CoreOptionDef(
            key = "reicast_digital_triggers",
            displayName = "Digital Triggers",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Treats trigger buttons as digital on/off instead of analog"
        ),
        CoreOptionDef(
            key = "reicast_enable_dsp",
            displayName = "Enable DSP",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            coreDefault = "disabled",
            description = "Enables the Dreamcast's audio DSP for more accurate sound processing"
        ),
        CoreOptionDef(
            key = "reicast_anisotropic_filtering",
            displayName = "Anisotropic Filtering",
            values = listOf("off", "2", "4", "8", "16"),
            defaultValue = "4",
            coreDefault = "off",
            description = "Improves texture clarity at steep viewing angles",
            valueLabels = mapOf(
                "off" to "Off", "2" to "2x", "4" to "4x", "8" to "8x", "16" to "16x"
            )
        ),
        CoreOptionDef(
            key = "reicast_pvr2_filtering",
            displayName = "PowerVR2 Post-Processing Filter",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Applies the Dreamcast's native bilinear post-processing filter"
        ),
        CoreOptionDef(
            key = "reicast_threaded_rendering",
            displayName = "Threaded Rendering",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Runs the GPU and CPU on separate threads for better performance"
        ),
        CoreOptionDef(
            key = "reicast_delay_frame_swapping",
            displayName = "Delay Frame Swapping",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Delays frame buffer swap to reduce screen tearing or fix glitches"
        ),
        CoreOptionDef(
            key = "reicast_frame_skipping",
            displayName = "Frame Skipping",
            values = listOf("disabled", "1", "2", "3", "4", "5", "6"),
            defaultValue = "disabled",
            description = "Sets how many frames to skip between each rendered frame",
            valueLabels = mapOf(
                "disabled" to "Off", "1" to "1 frame", "2" to "2 frames",
                "3" to "3 frames", "4" to "4 frames", "5" to "5 frames", "6" to "6 frames"
            )
        ),
        CoreOptionDef(
            key = "reicast_allow_service_buttons",
            displayName = "Allow NAOMI Service Buttons",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables the SERVICE button for NAOMI to enter cabinet settings"
        ),
        CoreOptionDef(
            key = "reicast_custom_textures",
            displayName = "Custom Textures",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Loads replacement texture packs from the textures directory"
        ),
        CoreOptionDef(
            key = "reicast_dump_textures",
            displayName = "Dump Textures",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Saves game textures to disk for creating texture packs"
        )
    )
}
