package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object ViceX64Manifest : CoreOptionManifest {
    override val coreId = "vice_x64"
    override val options = listOf(
        CoreOptionDef(
            key = "vice_c64_model",
            displayName = "Model",
            values = listOf(
                "C64 PAL auto", "C64 NTSC auto", "C64C PAL auto", "C64C NTSC auto",
                "C64 PAL", "C64 NTSC", "C64C PAL", "C64C NTSC",
                "C64SX PAL", "C64SX NTSC", "PET64 PAL", "PET64 NTSC",
                "C64 GS PAL", "C64 JAP NTSC"
            ),
            defaultValue = "C64 PAL auto",
            description = "Selects the C64 hardware model and video standard to emulate"
        ),
        CoreOptionDef(
            key = "vice_jiffydos",
            displayName = "JiffyDOS",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables the JiffyDOS speed-enhanced disk operating system"
        ),
        CoreOptionDef(
            key = "vice_ram_expansion_unit",
            displayName = "RAM Expansion Unit",
            values = listOf("none", "128kB", "256kB", "512kB", "1024kB", "2048kB", "4096kB", "16384kB"),
            defaultValue = "none",
            description = "Attaches a RAM expansion unit with the selected capacity"
        ),
        CoreOptionDef(
            key = "vice_printer",
            displayName = "Printer",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables the emulated printer output device"
        ),
        CoreOptionDef(
            key = "vice_read_vicerc",
            displayName = "Read 'vicerc'",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Loads settings from the vicerc configuration file"
        ),
        CoreOptionDef(
            key = "vice_reset",
            displayName = "Reset Type",
            values = listOf("autostart", "soft", "hard", "freeze"),
            defaultValue = "autostart",
            description = "Selects which reset method to use when restarting content"
        ),
        CoreOptionDef(
            key = "vice_autoloadwarp",
            displayName = "Automatic Load Warp",
            values = listOf("disabled", "enabled", "mute", "disk", "disk_mute", "tape", "tape_mute"),
            defaultValue = "disabled",
            description = "Activates warp speed during disk or tape loading"
        ),
        CoreOptionDef(
            key = "vice_warp_boost",
            displayName = "Warp Boost",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables additional warp speed optimizations"
        ),
        CoreOptionDef(
            key = "vice_autostart",
            displayName = "Autostart",
            values = listOf("disabled", "enabled", "warp"),
            defaultValue = "enabled",
            description = "Automatically runs loaded content on startup"
        ),
        CoreOptionDef(
            key = "vice_drive_true_emulation",
            displayName = "True Drive Emulation",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Emulates disk drive hardware at cycle-exact level for compatibility"
        ),
        CoreOptionDef(
            key = "vice_virtual_device_traps",
            displayName = "Virtual Device Traps",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables fast I/O traps that bypass true drive emulation"
        ),
        CoreOptionDef(
            key = "vice_floppy_multidrive",
            displayName = "Floppy MultiDrive",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables multiple floppy drives for multi-disk programs"
        ),
        CoreOptionDef(
            key = "vice_floppy_write_protection",
            displayName = "Floppy Write Protection",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Write-protects all inserted floppy disk images"
        ),
        CoreOptionDef(
            key = "vice_easyflash_write_protection",
            displayName = "EasyFlash Write Protection",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Write-protects EasyFlash cartridge images"
        ),
        CoreOptionDef(
            key = "vice_work_disk",
            displayName = "Global Work Disk",
            values = listOf("disabled", "8_d64", "9_d64", "8_d71", "9_d71", "8_d81", "9_d81", "8_fs", "9_fs"),
            defaultValue = "disabled",
            description = "Attaches a work disk for saving data across sessions"
        ),
        CoreOptionDef(
            key = "vice_video_options_display",
            displayName = "Show Video Options",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Reveals additional video configuration options"
        ),
        CoreOptionDef(
            key = "vice_aspect_ratio",
            displayName = "Pixel Aspect Ratio",
            values = listOf("auto", "pal", "ntsc", "raw"),
            defaultValue = "auto"
        ),
        CoreOptionDef(
            key = "vice_crop",
            displayName = "Crop",
            values = listOf("disabled", "small", "medium", "maximum", "auto", "auto_disable", "manual"),
            defaultValue = "disabled",
            description = "Removes border areas from the display"
        ),
        CoreOptionDef(
            key = "vice_crop_delay",
            displayName = "Automatic Crop Delay",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Adds a delay before automatic crop adjustments to prevent flicker"
        ),
        CoreOptionDef(
            key = "vice_crop_mode",
            displayName = "Crop Mode",
            values = listOf("both", "horizontal", "vertical", "16:9", "16:10", "4:3", "5:4"),
            defaultValue = "both",
            description = "Selects which edges or aspect ratio to crop to"
        ),
        CoreOptionDef(
            key = "vice_gfx_colors",
            displayName = "Color Depth",
            values = listOf("16bit", "24bit"),
            defaultValue = "24bit"
        ),
        CoreOptionDef(
            key = "vice_vicii_filter",
            displayName = "VIC-II Filter",
            values = listOf("disabled", "enabled_noblur", "enabled_lowblur", "enabled_medblur", "enabled"),
            defaultValue = "disabled",
            description = "Applies CRT-style filtering to the VIC-II video output"
        ),
        CoreOptionDef(
            key = "vice_external_palette",
            displayName = "VIC-II Color Palette",
            values = listOf(
                "default", "c64hq", "c64s", "ccs64", "cjam", "colodore",
                "community-colors", "deekay", "frodo", "godot", "lemon64",
                "palette", "palette_6569R1_v1r", "palette_6569R5_v1r",
                "palette_8565R2_v1r", "palette_C64_amber", "palette_C64_cyan",
                "palette_C64_green", "pc64", "pepto-pal", "pepto-palold",
                "pepto-ntsc", "pepto-ntsc-sony", "pixcen", "ptoing", "rgb",
                "the64", "vice"
            ),
            defaultValue = "default",
            description = "Selects a color palette to match different C64 hardware revisions"
        ),
        CoreOptionDef(
            key = "vice_vkbd_theme",
            displayName = "Virtual KBD Theme",
            values = listOf(
                "auto", "auto_outline", "brown", "brown_outline",
                "beige", "beige_outline", "dark", "dark_outline",
                "light", "light_outline"
            ),
            defaultValue = "auto"
        ),
        CoreOptionDef(
            key = "vice_vkbd_transparency",
            displayName = "Virtual KBD Transparency",
            values = listOf("0%", "25%", "50%", "75%", "100%"),
            defaultValue = "25%"
        ),
        CoreOptionDef(
            key = "vice_vkbd_dimming",
            displayName = "Virtual KBD Dimming",
            values = listOf("0%", "25%", "50%", "75%", "100%"),
            defaultValue = "25%"
        ),
        CoreOptionDef(
            key = "vice_statusbar",
            displayName = "Statusbar Mode",
            values = listOf(
                "bottom", "bottom_minimal", "bottom_basic", "bottom_basic_minimal",
                "top", "top_minimal", "top_basic", "top_basic_minimal"
            ),
            defaultValue = "bottom"
        ),
        CoreOptionDef(
            key = "vice_statusbar_startup",
            displayName = "Statusbar Startup",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Shows the statusbar when the core first starts"
        ),
        CoreOptionDef(
            key = "vice_statusbar_messages",
            displayName = "Statusbar Messages",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Shows emulator status messages on the statusbar"
        ),
        CoreOptionDef(
            key = "vice_joyport_pointer_color",
            displayName = "Light Pen/Gun Pointer Color",
            values = listOf("disabled", "black", "white", "red", "green", "blue", "yellow", "purple"),
            defaultValue = "blue"
        ),
        CoreOptionDef(
            key = "vice_audio_options_display",
            displayName = "Show Audio Options",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Reveals additional audio configuration options"
        ),
        CoreOptionDef(
            key = "vice_drive_sound_emulation",
            displayName = "Drive Sound Emulation",
            values = listOf(
                "disabled", "5%", "10%", "15%", "20%", "25%", "30%", "35%",
                "40%", "45%", "50%", "55%", "60%", "65%", "70%", "75%",
                "80%", "85%", "90%", "95%", "100%"
            ),
            defaultValue = "20%",
            description = "Plays floppy drive mechanical sounds at the selected volume"
        ),
        CoreOptionDef(
            key = "vice_audio_leak_emulation",
            displayName = "Audio Leak Emulation",
            values = listOf("disabled", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"),
            defaultValue = "disabled",
            description = "Simulates audio signal leakage between channels"
        ),
        CoreOptionDef(
            key = "vice_sid_engine",
            displayName = "SID Engine",
            values = listOf("FastSID", "ReSID", "ReSID-FP"),
            defaultValue = "ReSID",
            description = "Selects the sound chip emulation engine for audio quality vs performance"
        ),
        CoreOptionDef(
            key = "vice_sid_model",
            displayName = "SID Model",
            values = listOf("default", "6581", "8580", "8580RD"),
            defaultValue = "default",
            description = "Selects the SID chip revision to emulate, each with a distinct sound"
        ),
        CoreOptionDef(
            key = "vice_sid_extra",
            displayName = "SID Extra",
            values = listOf("disabled", "0xd420", "0xd500", "0xde00", "0xdf00"),
            defaultValue = "disabled",
            description = "Enables a second SID chip at the specified address for stereo music"
        ),
        CoreOptionDef(
            key = "vice_resid_sampling",
            displayName = "ReSID Sampling",
            values = listOf("fast", "interpolation", "fast resampling", "resampling"),
            defaultValue = "resampling",
            description = "Selects the ReSID resampling method for audio quality vs performance"
        ),
        CoreOptionDef(
            key = "vice_resid_passband",
            displayName = "ReSID Filter Passband",
            values = listOf("0", "10", "20", "30", "40", "50", "60", "70", "80", "90"),
            defaultValue = "90",
            description = "Adjusts the ReSID resampling filter passband percentage"
        ),
        CoreOptionDef(
            key = "vice_resid_gain",
            displayName = "ReSID Filter Gain",
            values = listOf("90", "91", "92", "93", "94", "95", "96", "97", "98", "99", "100"),
            defaultValue = "97",
            description = "Adjusts the ReSID filter output gain percentage"
        ),
        CoreOptionDef(
            key = "vice_resid_filterbias",
            displayName = "ReSID Filter 6581 Bias",
            values = listOf(
                "-5000", "-4500", "-4000", "-3500", "-3000", "-2500", "-2000",
                "-1500", "-1000", "-500", "0", "500", "1000", "1500", "2000",
                "2500", "3000", "3500", "4000", "4500", "5000"
            ),
            defaultValue = "500",
            description = "Fine-tunes the 6581 SID filter characteristics"
        ),
        CoreOptionDef(
            key = "vice_resid_8580filterbias",
            displayName = "ReSID Filter 8580 Bias",
            values = listOf(
                "-5000", "-4500", "-4000", "-3500", "-3000", "-2500", "-2000",
                "-1500", "-1000", "-500", "0", "500", "1000", "1500", "2000",
                "2500", "3000", "3500", "4000", "4500", "5000"
            ),
            defaultValue = "0",
            description = "Fine-tunes the 8580 SID filter characteristics"
        ),
        CoreOptionDef(
            key = "vice_sfx_sound_expander",
            displayName = "SFX Sound Expander",
            values = listOf("disabled", "3526", "3812"),
            defaultValue = "disabled",
            description = "Enables the SFX Sound Expander FM synthesis add-on"
        ),
        CoreOptionDef(
            key = "vice_sound_sample_rate",
            displayName = "Sample Rate",
            values = listOf("22050", "44100", "48000", "96000"),
            defaultValue = "48000"
        ),
        CoreOptionDef(
            key = "vice_analogmouse",
            displayName = "Analog Stick Mouse",
            values = listOf("disabled", "left", "right", "both"),
            defaultValue = "left",
            description = "Maps analog sticks to mouse movement"
        ),
        CoreOptionDef(
            key = "vice_analogmouse_deadzone",
            displayName = "Analog Stick Mouse Deadzone",
            values = listOf("0", "5", "10", "15", "20", "25", "30", "35", "40", "45", "50"),
            defaultValue = "20"
        ),
        CoreOptionDef(
            key = "vice_analogmouse_speed",
            displayName = "Left Analog Stick Mouse Speed",
            values = listOf(
                "0.1", "0.2", "0.3", "0.4", "0.5", "0.6", "0.7", "0.8", "0.9",
                "1.0", "1.1", "1.2", "1.3", "1.4", "1.5", "1.6", "1.7", "1.8",
                "1.9", "2.0", "2.1", "2.2", "2.3", "2.4", "2.5", "2.6", "2.7",
                "2.8", "2.9", "3.0"
            ),
            defaultValue = "1.0"
        ),
        CoreOptionDef(
            key = "vice_analogmouse_speed_right",
            displayName = "Right Analog Stick Mouse Speed",
            values = listOf(
                "0.1", "0.2", "0.3", "0.4", "0.5", "0.6", "0.7", "0.8", "0.9",
                "1.0", "1.1", "1.2", "1.3", "1.4", "1.5", "1.6", "1.7", "1.8",
                "1.9", "2.0", "2.1", "2.2", "2.3", "2.4", "2.5", "2.6", "2.7",
                "2.8", "2.9", "3.0"
            ),
            defaultValue = "1.0"
        ),
        CoreOptionDef(
            key = "vice_dpadmouse_speed",
            displayName = "D-Pad Mouse Speed",
            values = listOf(
                "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
                "10", "11", "12", "13", "14", "15", "16", "17", "18"
            ),
            defaultValue = "6"
        ),
        CoreOptionDef(
            key = "vice_mouse_speed",
            displayName = "Mouse Speed",
            values = listOf(
                "10", "20", "30", "40", "50", "60", "70", "80", "90", "100",
                "110", "120", "130", "140", "150", "160", "170", "180", "190",
                "200", "210", "220", "230", "240", "250", "260", "270", "280",
                "290", "300"
            ),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "vice_physical_keyboard_pass_through",
            displayName = "Keyboard Pass-through",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Passes physical keyboard input directly to the emulated machine"
        ),
        CoreOptionDef(
            key = "vice_datasette_hotkeys",
            displayName = "Datasette Hotkeys",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables hotkey shortcuts for datasette tape deck controls"
        ),
        CoreOptionDef(
            key = "vice_keyrah_keypad_mappings",
            displayName = "Keyrah Keypad Mappings",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables Keyrah keyboard adapter numpad mappings"
        ),
        CoreOptionDef(
            key = "vice_keyboard_keymap",
            displayName = "Keyboard Keymap",
            values = listOf("positional", "symbolic", "positional-user", "symbolic-user"),
            defaultValue = "positional",
            description = "Selects how physical keyboard keys map to the C64 keyboard"
        ),
        CoreOptionDef(
            key = "vice_turbo_fire",
            displayName = "Turbo Fire",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables rapid-fire auto-repeat on the selected button"
        ),
        CoreOptionDef(
            key = "vice_turbo_fire_button",
            displayName = "Turbo Button",
            values = listOf("B", "A", "Y", "X", "L", "R", "L2", "R2"),
            defaultValue = "B",
            description = "Selects which gamepad button gets turbo fire functionality"
        ),
        CoreOptionDef(
            key = "vice_turbo_pulse",
            displayName = "Turbo Pulse",
            values = listOf("2", "4", "6", "8", "10", "12"),
            defaultValue = "6",
            description = "Sets the number of frames between each turbo fire press"
        ),
        CoreOptionDef(
            key = "vice_userport_joytype",
            displayName = "Userport Joystick Adapter",
            values = listOf("disabled", "CGA", "HIT", "Kingsoft", "Starbyte", "Hummer", "OEM", "PET"),
            defaultValue = "disabled",
            description = "Selects the userport joystick adapter type for additional players"
        ),
        CoreOptionDef(
            key = "vice_joyport",
            displayName = "Joystick Port",
            values = listOf("1", "2"),
            defaultValue = "2",
            description = "Selects which controller port the primary joystick is plugged into"
        ),
        CoreOptionDef(
            key = "vice_retropad_options",
            displayName = "RetroPad Face Button Options",
            values = listOf("disabled", "jump", "rotate", "rotate_jump"),
            defaultValue = "disabled",
            description = "Remaps face buttons for common joystick control schemes"
        ),
        CoreOptionDef(
            key = "vice_mapping_options_display",
            displayName = "Show Mapping Options",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Reveals additional button mapping configuration options"
        ),
    )
}
