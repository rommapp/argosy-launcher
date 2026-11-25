package com.nendo.argosy.data.emulator.config

import com.nendo.argosy.data.emulator.config.ConfigValueType.BOOL
import com.nendo.argosy.data.emulator.config.ConfigValueType.ENUM
import com.nendo.argosy.data.emulator.config.ConfigValueType.INT
import com.nendo.argosy.data.emulator.config.ConfigValueType.STRING

internal object EdenConfigSpec {
    val CONFIG = ConfigFileSpec(listOf("config/config.ini"), ConfigFormat.INI, ConfigFileRole.PRIMARY)

    private const val RENDERER = "Renderer"
    private const val CPU = "Cpu"
    private const val SYSTEM = "System"
    private const val AUDIO = "Audio"
    private const val CORE = "Core"
    private const val OVERLAY = "Overlay"
    private const val WEB_SERVICE = "WebService"

    private fun option(
        section: String,
        key: String,
        type: ConfigValueType,
        default: String?,
        values: List<ConfigValue> = emptyList(),
        editable: Boolean = false,
        range: ConfigRange? = null
    ) = ConfigOption(CONFIG, section, key, type, default, values, editable, range)

    const val DRIVERS_DIR = "gpu_drivers"

    val DRIVER_PATH = ConfigOption(CONFIG, "GpuDriver", "driver_path", STRING, "", driverDir = DRIVERS_DIR)

    val SPEC = EmulatorConfigSpec(
        files = listOf(CONFIG),
        compiledDefaultFlag = "\\default",
        secrets = listOf(
            ConfigKeyMatcher(CONFIG, WEB_SERVICE, "eden_token"),
            ConfigKeyMatcher(CONFIG, WEB_SERVICE, "web_api_url"),
            ConfigKeyMatcher(CONFIG, WEB_SERVICE, "eden_username")
        ),
        options = listOf(
            option(RENDERER, "backend", ENUM, "1", ordinals("GLSL", "Vulkan", "Null", "GLASM", "SPIRV")),
            option(
                RENDERER, "resolution_setup", ENUM, "3",
                ordinals("1/4x", "1/2x", "3/4x", "1x", "5/4x", "3/2x", "2x", "3x", "4x") + named("12" to "8x"),
                editable = true
            ),
            option(RENDERER, "use_vsync", ENUM, "2", ordinals("Immediate", "Mailbox", "Fifo", "FifoRelaxed"), editable = true),
            option(
                RENDERER, "scaling_filter", ENUM, "0",
                ordinals(
                    "Nearest", "Bilinear", "Bicubic", "Gaussian", "Lanczos", "ScaleForce", "FSR", "Area",
                    "ZeroTangent", "BSpline", "Mitchell", "Spline1", "MMPX", "SGSR", "SGSR Edge"
                ),
                editable = true
            ),
            option(RENDERER, "anti_aliasing", ENUM, "0", ordinals("None", "FXAA", "SMAA"), editable = true),
            option(RENDERER, "aspect_ratio", ENUM, "0", ordinals("16:9", "4:3", "21:9", "16:10", "Stretch"), editable = true),
            option(RENDERER, "gpu_accuracy", ENUM, "0", ordinals("Low", "High"), editable = true),
            option(RENDERER, "dma_accuracy", ENUM, "0", ordinals("Default", "Unsafe", "Safe"), editable = true),
            option(RENDERER, "max_anisotropy", ENUM, "1", ordinals("Auto", "Default", "x2", "x4", "x8", "x16"), editable = true),
            option(RENDERER, "nvdec_emulation", ENUM, "1", ordinals("Off", "Cpu", "Gpu"), editable = true),
            option(RENDERER, "accelerate_astc", ENUM, "1", ordinals("Cpu", "Gpu", "CpuAsync")),
            option(RENDERER, "frame_pacing_mode", ENUM, "0", ordinals("Auto", "30", "60", "90", "120")),
            option(CPU, "cpu_backend", ENUM, "1", ordinals("Dynarmic", "NCE"), editable = true),
            option(CPU, "cpu_accuracy", ENUM, "0", ordinals("Auto", "Accurate", "Unsafe", "Paranoid", "Debugging"), editable = true),
            option(SYSTEM, "use_docked_mode", ENUM, "0", ordinals("Handheld", "Docked"), editable = true),
            option(
                SYSTEM, "language_index", ENUM, "1",
                ordinals("JA", "EN-US", "FR", "DE", "IT", "ES", "ZH", "KO", "NL", "PT", "RU", "TW", "EN-GB"),
                editable = true
            ),
            option(
                SYSTEM, "region_index", ENUM, "1",
                ordinals("Japan", "USA", "Europe", "Australia", "China", "Korea", "Taiwan"),
                editable = true
            ),
            option(AUDIO, "output_engine", ENUM, "2", ordinals("auto", "cubeb", "sdl3", "null")),
            option(AUDIO, "volume", INT, "100", editable = true, range = range(0.0, 200.0, 5.0)),
            option(CORE, "use_multi_core", BOOL, "true"),
            option(CORE, "memory_layout_mode", ENUM, "0", ordinals("4GB", "6GB", "8GB", "10GB", "12GB")),
            option(CORE, "use_speed_limit", BOOL, "true", editable = true),
            option(CORE, "speed_limit", INT, "100", editable = true, range = range(50.0, 400.0, 10.0)),
            option("Services", "airplane_mode", BOOL, "true"),
            option(OVERLAY, "show_performance_overlay", BOOL, null, editable = true),
            option(OVERLAY, "show_fps", BOOL, null, editable = true),
            option(OVERLAY, "show_input_overlay", BOOL, null, editable = true),
            DRIVER_PATH
        )
    )
}
