package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object MednafenPsxHwManifest : CoreOptionManifest {
    override val coreId = "mednafen_psx_hw"
    override val options = listOf(
        // Renderer
        CoreOptionDef(
            key = "beetle_psx_hw_renderer",
            displayName = "Renderer (Restart)",
            values = listOf("hardware", "hardware_gl", "hardware_vk", "software"),
            defaultValue = "hardware",
            description = "Selects the rendering backend used for graphics output"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_renderer_software_fb",
            displayName = "Software Framebuffer",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Enables accurate software framebuffer operations in hardware mode"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_internal_resolution",
            displayName = "Internal GPU Resolution",
            values = listOf("1x(native)", "2x", "4x", "8x", "16x"),
            defaultValue = "1x(native)",
            description = "Increases the internal 3D rendering resolution"
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
            key = "beetle_psx_hw_filter",
            displayName = "Texture Filtering",
            values = listOf("nearest", "SABR", "xBR", "bilinear", "3-point", "JINC2"),
            defaultValue = "nearest",
            description = "Selects the texture filtering shader for smoother or sharper textures"
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
            key = "beetle_psx_hw_dump_textures",
            displayName = "Dump Textures",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Saves game textures to disk for creating replacement texture packs"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_replace_textures",
            displayName = "Replace Textures",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Loads custom high-resolution replacement textures"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_wireframe",
            displayName = "Wireframe Mode",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Renders geometry as wireframe outlines for debugging"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_display_vram",
            displayName = "Display Full VRAM",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Shows the entire VRAM contents on screen for debugging"
        ),
        // PGXP
        CoreOptionDef(
            key = "beetle_psx_hw_pgxp_mode",
            displayName = "PGXP Operation Mode",
            values = listOf("disabled", "memory only", "memory + CPU"),
            defaultValue = "disabled",
            description = "Enables sub-pixel precision to reduce polygon jitter and wobble"
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
        // Display
        CoreOptionDef(
            key = "beetle_psx_hw_display_internal_fps",
            displayName = "Display Internal FPS",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Shows the emulated system's internal frame rate on screen"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_line_render",
            displayName = "Line-to-Quad Hack",
            values = listOf("default", "aggressive", "disabled"),
            defaultValue = "default",
            description = "Converts single-pixel lines to quads to fix rendering at higher resolutions"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_frame_duping",
            displayName = "Frame Duping (Speedup)",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Reuses the previous frame when no new frame is ready to save processing"
        ),
        // CPU
        CoreOptionDef(
            key = "beetle_psx_hw_cpu_dynarec",
            displayName = "CPU Dynarec",
            values = listOf("disabled", "execute", "execute_once", "run_interpreter"),
            defaultValue = "disabled",
            description = "Selects the dynamic recompiler mode for CPU emulation"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_dynarec_invalidate",
            displayName = "Dynarec Code Invalidation",
            values = listOf("full", "dma"),
            defaultValue = "full",
            description = "Controls when the dynarec invalidates compiled code blocks"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_dynarec_eventcycles",
            displayName = "Dynarec DMA/GPU Event Cycles",
            values = listOf("128", "256", "384", "512", "640", "768", "896", "1024"),
            defaultValue = "128",
            description = "Sets how often the dynarec checks for DMA and GPU events"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_cpu_freq_scale",
            displayName = "CPU Frequency Scaling (Overclock)",
            values = listOf(
                "50%", "60%", "70%", "80%", "90%", "100%(native)",
                "110%", "120%", "130%", "140%", "150%", "160%", "170%", "180%", "190%", "200%",
                "210%", "220%", "230%", "240%", "250%", "260%", "270%", "280%", "290%", "300%",
                "310%", "320%", "330%", "340%", "350%", "360%", "370%", "380%", "390%", "400%",
                "410%", "420%", "430%", "440%", "450%", "460%", "470%", "480%", "490%", "500%",
                "510%", "520%", "530%", "540%", "550%", "560%", "570%", "580%", "590%", "600%",
                "610%", "620%", "630%", "640%", "650%", "660%", "670%", "680%", "690%", "700%",
                "710%", "720%", "730%", "740%", "750%"
            ),
            defaultValue = "100%(native)",
            description = "Adjusts the emulated CPU clock speed to reduce slowdown or fix timing"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_gte_overclock",
            displayName = "GTE Overclock",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Removes GTE instruction delays to speed up 3D geometry calculations"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_gpu_overclock",
            displayName = "GPU Rasterizer Overclock",
            values = listOf("1x(native)", "2x", "4x", "8x", "16x", "32x"),
            defaultValue = "1x(native)",
            description = "Speeds up the GPU rasterizer to reduce polygon pop-in"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_skip_bios",
            displayName = "Skip BIOS",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Skips the PlayStation BIOS startup animation"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_core_timing_fps",
            displayName = "Core-Reported FPS Timing",
            values = listOf("force_progressive", "force_interlaced", "auto_toggle"),
            defaultValue = "force_progressive",
            description = "Controls how the core reports frame timing to the frontend"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_aspect_ratio",
            displayName = "Core Aspect Ratio",
            values = listOf("corrected", "uncorrected", "4:3"),
            defaultValue = "corrected"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_widescreen_hack",
            displayName = "Widescreen Mode Hack",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Forces games to render in widescreen by adjusting the 3D projection"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_crop_overscan",
            displayName = "Crop Horizontal Overscan",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Removes empty border pixels from the left and right edges"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_image_crop",
            displayName = "Additional Cropping",
            values = listOf("disabled", "1px", "2px", "3px", "4px", "5px", "6px", "7px", "8px"),
            defaultValue = "disabled",
            description = "Crops additional pixels from the edges of the display"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_image_offset",
            displayName = "Offset Cropped Image",
            values = listOf(
                "disabled", "-4px", "-3px", "-2px", "-1px", "+1px", "+2px", "+3px", "+4px"
            ),
            defaultValue = "disabled",
            description = "Shifts the cropped image horizontally to correct alignment"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_image_offset_cycles",
            displayName = "Horizontal Image Offset (GPU Cycles)",
            values = listOf(
                "-24", "-23", "-22", "-21", "-20", "-19", "-18", "-17",
                "-16", "-15", "-14", "-13", "-12", "-11", "-10", "-9",
                "-8", "-7", "-6", "-5", "-4", "-3", "-2", "-1",
                "0",
                "1", "2", "3", "4", "5", "6", "7",
                "8", "9", "10", "11", "12", "13", "14", "15",
                "16", "17", "18", "19", "20", "21", "22", "23", "24"
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
        // CD
        CoreOptionDef(
            key = "beetle_psx_hw_cd_access_method",
            displayName = "CD Access Method (Restart)",
            values = listOf("sync", "async", "precache"),
            defaultValue = "sync",
            description = "Selects how the CD image is accessed during gameplay"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_cd_fastload",
            displayName = "CD Loading Speed",
            values = listOf("2x(native)", "4x", "6x", "8x", "10x", "12x", "14x"),
            defaultValue = "2x(native)",
            description = "Increases the CD read speed multiplier to reduce loading times"
        ),
        // Memory Cards
        CoreOptionDef(
            key = "beetle_psx_hw_use_mednafen_memcard0_method",
            displayName = "Memory Card 0 Method (Restart)",
            values = listOf("libretro", "mednafen"),
            defaultValue = "libretro",
            description = "Selects the save format used for memory card 0"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_enable_memcard1",
            displayName = "Enable Memory Card 1 (Restart)",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Enables the second memory card slot"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_shared_memory_cards",
            displayName = "Shared Memory Cards (Restart)",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Shares memory card saves across all games instead of per-game"
        ),
        // Input
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
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Allows toggling between digital and analog mode on DualShock controllers"
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
            description = "Selects the input device used for light gun games"
        ),
        CoreOptionDef(
            key = "beetle_psx_hw_gun_cursor",
            displayName = "Gun Cursor",
            values = listOf("cross", "dot", "off"),
            defaultValue = "cross",
            description = "Sets the light gun cursor style shown on screen"
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
    )
}
