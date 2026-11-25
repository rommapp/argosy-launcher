package com.nendo.argosy.data.emulator.config

import com.nendo.argosy.data.emulator.config.ConfigValueType.BOOL
import com.nendo.argosy.data.emulator.config.ConfigValueType.ENUM
import com.nendo.argosy.data.emulator.config.ConfigValueType.FLOAT
import com.nendo.argosy.data.emulator.config.ConfigValueType.INT
import com.nendo.argosy.data.emulator.config.ConfigValueType.STRING

internal object Vita3kConfigSpec {
    val CONFIG = ConfigFileSpec(listOf("config.yml"), ConfigFormat.YAML, ConfigFileRole.PRIMARY)

    private fun option(
        key: String,
        type: ConfigValueType,
        default: String?,
        values: List<ConfigValue> = emptyList(),
        editable: Boolean = false,
        range: ConfigRange? = null
    ) = ConfigOption(CONFIG, "", key, type, default, values, editable, range)

    val SPEC = EmulatorConfigSpec(
        files = listOf(CONFIG),
        secrets = emptyList(),
        options = listOf(
            option("backend-renderer", ENUM, "Vulkan", tokens("Vulkan", "OpenGL")),
            option("custom-driver-name", STRING, ""),
            option("gpu-idx", INT, "0"),
            option("turbo-mode", BOOL, "false", editable = true),
            option("use-astc", BOOL, "false"),
            option("resolution-multiplier", FLOAT, "1.0", editable = true, range = range(0.5, 8.0, 0.25)),
            option("screen-filter", ENUM, "Bilinear", tokens("FSR", "FXAA", "Bicubic", "Nearest", "Bilinear"), editable = true),
            option(
                "anisotropic-filtering", INT, "1",
                named("1" to "1x", "2" to "2x", "4" to "4x", "8" to "8x", "16" to "16x"),
                editable = true
            ),
            option("v-sync", BOOL, "true"),
            option("vk-mapping", ENUM, "Mailbox", tokens("Mailbox", "Fifo", "FifoRelaxed", "Immediate", "FifoLatestReadyEXT")),
            option(
                "deep-stencil", ENUM, "D24UnormS8Uint",
                tokens("D24UnormS8Uint", "D32Sfloat", "D32SfloatS8Uint", "D16UnormS8Uint", "D16Unorm", "S8Uint", "X8D24UnormPack32")
            ),
            option("async-pipeline-compilation", BOOL, "true", editable = true),
            option(
                "memory-mapping", ENUM, "double-buffer",
                tokens("disabled", "double-buffer", "external-host", "page-table", "native-buffer"),
                editable = true
            ),
            option("high-accuracy", BOOL, "false", editable = true),
            option("cpu-backend", ENUM, "Dynarmic", tokens("Dynarmic", "Unicorn")),
            option("cpu-opt", BOOL, "true", editable = true),
            option("cpu-unsafe", BOOL, "false", editable = true),
            option("performance-overlay", BOOL, "false", editable = true),
            option("performance-overlay-detail", ENUM, "1", ordinals("Min", "Low", "Medium", "Max"), editable = true),
            option("performance-overlay-position", ENUM, "1", ordinals("TL", "TC", "TR", "BL", "BC", "BR"), editable = true),
            option("audio-volume", INT, "100", editable = true, range = range(0.0, 100.0, 5.0)),
            option("ngs-enable", BOOL, "true", editable = true),
            option("sys-lang", INT, "1", editable = true, range = range(0.0, 19.0)),
            option("sys-button", ENUM, "1", ordinals("Circle", "Cross"), editable = true),
            option("screenshot-format", ENUM, "1", ordinals("None", "JPEG", "PNG"), editable = true)
        )
    )
}
