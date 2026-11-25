package com.nendo.argosy.data.emulator.config

import com.nendo.argosy.data.emulator.config.ConfigValueType.BOOL
import com.nendo.argosy.data.emulator.config.ConfigValueType.ENUM
import com.nendo.argosy.data.emulator.config.ConfigValueType.INT
import com.nendo.argosy.data.emulator.config.ConfigValueType.STRING

internal object CemuConfigSpec {
    val SETTINGS = ConfigFileSpec(listOf("settings.xml"), ConfigFormat.XML, ConfigFileRole.PRIMARY)

    private const val GRAPHIC = "Graphic"
    private const val AUDIO = "Audio"

    private val FILTERS = ordinals("linear", "bicubic", "bicubic Hermite", "nearest")
    private val POSITIONS = ordinals("Disabled", "TL", "TC", "TR", "BL", "BC", "BR")
    private val CHANNELS = ordinals("mono", "stereo", "surround")

    private fun option(
        section: String,
        key: String,
        type: ConfigValueType,
        default: String?,
        values: List<ConfigValue> = emptyList(),
        editable: Boolean = false,
        range: ConfigRange? = null
    ) = ConfigOption(SETTINGS, section, key, type, default, values, editable, range)

    const val DRIVERS_DIR = "customDrivers"

    val CUSTOM_DRIVER_PATH = ConfigOption(SETTINGS, "", "custom_driver_path", STRING, "", driverDir = DRIVERS_DIR)

    private val VOLUME = range(0.0, 100.0, 5.0)

    val SPEC = EmulatorConfigSpec(
        files = listOf(SETTINGS),
        secrets = listOf(ConfigKeyMatcher(SETTINGS, "Account", null)),
        options = listOf(
            option(
                "", "console_language", ENUM, "1",
                ordinals("JA", "EN", "FR", "DE", "IT", "ES", "ZH", "KO", "NL", "PT", "RU", "TW"),
                editable = true
            ),
            option("", "mlc_path", STRING, ""),
            CUSTOM_DRIVER_PATH,
            option(GRAPHIC, "api", ENUM, "0", ordinals("GL", "Vulkan", "Metal")),
            option(GRAPHIC, "VSync", ENUM, "0", ordinals("Off", "Double", "Triple", "sync-and-limit"), editable = true),
            option(GRAPHIC, "UpscaleFilter", ENUM, "2", FILTERS, editable = true),
            option(GRAPHIC, "DownscaleFilter", ENUM, "0", FILTERS, editable = true),
            option(GRAPHIC, "FullscreenScaling", ENUM, "0", ordinals("keep aspect", "stretch"), editable = true),
            option(GRAPHIC, "AsyncCompile", BOOL, "true", editable = true),
            option(GRAPHIC, "vkAccurateBarriers", BOOL, "true", editable = true),
            option(GRAPHIC, "GX2DrawdoneSync", BOOL, "true", editable = true),
            option("$GRAPHIC/Overlay", "Position", ENUM, "0", POSITIONS, editable = true),
            option("$GRAPHIC/Overlay", "FPS", BOOL, "true", editable = true),
            option("$GRAPHIC/Overlay", "TextScale", INT, "100", editable = true, range = range(50.0, 300.0, 10.0)),
            option("$GRAPHIC/Notification", "Position", ENUM, "1", POSITIONS, editable = true),
            option(AUDIO, "delay", INT, "2", editable = true, range = range(0.0, 23.0)),
            option(AUDIO, "TVChannels", ENUM, "1", CHANNELS, editable = true),
            option(AUDIO, "TVVolume", INT, "20", editable = true, range = VOLUME),
            option(AUDIO, "PadVolume", INT, "0", editable = true, range = VOLUME)
        )
    )
}
