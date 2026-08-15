package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object AzaharManifest : CoreOptionManifest {
    override val coreId = "azahar"
    override val options = listOf(
        CoreOptionDef(
            key = "citra_use_cpu_jit",
            displayName = "CPU JIT",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            coreDefault = "enabled",
            description = "Compiles 3DS CPU code as it runs. Turning this off is far slower"
        ),
        CoreOptionDef(
            key = "citra_cpu_clock_percentage",
            displayName = "CPU Clock Speed",
            values = listOf(
                "25", "50", "75", "100", "125", "150", "175", "200",
                "225", "250", "275", "300", "325", "350", "375", "400"
            ),
            defaultValue = "100",
            coreDefault = "100",
            description = "Emulated CPU speed as a percentage of the real console",
            valueLabels = mapOf(
                "25" to "25%", "50" to "50%", "75" to "75%", "100" to "100%",
                "125" to "125%", "150" to "150%", "175" to "175%", "200" to "200%",
                "225" to "225%", "250" to "250%", "275" to "275%", "300" to "300%",
                "325" to "325%", "350" to "350%", "375" to "375%", "400" to "400%"
            )
        ),
        CoreOptionDef(
            key = "citra_is_new_3ds",
            displayName = "System Model",
            values = listOf("New 3DS", "Old 3DS"),
            defaultValue = "New 3DS",
            coreDefault = "New 3DS",
            description = "New 3DS has more CPU power and memory, and some games require it",
            valueLabels = mapOf("Old 3DS" to "Original 3DS")
        ),
        CoreOptionDef(
            key = "citra_region_value",
            displayName = "System Region",
            values = listOf("Auto", "Japan", "USA", "Europe", "Australia", "China", "Korea", "Taiwan"),
            defaultValue = "Auto",
            coreDefault = "Auto",
            description = "Region-locked games need a matching region"
        ),
        CoreOptionDef(
            key = "citra_language_value",
            displayName = "System Language",
            values = listOf(
                "English", "Japanese", "French", "Spanish", "German", "Italian",
                "Dutch", "Portuguese", "Russian", "Korean",
                "Traditional Chinese", "Simplified Chinese"
            ),
            defaultValue = "English",
            coreDefault = "English",
            description = "Sets in-game text language where the game supports it"
        ),
        CoreOptionDef(
            key = "citra_audio_emulation",
            displayName = "Audio Emulation",
            values = listOf("hle", "lle", "lle_multithread"),
            defaultValue = "hle",
            coreDefault = "hle",
            description = "HLE is fast, LLE is accurate and much heavier",
            valueLabels = mapOf(
                "hle" to "HLE (Fast)",
                "lle" to "LLE (Accurate)",
                "lle_multithread" to "LLE Multithreaded"
            )
        ),
        CoreOptionDef(
            key = "citra_input_type",
            displayName = "Microphone Input",
            values = listOf("auto", "none", "static_noise", "frontend"),
            defaultValue = "auto",
            coreDefault = "auto",
            description = "How microphone input is fed to games that ask for it",
            valueLabels = mapOf(
                "auto" to "Auto",
                "none" to "None",
                "static_noise" to "Static Noise",
                "frontend" to "Frontend"
            )
        ),
        CoreOptionDef(
            key = "citra_graphics_api",
            displayName = "Graphics API",
            values = listOf("Auto", "Vulkan", "OpenGL", "Software"),
            defaultValue = "OpenGL",
            coreDefault = "Auto",
            description = "OpenGL is the only backend the built-in player can present"
        ),
        CoreOptionDef(
            key = "citra_use_hw_shader",
            displayName = "Hardware Shaders",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            coreDefault = "enabled",
            description = "Runs 3DS shaders on the GPU. Turning this off is far slower"
        ),
        CoreOptionDef(
            key = "citra_use_shader_jit",
            displayName = "Shader JIT",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            coreDefault = "enabled",
            description = "Compiles shaders as they run. Turn off if a game renders wrong"
        ),
        CoreOptionDef(
            key = "citra_shaders_accurate_mul",
            displayName = "Accurate Multiplication",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            coreDefault = "enabled",
            description = "Fixes lighting and shadow errors at some cost to speed"
        ),
        CoreOptionDef(
            key = "citra_use_disk_shader_cache",
            displayName = "Shader Cache",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            coreDefault = "enabled",
            description = "Keeps compiled shaders on disk so later runs load faster"
        ),
        CoreOptionDef(
            key = "citra_resolution_factor",
            displayName = "Internal Resolution",
            values = (1..18).map { it.toString() },
            defaultValue = "1",
            coreDefault = "1",
            description = "Renders above native 400x240. Costs performance quickly",
            valueLabels = (1..18).associate { it.toString() to "${it}x" }
        ),
        CoreOptionDef(
            key = "citra_texture_filter",
            displayName = "Texture Filter",
            values = listOf("none", "Anime4K Ultrafast", "Bicubic", "ScaleForce", "MMPX"),
            defaultValue = "none",
            coreDefault = "none",
            description = "Smooths or sharpens game textures",
            valueLabels = mapOf("none" to "None")
        ),
        CoreOptionDef(
            key = "citra_texture_sampling",
            displayName = "Texture Sampling",
            values = listOf("GameControlled", "NearestNeighbor", "Linear"),
            defaultValue = "GameControlled",
            coreDefault = "GameControlled",
            valueLabels = mapOf(
                "GameControlled" to "Game Controlled",
                "NearestNeighbor" to "Nearest Neighbor",
                "Linear" to "Linear"
            )
        ),
        CoreOptionDef(
            key = "citra_custom_textures",
            displayName = "Custom Textures",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            coreDefault = "disabled",
            description = "Loads replacement texture packs from disk"
        ),
        CoreOptionDef(
            key = "citra_dump_textures",
            displayName = "Dump Textures",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            coreDefault = "disabled",
            description = "Writes game textures to disk for building texture packs"
        ),
        CoreOptionDef(
            key = "citra_layout_option",
            displayName = "Screen Layout",
            values = listOf("default", "single_screen", "large_screen", "side_by_side"),
            defaultValue = "default",
            coreDefault = "default",
            description = "How the two 3DS screens are arranged in the picture",
            valueLabels = mapOf(
                "default" to "Top and Bottom",
                "single_screen" to "Single Screen",
                "large_screen" to "Large and Small",
                "side_by_side" to "Side by Side"
            )
        ),
        CoreOptionDef(
            key = "citra_swap_screen",
            displayName = "Prominent Screen",
            values = listOf("Top", "Bottom"),
            defaultValue = "Top",
            coreDefault = "Top",
            description = "Which screen gets the space in single and large screen layouts",
            valueLabels = mapOf("Top" to "Top Screen", "Bottom" to "Bottom Screen")
        ),
        CoreOptionDef(
            key = "citra_swap_screen_mode",
            displayName = "Screen Swap Mode",
            values = listOf("Toggle", "Hold"),
            defaultValue = "Toggle",
            coreDefault = "Toggle",
            description = "Whether the swap button toggles the screens or swaps while held"
        ),
        CoreOptionDef(
            key = "citra_large_screen_proportion",
            displayName = "Large Screen Proportion",
            values = listOf(
                "1.00", "1.25", "1.50", "1.75", "2.00", "2.25", "2.50",
                "2.75", "3.00", "3.25", "3.50", "3.75", "4.00", "4.25",
                "4.50", "4.75", "5.00", "5.25", "5.50", "5.75", "6.00"
            ),
            defaultValue = "4.00",
            coreDefault = "4.00",
            description = "How much bigger the main screen is in the Large and Small layout"
        ),
        CoreOptionDef(
            key = "citra_use_virtual_sd",
            displayName = "Virtual SD Card",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            coreDefault = "enabled",
            description = "Needed by homebrew and by games that write to the SD card"
        ),
        CoreOptionDef(
            key = "citra_use_libretro_save_path",
            displayName = "Save Location",
            values = listOf("LibRetro Default", "Azahar Default"),
            defaultValue = "LibRetro Default",
            coreDefault = "LibRetro Default",
            description = "Where save data and system files are kept"
        ),
        CoreOptionDef(
            key = "citra_analog_function",
            displayName = "Right Stick Function",
            values = listOf("c_stick_and_touchscreen", "touchscreen_pointer", "c_stick"),
            defaultValue = "c_stick_and_touchscreen",
            coreDefault = "c_stick_and_touchscreen",
            description = "The touchscreen pointer is the only way to tap without a touchscreen",
            valueLabels = mapOf(
                "c_stick_and_touchscreen" to "C-Stick and Pointer",
                "touchscreen_pointer" to "Pointer Only",
                "c_stick" to "C-Stick Only"
            )
        ),
        CoreOptionDef(
            key = "citra_analog_deadzone",
            displayName = "Analog Deadzone",
            values = listOf("0", "5", "10", "15", "20", "25", "30", "35"),
            defaultValue = "15",
            coreDefault = "15",
            description = "Ignores small stick movement so a worn stick does not drift",
            valueLabels = mapOf(
                "0" to "0%", "5" to "5%", "10" to "10%", "15" to "15%",
                "20" to "20%", "25" to "25%", "30" to "30%", "35" to "35%"
            )
        ),
        CoreOptionDef(
            key = "citra_enable_mouse_touchscreen",
            displayName = "Mouse Touchscreen",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            coreDefault = "enabled",
            description = "Lets a connected mouse drive the 3DS touchscreen"
        ),
        CoreOptionDef(
            key = "citra_enable_touch_touchscreen",
            displayName = "Touch Support",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            coreDefault = "enabled",
            description = "Lets a tap on the screen drive the 3DS touchscreen"
        ),
        CoreOptionDef(
            key = "citra_enable_touch_pointer_timeout",
            displayName = "Hide Pointer When Idle",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            coreDefault = "enabled",
            description = "Fades the touchscreen pointer out after a spell of no input"
        ),
        CoreOptionDef(
            key = "citra_enable_motion",
            displayName = "Motion Controls",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            coreDefault = "enabled",
            description = "Feeds gyroscope and accelerometer readings to games that use them"
        ),
        CoreOptionDef(
            key = "citra_motion_sensitivity",
            displayName = "Motion Sensitivity",
            values = listOf("0.1", "0.25", "0.5", "0.75", "1.0", "1.25", "1.5", "2.0"),
            defaultValue = "1.0",
            coreDefault = "1.0",
            valueLabels = mapOf(
                "0.1" to "10%", "0.25" to "25%", "0.5" to "50%", "0.75" to "75%",
                "1.0" to "100%", "1.25" to "125%", "1.5" to "150%", "2.0" to "200%"
            )
        )
    )
}
