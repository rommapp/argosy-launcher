package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object PpssppManifest : CoreOptionManifest {
    override val coreId = "ppsspp"
    override val options = listOf(
        // System
        CoreOptionDef(
            key = "ppsspp_cpu_core",
            displayName = "CPU Core",
            values = listOf("jit", "IR jit", "interpreter"),
            defaultValue = "jit",
            description = "Selects the CPU emulation method, JIT is fastest but less compatible"
        ),
        CoreOptionDef(
            key = "ppsspp_locked_cpu_speed",
            displayName = "Locked CPU Speed",
            values = listOf("off", "222MHz", "266MHz", "333MHz"),
            defaultValue = "off",
            description = "Forces the emulated PSP CPU to run at a fixed clock speed"
        ),
        CoreOptionDef(
            key = "ppsspp_language",
            displayName = "Language",
            values = listOf(
                "automatic", "english", "japanese", "french", "spanish", "german",
                "italian", "dutch", "portuguese", "russian", "korean",
                "chinese_traditional", "chinese_simplified"
            ),
            defaultValue = "automatic"
        ),
        CoreOptionDef(
            key = "ppsspp_button_preference",
            displayName = "Confirmation Button",
            values = listOf("cross", "circle"),
            defaultValue = "cross",
            description = "Sets which button is used for confirm actions in the PSP menu"
        ),
        CoreOptionDef(
            key = "ppsspp_fast_memory",
            displayName = "Fast Memory (Speedhack)",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Skips memory access safety checks for faster emulation"
        ),
        CoreOptionDef(
            key = "ppsspp_cheats",
            displayName = "Internal Cheats Support",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables loading and applying cheat codes from cheat database files"
        ),
        // Video
        CoreOptionDef(
            key = "ppsspp_rendering_mode",
            displayName = "Rendering Mode",
            values = listOf("buffered", "nonbuffered"),
            defaultValue = "buffered",
            description = "Buffered mode is needed for effects but nonbuffered is faster"
        ),
        CoreOptionDef(
            key = "ppsspp_true_color",
            displayName = "True Color Depth",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Renders at full 32-bit color instead of the PSP's native 16-bit"
        ),
        CoreOptionDef(
            key = "ppsspp_internal_resolution",
            displayName = "Internal Resolution",
            values = listOf(
                "480x272", "960x544", "1440x816", "1920x1088", "2400x1360",
                "2880x1632", "3360x1904", "3840x2176", "4320x2448", "4800x2720"
            ),
            defaultValue = "480x272",
            description = "Sets the 3D rendering resolution, higher values look sharper"
        ),
        CoreOptionDef(
            key = "ppsspp_texture_scaling_level",
            displayName = "Texture Scaling Level",
            values = listOf("0", "1", "2", "3", "4", "5"),
            defaultValue = "1",
            description = "Upscales textures using the selected algorithm for sharper visuals"
        ),
        CoreOptionDef(
            key = "ppsspp_texture_scaling_type",
            displayName = "Texture Scaling Type",
            values = listOf("xbrz", "hybrid", "bicubic", "hybrid_bicubic"),
            defaultValue = "xbrz",
            description = "Selects the algorithm used for texture upscaling"
        ),
        CoreOptionDef(
            key = "ppsspp_texture_filtering",
            displayName = "Texture Filtering",
            values = listOf("auto", "nearest", "linear", "linear(FMV)"),
            defaultValue = "auto",
            description = "Selects the texture sampling filter method"
        ),
        CoreOptionDef(
            key = "ppsspp_texture_anisotropic_filtering",
            displayName = "Anisotropic Filtering",
            values = listOf("off", "1x", "2x", "4x", "8x", "16x"),
            defaultValue = "off",
            description = "Improves texture clarity at steep viewing angles"
        ),
        CoreOptionDef(
            key = "ppsspp_texture_deposterize",
            displayName = "Texture Deposterize",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Smooths color banding artifacts in textures"
        ),
        CoreOptionDef(
            key = "ppsspp_gpu_hardware_transform",
            displayName = "GPU Hardware T&L",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Uses GPU hardware for transform and lighting calculations"
        ),
        CoreOptionDef(
            key = "ppsspp_vertex_cache",
            displayName = "Vertex Cache (Speedhack)",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Caches transformed vertices to avoid redundant processing"
        ),
        CoreOptionDef(
            key = "ppsspp_block_transfer_gpu",
            displayName = "Block Transfer GPU",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Handles block transfers on GPU, needed for some effects and textures"
        ),
        // Performance
        CoreOptionDef(
            key = "ppsspp_auto_frameskip",
            displayName = "Auto Frameskip",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Automatically skips frames to maintain full speed"
        ),
        CoreOptionDef(
            key = "ppsspp_frameskip",
            displayName = "Frameskip",
            values = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"),
            defaultValue = "0",
            description = "Sets how many frames to skip between each rendered frame"
        ),
        CoreOptionDef(
            key = "ppsspp_force_max_fps",
            displayName = "Force Max FPS",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Prevents the game from running faster than the native frame rate"
        ),
        CoreOptionDef(
            key = "ppsspp_separate_io_thread",
            displayName = "IO Threading",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Runs file I/O on a separate thread to reduce loading stalls"
        ),
        CoreOptionDef(
            key = "ppsspp_unsafe_func_replacements",
            displayName = "Unsafe FuncReplacements",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Replaces known PSP functions with faster native implementations"
        ),
        CoreOptionDef(
            key = "ppsspp_sound_speedhack",
            displayName = "Sound Speedhack",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Reduces audio processing accuracy for better performance"
        ),
        // Audio
        CoreOptionDef(
            key = "ppsspp_audio_latency",
            displayName = "Audio Latency",
            values = listOf("low", "medium", "high"),
            defaultValue = "low",
            description = "Sets the audio buffer size, higher reduces crackling but adds delay"
        ),
    )
}
