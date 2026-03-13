package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object DolphinManifest : CoreOptionManifest {
    override val coreId = "dolphin"
    override val options = listOf(
        // CPU
        CoreOptionDef(
            key = "dolphin_cpu_core",
            displayName = "CPU Core",
            values = listOf("0", "1", "4", "5"),
            defaultValue = "1",
            description = "Selects the CPU emulation method (0=Interpreter, 1=JIT, 4=JITIL, 5=CachedInterpreter)"
        ),
        CoreOptionDef(
            key = "dolphin_cpu_clock_rate",
            displayName = "CPU Clock Rate",
            values = listOf(
                "0.05", "0.10", "0.20", "0.30", "0.40", "0.50",
                "0.60", "0.70", "0.80", "0.90", "1.00",
                "1.50", "2.00", "2.50", "3.00"
            ),
            defaultValue = "1.00",
            description = "Adjusts the emulated CPU clock speed as a multiplier of native"
        ),
        CoreOptionDef(
            key = "dolphin_emulation_speed",
            displayName = "Emulation Speed",
            values = listOf("1.0", "0.0"),
            defaultValue = "0.0",
            description = "Limits emulation speed (0.0 = unlimited, 1.0 = 100%)"
        ),
        CoreOptionDef(
            key = "dolphin_main_cpu_thread",
            displayName = "Dual Core Mode",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Runs CPU and GPU on separate threads for better performance"
        ),
        CoreOptionDef(
            key = "dolphin_main_precision_frame_timing",
            displayName = "Precision Frame Timing",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Uses more precise frame timing for accurate emulation speed"
        ),
        CoreOptionDef(
            key = "dolphin_fastmem",
            displayName = "Fastmem",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Speeds up memory access emulation using host MMU tricks"
        ),
        CoreOptionDef(
            key = "dolphin_fastmem_arena",
            displayName = "Fastmem Arena",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Allocates a large memory arena for faster address translation"
        ),
        CoreOptionDef(
            key = "dolphin_main_accurate_cpu_cache",
            displayName = "Accurate CPU Cache",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Emulates the CPU instruction cache for higher accuracy at lower speed"
        ),
        CoreOptionDef(
            key = "dolphin_cheats_enabled",
            displayName = "Internal Cheats",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables Dolphin's built-in cheat code system"
        ),
        CoreOptionDef(
            key = "dolphin_skip_gc_bios",
            displayName = "Skip GameCube BIOS",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Skips the GameCube startup animation"
        ),
        CoreOptionDef(
            key = "dolphin_language",
            displayName = "System Language",
            values = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"),
            defaultValue = "1",
            description = "Sets the system language (0=JP, 1=EN, 2=DE, 3=FR, 4=ES, 5=IT, 6=NL, 7=ZH-S, 8=ZH-T, 9=KO)"
        ),
        CoreOptionDef(
            key = "dolphin_fast_disc_speed",
            displayName = "Speed Up Disc Transfer",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Removes disc read speed limits to reduce loading times"
        ),
        CoreOptionDef(
            key = "dolphin_main_mmu",
            displayName = "Enable MMU",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables full memory management unit emulation required by some games"
        ),
        CoreOptionDef(
            key = "dolphin_rush_frame_presentation",
            displayName = "Rush Frame Presentation",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Presents frames as soon as they are ready to reduce latency"
        ),
        CoreOptionDef(
            key = "dolphin_smooth_early_presentation",
            displayName = "Smooth Early Presentation",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Smooths out early frame presentation timing for less jitter"
        ),
        // Interface
        CoreOptionDef(
            key = "dolphin_osd_enabled",
            displayName = "On-Screen Display",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Shows on-screen messages for emulator events and info"
        ),
        CoreOptionDef(
            key = "dolphin_log_level",
            displayName = "Log Level",
            values = listOf("1", "2", "3", "4", "5"),
            defaultValue = "4",
            description = "Sets the verbosity of log output (1=Notice to 5=Debug)"
        ),
        CoreOptionDef(
            key = "dolphin_enable_debugging",
            displayName = "Enable Debugging",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables the built-in debugger for development use"
        ),
        // Audio / DSP
        CoreOptionDef(
            key = "dolphin_dsp_hle",
            displayName = "DSP HLE",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Uses high-level audio emulation for better performance"
        ),
        CoreOptionDef(
            key = "dolphin_dsp_jit",
            displayName = "DSP JIT",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Uses JIT recompilation for DSP audio processing"
        ),
        CoreOptionDef(
            key = "dolphin_call_back_audio",
            displayName = "Async Audio Callback",
            values = listOf("0", "1", "2"),
            defaultValue = "0",
            description = "Controls the audio callback method (0=sync, 1=async, 2=force)"
        ),
        // GameCube System
        CoreOptionDef(
            key = "dolphin_sysconf_gc_sp1_device",
            displayName = "SP1 Device",
            values = listOf("255", "0", "5", "10", "11", "12", "14", "13", "6"),
            defaultValue = "255",
            description = "Selects the serial port 1 device (255=none, 5=BBA network)"
        ),
        // Wii System
        CoreOptionDef(
            key = "dolphin_sysconf_widescreen",
            displayName = "Widescreen (Wii)",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Sets the Wii system setting to widescreen 16:9 output"
        ),
        CoreOptionDef(
            key = "dolphin_sysconf_progressive_scan",
            displayName = "Progressive Scan",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Enables progressive scan video output in the system settings"
        ),
        CoreOptionDef(
            key = "dolphin_sysconf_pal60",
            displayName = "PAL60 Mode",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Forces PAL games to run at 60Hz instead of 50Hz"
        ),
        CoreOptionDef(
            key = "dolphin_sysconf_sensor_bar_position",
            displayName = "Sensor Bar Position",
            values = listOf("0", "1"),
            defaultValue = "0",
            description = "Sets the sensor bar position (0=bottom, 1=top)"
        ),
        CoreOptionDef(
            key = "dolphin_sysconf_enable_rumble",
            displayName = "Controller Rumble",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled"
        ),
        CoreOptionDef(
            key = "dolphin_sysconf_wiimote_continuous_scanning",
            displayName = "Wiimote Continuous Scanning",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Keeps scanning for Wiimote connections continuously"
        ),
        CoreOptionDef(
            key = "dolphin_sysconf_alt_gc_ports_on_wii",
            displayName = "Alt GC Ports on Wii",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables alternative GameCube controller port mapping on Wii"
        ),
        CoreOptionDef(
            key = "dolphin_main_bluetooth_bluetooth_passthrough",
            displayName = "Bluetooth Passthrough Mode",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Passes Bluetooth directly to the emulator for real Wiimote support"
        ),
        // Graphics - Settings
        CoreOptionDef(
            key = "dolphin_gfx_settings_renderer",
            displayName = "Graphics Backend",
            values = listOf("Hardware", "Software", "Null"),
            defaultValue = "Hardware",
            description = "Selects the graphics rendering backend"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_settings_widescreen_hack",
            displayName = "Widescreen Hack",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Forces games to render in widescreen by adjusting the projection"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_settings_crop_overscan",
            displayName = "Crop Overscan",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Removes blank border areas from the edges of the display"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_settings_efb_scale",
            displayName = "Internal Resolution",
            values = listOf("1", "2", "3", "4", "5", "6"),
            defaultValue = "1",
            description = "Sets the internal rendering resolution multiplier"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_settings_shader_compilation_mode",
            displayName = "Shader Compilation Mode",
            values = listOf("0", "3", "1", "2"),
            defaultValue = "2",
            coreDefault = "0",
            description = "Controls when shaders are compiled (0=sync, 1=sync on first use, 2=async, 3=async skip)"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_settings_wait_for_shaders",
            displayName = "Wait for Shaders",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            coreDefault = "disabled",
            description = "Precompiles shaders before starting to prevent first-run stuttering"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_settings_anti_aliasing",
            displayName = "Anti-Aliasing",
            values = listOf("0", "1", "2", "3", "4", "5", "6"),
            defaultValue = "0",
            description = "Sets the anti-aliasing sample count for smoother edges"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_settings_texture_cache_accuracy",
            displayName = "Texture Cache Accuracy",
            values = listOf("128", "512", "0"),
            defaultValue = "128",
            description = "Controls how often textures are rechecked (0=safe, 128=fast, 512=fastest)"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_settings_gpu_texture_decoding",
            displayName = "GPU Texture Decoding",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            coreDefault = "disabled",
            description = "Offloads texture decoding to the GPU for better performance on Adreno/Mali"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_settings_enable_pixel_lighting",
            displayName = "Pixel Lighting",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Calculates lighting per-pixel instead of per-vertex for better quality"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_settings_fast_depth_calculation",
            displayName = "Fast Depth Calculation",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Uses a faster but less accurate depth buffer calculation"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_settings_disable_fog",
            displayName = "Disable Fog",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Removes fog effects from the rendered scene"
        ),
        // Graphics - Enhancements
        CoreOptionDef(
            key = "dolphin_gfx_enhancements_force_texture_filtering_mode",
            displayName = "Texture Filtering",
            values = listOf("0", "1", "2"),
            defaultValue = "0",
            description = "Forces a texture filtering mode (0=default, 1=nearest, 2=linear)"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_enhancements_max_anisotropy",
            displayName = "Anisotropic Filtering",
            values = listOf("0", "1", "2", "3", "4"),
            defaultValue = "0",
            description = "Sets the anisotropic filtering level for sharper textures at angles"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_enhancements_load_custom_textures",
            displayName = "Load Custom Textures",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Loads user-provided replacement texture packs"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_enhancements_cache_custom_textures",
            displayName = "Prefetch Custom Textures",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Preloads all custom textures into memory to avoid stuttering"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_enhancements_gfx_enhance_output_resampling",
            displayName = "Output Resampling",
            values = listOf("0", "1", "2", "3", "4", "5", "6"),
            defaultValue = "0",
            description = "Selects the output downsampling filter for smoother final image"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_enhancements_force_true_color",
            displayName = "Force True Color",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Renders at full 24-bit color depth instead of the native 16-bit"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_enhancements_gfx_enhance_disable_copy_filter",
            displayName = "Disable Copy Filter",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Disables the copy filter that can blur the image"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_enhancements_gfx_enhance_hdr_output",
            displayName = "HDR Output",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables HDR rendering output for compatible displays"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_enhancements_gfx_arbitrary_mipmap_detection",
            displayName = "Arbitrary Mipmap Detection",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Detects and handles custom mipmaps that don't follow standard patterns"
        ),
        // Graphics - Hacks
        CoreOptionDef(
            key = "dolphin_gfx_hacks_efb_access_enable",
            displayName = "EFB Access from CPU",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Allows the CPU to read from the embedded framebuffer for effects like peeking"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_hacks_efb_access_defer_invalidation",
            displayName = "EFB Access Defer Invalidation",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Defers EFB cache invalidation for potential performance gains"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_hacks_efb_access_tile_size",
            displayName = "EFB Access Tile Size",
            values = listOf("1", "4", "8", "16", "32", "64"),
            defaultValue = "64",
            description = "Sets the tile size for EFB access operations"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_hacks_bbox_enabled",
            displayName = "Bounding Box Emulation",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables bounding box emulation used by a few games for collision"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_hacks_force_progressive",
            displayName = "Force Progressive",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Forces progressive scan output for all games"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_hacks_efb_to_texture",
            displayName = "Skip EFB Copy to RAM",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Keeps EFB copies in GPU texture memory instead of copying to RAM"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_hacks_xfb_to_texture_enable",
            displayName = "Skip XFB Copy to RAM",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Keeps XFB copies in GPU texture memory instead of copying to RAM"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_hacks_efb_to_vram",
            displayName = "Disable EFB to VRAM",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Prevents EFB copies from being stored in VRAM"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_hacks_defer_efb_copies",
            displayName = "Defer EFB Copies",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Delays EFB copy operations to improve performance"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_hacks_immediate_xfb",
            displayName = "Immediate XFB",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Presents XFB copies immediately for more accurate timing"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_hacks_skip_dupe_frames",
            displayName = "Skip Duplicate Frames",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Avoids presenting identical consecutive frames to save processing"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_hacks_early_xfb_output",
            displayName = "Early XFB Output",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Outputs XFB data early to reduce input latency"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_hacks_efb_scaled_copy",
            displayName = "EFB Scaled Copy",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Scales EFB copies to match the internal resolution"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_hacks_efb_emulate_format_changes",
            displayName = "EFB Emulate Format Changes",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            coreDefault = "disabled",
            description = "Accurately emulates EFB format changes (fixes artifacts in some games)"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_hacks_vertex_rounding",
            displayName = "Vertex Rounding",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Rounds vertices to native resolution to fix position errors at high res"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_hacks_vi_skip",
            displayName = "VI Skip",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Skips rendering some VI frames to improve performance"
        ),
        CoreOptionDef(
            key = "dolphin_gfx_hacks_fast_texture_sampling",
            displayName = "Fast Texture Sampling",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Uses a faster but less accurate method for texture sampling"
        ),
        // Wiimote
        CoreOptionDef(
            key = "dolphin_wiimote_hotkey_sideways_toggle",
            displayName = "Sideways Toggle Button",
            values = listOf(
                "Disabled", "None", "L3", "R3", "L1", "R1",
                "L2", "R2", "A", "B", "X", "Y", "Start", "Select"
            ),
            defaultValue = "L3",
            description = "Selects the button that toggles sideways Wiimote orientation"
        ),
        CoreOptionDef(
            key = "dolphin_wiimote_ir_mode",
            displayName = "Wiimote IR Mode",
            values = listOf("0", "1", "2"),
            defaultValue = "1",
            description = "Sets the IR pointer emulation mode (0=direct, 1=analog, 2=mixed)"
        ),
        CoreOptionDef(
            key = "dolphin_wiimote_ir_offset",
            displayName = "Wiimote IR Vertical Offset",
            values = (-50..50).map { it.toString() },
            defaultValue = "0",
            description = "Shifts the IR cursor position vertically to correct alignment"
        ),
        CoreOptionDef(
            key = "dolphin_wiimote_ir_yaw",
            displayName = "Wiimote IR Total Yaw",
            values = (15..100).map { it.toString() } + (0..14).map { it.toString() },
            defaultValue = "25",
            description = "Sets the total horizontal range of the emulated IR pointer"
        ),
        CoreOptionDef(
            key = "dolphin_wiimote_ir_pitch",
            displayName = "Wiimote IR Total Pitch",
            values = (15..100).map { it.toString() } + (0..14).map { it.toString() },
            defaultValue = "25",
            description = "Sets the total vertical range of the emulated IR pointer"
        ),
        CoreOptionDef(
            key = "dolphin_wiimote_ir_deadzone",
            displayName = "Wiimote IR Deadzone",
            values = (0..50).map { it.toString() },
            defaultValue = "0"
        ),
        CoreOptionDef(
            key = "dolphin_wiimote_ir_modifier",
            displayName = "IR Modifier Button",
            values = listOf(
                "Disabled", "None", "L3", "R3", "L1", "R1",
                "L2", "R2", "A", "B", "X", "Y", "Start", "Select"
            ),
            defaultValue = "None",
            description = "Selects a button that slows IR movement for precision aiming"
        ),
        CoreOptionDef(
            key = "dolphin_wiimote_swing_modifier",
            displayName = "Swing Modifier Button",
            values = listOf(
                "Disabled", "None", "L3", "R3", "L1", "R1",
                "L2", "R2", "A", "B", "X", "Y", "Start", "Select"
            ),
            defaultValue = "Disabled",
            description = "Selects a button that activates swing motion on the analog stick"
        ),
        CoreOptionDef(
            key = "dolphin_wiimote_swing_angle",
            displayName = "Swing Angle",
            values = listOf(
                "1", "5", "10", "15", "20", "25", "30", "35",
                "40", "45", "50", "55", "60", "65", "70", "75",
                "80", "85", "90", "100", "110", "120", "130",
                "140", "150", "160", "170", "180"
            ),
            defaultValue = "90",
            description = "Sets the swing motion angle in degrees"
        ),
        CoreOptionDef(
            key = "dolphin_wiimote_save_load_settings",
            displayName = "Load and Prevent Save Settings",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Loads Wiimote settings from file and prevents changes from saving"
        ),
    )
}
