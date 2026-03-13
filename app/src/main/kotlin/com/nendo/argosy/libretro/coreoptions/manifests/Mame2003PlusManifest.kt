package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object Mame2003PlusManifest : CoreOptionManifest {
    override val coreId = "mame2003_plus"
    override val options = listOf(
        CoreOptionDef(
            key = "mame2003-plus_four_way_emulation",
            displayName = "4-Way Joystick Emulation on 8-Way Joysticks",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Restricts 8-way joystick input to 4 directions for games that require it"
        ),
        CoreOptionDef(
            key = "mame2003-plus_xy_device",
            displayName = "X-Y Device",
            values = listOf("mouse", "pointer", "lightgun", "disabled"),
            defaultValue = "mouse",
            description = "Selects which input device to use for trackball and dial controls"
        ),
        CoreOptionDef(
            key = "mame2003-plus_crosshair_enabled",
            displayName = "Show Lightgun Crosshairs",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled"
        ),
        CoreOptionDef(
            key = "mame2003-plus_crosshair_appearance",
            displayName = "Lightgun Crosshair Appearance",
            values = listOf("simple", "enhanced"),
            defaultValue = "simple"
        ),
        CoreOptionDef(
            key = "mame2003-plus_skip_disclaimer",
            displayName = "Skip Disclaimer",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Skips the initial MAME disclaimer screen on startup"
        ),
        CoreOptionDef(
            key = "mame2003-plus_skip_warnings",
            displayName = "Skip Warnings",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Skips game-specific warning screens about imperfect emulation"
        ),
        CoreOptionDef(
            key = "mame2003-plus_display_setup",
            displayName = "Display MAME Menu",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Shows the internal MAME configuration menu"
        ),
        CoreOptionDef(
            key = "mame2003-plus_brightness",
            displayName = "Brightness",
            values = listOf(
                "0.2", "0.3", "0.4", "0.5", "0.6", "0.7", "0.8", "0.9",
                "1.0", "1.1", "1.2", "1.3", "1.4", "1.5", "1.6", "1.7",
                "1.8", "1.9", "2.0"
            ),
            defaultValue = "1.0"
        ),
        CoreOptionDef(
            key = "mame2003-plus_gamma",
            displayName = "Gamma Correction",
            values = listOf(
                "0.5", "0.6", "0.7", "0.8", "0.9",
                "1.0", "1.1", "1.2", "1.3", "1.4", "1.5", "1.6", "1.7",
                "1.8", "1.9", "2.0"
            ),
            defaultValue = "1.0"
        ),
        CoreOptionDef(
            key = "mame2003-plus_display_artwork",
            displayName = "Display Artwork",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Shows cabinet artwork bezels and overlays around the game screen"
        ),
        CoreOptionDef(
            key = "mame2003-plus_art_resolution",
            displayName = "Artwork Resolution Multiplier",
            values = listOf("1", "2", "3", "4", "5", "6", "7", "8"),
            defaultValue = "1",
            description = "Increases the rendering resolution of artwork overlays"
        ),
        CoreOptionDef(
            key = "mame2003-plus_art_overlay_opacity",
            displayName = "Artwork Hardcoded Overlay Opacity",
            values = listOf(
                "default", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
                "10", "11", "12", "13", "14", "15", "16", "17", "18", "19",
                "20", "21", "22", "23", "24", "25", "50", "70"
            ),
            defaultValue = "default",
            description = "Controls the transparency level of hardcoded artwork overlays"
        ),
        CoreOptionDef(
            key = "mame2003-plus_neogeo_bios",
            displayName = "Specify Neo Geo BIOS",
            values = listOf(
                "default", "euro", "euro-s1", "us", "us-e", "asia",
                "japan", "japan-s2", "unibios40", "unibios33", "unibios20",
                "unibios13", "unibios11", "unibios10", "debug", "asia-aes"
            ),
            defaultValue = "default",
            description = "Selects which Neo Geo BIOS to use for region and feature control"
        ),
        CoreOptionDef(
            key = "mame2003-plus_stv_bios",
            displayName = "Specify Sega ST-V BIOS",
            values = listOf("default", "japan", "japana", "us", "japan_b", "taiwan", "europe"),
            defaultValue = "default",
            description = "Selects which Sega ST-V arcade BIOS to use"
        ),
        CoreOptionDef(
            key = "mame2003-plus_use_samples",
            displayName = "Use Samples",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Plays pre-recorded audio samples for games with unemulated sound chips"
        ),
        CoreOptionDef(
            key = "mame2003-plus_use_alt_sound",
            displayName = "Use CD Soundtrack",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Replaces the game's original audio with an alternate CD soundtrack"
        ),
        CoreOptionDef(
            key = "mame2003-plus_dialsharexy",
            displayName = "Share 2 Player Dial Controls Across One X/Y Device",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Maps both player dial controls to a single mouse's X and Y axes"
        ),
        CoreOptionDef(
            key = "mame2003-plus_dial_swap_xy",
            displayName = "Swap X and Y Dial Axis",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Swaps the horizontal and vertical axes for dial controls"
        ),
        CoreOptionDef(
            key = "mame2003-plus_tate_mode",
            displayName = "TATE Mode",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Rotates the display for vertical arcade games on rotated monitors"
        ),
        CoreOptionDef(
            key = "mame2003-plus_vector_resolution",
            displayName = "Vector Resolution",
            values = listOf(
                "640x480", "1024x768", "1280x960", "1440x1080",
                "1600x1200", "1707x1280", "original"
            ),
            defaultValue = "1024x768",
            description = "Sets the rendering resolution for vector graphics games"
        ),
        CoreOptionDef(
            key = "mame2003-plus_vector_antialias",
            displayName = "Vector Antialiasing",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Smooths the edges of vector lines to reduce jaggedness"
        ),
        CoreOptionDef(
            key = "mame2003-plus_vector_beam_width",
            displayName = "Vector Beam Width",
            values = listOf(
                "1", "1.2", "1.4", "1.6", "1.8", "2", "2.5",
                "3", "4", "5", "6", "7", "8", "9", "10", "11", "12"
            ),
            defaultValue = "2",
            description = "Sets the thickness of vector lines"
        ),
        CoreOptionDef(
            key = "mame2003-plus_vector_translucency",
            displayName = "Vector Translucency",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Enables translucent rendering of overlapping vector lines"
        ),
        CoreOptionDef(
            key = "mame2003-plus_vector_flicker",
            displayName = "Vector Flicker",
            values = listOf("0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100"),
            defaultValue = "20",
            description = "Simulates the flicker of a real vector CRT display"
        ),
        CoreOptionDef(
            key = "mame2003-plus_vector_intensity",
            displayName = "Vector Intensity",
            values = listOf("0.5", "1", "1.5", "2", "2.5", "3"),
            defaultValue = "1.5",
            description = "Controls the brightness of vector lines"
        ),
        CoreOptionDef(
            key = "mame2003-plus_nvram_bootstraps",
            displayName = "NVRAM Bootstraps",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Initializes NVRAM with defaults so games work without manual setup"
        ),
        CoreOptionDef(
            key = "mame2003-plus_sample_rate",
            displayName = "Sample Rate",
            values = listOf("8000", "11025", "22050", "30000", "44100", "48000"),
            defaultValue = "48000"
        ),
        CoreOptionDef(
            key = "mame2003-plus_input_interface",
            displayName = "Input Interface",
            values = listOf("simultaneous", "retropad", "keyboard"),
            defaultValue = "simultaneous",
            description = "Selects which input devices the core accepts"
        ),
        CoreOptionDef(
            key = "mame2003-plus_mame_remapping",
            displayName = "Legacy Remapping",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Enables MAME's built-in input remapping system"
        ),
        CoreOptionDef(
            key = "mame2003-plus_frameskip",
            displayName = "Frameskip",
            values = listOf(
                "disabled", "1", "2", "3", "4", "5", "6", "7", "8", "9",
                "10", "11", "auto", "auto_aggressive", "auto_max"
            ),
            defaultValue = "disabled",
            description = "Skips rendering frames to improve performance on slower devices"
        ),
        CoreOptionDef(
            key = "mame2003-plus_core_sys_subfolder",
            displayName = "Locate System Files Within a Subfolder",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Looks for system files in a core-specific subfolder"
        ),
        CoreOptionDef(
            key = "mame2003-plus_core_save_subfolder",
            displayName = "Locate Save Files Within a Subfolder",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Stores save files in a core-specific subfolder"
        ),
        CoreOptionDef(
            key = "mame2003-plus_autosave_hiscore",
            displayName = "Autosave Hiscore",
            values = listOf("default", "recursively", "disabled"),
            defaultValue = "default",
            description = "Automatically saves high scores when exiting a game"
        ),
        CoreOptionDef(
            key = "mame2003-plus_cheat_input_ports",
            displayName = "Dip Switch/Cheat Input Ports",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Exposes dip switch and cheat input ports for configuration"
        ),
        CoreOptionDef(
            key = "mame2003-plus_digital_joy_centering",
            displayName = "Center Joystick Axis for Digital Controls",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Snaps the analog axis to center when using digital input"
        ),
        CoreOptionDef(
            key = "mame2003-plus_cpu_clock_scale",
            displayName = "CPU Clock Scale",
            values = listOf(
                "default", "25", "30", "35", "40", "45", "50", "55", "60",
                "65", "70", "75", "80", "85", "90", "95", "105", "110",
                "115", "120", "125", "200", "250", "300"
            ),
            defaultValue = "default",
            description = "Scales the emulated CPU clock speed to adjust game difficulty or speed"
        ),
        CoreOptionDef(
            key = "mame2003-plus_override_ad_stick",
            displayName = "Use Lightgun as an Analog Stick",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Remaps lightgun input to act as an analog stick"
        ),
        CoreOptionDef(
            key = "mame2003-plus_input_toggle",
            displayName = "Allow Input Button to Act as a Toggle Switch",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Lets certain input buttons toggle state instead of requiring hold"
        ),
    )
}
