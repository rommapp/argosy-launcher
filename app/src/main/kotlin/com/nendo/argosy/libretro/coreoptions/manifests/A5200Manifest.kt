package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object A5200Manifest : CoreOptionManifest {
    override val coreId = "a5200"
    override val options = listOf(
        CoreOptionDef(
            key = "a5200_bios",
            displayName = "BIOS (Restart)",
            values = listOf("official", "internal"),
            defaultValue = "official",
            description = "Selects whether to use the official Atari BIOS or a built-in replacement"
        ),
        CoreOptionDef(
            key = "a5200_mix_frames",
            displayName = "Interframe Blending",
            values = listOf("disabled", "mix", "ghost_65", "ghost_75", "ghost_85", "ghost_95"),
            defaultValue = "disabled",
            description = "Blends consecutive frames to simulate CRT persistence or reduce flicker"
        ),
        CoreOptionDef(
            key = "a5200_artifacting_mode",
            displayName = "Hi-Res Artifacting Mode",
            values = listOf("none", "blue/brown 1", "blue/brown 2", "GTIA", "CTIA"),
            defaultValue = "none",
            description = "Simulates NTSC color artifacting used by some games for extra colors"
        ),
        CoreOptionDef(
            key = "a5200_enable_new_pokey",
            displayName = "High Fidelity POKEY (Restart)",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Uses a more accurate POKEY sound chip emulation at higher CPU cost"
        ),
        CoreOptionDef(
            key = "a5200_low_pass_filter",
            displayName = "Audio Filter",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Applies a low-pass filter to soften harsh audio"
        ),
        CoreOptionDef(
            key = "a5200_low_pass_range",
            displayName = "Audio Filter Level",
            values = listOf(
                "5", "10", "15", "20", "25", "30", "35", "40", "45",
                "50", "55", "60", "65", "70", "75", "80", "85", "90", "95"
            ),
            defaultValue = "60",
            description = "Sets the cutoff strength of the low-pass audio filter"
        ),
        CoreOptionDef(
            key = "a5200_input_hack",
            displayName = "Controller Hacks",
            values = listOf("disabled", "dual_stick", "swap_ports"),
            defaultValue = "disabled",
            description = "Applies input workarounds like dual-stick mode or swapping controller ports"
        ),
        CoreOptionDef(
            key = "a5200_pause_is_reset",
            displayName = "Pause Acts as Reset",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Makes the pause button perform a game reset instead of pausing"
        ),
        CoreOptionDef(
            key = "a5200_digital_sensitivity",
            displayName = "Digital Joystick Sensitivity",
            values = listOf(
                "auto", "5", "7", "10", "12", "15", "17", "20", "22", "25",
                "27", "30", "32", "35", "37", "40", "42", "45", "47", "50",
                "52", "55", "57", "60", "62", "65", "67", "70", "72", "75",
                "77", "80", "82", "85", "87", "90", "92", "95", "97", "100"
            ),
            defaultValue = "auto"
        ),
        CoreOptionDef(
            key = "a5200_analog_sensitivity",
            displayName = "Analog Joystick Sensitivity",
            values = listOf(
                "auto", "5", "7", "10", "12", "15", "17", "20", "22", "25",
                "27", "30", "32", "35", "37", "40", "42", "45", "47", "50",
                "52", "55", "57", "60", "62", "65", "67", "70", "72", "75",
                "77", "80", "82", "85", "87", "90", "92", "95", "97", "100"
            ),
            defaultValue = "auto"
        ),
        CoreOptionDef(
            key = "a5200_analog_response",
            displayName = "Analog Joystick Response",
            values = listOf("linear", "quadratic"),
            defaultValue = "linear",
            description = "Sets the response curve for analog stick input"
        ),
        CoreOptionDef(
            key = "a5200_analog_deadzone",
            displayName = "Analog Joystick Deadzone",
            values = listOf(
                "0", "3", "5", "7", "10", "13", "15", "17", "20", "23", "25", "27", "30"
            ),
            defaultValue = "15"
        ),
        CoreOptionDef(
            key = "a5200_analog_device",
            displayName = "Analog Device",
            values = listOf("analog_stick", "mouse"),
            defaultValue = "analog_stick",
            description = "Selects whether analog input comes from a stick or mouse"
        ),
    )
}
