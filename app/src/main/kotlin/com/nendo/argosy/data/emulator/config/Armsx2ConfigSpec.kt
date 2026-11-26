package com.nendo.argosy.data.emulator.config

import com.nendo.argosy.data.emulator.config.ConfigValueType.BOOL
import com.nendo.argosy.data.emulator.config.ConfigValueType.ENUM
import com.nendo.argosy.data.emulator.config.ConfigValueType.FLOAT
import com.nendo.argosy.data.emulator.config.ConfigValueType.INT
import com.nendo.argosy.data.emulator.config.ConfigValueType.STRING
import com.nendo.argosy.data.emulator.savepath.Pcsx2DataFolder

internal object Armsx2ConfigSpec {
    val INI = ConfigFileSpec(
        listOf("inis/${Pcsx2DataFolder.INI_NAME}", Pcsx2DataFolder.INI_NAME),
        ConfigFormat.INI,
        ConfigFileRole.PRIMARY
    )
    val MIRROR = ConfigFileSpec(listOf("armsx2-settings.json"), ConfigFormat.JSON, ConfigFileRole.SETTINGS)

    private const val GLOBAL = "global"
    private const val PREFS_GLOBAL = "config.global"

    val PREFS = ConfigFileSpec(
        listOf("shared_prefs/ARMSX2.xml"),
        ConfigFormat.PREFS_JSON,
        ConfigFileRole.SETTINGS,
        location = ConfigFileLocation.PACKAGE_DATA,
        fallback = ConfigFallback(MIRROR, GLOBAL)
    )

    private fun option(
        key: String,
        type: ConfigValueType,
        default: String?,
        values: List<ConfigValue> = emptyList(),
        editable: Boolean = false,
        range: ConfigRange? = null
    ) = ConfigOption(PREFS, PREFS_GLOBAL, key, type, default, values, editable, range)

    val SPEC = EmulatorConfigSpec(
        files = listOf(INI, PREFS, MIRROR),
        secrets = emptyList(),
        options = listOf(
            option("renderer", ENUM, "vulkan", tokens("auto", "opengl", "vulkan", "software"), editable = true),
            option("upscaleFloat", FLOAT, "1.0", editable = true, range = range(0.25, 5.0, 0.25)),
            option("customDriverId", STRING, ""),
            option(
                "aspectRatio", ENUM, "1",
                ordinals("Stretch", "Auto 4:3/3:2", "4:3", "16:9", "10:7", "21:9", "20:9", "19.5:9", "Custom"),
                editable = true
            ),
            option(
                "deinterlaceMode", ENUM, "0",
                ordinals("Auto", "Off", "Weave", "Weave", "Bob", "Bob", "Blend", "Blend", "Adaptive", "Adaptive")
            ),
            option("vsyncEnable", BOOL, "false", editable = true),
            option(
                "accurateBlendingUnit", ENUM, "1", ordinals("Min", "Basic", "Medium", "High", "Full", "Max"),
                editable = true
            ),
            option(
                "textureFiltering", ENUM, "2", ordinals("Nearest", "Forced", "PS2", "Forced but sprite"),
                editable = true
            ),
            option(
                "hardwareDownloadMode", ENUM, "0",
                ordinals("Accurate", "ForceFull", "NoReadbacks", "Unsync", "Disabled", "Async")
            ),
            option(
                "upscaler", ENUM, "0", named("0" to "Off", "2" to "FSR1", "3" to "SGSR", "4" to "SGSR edge"),
                editable = true
            ),
            option("eeCycleRate", INT, "0"),
            option("eeCycleSkip", INT, "0"),
            option("eeClampMode", INT, "1"),
            option("mtvu", BOOL, "true"),
            option("frameLimitEnable", BOOL, "true", editable = true),
            option("nominalSpeedPercent", INT, "100"),
            option("enableCheats", BOOL, "false"),
            option("enableWideScreenPatches", BOOL, "false", editable = true),
            option("achievementsEnabled", BOOL, "true"),
            option("achievementsHardcore", BOOL, "false"),
            option("audioVolume", INT, "100", editable = true, range = range(0.0, 100.0, 5.0)),
            option("affinityMode", INT, "7"),
            option("gpuProfile", ENUM, "0", ordinals("auto", "Mali", "Adreno", "PowerVR", "Xclipse")),
            ConfigOption(
                INI, "EmuCore/GS", "Renderer", ENUM, null,
                named(
                    "-1" to "Auto", "3" to "DX11", "11" to "Null", "12" to "OGL", "13" to "SW",
                    "14" to "VK", "15" to "DX12", "17" to "Metal", "18" to "NullHW"
                )
            )
        )
    )
}
