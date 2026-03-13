package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object VbamManifest : CoreOptionManifest {
    override val coreId = "vbam"
    override val options = listOf(
        CoreOptionDef(
            key = "vbam_usebios",
            displayName = "Use BIOS File if Found",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Uses the official GBA BIOS for more accurate startup and emulation"
        ),
        CoreOptionDef(
            key = "vbam_forceRTCenable",
            displayName = "Force Enable RTC",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Forces the real-time clock on for games that need it but aren't auto-detected"
        ),
        CoreOptionDef(
            key = "vbam_gbHardware",
            displayName = "(GB) Emulated Hardware",
            values = listOf("gbc", "auto", "sgb", "gb", "gba", "sgb2"),
            defaultValue = "gbc",
            description = "Selects which Game Boy hardware variant to emulate"
        ),
        CoreOptionDef(
            key = "vbam_allowcolorizerhack",
            displayName = "(GB) Enable Colorizer Hack",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Applies color to original Game Boy games using predefined palettes"
        ),
        CoreOptionDef(
            key = "vbam_palettes",
            displayName = "(GB) Color Palette",
            values = listOf(
                "standard", "blue sea", "dark knight", "green forest",
                "hot desert", "pink dreams", "wierd colors", "original gameboy",
                "gba sp"
            ),
            defaultValue = "standard",
            description = "Selects the color palette used for original Game Boy games"
        ),
        CoreOptionDef(
            key = "vbam_showborders",
            displayName = "(GB) Show Borders",
            values = listOf("disabled", "enabled", "auto"),
            defaultValue = "disabled",
            description = "Displays Super Game Boy borders around the game screen"
        ),
        CoreOptionDef(
            key = "vbam_gbcoloroption",
            displayName = "(GB) Color Correction",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Adjusts colors to more closely match the original Game Boy Color screen"
        ),
        CoreOptionDef(
            key = "vbam_lcdfilter",
            displayName = "LCD Filter",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Simulates the appearance of a Game Boy LCD screen"
        ),
        CoreOptionDef(
            key = "vbam_interframeblending",
            displayName = "Interframe Blending",
            values = listOf("disabled", "smart", "motion blur"),
            defaultValue = "disabled",
            description = "Blends consecutive frames to simulate LCD ghosting effects"
        ),
        CoreOptionDef(
            key = "vbam_soundinterpolation",
            displayName = "Sound Interpolation",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Smooths audio output to reduce aliasing artifacts"
        ),
        CoreOptionDef(
            key = "vbam_soundfiltering",
            displayName = "Sound Filtering",
            values = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"),
            defaultValue = "5",
            description = "Controls the amount of audio filtering applied to the sound output"
        ),
        CoreOptionDef(
            key = "vbam_turboenable",
            displayName = "Enable Turbo Buttons",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Allows turbo (rapid-fire) functionality on mapped buttons"
        ),
        CoreOptionDef(
            key = "vbam_turbodelay",
            displayName = "Turbo Delay (in frames)",
            values = listOf(
                "1", "2", "3", "4", "5", "6", "7", "8",
                "9", "10", "11", "12", "13", "14", "15"
            ),
            defaultValue = "3",
            description = "Sets how many frames between each turbo button press"
        ),
        CoreOptionDef(
            key = "vbam_solarsensor",
            displayName = "Solar Sensor Level",
            values = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"),
            defaultValue = "0",
            description = "Sets the simulated sunlight level for Boktai solar sensor games"
        ),
        CoreOptionDef(
            key = "vbam_astick_deadzone",
            displayName = "Analog Deadzone (%)",
            values = listOf("0", "5", "10", "15", "20", "25", "30"),
            defaultValue = "15"
        ),
        CoreOptionDef(
            key = "vbam_gyro_sensitivity",
            displayName = "Sensor Sensitivity (Gyroscope) (%)",
            values = listOf(
                "10", "15", "20", "25", "30", "35", "40", "45", "50",
                "55", "60", "65", "70", "75", "80", "85", "90", "95",
                "100", "105", "110", "115", "120"
            ),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "vbam_tilt_sensitivity",
            displayName = "Sensor Sensitivity (Tilt) (%)",
            values = listOf(
                "10", "15", "20", "25", "30", "35", "40", "45", "50",
                "55", "60", "65", "70", "75", "80", "85", "90", "95",
                "100", "105", "110", "115", "120"
            ),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "vbam_swap_astick",
            displayName = "Swap Left/Right Analog",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Swaps the function of the left and right analog sticks"
        ),
        CoreOptionDef(
            key = "vbam_sound_1",
            displayName = "Sound Channel 1",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled"
        ),
        CoreOptionDef(
            key = "vbam_sound_2",
            displayName = "Sound Channel 2",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled"
        ),
        CoreOptionDef(
            key = "vbam_sound_3",
            displayName = "Sound Channel 3",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled"
        ),
        CoreOptionDef(
            key = "vbam_sound_4",
            displayName = "Sound Channel 4",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled"
        ),
        CoreOptionDef(
            key = "vbam_sound_5",
            displayName = "Sound DMA Channel A",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled"
        ),
        CoreOptionDef(
            key = "vbam_sound_6",
            displayName = "Sound DMA Channel B",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled"
        ),
        CoreOptionDef(
            key = "vbam_layer_1",
            displayName = "Show Layer 1",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled"
        ),
        CoreOptionDef(
            key = "vbam_layer_2",
            displayName = "Show Layer 2",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled"
        ),
        CoreOptionDef(
            key = "vbam_layer_3",
            displayName = "Show Layer 3",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled"
        ),
        CoreOptionDef(
            key = "vbam_layer_4",
            displayName = "Show Layer 4",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled"
        ),
        CoreOptionDef(
            key = "vbam_layer_5",
            displayName = "Show Sprite Layer",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled"
        ),
        CoreOptionDef(
            key = "vbam_layer_6",
            displayName = "Show Window Layer 1",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled"
        ),
        CoreOptionDef(
            key = "vbam_layer_7",
            displayName = "Show Window Layer 2",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled"
        ),
        CoreOptionDef(
            key = "vbam_layer_8",
            displayName = "Show Object Window Layer",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled"
        ),
    )
}
