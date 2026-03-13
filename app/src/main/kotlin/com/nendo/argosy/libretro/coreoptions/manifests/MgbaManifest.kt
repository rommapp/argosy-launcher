package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object MgbaManifest : CoreOptionManifest {
    override val coreId = "mgba"
    override val options = listOf(
        CoreOptionDef(
            key = "mgba_gb_model",
            displayName = "Game Boy Model",
            values = listOf(
                "Autodetect", "Game Boy", "Super Game Boy",
                "Game Boy Color", "Super Game Boy Color", "Game Boy Advance"
            ),
            defaultValue = "Autodetect",
            description = "Forces emulation of a specific Game Boy hardware model"
        ),
        CoreOptionDef(
            key = "mgba_use_bios",
            displayName = "Use BIOS File if Found",
            values = listOf("ON", "OFF"),
            defaultValue = "ON",
            description = "Uses the official BIOS file for more accurate startup behavior"
        ),
        CoreOptionDef(
            key = "mgba_skip_bios",
            displayName = "Skip BIOS Intro",
            values = listOf("OFF", "ON"),
            defaultValue = "OFF",
            description = "Skips the boot logo animation when starting a game"
        ),
        CoreOptionDef(
            key = "mgba_sgb_borders",
            displayName = "Use Super Game Boy Borders",
            values = listOf("ON", "OFF"),
            defaultValue = "ON",
            description = "Shows decorative screen borders when running in Super Game Boy mode"
        ),
        CoreOptionDef(
            key = "mgba_color_correction",
            displayName = "Color Correction",
            values = listOf("OFF", "GBA", "GBC", "Auto"),
            defaultValue = "OFF",
            description = "Adjusts colors to more closely match the original hardware screen"
        ),
        CoreOptionDef(
            key = "mgba_interframe_blending",
            displayName = "Interframe Blending",
            values = listOf("OFF", "mix", "mix_smart", "lcd_ghosting", "lcd_ghosting_fast"),
            defaultValue = "OFF",
            description = "Blends consecutive frames to simulate LCD ghosting or reduce flicker"
        ),
        CoreOptionDef(
            key = "mgba_audio_low_pass_filter",
            displayName = "Audio Low-Pass Filter",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Applies a low-pass filter to soften harsh audio"
        ),
        CoreOptionDef(
            key = "mgba_audio_low_pass_range",
            displayName = "Audio Low-Pass Filter Range",
            values = listOf(
                "5", "10", "15", "20", "25", "30", "35", "40", "45",
                "50", "55", "60", "65", "70", "75", "80", "85", "90", "95"
            ),
            defaultValue = "60",
            description = "Sets the cutoff strength of the low-pass audio filter"
        ),
        CoreOptionDef(
            key = "mgba_allow_opposing_directions",
            displayName = "Allow Opposing Directions",
            values = listOf("no", "yes"),
            defaultValue = "no",
            description = "Allows pressing left+right or up+down at the same time"
        ),
        CoreOptionDef(
            key = "mgba_solar_sensor_level",
            displayName = "Solar Sensor Level",
            values = listOf(
                "sensor", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"
            ),
            defaultValue = "0",
            description = "Sets the simulated sunlight level for Boktai solar sensor cartridges"
        ),
        CoreOptionDef(
            key = "mgba_force_gbp",
            displayName = "Game Boy Player Rumble",
            values = listOf("OFF", "ON"),
            defaultValue = "OFF",
            description = "Enables rumble feedback for Game Boy Player compatible games"
        ),
        CoreOptionDef(
            key = "mgba_idle_optimization",
            displayName = "Idle Loop Removal",
            values = listOf("Remove Known", "Detect and Remove", "Don't Remove"),
            defaultValue = "Remove Known",
            description = "Skips CPU idle loops to reduce power usage and improve performance"
        ),
        CoreOptionDef(
            key = "mgba_frameskip",
            displayName = "Frameskip",
            values = listOf("disabled", "auto", "auto_threshold", "fixed_interval"),
            defaultValue = "disabled",
            description = "Selects the frameskip method used to improve performance"
        ),
        CoreOptionDef(
            key = "mgba_frameskip_threshold",
            displayName = "Frameskip Threshold",
            values = listOf(
                "15", "18", "21", "24", "27", "30", "33",
                "36", "39", "42", "45", "48", "51", "54", "57", "60"
            ),
            defaultValue = "33",
            description = "Sets the audio buffer occupancy below which frames will be skipped"
        ),
        CoreOptionDef(
            key = "mgba_frameskip_interval",
            displayName = "Frameskip Interval",
            values = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"),
            defaultValue = "0",
            description = "Sets how many frames to skip between each rendered frame"
        ),
    )
}
