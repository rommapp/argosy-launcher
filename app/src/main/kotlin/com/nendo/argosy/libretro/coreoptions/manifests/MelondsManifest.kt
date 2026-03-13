package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object MelondsManifest : CoreOptionManifest {
    override val coreId = "melonds"
    override val options = listOf(
        // System
        CoreOptionDef(
            key = "melonds_console_mode",
            displayName = "Console Mode",
            values = listOf("DS", "DSi"),
            defaultValue = "DS",
            description = "Selects whether to emulate DS or DSi hardware"
        ),
        CoreOptionDef(
            key = "melonds_boot_directly",
            displayName = "Boot Game Directly",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Skips the DS firmware menu and boots the game immediately"
        ),
        CoreOptionDef(
            key = "melonds_use_fw_settings",
            displayName = "Use Firmware Settings",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Uses name, birthday, and other settings from the firmware file"
        ),
        CoreOptionDef(
            key = "melonds_language",
            displayName = "Language",
            values = listOf("Japanese", "English", "French", "German", "Italian", "Spanish"),
            defaultValue = "English"
        ),
        CoreOptionDef(
            key = "melonds_randomize_mac_address",
            displayName = "Randomize MAC Address",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Generates a random MAC address on each boot for online features"
        ),
        CoreOptionDef(
            key = "melonds_dsi_sdcard",
            displayName = "Enable DSi SD Card",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Emulates an SD card inserted in the DSi for homebrew and DSiWare"
        ),
        // Video
        CoreOptionDef(
            key = "melonds_threaded_renderer",
            displayName = "Threaded Software Renderer",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Runs software rendering on a separate thread for better performance"
        ),
        CoreOptionDef(
            key = "melonds_opengl_renderer",
            displayName = "OpenGL Renderer (Restart)",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Uses hardware-accelerated OpenGL rendering instead of software"
        ),
        CoreOptionDef(
            key = "melonds_opengl_resolution",
            displayName = "OpenGL Internal Resolution",
            values = listOf(
                "1x native (256x192)", "2x native (512x384)", "3x native (768x576)",
                "4x native (1024x768)", "5x native (1280x960)", "6x native (1536x1152)",
                "7x native (1792x1344)", "8x native (2048x1536)"
            ),
            defaultValue = "1x native (256x192)",
            description = "Sets the 3D rendering resolution when using the OpenGL renderer"
        ),
        CoreOptionDef(
            key = "melonds_opengl_better_polygons",
            displayName = "OpenGL Improved Polygon Splitting",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Reduces polygon seam artifacts at higher internal resolutions"
        ),
        CoreOptionDef(
            key = "melonds_opengl_filtering",
            displayName = "OpenGL Filtering",
            values = listOf("nearest", "linear"),
            defaultValue = "nearest",
            description = "Sets the texture filtering method for OpenGL rendering"
        ),
        // Audio
        CoreOptionDef(
            key = "melonds_mic_input",
            displayName = "Microphone Input",
            values = listOf("Blow Noise", "White Noise"),
            defaultValue = "Blow Noise",
            description = "Selects the simulated sound fed to the DS microphone when activated"
        ),
        CoreOptionDef(
            key = "melonds_audio_bitrate",
            displayName = "Audio Bitrate",
            values = listOf("Automatic", "10-bit", "16-bit"),
            defaultValue = "Automatic",
            description = "Sets the audio output bit depth, 10-bit matches original hardware"
        ),
        CoreOptionDef(
            key = "melonds_audio_interpolation",
            displayName = "Audio Interpolation",
            values = listOf("None", "Linear", "Cosine", "Cubic"),
            defaultValue = "None",
            description = "Sets the quality of audio sample interpolation"
        ),
        // Screen
        CoreOptionDef(
            key = "melonds_touch_mode",
            displayName = "Touch Mode",
            values = listOf("Mouse", "Touch", "Joystick", "disabled"),
            defaultValue = "Mouse",
            description = "Selects the input device used for the DS touch screen"
        ),
        CoreOptionDef(
            key = "melonds_swapscreen_mode",
            displayName = "Swap Screen Mode",
            values = listOf("Toggle", "Hold"),
            defaultValue = "Toggle",
            description = "Sets whether screen swap is toggled or only active while held"
        ),
        CoreOptionDef(
            key = "melonds_screen_layout",
            displayName = "Screen Layout",
            values = listOf(
                "Top/Bottom", "Bottom/Top", "Left/Right", "Right/Left",
                "Top Only", "Bottom Only", "Hybrid Top", "Hybrid Bottom"
            ),
            defaultValue = "Top/Bottom",
            description = "Sets the arrangement of the DS top and bottom screens"
        ),
        CoreOptionDef(
            key = "melonds_screen_gap",
            displayName = "Screen Gap",
            values = (0..126).map { it.toString() },
            defaultValue = "0",
            description = "Sets the pixel gap between the top and bottom screens"
        ),
        CoreOptionDef(
            key = "melonds_hybrid_small_screen",
            displayName = "Hybrid Small Screen Mode",
            values = listOf("Bottom", "Top", "Duplicate"),
            defaultValue = "Bottom",
            description = "Selects which screen appears as the small inset in hybrid layout"
        ),
        CoreOptionDef(
            key = "melonds_hybrid_ratio",
            displayName = "Hybrid Ratio (OpenGL Only)",
            values = listOf("2", "3"),
            defaultValue = "2",
            description = "Sets the size ratio between the large and small screen in hybrid layout"
        ),
        // CPU Emulation
        CoreOptionDef(
            key = "melonds_jit_enable",
            displayName = "JIT Enable (Restart)",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Enables JIT dynamic recompilation for faster CPU emulation"
        ),
        CoreOptionDef(
            key = "melonds_jit_block_size",
            displayName = "JIT Block Size",
            values = (1..32).map { it.toString() },
            defaultValue = "32",
            description = "Sets the maximum number of instructions compiled per JIT block"
        ),
        CoreOptionDef(
            key = "melonds_jit_branch_optimisations",
            displayName = "JIT Branch Optimisations",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Enables branch prediction optimizations in the JIT compiler"
        ),
        CoreOptionDef(
            key = "melonds_jit_literal_optimisations",
            displayName = "JIT Literal Optimisations",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Enables literal pool optimizations in the JIT compiler"
        ),
        CoreOptionDef(
            key = "melonds_jit_fast_memory",
            displayName = "JIT Fast Memory",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Skips memory access safety checks in JIT for faster emulation"
        ),
    )
}
