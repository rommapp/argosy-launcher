package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object PcsxRearmedManifest : CoreOptionManifest {
    override val coreId = "pcsx_rearmed"
    override val options = listOf(
        // System
        CoreOptionDef(
            key = "pcsx_rearmed_bios",
            displayName = "Use BIOS",
            values = listOf("auto", "HLE"),
            defaultValue = "auto",
            description = "Selects between the real PSX BIOS or high-level emulation"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_region",
            displayName = "Region",
            values = listOf("auto", "NTSC", "PAL"),
            defaultValue = "auto"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_show_bios_bootlogo",
            displayName = "Show Bios Bootlogo (Breaks some games)",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Shows the PlayStation startup animation when booting"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_memcard2",
            displayName = "Enable Second Memory Card",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables a second memory card in slot 2 for extra save storage"
        ),
        // Video
        CoreOptionDef(
            key = "pcsx_rearmed_frameskip",
            displayName = "Frameskip",
            values = listOf("0", "1", "2", "3"),
            defaultValue = "0",
            description = "Sets how many frames to skip to improve performance"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_dithering",
            displayName = "Enable Dithering",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Applies the PSX dithering pattern to simulate more colors"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_duping_enable",
            displayName = "Frame Duping",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Reuses the previous frame when the GPU has no new output to save processing"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_display_internal_fps",
            displayName = "Display Internal FPS",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Shows the emulated system's internal frame rate on screen"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_gpu_thread_rendering",
            displayName = "Threaded Rendering",
            values = listOf("disabled", "sync", "async"),
            defaultValue = "disabled",
            description = "Offloads GPU rendering to a separate thread for better performance"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_neon_interlace_enable",
            displayName = "Enable Interlacing Mode(s)",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Simulates interlaced video output for compatible games"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_neon_enhancement_enable",
            displayName = "Enhanced Resolution (Slow)",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Doubles the internal rendering resolution at a performance cost"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_neon_enhancement_no_main",
            displayName = "Enhanced Resolution Speed Hack",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Skips enhanced rendering for the main display to improve speed"
        ),
        // Audio
        CoreOptionDef(
            key = "pcsx_rearmed_spu_reverb",
            displayName = "Sound: Reverb",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Enables the SPU reverb effect for ambient audio"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_spu_interpolation",
            displayName = "Sound: Interpolation",
            values = listOf("simple", "gaussian", "cubic", "off"),
            defaultValue = "simple",
            description = "Selects the audio sample interpolation method"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_noxadecoding",
            displayName = "XA Decoding",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Enables decoding of XA audio streams used for voice and music"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_nocdaudio",
            displayName = "CD Audio",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Enables playback of CD-DA audio tracks"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_spuirq",
            displayName = "SPU IRQ Always Enabled",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Forces SPU interrupts on for games with audio timing issues"
        ),
        // CD
        CoreOptionDef(
            key = "pcsx_rearmed_async_cd",
            displayName = "CD Access Method (Restart)",
            values = listOf("sync", "async", "precache"),
            defaultValue = "sync",
            description = "Selects how the CD image is accessed during gameplay"
        ),
        // Input
        CoreOptionDef(
            key = "pcsx_rearmed_input_sensitivity",
            displayName = "Emulated Mouse Sensitivity",
            values = listOf(
                "0.05", "0.10", "0.15", "0.20", "0.25", "0.30", "0.35", "0.40", "0.45", "0.50",
                "0.55", "0.60", "0.65", "0.70", "0.75", "0.80", "0.85", "0.90", "0.95", "1.00",
                "1.05", "1.10", "1.15", "1.20", "1.25", "1.30", "1.35", "1.40", "1.45", "1.50",
                "1.55", "1.60", "1.65", "1.70", "1.75", "1.80", "1.85", "1.90", "1.95", "2.00"
            ),
            defaultValue = "1.00"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_multitap",
            displayName = "Multitap Mode (Restart)",
            values = listOf("disabled", "port 1 only", "port 2 only", "both"),
            defaultValue = "disabled",
            description = "Enables multitap adapters for more than 2 players"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_vibration",
            displayName = "Enable Vibration",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Enables DualShock controller vibration feedback"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_negcon_deadzone",
            displayName = "NegCon Twist Deadzone (Percent)",
            values = listOf("0", "5", "10", "15", "20", "25", "30"),
            defaultValue = "0",
            description = "Sets the deadzone for NegCon twist input used in racing games"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_negcon_response",
            displayName = "NegCon Twist Response",
            values = listOf("linear", "quadratic", "cubic"),
            defaultValue = "linear",
            description = "Sets the response curve for NegCon twist input"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_analog_axis_modifier",
            displayName = "Analog Axis Bounds",
            values = listOf("circle", "square"),
            defaultValue = "circle",
            description = "Sets the analog stick range shape to match the DualShock's circular gate"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_gunconadjustx",
            displayName = "Guncon Adjust X",
            values = (-25..25).map { it.toString() },
            defaultValue = "0",
            description = "Fine-tunes the horizontal position of the Guncon light gun cursor"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_gunconadjusty",
            displayName = "Guncon Adjust Y",
            values = (-25..25).map { it.toString() },
            defaultValue = "0",
            description = "Fine-tunes the vertical position of the Guncon light gun cursor"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_gunconadjustratiox",
            displayName = "Guncon Adjust Ratio X",
            values = listOf(
                "0.75", "0.76", "0.77", "0.78", "0.79", "0.80", "0.81", "0.82", "0.83", "0.84",
                "0.85", "0.86", "0.87", "0.88", "0.89", "0.90", "0.91", "0.92", "0.93", "0.94",
                "0.95", "0.96", "0.97", "0.98", "0.99", "1.00", "1.01", "1.02", "1.03", "1.04",
                "1.05", "1.06", "1.07", "1.08", "1.09", "1.10", "1.11", "1.12", "1.13", "1.14",
                "1.15", "1.16", "1.17", "1.18", "1.19", "1.20", "1.21", "1.22", "1.23", "1.24",
                "1.25"
            ),
            defaultValue = "1.00",
            description = "Adjusts the horizontal scale ratio for Guncon cursor alignment"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_gunconadjustratioy",
            displayName = "Guncon Adjust Ratio Y",
            values = listOf(
                "0.75", "0.76", "0.77", "0.78", "0.79", "0.80", "0.81", "0.82", "0.83", "0.84",
                "0.85", "0.86", "0.87", "0.88", "0.89", "0.90", "0.91", "0.92", "0.93", "0.94",
                "0.95", "0.96", "0.97", "0.98", "0.99", "1.00", "1.01", "1.02", "1.03", "1.04",
                "1.05", "1.06", "1.07", "1.08", "1.09", "1.10", "1.11", "1.12", "1.13", "1.14",
                "1.15", "1.16", "1.17", "1.18", "1.19", "1.20", "1.21", "1.22", "1.23", "1.24",
                "1.25"
            ),
            defaultValue = "1.00",
            description = "Adjusts the vertical scale ratio for Guncon cursor alignment"
        ),
        // CPU
        CoreOptionDef(
            key = "pcsx_rearmed_drc",
            displayName = "Dynamic Recompiler",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Uses a dynamic recompiler for faster CPU emulation"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_psxclock",
            displayName = "PSX CPU Clock",
            values = (30..100).map { it.toString() },
            defaultValue = "57",
            description = "Adjusts the emulated PSX CPU clock speed percentage"
        ),
        // Game Fixes
        CoreOptionDef(
            key = "pcsx_rearmed_idiablofix",
            displayName = "Diablo Music Fix",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Fixes missing music in Diablo"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_pe2_fix",
            displayName = "Parasite Eve 2/Vandal Hearts 1/2 Fix",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Fixes graphics glitches in Parasite Eve 2 and Vandal Hearts"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_inuyasha_fix",
            displayName = "InuYasha Sengoku Battle Fix",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Fixes a game-specific bug in InuYasha Sengoku Battle"
        ),
        // GPU P.E.Op.S. Plugin
        CoreOptionDef(
            key = "pcsx_rearmed_gpu_peops_odd_even_bit",
            displayName = "(GPU) Odd/Even Bit Hack",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Fixes interlacing issues in games that check the odd/even bit"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_gpu_peops_expand_screen_width",
            displayName = "(GPU) Expand Screen Width",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Extends the rendered screen width to fill horizontal black bars"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_gpu_peops_ignore_brightness",
            displayName = "(GPU) Ignore Brightness Color",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Ignores brightness changes that cause incorrect dark or washed-out colors"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_gpu_peops_disable_coord_check",
            displayName = "(GPU) Disable Coordinate Check",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Disables polygon coordinate validation for compatibility"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_gpu_peops_lazy_screen_update",
            displayName = "(GPU) Lazy Screen Update",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Delays screen updates to fix flickering or tearing in some games"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_gpu_peops_old_frame_skip",
            displayName = "(GPU) Old Frame Skipping",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Uses a legacy frame skipping method for the P.E.Op.S. GPU plugin"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_gpu_peops_repeated_triangles",
            displayName = "(GPU) Repeated Flat Tex Triangles",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Fixes visual artifacts from repeated flat-textured triangles"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_gpu_peops_quads_with_triangles",
            displayName = "(GPU) Draw Quads with Triangles",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Renders quad polygons as triangle pairs for compatibility"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_gpu_peops_fake_busy_state",
            displayName = "(GPU) Fake 'Gpu Busy' States",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Simulates GPU busy states to fix timing issues in some games"
        ),
        // GPU UNAI Plugin
        CoreOptionDef(
            key = "pcsx_rearmed_gpu_unai_blending",
            displayName = "(GPU) Enable Blending",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Enables semi-transparency blending effects"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_gpu_unai_lighting",
            displayName = "(GPU) Enable Lighting",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Enables vertex lighting calculations for shaded polygons"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_gpu_unai_fast_lighting",
            displayName = "(GPU) Enable Fast Lighting",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Uses a simplified lighting algorithm for better performance"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_gpu_unai_ilace_force",
            displayName = "(GPU) Enable Forced Interlace",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Forces interlaced rendering for all content"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_gpu_unai_pixel_skip",
            displayName = "(GPU) Enable Pixel Skip",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Renders every other pixel to double performance at lower quality"
        ),
        CoreOptionDef(
            key = "pcsx_rearmed_gpu_unai_scale_hires",
            displayName = "(GPU) Enable Hi-Res Downscaling",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Downscales hi-res GPU output to native resolution"
        ),
    )
}
