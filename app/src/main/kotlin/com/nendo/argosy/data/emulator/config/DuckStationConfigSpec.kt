package com.nendo.argosy.data.emulator.config

import com.nendo.argosy.data.emulator.config.ConfigValueType.BOOL
import com.nendo.argosy.data.emulator.config.ConfigValueType.ENUM
import com.nendo.argosy.data.emulator.config.ConfigValueType.FLOAT
import com.nendo.argosy.data.emulator.config.ConfigValueType.INT
import com.nendo.argosy.data.emulator.config.ConfigValueType.STRING

internal object DuckStationConfigSpec {
    val SETTINGS = ConfigFileSpec(listOf("settings.ini"), ConfigFormat.INI, ConfigFileRole.PRIMARY)

    private const val GPU = "GPU"
    private const val DISPLAY = "Display"
    private const val MAIN = "Main"
    private const val AUDIO = "Audio"
    private const val CHEEVOS = "Cheevos"
    private const val MAX_RESOLUTION_SCALE = 16

    private val TEXTURE_FILTERS = tokens("Nearest", "Bilinear", "JINC2", "xBR", "MMPX")

    private val RESOLUTION_SCALES = listOf(ConfigValue("0", "Automatic")) +
        (1..MAX_RESOLUTION_SCALE).map { ConfigValue(it.toString(), "${it}x") }

    private fun option(
        section: String,
        key: String,
        type: ConfigValueType,
        default: String?,
        values: List<ConfigValue> = emptyList(),
        editable: Boolean = false,
        range: ConfigRange? = null
    ) = ConfigOption(SETTINGS, section, key, type, default, values, editable, range)

    val SPEC = EmulatorConfigSpec(
        files = listOf(SETTINGS),
        secrets = listOf(ConfigKeyMatcher(SETTINGS, CHEEVOS, "Token")),
        options = listOf(
            option(GPU, "Renderer", ENUM, "Automatic", tokens("Automatic", "Vulkan", "OpenGL", "Software"), editable = true),
            option(GPU, "ResolutionScale", INT, "1", RESOLUTION_SCALES, editable = true),
            option(GPU, "Multisamples", INT, "1", named("1" to "1x", "2" to "2x", "4" to "4x", "8" to "8x"), editable = true),
            option(GPU, "TextureFilter", ENUM, "Nearest", TEXTURE_FILTERS, editable = true),
            option(GPU, "SpriteTextureFilter", ENUM, "Nearest", TEXTURE_FILTERS, editable = true),
            option(
                GPU, "DitheringMode", ENUM, "TrueColor",
                tokens("Unscaled", "UnscaledShaderBlend", "Scaled", "ScaledShaderBlend", "TrueColor", "TrueColorFull"),
                editable = true
            ),
            option(GPU, "WidescreenHack", BOOL, "false", editable = true),
            option(GPU, "PGXPEnable", BOOL, "false", editable = true),
            option(
                GPU, "PGXPNormalizeDepth", ENUM, "Automatic",
                tokens("Automatic", "Disabled", "DepthBufferOnly", "Always"),
                editable = true
            ),
            option(
                GPU, "DeinterlacingMode", ENUM, "Progressive",
                tokens("Disabled", "Weave", "Blend", "Adaptive", "Progressive")
            ),
            option(
                DISPLAY, "AspectRatio", STRING, "Auto (Game Native)",
                tokens("Auto (Game Native)", "Stretch To Fill", "PAR 1:1"),
                editable = true
            ),
            option(
                DISPLAY, "CropMode", ENUM, "Overscan",
                tokens("None", "Overscan", "OverscanUncorrected", "Borders", "BordersUncorrected"),
                editable = true
            ),
            option(
                DISPLAY, "Scaling", ENUM, "BilinearSmooth",
                tokens(
                    "Nearest", "NearestInteger", "BilinearSmooth", "BilinearHybrid", "BilinearSharp",
                    "BilinearInteger", "Lanczos"
                ),
                editable = true
            ),
            option(DISPLAY, "VSync", BOOL, "false", editable = true),
            option(DISPLAY, "ShowFPS", BOOL, "false", editable = true),
            option("CPU", "ExecutionMode", ENUM, "Recompiler", tokens("Interpreter", "CachedInterpreter", "Recompiler")),
            option(MAIN, "RewindEnable", BOOL, "false", editable = true),
            option(MAIN, "RunaheadFrameCount", INT, "0", editable = true, range = range(0.0, 255.0)),
            option(MAIN, "SaveStateOnExit", BOOL, "true", editable = true),
            option(MAIN, "EmulationSpeed", FLOAT, "1"),
            option(AUDIO, "OutputVolume", INT, "100", editable = true, range = range(0.0, 100.0, 5.0)),
            option(AUDIO, "OutputMuted", BOOL, "false", editable = true),
            option("Console", "Region", ENUM, "Auto", tokens("Auto", "NTSC-J", "NTSC-U", "PAL")),
            option(
                "MemoryCards", "Card1Type", ENUM, "PerGameTitle",
                tokens("None", "Shared", "PerGame", "PerGameTitle", "PerGameFileTitle", "NonPersistent")
            ),
            option(CHEEVOS, "Enabled", BOOL, "false"),
            option(CHEEVOS, "ChallengeMode", BOOL, "false")
        )
    )
}
