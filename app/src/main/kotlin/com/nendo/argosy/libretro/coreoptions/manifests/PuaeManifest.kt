package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object PuaeManifest : CoreOptionManifest {
    override val coreId = "puae"
    override val options = listOf(
        CoreOptionDef(
            key = "puae_model",
            displayName = "Model",
            values = listOf(
                "auto", "A500OG", "A500", "A500PLUS", "A600", "A1200OG",
                "A1200", "A2000OG", "A2000", "A4030", "A4040", "CDTV",
                "CD32", "CD32FR"
            ),
            defaultValue = "auto",
            description = "Selects the Amiga hardware model to emulate"
        ),
        CoreOptionDef(
            key = "puae_model_options_display",
            displayName = "Show Automatic Model Options",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Reveals settings for automatic model selection per media type"
        ),
        CoreOptionDef(
            key = "puae_model_fd",
            displayName = "Automatic Floppy",
            values = listOf(
                "A500OG", "A500", "A500PLUS", "A600", "A1200OG",
                "A1200", "A2000OG", "A2000", "A4030", "A4040"
            ),
            defaultValue = "A500",
            description = "Selects the Amiga model used when auto-detecting floppy content"
        ),
        CoreOptionDef(
            key = "puae_model_hd",
            displayName = "Automatic HD",
            values = listOf("A600", "A1200OG", "A1200", "A2000", "A4030", "A4040"),
            defaultValue = "A1200",
            description = "Selects the Amiga model used when auto-detecting hard drive content"
        ),
        CoreOptionDef(
            key = "puae_model_cd",
            displayName = "Automatic CD",
            values = listOf("CDTV", "CD32", "CD32FR"),
            defaultValue = "CD32",
            description = "Selects the Amiga model used when auto-detecting CD content"
        ),
        CoreOptionDef(
            key = "puae_kickstart",
            displayName = "Kickstart ROM",
            values = listOf(
                "auto", "aros", "kick33180.A500", "kick34005.A500",
                "kick37175.A500", "kick37350.A600", "kick40063.A600",
                "kick39106.A1200", "kick40068.A1200", "kick39106.A4000",
                "kick40068.A4000"
            ),
            defaultValue = "auto",
            description = "Selects the Kickstart ROM firmware to use"
        ),
        CoreOptionDef(
            key = "puae_chipmem_size",
            displayName = "Chip RAM",
            values = listOf("auto", "1", "2", "3", "4"),
            defaultValue = "auto",
            description = "Sets the Chip RAM size in MB"
        ),
        CoreOptionDef(
            key = "puae_bogomem_size",
            displayName = "Slow RAM",
            values = listOf("auto", "0", "2", "4", "6", "7"),
            defaultValue = "auto",
            description = "Sets the Slow (Bogo) RAM size"
        ),
        CoreOptionDef(
            key = "puae_fastmem_size",
            displayName = "Z2 Fast RAM",
            values = listOf("auto", "0", "1", "2", "4", "8"),
            defaultValue = "auto",
            description = "Sets the Zorro II Fast RAM size in MB"
        ),
        CoreOptionDef(
            key = "puae_z3mem_size",
            displayName = "Z3 Fast RAM",
            values = listOf("auto", "0", "1", "2", "4", "8", "16", "32", "64", "128", "256", "512"),
            defaultValue = "auto",
            description = "Sets the Zorro III Fast RAM size in MB"
        ),
        CoreOptionDef(
            key = "puae_cpu_model",
            displayName = "CPU Model",
            values = listOf("auto", "68000", "68010", "68020", "68030", "68040", "68060"),
            defaultValue = "auto",
            description = "Selects the Motorola 680x0 CPU model to emulate"
        ),
        CoreOptionDef(
            key = "puae_fpu_model",
            displayName = "FPU Model",
            values = listOf("auto", "0", "68881", "68882", "cpu"),
            defaultValue = "auto",
            description = "Selects the floating-point unit to emulate"
        ),
        CoreOptionDef(
            key = "puae_cpu_throttle",
            displayName = "CPU Speed",
            values = listOf(
                "-900.0", "-800.0", "-700.0", "-600.0", "-500.0", "-400.0",
                "-300.0", "-200.0", "-100.0", "0.0", "100.0", "200.0", "300.0",
                "400.0", "500.0", "600.0", "700.0", "800.0", "900.0", "1000.0",
                "2000.0", "3000.0", "4000.0", "5000.0", "10000.0"
            ),
            defaultValue = "0.0",
            description = "Adjusts the emulated CPU speed as a cycle offset percentage"
        ),
        CoreOptionDef(
            key = "puae_cpu_multiplier",
            displayName = "CPU Cycle-exact Speed",
            values = listOf("0", "1", "2", "4", "8", "10", "12", "16"),
            defaultValue = "0",
            description = "Sets the CPU speed multiplier in cycle-exact mode"
        ),
        CoreOptionDef(
            key = "puae_cpu_compatibility",
            displayName = "CPU Compatibility",
            values = listOf("normal", "compatible", "memory", "exact"),
            defaultValue = "normal",
            description = "Sets the CPU emulation accuracy level"
        ),
        CoreOptionDef(
            key = "puae_autoloadfastforward",
            displayName = "Automatic Load Fast-Forward",
            values = listOf("disabled", "enabled", "fd", "hd", "cd"),
            defaultValue = "disabled",
            description = "Fast-forwards during loading for the selected media types"
        ),
        CoreOptionDef(
            key = "puae_floppy_speed",
            displayName = "Floppy Speed",
            values = listOf("100", "200", "400", "800", "0"),
            defaultValue = "100",
            description = "Sets the floppy drive speed as a percentage (0 = turbo)"
        ),
        CoreOptionDef(
            key = "puae_floppy_multidrive",
            displayName = "Floppy MultiDrive",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Enables multiple floppy drives for multi-disk programs"
        ),
        CoreOptionDef(
            key = "puae_floppy_write_protection",
            displayName = "Floppy Write Protection",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Write-protects all inserted floppy disk images"
        ),
        CoreOptionDef(
            key = "puae_floppy_write_redirect",
            displayName = "Floppy Write Redirect",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Redirects floppy writes to separate save files to preserve originals"
        ),
        CoreOptionDef(
            key = "puae_cd_speed",
            displayName = "CD Speed",
            values = listOf("100", "0"),
            defaultValue = "100",
            description = "Sets the CD read speed (0 = maximum)"
        ),
        CoreOptionDef(
            key = "puae_cd_startup_delayed_insert",
            displayName = "CD Startup Delayed Insert",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Delays CD insertion at startup to fix detection in some games"
        ),
        CoreOptionDef(
            key = "puae_shared_nvram",
            displayName = "CD32/CDTV Shared NVRAM",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Shares NVRAM save data across all CD32/CDTV games"
        ),
        CoreOptionDef(
            key = "puae_use_whdload",
            displayName = "WHDLoad Support",
            values = listOf("disabled", "files", "hdfs"),
            defaultValue = "files",
            description = "Enables WHDLoad for running floppy games from hard drive"
        ),
        CoreOptionDef(
            key = "puae_use_whdload_theme",
            displayName = "WHDLoad Theme",
            values = listOf("default", "native"),
            defaultValue = "default"
        ),
        CoreOptionDef(
            key = "puae_use_whdload_prefs",
            displayName = "WHDLoad Splash Screen",
            values = listOf("disabled", "config", "splash", "both"),
            defaultValue = "disabled",
            description = "Controls the WHDLoad configuration and splash screen display"
        ),
        CoreOptionDef(
            key = "puae_use_whdload_nowritecache",
            displayName = "WHDLoad NoWriteCache",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Disables WHDLoad's write cache to flush saves immediately"
        ),
        CoreOptionDef(
            key = "puae_use_boot_hd",
            displayName = "Global Boot HD",
            values = listOf("disabled", "files", "hdf20", "hdf40", "hdf80", "hdf128", "hdf256", "hdf512"),
            defaultValue = "disabled",
            description = "Attaches a virtual hard drive image for the system to boot from"
        ),
        CoreOptionDef(
            key = "puae_video_options_display",
            displayName = "Show Video Options",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Reveals additional video configuration options"
        ),
        CoreOptionDef(
            key = "puae_video_allow_hz_change",
            displayName = "Allow PAL/NTSC Hz Change",
            values = listOf("disabled", "enabled", "locked"),
            defaultValue = "locked",
            description = "Controls whether the refresh rate can change between PAL and NTSC"
        ),
        CoreOptionDef(
            key = "puae_video_standard",
            displayName = "Standard",
            values = listOf("PAL auto", "NTSC auto", "PAL", "NTSC"),
            defaultValue = "PAL auto",
            description = "Selects the video standard and whether to auto-detect it"
        ),
        CoreOptionDef(
            key = "puae_video_aspect",
            displayName = "Pixel Aspect Ratio",
            values = listOf("auto", "PAL", "NTSC", "1:1"),
            defaultValue = "auto"
        ),
        CoreOptionDef(
            key = "puae_video_resolution",
            displayName = "Resolution",
            values = listOf("auto", "auto-lores", "auto-superhires", "lores", "hires", "superhires"),
            defaultValue = "auto",
            description = "Sets the video output resolution mode"
        ),
        CoreOptionDef(
            key = "puae_video_vresolution",
            displayName = "Line Mode",
            values = listOf("auto", "single", "double"),
            defaultValue = "auto",
            description = "Controls whether lines are rendered single or doubled for interlace"
        ),
        CoreOptionDef(
            key = "puae_crop",
            displayName = "Crop",
            values = listOf("disabled", "minimum", "smaller", "small", "medium", "large", "larger", "maximum", "auto"),
            defaultValue = "disabled",
            description = "Removes border areas from the display"
        ),
        CoreOptionDef(
            key = "puae_crop_mode",
            displayName = "Crop Mode",
            values = listOf("both", "horizontal", "vertical", "16:9", "16:10", "4:3", "5:4"),
            defaultValue = "both",
            description = "Selects which edges or aspect ratio to crop to"
        ),
        CoreOptionDef(
            key = "puae_vertical_pos",
            displayName = "Vertical Position",
            values = listOf("auto", "0"),
            defaultValue = "auto",
            description = "Adjusts the vertical position of the displayed image"
        ),
        CoreOptionDef(
            key = "puae_horizontal_pos",
            displayName = "Horizontal Position",
            values = listOf("auto", "0"),
            defaultValue = "auto",
            description = "Adjusts the horizontal position of the displayed image"
        ),
        CoreOptionDef(
            key = "puae_immediate_blits",
            displayName = "Immediate/Waiting Blits",
            values = listOf("false", "immediate", "waiting"),
            defaultValue = "waiting",
            description = "Controls blitter timing (immediate is fastest, waiting is more accurate)"
        ),
        CoreOptionDef(
            key = "puae_collision_level",
            displayName = "Collision Level",
            values = listOf("none", "sprites", "playfields", "full"),
            defaultValue = "playfields",
            description = "Sets the sprite and playfield collision detection accuracy"
        ),
        CoreOptionDef(
            key = "puae_gfx_flickerfixer",
            displayName = "Remove Interlace Artifacts",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Applies a flicker-fixer to eliminate interlace line flickering"
        ),
        CoreOptionDef(
            key = "puae_gfx_framerate",
            displayName = "Frameskip",
            values = listOf("disabled", "1", "2"),
            defaultValue = "disabled",
            description = "Skips rendering frames to improve performance"
        ),
        CoreOptionDef(
            key = "puae_gfx_colors",
            displayName = "Color Depth",
            values = listOf("16bit", "24bit"),
            defaultValue = "24bit"
        ),
        CoreOptionDef(
            key = "puae_vkbd_theme",
            displayName = "Virtual KBD Theme",
            values = listOf(
                "auto", "auto_outline", "beige", "beige_outline",
                "cd32", "cd32_outline", "light", "light_outline",
                "dark", "dark_outline"
            ),
            defaultValue = "auto"
        ),
        CoreOptionDef(
            key = "puae_vkbd_transparency",
            displayName = "Virtual KBD Transparency",
            values = listOf("0%", "25%", "50%", "75%", "100%"),
            defaultValue = "25%"
        ),
        CoreOptionDef(
            key = "puae_vkbd_dimming",
            displayName = "Virtual KBD Dimming",
            values = listOf("0%", "25%", "50%", "75%", "100%"),
            defaultValue = "25%"
        ),
        CoreOptionDef(
            key = "puae_statusbar",
            displayName = "Statusbar Mode",
            values = listOf(
                "bottom", "bottom_minimal", "bottom_basic", "bottom_basic_minimal",
                "top", "top_minimal", "top_basic", "top_basic_minimal"
            ),
            defaultValue = "bottom"
        ),
        CoreOptionDef(
            key = "puae_statusbar_startup",
            displayName = "Statusbar Startup",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Shows the statusbar when the core first starts"
        ),
        CoreOptionDef(
            key = "puae_statusbar_messages",
            displayName = "Statusbar Messages",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Shows emulator status messages on the statusbar"
        ),
        CoreOptionDef(
            key = "puae_joyport_pointer_color",
            displayName = "Light Pen/Gun Pointer Color",
            values = listOf("disabled", "black", "white", "red", "green", "blue", "yellow", "purple"),
            defaultValue = "blue"
        ),
        CoreOptionDef(
            key = "puae_audio_options_display",
            displayName = "Show Audio Options",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Reveals additional audio configuration options"
        ),
        CoreOptionDef(
            key = "puae_sound_stereo_separation",
            displayName = "Stereo Separation",
            values = listOf("0%", "10%", "20%", "30%", "40%", "50%", "60%", "70%", "80%", "90%", "100%"),
            defaultValue = "100%",
            description = "Controls the width of stereo separation between audio channels"
        ),
        CoreOptionDef(
            key = "puae_sound_interpol",
            displayName = "Interpolation",
            values = listOf("none", "anti", "sinc", "rh", "crux"),
            defaultValue = "anti",
            description = "Selects the audio interpolation method for sample playback"
        ),
        CoreOptionDef(
            key = "puae_sound_filter",
            displayName = "Filter",
            values = listOf("emulated", "off", "on"),
            defaultValue = "emulated",
            description = "Controls the Amiga audio low-pass filter"
        ),
        CoreOptionDef(
            key = "puae_sound_filter_type",
            displayName = "Filter Type",
            values = listOf("auto", "standard", "enhanced"),
            defaultValue = "auto",
            description = "Selects the audio filter implementation quality"
        ),
        CoreOptionDef(
            key = "puae_floppy_sound",
            displayName = "Floppy Sound Emulation",
            values = listOf(
                "100", "95", "90", "85", "80", "75", "70", "65", "60", "55",
                "50", "45", "40", "35", "30", "25", "20", "15", "10", "5", "0"
            ),
            defaultValue = "80",
            description = "Sets the volume of emulated floppy drive mechanical sounds"
        ),
        CoreOptionDef(
            key = "puae_floppy_sound_empty_mute",
            displayName = "Floppy Sound Mute Ejected",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Silences floppy drive sounds when no disk is inserted"
        ),
        CoreOptionDef(
            key = "puae_floppy_sound_type",
            displayName = "Floppy Sound Type",
            values = listOf("internal", "A500", "LOUD"),
            defaultValue = "internal",
            description = "Selects the floppy drive sound sample set"
        ),
        CoreOptionDef(
            key = "puae_sound_volume_cd",
            displayName = "CD Audio Volume",
            values = listOf(
                "0%", "5%", "10%", "15%", "20%", "25%", "30%", "35%", "40%",
                "45%", "50%", "55%", "60%", "65%", "70%", "75%", "80%", "85%",
                "90%", "95%", "100%"
            ),
            defaultValue = "100%"
        ),
        CoreOptionDef(
            key = "puae_analogmouse",
            displayName = "Analog Stick Mouse",
            values = listOf("disabled", "left", "right", "both"),
            defaultValue = "both",
            description = "Maps analog sticks to mouse movement"
        ),
        CoreOptionDef(
            key = "puae_analogmouse_deadzone",
            displayName = "Analog Stick Mouse Deadzone",
            values = listOf("0", "5", "10", "15", "20", "25", "30", "35", "40", "45", "50"),
            defaultValue = "20"
        ),
        CoreOptionDef(
            key = "puae_analogmouse_speed",
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
            key = "puae_analogmouse_speed_right",
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
            key = "puae_dpadmouse_speed",
            displayName = "D-Pad Mouse Speed",
            values = listOf(
                "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
                "10", "11", "12", "13", "14", "15", "16", "17", "18"
            ),
            defaultValue = "6"
        ),
        CoreOptionDef(
            key = "puae_mouse_speed",
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
            key = "puae_physicalmouse",
            displayName = "Physical Mouse",
            values = listOf("disabled", "enabled", "double"),
            defaultValue = "enabled",
            description = "Controls how physical mouse input is handled"
        ),
        CoreOptionDef(
            key = "puae_physical_keyboard_pass_through",
            displayName = "Keyboard Pass-through",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Passes physical keyboard input directly to the emulated machine"
        ),
        CoreOptionDef(
            key = "puae_keyrah_keypad_mappings",
            displayName = "Keyrah Keypad Mappings",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables Keyrah keyboard adapter numpad mappings"
        ),
        CoreOptionDef(
            key = "puae_turbo_fire",
            displayName = "Turbo Fire",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables rapid-fire auto-repeat on the selected button"
        ),
        CoreOptionDef(
            key = "puae_turbo_fire_button",
            displayName = "Turbo Button",
            values = listOf("B", "A", "Y", "X", "L", "R", "L2", "R2"),
            defaultValue = "B",
            description = "Selects which gamepad button gets turbo fire functionality"
        ),
        CoreOptionDef(
            key = "puae_turbo_pulse",
            displayName = "Turbo Pulse",
            values = listOf("2", "4", "6", "8", "10", "12"),
            defaultValue = "6",
            description = "Sets the number of frames between each turbo fire press"
        ),
        CoreOptionDef(
            key = "puae_joyport",
            displayName = "Joystick/Mouse",
            values = listOf("joystick", "mouse"),
            defaultValue = "joystick",
            description = "Selects the default input device for the primary port"
        ),
        CoreOptionDef(
            key = "puae_joyport_order",
            displayName = "Joystick Port Order",
            values = listOf("1234", "2143", "3412", "4321"),
            defaultValue = "1234",
            description = "Sets the mapping order of physical controllers to Amiga ports"
        ),
        CoreOptionDef(
            key = "puae_retropad_options",
            displayName = "RetroPad Face Button Options",
            values = listOf("disabled", "jump", "rotate", "rotate_jump"),
            defaultValue = "disabled",
            description = "Remaps face buttons for common joystick control schemes"
        ),
        CoreOptionDef(
            key = "puae_cd32pad_options",
            displayName = "CD32 Pad Face Button Options",
            values = listOf("disabled", "jump", "rotate", "rotate_jump"),
            defaultValue = "disabled",
            description = "Remaps CD32 controller face buttons for common control schemes"
        ),
        CoreOptionDef(
            key = "puae_mapping_options_display",
            displayName = "Show Mapping Options",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Reveals additional button mapping configuration options"
        ),
    )
}
