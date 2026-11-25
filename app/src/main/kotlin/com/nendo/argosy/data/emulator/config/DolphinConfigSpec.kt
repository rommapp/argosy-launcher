package com.nendo.argosy.data.emulator.config

import com.nendo.argosy.data.emulator.config.ConfigValueType.BOOL
import com.nendo.argosy.data.emulator.config.ConfigValueType.ENUM
import com.nendo.argosy.data.emulator.config.ConfigValueType.FLOAT
import com.nendo.argosy.data.emulator.config.ConfigValueType.INT
import com.nendo.argosy.data.emulator.config.ConfigValueType.STRING

internal object DolphinConfigSpec {
    val MAIN = ConfigFileSpec(listOf("Config/Dolphin.ini"), ConfigFormat.INI, ConfigFileRole.PRIMARY)
    val GFX = ConfigFileSpec(listOf("Config/GFX.ini"), ConfigFormat.INI, ConfigFileRole.SETTINGS)
    val ACHIEVEMENTS = ConfigFileSpec(listOf("Config/RetroAchievements.ini"), ConfigFormat.INI, ConfigFileRole.SETTINGS)
    val TIME_PLAYED = ConfigFileSpec(listOf("Config/TimePlayed.ini"), ConfigFormat.INI, ConfigFileRole.PLAY_TIME)

    private const val CORE = "Core"
    private const val DSP = "DSP"
    private const val SETTINGS = "Settings"
    private const val ENHANCEMENTS = "Enhancements"

    val SPEC = EmulatorConfigSpec(
        files = listOf(MAIN, GFX, ACHIEVEMENTS, TIME_PLAYED),
        keysIgnoreCase = true,
        secrets = listOf(
            ConfigKeyMatcher(ACHIEVEMENTS, null, "ApiToken"),
            ConfigKeyMatcher(MAIN, "Analytics", "ID"),
            ConfigKeyMatcher(MAIN, "General", "WirelessMac")
        ),
        options = listOf(
            ConfigOption(MAIN, CORE, "GFXBackend", ENUM, null, tokens("OGL", "Vulkan", "Software Renderer", "Null")),
            ConfigOption(GFX, SETTINGS, "InternalResolution", INT, "1", editable = true, range = range(1.0, 6.0)),
            ConfigOption(
                GFX, SETTINGS, "AspectRatio", ENUM, "0",
                ordinals("Auto", "16:9", "4:3", "Stretch", "Custom", "CustomStretch", "Raw"),
                editable = true
            ),
            ConfigOption(GFX, SETTINGS, "wideScreenHack", BOOL, "False", editable = true),
            ConfigOption(GFX, "Hardware", "VSync", BOOL, "False", editable = true),
            ConfigOption(
                GFX, SETTINGS, "ShaderCompilationMode", ENUM, "0",
                ordinals("Sync", "Sync ubershaders", "Async ubershaders", "Async skip"),
                editable = true
            ),
            ConfigOption(GFX, SETTINGS, "MSAA", ENUM, "1", tokens("1", "2", "4", "8"), editable = true),
            ConfigOption(
                GFX, ENHANCEMENTS, "MaxAnisotropy", ENUM, "-1",
                named("-1" to "Default", "0" to "1x", "1" to "2x", "2" to "4x", "3" to "8x", "4" to "16x"),
                editable = true
            ),
            ConfigOption(
                GFX, ENHANCEMENTS, "ForceTextureFiltering", ENUM, "0",
                ordinals("Default", "Nearest", "Linear"),
                editable = true
            ),
            ConfigOption(GFX, ENHANCEMENTS, "PostProcessingShader", STRING, ""),
            ConfigOption(GFX, SETTINGS, "DriverLibName", STRING, ""),
            ConfigOption(GFX, SETTINGS, "ShowFPS", BOOL, "False", editable = true),
            ConfigOption(
                MAIN, CORE, "CPUCore", ENUM, "4",
                named("0" to "Interpreter", "1" to "JIT64", "4" to "JITARM64", "5" to "Cached interpreter")
            ),
            ConfigOption(MAIN, CORE, "CPUThread", BOOL, "True", editable = true),
            ConfigOption(MAIN, CORE, "DSPHLE", BOOL, "True"),
            ConfigOption(MAIN, DSP, "EnableJIT", BOOL, "True"),
            ConfigOption(MAIN, CORE, "MMU", BOOL, "False"),
            ConfigOption(MAIN, CORE, "EmulationSpeed", FLOAT, "1.0", editable = true, range = range(0.1, 2.0, 0.1)),
            ConfigOption(MAIN, CORE, "OverclockEnable", BOOL, "False"),
            ConfigOption(MAIN, CORE, "Overclock", FLOAT, "1.0"),
            ConfigOption(MAIN, DSP, "Backend", STRING, "OpenSLES"),
            ConfigOption(MAIN, DSP, "Volume", INT, "100", editable = true, range = range(0.0, 100.0, 5.0)),
            ConfigOption(MAIN, CORE, "EnableCheats", BOOL, "False"),
            ConfigOption(MAIN, "General", "EnablePlayTimeTracking", BOOL, "True"),
            ConfigOption(ACHIEVEMENTS, "Achievements", "Enabled", BOOL, "False"),
            ConfigOption(ACHIEVEMENTS, "Achievements", "HardcoreEnabled", BOOL, "True")
        )
    )
}
