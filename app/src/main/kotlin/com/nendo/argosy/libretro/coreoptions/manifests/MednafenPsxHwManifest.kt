package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object MednafenPsxHwManifest : CoreOptionManifest {
    override val coreId = "mednafen_psx_hw"
    override val options = listOf(
        CoreOptionDef(
            key = "beetle_psx_hw_cpu_freq_scale",
            displayName = "CPU Frequency Scaling (Overclock)",
            values = listOf(
                "50%", "60%", "70%", "80%", "90%", "100%",
                "110%", "120%", "130%", "140%", "150%", "160%", "170%", "180%", "190%", "200%",
                "210%", "220%", "230%", "240%", "250%", "300%", "350%", "400%", "450%", "500%",
                "550%", "600%", "650%", "700%", "750%"
            ),
            defaultValue = "100%",
            description = "Adjusts the emulated CPU clock speed to reduce slowdown or fix timing",
            valueLabels = mapOf("100%" to "100% (Native)")
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_gpu_overclock",
            displayName = "GPU Rasterizer Overclock",
            values = listOf("1x(native)", "2x", "4x", "8x", "16x", "32x"),
            defaultValue = "1x(native)",
            description = "Speeds up the GPU rasterizer to reduce polygon pop-in"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_gte_overclock",
            displayName = "GTE Overclock",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Removes GTE instruction delays to speed up 3D geometry calculations"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_skip_bios",
            displayName = "Skip BIOS",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Skips the PlayStation BIOS startup animation"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_override_bios",
            displayName = "Override BIOS (Restart)",
            values = listOf("disabled", "psxonpsp", "ps1_rom", "openbios"),
            defaultValue = "disabled",
            description = "Overrides the region BIOS with a region-free one if found",
            valueLabels = mapOf(
                "psxonpsp" to "PSP PS1 BIOS", "ps1_rom" to "PS3 PS1 BIOS", "openbios" to "OpenBIOS"
            )
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_region",
            displayName = "System Region (Restart)",
            values = listOf("auto", "ntsc-j", "ntsc-u", "pal"),
            defaultValue = "auto",
            description = "Sets the fallback region when content cannot be auto-detected",
            valueLabels = mapOf(
                "ntsc-j" to "NTSC-J (Japan)", "ntsc-u" to "NTSC-U (North America)", "pal" to "PAL (Europe)"
            )
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_internal_resolution",
            displayName = "Internal GPU Resolution",
            values = listOf("1x(native)", "2x", "4x", "8x", "16x"),
            defaultValue = "1x(native)",
            description = "Increases the internal 3D rendering resolution"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_renderer",
            displayName = "Renderer (Restart)",
            values = listOf("hardware", "hardware_gl", "hardware_vk", "software"),
            defaultValue = "software",
            coreDefault = "hardware",
            description = "Selects the rendering backend used for graphics output",
            valueLabels = mapOf(
                "hardware" to "Hardware (Auto)", "hardware_gl" to "Hardware (OpenGL)",
                "hardware_vk" to "Hardware (Vulkan)", "software" to "Software"
            )
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_renderer_software_fb",
            displayName = "Software Framebuffer",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Enables accurate software framebuffer operations in hardware mode"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_depth",
            displayName = "Internal Color Depth",
            values = listOf("16bpp(native)", "32bpp"),
            defaultValue = "16bpp(native)",
            description = "Increases color depth to reduce banding artifacts"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_dither_mode",
            displayName = "Dithering Pattern",
            values = listOf("1x(native)", "internal resolution", "disabled"),
            defaultValue = "1x(native)",
            description = "Controls the dithering pattern scale relative to internal resolution"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_scaled_uv_offset",
            displayName = "Texture UV Offset",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Samples 3D textures at an offset above native resolution to reduce seams"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_filter",
            displayName = "Texture Filtering",
            values = listOf("nearest", "SABR", "xBR", "bilinear", "3-point", "JINC2"),
            defaultValue = "nearest",
            description = "Selects the texture filtering shader for smoother or sharper textures"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_filter_exclude_sprite",
            displayName = "Exclude Sprites from Filtering",
            values = listOf("disabled", "opaque", "all"),
            defaultValue = "disabled",
            description = "Skips texture filtering on sprites to prevent seams in 2D backgrounds",
            valueLabels = mapOf("opaque" to "Opaque Only", "all" to "Opaque and Semi-Transparent")
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_filter_exclude_2d_polygon",
            displayName = "Exclude 2D Polygons from Filtering",
            values = listOf("disabled", "opaque", "all"),
            defaultValue = "disabled",
            description = "Skips texture filtering on detected 2D polygons",
            valueLabels = mapOf("opaque" to "Opaque Only", "all" to "Opaque and Semi-Transparent")
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_adaptive_smoothing",
            displayName = "Adaptive Smoothing",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Applies edge-aware smoothing to reduce jagged edges and dithering"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_super_sampling",
            displayName = "Supersampling (Downsample to Native Resolution)",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Renders at high resolution then downsamples for anti-aliased output"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_msaa",
            displayName = "Multi-Sampled Anti Aliasing",
            values = listOf("1x", "2x", "4x", "8x", "16x"),
            defaultValue = "1x",
            description = "Sets the MSAA level for smoother polygon edges"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_mdec_yuv",
            displayName = "MDEC YUV Chroma Filter",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Smooths color artifacts in FMV sequences"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_track_textures",
            displayName = "Track Textures",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables texture tracking needed for texture dumping and replacement"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_replace_textures",
            displayName = "Replace Textures",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Loads custom high-resolution replacement textures"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_core_timing_fps",
            displayName = "Core-Reported FPS Timing",
            values = listOf("auto_toggle", "force_progressive", "force_interlaced"),
            defaultValue = "auto_toggle",
            description = "Controls how the core reports frame timing to the frontend",
            valueLabels = mapOf(
                "auto_toggle" to "Automatic Toggling", "force_progressive" to "Force Progressive",
                "force_interlaced" to "Force Interlaced"
            )
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_deinterlacer",
            displayName = "Deinterlace Method",
            values = listOf("weave", "bob", "bob_offset", "fastmad", "off"),
            defaultValue = "weave",
            description = "Selects how interlaced video is deinterlaced for display",
            valueLabels = mapOf(
                "weave" to "Weave", "bob" to "Bob", "bob_offset" to "Bob (Offset)",
                "fastmad" to "FastMAD (Motion Adaptive)", "off" to "Off"
            )
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_frame_duping",
            displayName = "Frame Duping (Speedup)",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Reuses the previous frame when no new frame is ready to save processing"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_pal_video_timing_override",
            displayName = "PAL Video Timing Override",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Forces PAL games to run at the NTSC frame rate to fix slowdown"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_aspect_ratio",
            displayName = "Core Aspect Ratio",
            values = listOf("corrected", "uncorrected", "4:3", "ntsc"),
            defaultValue = "corrected",
            valueLabels = mapOf(
                "corrected" to "Corrected", "uncorrected" to "Uncorrected",
                "4:3" to "Force 4:3", "ntsc" to "Force NTSC"
            )
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_crop_overscan",
            displayName = "Crop Overscan",
            values = listOf("disabled", "static", "smart"),
            defaultValue = "smart",
            description = "Removes empty border pixels from the edges of the display",
            valueLabels = mapOf(
                "disabled" to "Disabled", "static" to "Horizontal", "smart" to "Horizontal + Vertical"
            )
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_image_crop",
            displayName = "Additional Cropping",
            values = listOf(
                "disabled", "1px", "2px", "3px", "4px", "5px", "6px", "7px", "8px",
                "9px", "10px", "11px", "12px", "13px", "14px", "15px", "16px",
                "17px", "18px", "19px", "20px"
            ),
            defaultValue = "disabled",
            description = "Crops additional pixels from the edges of the display"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_image_offset",
            displayName = "Offset Cropped Image",
            values = listOf(
                "-12px", "-11px", "-10px", "-9px", "-8px", "-7px", "-6px", "-5px",
                "-4px", "-3px", "-2px", "-1px", "disabled",
                "+1px", "+2px", "+3px", "+4px", "+5px", "+6px", "+7px", "+8px",
                "+9px", "+10px", "+11px", "+12px"
            ),
            defaultValue = "disabled",
            description = "Shifts the cropped image horizontally to correct alignment"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_image_offset_cycles",
            displayName = "Horizontal Image Offset (GPU Cycles)",
            values = listOf(
                "-40", "-32", "-24", "-16", "-8",
                "-7", "-6", "-5", "-4", "-3", "-2", "-1",
                "0",
                "+1", "+2", "+3", "+4", "+5", "+6", "+7",
                "+8", "+16", "+24", "+32", "+40"
            ),
            defaultValue = "0",
            description = "Fine-tunes horizontal image position in GPU clock cycles"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_initial_scanline",
            displayName = "Initial Scanline - NTSC",
            values = (0..40).map { it.toString() },
            defaultValue = "0",
            description = "Sets the first visible scanline for NTSC to crop the top border"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_last_scanline",
            displayName = "Last Scanline - NTSC",
            values = (210..239).map { it.toString() },
            defaultValue = "239",
            description = "Sets the last visible scanline for NTSC to crop the bottom border"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_initial_scanline_pal",
            displayName = "Initial Scanline - PAL",
            values = (0..40).map { it.toString() },
            defaultValue = "0",
            description = "Sets the first visible scanline for PAL to crop the top border"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_last_scanline_pal",
            displayName = "Last Scanline - PAL",
            values = (230..287).map { it.toString() },
            defaultValue = "287",
            description = "Sets the last visible scanline for PAL to crop the bottom border"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_pgxp_mode",
            displayName = "PGXP Operation Mode",
            values = listOf("disabled", "memory only", "memory + CPU"),
            defaultValue = "disabled",
            description = "Enables sub-pixel precision to reduce polygon jitter and wobble",
            valueLabels = mapOf(
                "memory only" to "Memory Only", "memory + CPU" to "Memory + CPU (Buggy)"
            )
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_pgxp_2d_tol",
            displayName = "PGXP 2D Geometry Tolerance",
            values = listOf(
                "disabled", "0px", "1px", "2px", "3px", "4px", "5px", "6px", "7px", "8px"
            ),
            defaultValue = "disabled",
            description = "Keeps PGXP values for geometry lacking proper depth within a pixel tolerance"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_pgxp_nclip",
            displayName = "PGXP Primitive Culling",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Reduces holes in geometry but may cause some games to lock up"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_pgxp_vertex",
            displayName = "PGXP Vertex Cache",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Caches PGXP vertex data to improve precision consistency"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_pgxp_texture",
            displayName = "PGXP Perspective Correct Texturing",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Applies perspective-correct texture mapping to fix warping"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_display_internal_fps",
            displayName = "Display Internal FPS",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Shows the emulated system's internal frame rate on screen"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_display_osd",
            displayName = "Display OSD Messages",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Displays on-screen messages generated by the core"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_display_vram",
            displayName = "Display Full VRAM (Debug)",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Shows the entire VRAM contents on screen for debugging"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_analog_calibration",
            displayName = "Analog Self-Calibration",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Automatically calibrates analog stick range during gameplay"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_analog_toggle",
            displayName = "Enable DualShock Analog Mode Toggle",
            values = listOf("disabled", "enabled", "enabled-analog"),
            defaultValue = "disabled",
            description = "Allows toggling between digital and analog mode on DualShock controllers",
            valueLabels = mapOf("enabled-analog" to "Default-Analog")
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_analog_toggle_combo",
            displayName = "DualShock Analog Mode Combo",
            values = listOf(
                "l1+l2+r1+r2+start+select", "l1+r1+select", "l1+r1+start", "l1+r1+l3", "l1+r1+r3",
                "l2+r2+select", "l2+r2+start", "l2+r2+l3", "l2+r2+r3", "l3+r3"
            ),
            defaultValue = "l1+r1+select",
            description = "Sets the button combination that toggles analog mode",
            valueLabels = mapOf(
                "l1+l2+r1+r2+start+select" to "L1 + L2 + R1 + R2 + Start + Select",
                "l1+r1+select" to "L1 + R1 + Select", "l1+r1+start" to "L1 + R1 + Start",
                "l1+r1+l3" to "L1 + R1 + L3", "l1+r1+r3" to "L1 + R1 + R3",
                "l2+r2+select" to "L2 + R2 + Select", "l2+r2+start" to "L2 + R2 + Start",
                "l2+r2+l3" to "L2 + R2 + L3", "l2+r2+r3" to "L2 + R2 + R3", "l3+r3" to "L3 + R3"
            )
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_analog_toggle_hold",
            displayName = "DualShock Analog Mode Combo Hold Delay",
            values = listOf("0", "1", "2", "3", "4", "5"),
            defaultValue = "1",
            description = "Sets how long the analog mode combo must be held",
            valueLabels = mapOf(
                "0" to "0 Second Delay", "1" to "1 Second Delay", "2" to "2 Second Delay",
                "3" to "3 Second Delay", "4" to "4 Second Delay", "5" to "5 Second Delay"
            )
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_enable_multitap_port1",
            displayName = "Port 1: Multitap Enable",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables the multitap adapter on port 1 for extra players"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_enable_multitap_port2",
            displayName = "Port 2: Multitap Enable",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables the multitap adapter on port 2 for extra players"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_gun_input_mode",
            displayName = "Gun Input Mode",
            values = listOf("lightgun", "touchscreen"),
            defaultValue = "lightgun",
            description = "Selects the input device used for light gun games",
            valueLabels = mapOf("lightgun" to "Light Gun", "touchscreen" to "Touchscreen")
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_gun_cursor",
            displayName = "Gun Cursor",
            values = listOf("off", "cross", "dot"),
            defaultValue = "cross",
            description = "Sets the light gun cursor style shown on screen",
            valueLabels = mapOf("off" to "Off", "cross" to "Cross", "dot" to "Dot")
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_crosshair_color_p1",
            displayName = "Gun Crosshair Color: Port 1",
            values = listOf(
                "red", "blue", "green", "orange", "yellow", "cyan", "pink", "purple", "black", "white"
            ),
            defaultValue = "red",
            description = "Sets the light gun crosshair color for port 1"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_crosshair_color_p2",
            displayName = "Gun Crosshair Color: Port 2",
            values = listOf(
                "blue", "red", "green", "orange", "yellow", "cyan", "pink", "purple", "black", "white"
            ),
            defaultValue = "blue",
            description = "Sets the light gun crosshair color for port 2"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_mouse_sensitivity",
            displayName = "Mouse Sensitivity",
            values = listOf(
                "5%", "10%", "15%", "20%", "25%", "30%", "35%", "40%", "45%", "50%",
                "55%", "60%", "65%", "70%", "75%", "80%", "85%", "90%", "95%", "100%",
                "105%", "110%", "115%", "120%", "125%", "130%", "135%", "140%", "145%", "150%",
                "155%", "160%", "165%", "170%", "175%", "180%", "185%", "190%", "195%", "200%"
            ),
            defaultValue = "100%"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_negcon_response",
            displayName = "NegCon Twist Response",
            values = listOf("linear", "quadratic", "cubic"),
            defaultValue = "linear",
            description = "Sets the response curve for NegCon twist input"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_negcon_deadzone",
            displayName = "NegCon Twist Deadzone",
            values = listOf("0%", "5%", "10%", "15%", "20%", "25%", "30%"),
            defaultValue = "0%",
            description = "Sets the deadzone for NegCon twist input used in racing games"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_use_mednafen_memcard0_method",
            displayName = "Memory Card 0 Method (Restart)",
            values = listOf("libretro", "mednafen"),
            defaultValue = "libretro",
            description = "Selects the save format used for memory card 0",
            valueLabels = mapOf("libretro" to "Libretro", "mednafen" to "Mednafen")
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_enable_memcard1",
            displayName = "Enable Memory Card 1 (Restart)",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables the second memory card slot"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_shared_memory_cards",
            displayName = "Shared Memory Cards (Restart)",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Shares memory card saves across all games instead of per-game"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_memcard_left_index",
            displayName = "Memory Card Slot 1 Index",
            values = (0..63).map { it.toString() },
            defaultValue = "0",
            description = "Selects the card loaded in the left slot (Mednafen method only)"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_memcard_right_index",
            displayName = "Memory Card Slot 2 Index",
            values = (0..63).map { it.toString() },
            defaultValue = "1",
            description = "Selects the card loaded in the right slot (slot 2 must be enabled)"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_line_render",
            displayName = "Line-to-Quad Hack",
            values = listOf("default", "aggressive", "disabled"),
            defaultValue = "default",
            description = "Converts single-pixel lines to quads to fix rendering at higher resolutions"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_widescreen_hack",
            displayName = "Widescreen Mode Hack",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Forces games to render in widescreen by adjusting the 3D projection"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_widescreen_hack_aspect_ratio",
            displayName = "Widescreen Mode Hack Aspect Ratio",
            values = listOf("16:9", "16:10", "18:9", "19:9", "20:9", "21:9", "32:9"),
            defaultValue = "16:9",
            description = "Sets the aspect ratio used by the widescreen mode hack"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_spu_silent_voice",
            displayName = "SPU Silent Voice Optimization",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Skips processing of fully released voices to save frame time"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_cpu_dynarec",
            displayName = "CPU Dynarec",
            values = listOf("disabled", "execute", "run_interpreter"),
            defaultValue = "disabled",
            description = "Selects the dynamic recompiler mode for CPU emulation",
            valueLabels = mapOf(
                "disabled" to "Disabled (Beetle Interpreter)", "execute" to "Max Performance",
                "run_interpreter" to "Lightrec Interpreter"
            )
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_dynarec_invalidate",
            displayName = "Dynarec Code Invalidation",
            values = listOf("full", "dma"),
            defaultValue = "full",
            description = "Controls when the dynarec invalidates compiled code blocks",
            valueLabels = mapOf("full" to "Full", "dma" to "DMA Only (Slightly Faster)")
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_dynarec_op_cycles",
            displayName = "Dynarec Cycles Per Instruction",
            values = listOf("2", "1"),
            defaultValue = "2",
            description = "Cycles charged per CPU instruction; 1 fixes some CD streaming stalls",
            valueLabels = mapOf("2" to "2 (Default)", "1" to "1 (Accurate, Fixes Parasite Eve 2 etc.)")
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_dynarec_eventcycles",
            displayName = "Dynarec DMA/GPU Event Cycles",
            values = listOf(
                "128", "256", "384", "512", "640", "768", "896", "1024",
                "1152", "1280", "1408", "1536", "1664", "1792", "1920", "2048"
            ),
            defaultValue = "128",
            description = "Sets how often the dynarec checks for DMA and GPU events"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_dynarec_spu_samples",
            displayName = "Dynarec SPU Samples",
            values = listOf("1", "4", "16"),
            defaultValue = "1",
            description = "Max SPU samples run before an SPU update; above 1 may cause sound glitches"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_dynarec_spgp_opt",
            displayName = "Dynarec SP GP Hit RAM Optimization",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Generates faster code by assuming sp/gp always point to RAM"
        ),
    )
}
