package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object GpspManifest : CoreOptionManifest {
    override val coreId = "gpsp"
    override val options = listOf(
        CoreOptionDef(
            key = "gpsp_bios",
            displayName = "BIOS",
            values = listOf("auto", "builtin", "official"),
            defaultValue = "auto",
            description = "Uses the official BIOS when one is present, otherwise the built-in replacement",
            valueLabels = mapOf(
                "auto" to "Auto select",
                "builtin" to "Builtin BIOS",
                "official" to "Original BIOS"
            )
        ),
        CoreOptionDef(
            key = "gpsp_boot_mode",
            displayName = "Boot Mode",
            values = listOf("game", "bios"),
            defaultValue = "game",
            description = "Boots straight into the game or through the BIOS intro",
            valueLabels = mapOf("game" to "Boot to game", "bios" to "Boot to BIOS")
        ),
        CoreOptionDef(
            key = "gpsp_drc",
            displayName = "Dynamic Recompiler",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Recompiles code for speed; disable only for compatibility"
        ),
        CoreOptionDef(
            key = "gpsp_sprlim",
            displayName = "No Sprite Limit",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Removes the hardware sprite-per-line limit"
        ),
        CoreOptionDef(
            key = "gpsp_rtc",
            displayName = "RTC Support",
            values = listOf("auto", "enabled", "disabled"),
            defaultValue = "auto",
            description = "Emulates the real-time clock some cartridges carry"
        ),
        CoreOptionDef(
            key = "gpsp_rtc_time_source",
            displayName = "RTC Time Source",
            values = listOf("deterministic", "system"),
            defaultValue = "deterministic",
            description = "Where the emulated clock takes its time from",
            valueLabels = mapOf("deterministic" to "Deterministic", "system" to "System Clock")
        ),
        CoreOptionDef(
            key = "gpsp_serial",
            displayName = "Link Cable Connectivity",
            values = listOf("auto", "disabled", "rfu", "mul_poke", "mul_aw1", "mul_aw2"),
            defaultValue = "auto",
            description = "Which link accessory the game sees",
            valueLabels = mapOf(
                "auto" to "Automatic",
                "disabled" to "Disabled",
                "rfu" to "GBA Wireless Adapter",
                "mul_poke" to "Link Cable - Pokemon Gen3 mode",
                "mul_aw1" to "Link Cable - Advance Wars 1 mode",
                "mul_aw2" to "Link Cable - Advance Wars 2 mode"
            )
        ),
        CoreOptionDef(
            key = "gpsp_rumble",
            displayName = "Rumble Support",
            values = listOf("auto", "enabled", "disabled"),
            defaultValue = "auto",
            description = "Passes cartridge rumble through to the controller"
        ),
        CoreOptionDef(
            key = "gpsp_sound_rate",
            displayName = "Sound Output Rate (Hz)",
            values = listOf("65536", "32768"),
            defaultValue = "32768",
            description = "Audio sample rate the core produces"
        ),
        CoreOptionDef(
            key = "gpsp_frameskip",
            displayName = "Frameskip",
            values = listOf("disabled", "auto", "auto_threshold", "fixed_interval"),
            defaultValue = "disabled",
            description = "Skips frames to keep audio in sync on slow devices",
            valueLabels = mapOf(
                "auto" to "Auto",
                "auto_threshold" to "Auto (Threshold)",
                "fixed_interval" to "Fixed Interval"
            )
        ),
        CoreOptionDef(
            key = "gpsp_frameskip_threshold",
            displayName = "Frameskip Threshold (%)",
            values = listOf(
                "15", "18", "21", "24", "27", "30", "33", "36",
                "39", "42", "45", "48", "51", "54", "57", "60"
            ),
            defaultValue = "33",
            description = "Audio buffer occupancy below which a frame is skipped in threshold mode"
        ),
        CoreOptionDef(
            key = "gpsp_frameskip_interval",
            displayName = "Frameskip Interval",
            values = (0..10).map { it.toString() },
            defaultValue = "1",
            description = "Frames to skip after each drawn frame in fixed interval mode"
        ),
        CoreOptionDef(
            key = "gpsp_color_correction",
            displayName = "Color Correction",
            values = listOf("enabled", "disabled"),
            defaultValue = "disabled",
            description = "Adjusts colours to match the original LCD"
        ),
        CoreOptionDef(
            key = "gpsp_frame_mixing",
            displayName = "Interframe Blending",
            values = listOf("enabled", "disabled"),
            defaultValue = "disabled",
            description = "Blends consecutive frames to mimic LCD ghosting"
        ),
        CoreOptionDef(
            key = "gpsp_turbo_period",
            displayName = "Turbo Button Period",
            values = (4..120).map { it.toString() },
            defaultValue = "4",
            description = "Frames between presses when a turbo button is held"
        )
    )
}
