package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object StellaManifest : CoreOptionManifest {
    override val coreId = "stella"
    override val options = listOf(
        CoreOptionDef(
            key = "stella_console",
            displayName = "Console Display",
            values = listOf("auto", "ntsc", "pal", "secam", "ntsc50", "pal60", "secam60"),
            defaultValue = "auto",
            description = "Sets the TV standard and refresh rate for the emulated console"
        ),
        CoreOptionDef(
            key = "stella_palette",
            displayName = "Palette Colors",
            values = listOf("standard", "z26", "user", "custom"),
            defaultValue = "standard",
            description = "Selects the color palette used to render Atari 2600 graphics"
        ),
        CoreOptionDef(
            key = "stella_filter",
            displayName = "TV Effects",
            values = listOf("disabled", "composite", "s-video", "rgb", "badly adjusted"),
            defaultValue = "disabled",
            description = "Applies a video filter that simulates different TV signal types"
        ),
        CoreOptionDef(
            key = "stella_crop_hoverscan",
            displayName = "Crop Horizontal Overscan",
            values = listOf("enabled", "disabled"),
            defaultValue = "disabled",
            description = "Removes the left and right border areas outside the visible screen"
        ),
        CoreOptionDef(
            key = "stella_crop_voverscan",
            displayName = "Crop Vertical Overscan",
            values = listOf(
                "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
                "11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
                "21", "22", "23", "24"
            ),
            defaultValue = "0",
            description = "Sets how many scanlines to crop from the top and bottom edges"
        ),
        CoreOptionDef(
            key = "stella_ntsc_aspect",
            displayName = "NTSC Aspect %",
            values = listOf(
                "par",
                "75", "76", "77", "78", "79", "80", "81", "82", "83", "84",
                "85", "86", "87", "88", "89", "90", "91", "92", "93", "94",
                "95", "96", "97", "98", "99", "100", "101", "102", "103", "104",
                "105", "106", "107", "108", "109", "110", "111", "112", "113", "114",
                "115", "116", "117", "118", "119", "120", "121", "122", "123", "124",
                "125"
            ),
            defaultValue = "par",
            description = "Adjusts the horizontal stretch for NTSC display output"
        ),
        CoreOptionDef(
            key = "stella_pal_aspect",
            displayName = "PAL Aspect %",
            values = listOf(
                "par",
                "75", "76", "77", "78", "79", "80", "81", "82", "83", "84",
                "85", "86", "87", "88", "89", "90", "91", "92", "93", "94",
                "95", "96", "97", "98", "99", "100", "101", "102", "103", "104",
                "105", "106", "107", "108", "109", "110", "111", "112", "113", "114",
                "115", "116", "117", "118", "119", "120", "121", "122", "123", "124",
                "125"
            ),
            defaultValue = "par",
            description = "Adjusts the horizontal stretch for PAL display output"
        ),
        CoreOptionDef(
            key = "stella_stereo",
            displayName = "Stereo Sound",
            values = listOf("auto", "off", "on"),
            defaultValue = "auto",
            description = "Outputs audio in stereo by panning channels left and right"
        ),
        CoreOptionDef(
            key = "stella_phosphor",
            displayName = "Phosphor Mode",
            values = listOf("auto", "off", "on"),
            defaultValue = "auto",
            description = "Simulates CRT phosphor persistence to reduce flicker"
        ),
        CoreOptionDef(
            key = "stella_phosphor_blend",
            displayName = "Phosphor Blend %",
            values = listOf(
                "0", "5", "10", "15", "20", "25", "30", "35", "40", "45", "50",
                "55", "60", "65", "70", "75", "80", "85", "90", "95", "100"
            ),
            defaultValue = "60",
            description = "Sets how strongly the previous frame blends into the current one"
        ),
        CoreOptionDef(
            key = "stella_paddle_mouse_sensitivity",
            displayName = "Paddle Mouse Sensitivity",
            values = listOf(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
                "11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
                "21", "22", "23", "24", "25", "26", "27", "28", "29", "30"
            ),
            defaultValue = "10"
        ),
        CoreOptionDef(
            key = "stella_paddle_joypad_sensitivity",
            displayName = "Paddle Joypad Sensitivity",
            values = listOf(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
                "11", "12", "13", "14", "15", "16", "17", "18", "19", "20"
            ),
            defaultValue = "3"
        ),
        CoreOptionDef(
            key = "stella_paddle_analog_sensitivity",
            displayName = "Paddle Analog Sensitivity",
            values = listOf(
                "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
                "11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
                "21", "22", "23", "24", "25", "26", "27", "28", "29", "30"
            ),
            defaultValue = "20"
        ),
        CoreOptionDef(
            key = "stella_paddle_analog_deadzone",
            displayName = "Paddle Analog Deadzone",
            values = listOf(
                "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
                "11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
                "21", "22", "23", "24", "25", "26", "27", "28", "29", "30"
            ),
            defaultValue = "15"
        ),
        CoreOptionDef(
            key = "stella_paddle_analog_absolute",
            displayName = "Paddle Analog Absolute",
            values = listOf("enabled", "disabled"),
            defaultValue = "disabled",
            description = "Maps analog stick position directly to paddle position instead of velocity"
        ),
        CoreOptionDef(
            key = "stella_lightgun_crosshair",
            displayName = "Lightgun Crosshair",
            values = listOf("enabled", "disabled"),
            defaultValue = "disabled",
            description = "Displays an aiming crosshair when using the light gun"
        ),
        CoreOptionDef(
            key = "stella_reload",
            displayName = "Enable Reload/Next Game",
            values = listOf("off", "on"),
            defaultValue = "off",
            description = "Allows cycling through multi-game cartridges using the reload button"
        ),
    )
}
