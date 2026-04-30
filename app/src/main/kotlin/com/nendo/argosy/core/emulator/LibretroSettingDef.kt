package com.nendo.argosy.core.emulator

sealed class LibretroSettingDef(
    val key: String,
    val section: String,
    val title: String,
    val subtitle: String? = null,
    val type: SettingType
) {
    sealed class SettingType {
        data class Cycle(val options: List<String>) : SettingType()
        data object Switch : SettingType()
    }

    data object Shader : LibretroSettingDef(
        key = "shader",
        section = "shaders",
        title = "Shader",
        type = SettingType.Cycle(listOf("None", "Sharp", "CUT", "CUT2", "CUT3", "CRT", "LCD", "Custom"))
    )

    data object Filter : LibretroSettingDef(
        key = "filter",
        section = "shaders",
        title = "Filter",
        type = SettingType.Cycle(listOf("Auto", "Nearest", "Bilinear"))
    )

    data object AspectRatio : LibretroSettingDef(
        key = "aspectRatio",
        section = "display",
        title = "Aspect Ratio",
        type = SettingType.Cycle(listOf("Core Provided", "4:3", "3:2", "16:9", "Integer", "Stretch"))
    )

    data object Rotation : LibretroSettingDef(
        key = "rotation",
        section = "display",
        title = "Screen Rotation",
        type = SettingType.Cycle(listOf("Auto", "0°", "90°", "180°", "270°"))
    )

    data object OverscanCrop : LibretroSettingDef(
        key = "overscanCrop",
        section = "display",
        title = "Crop Overscan",
        type = SettingType.Cycle(listOf("Off", "4px", "8px", "12px", "16px"))
    )

    data object Frame : LibretroSettingDef(
        key = "frame",
        section = "shaders",
        title = "Frame",
        subtitle = "Decorative bezel around the game",
        type = SettingType.Cycle(listOf("None"))
    )

    data object BlackFrameInsertion : LibretroSettingDef(
        key = "blackFrameInsertion",
        section = "display",
        title = "Black Frame Insertion",
        subtitle = "Reduce motion blur (requires 120Hz+ display)",
        type = SettingType.Switch
    )

    data object FastForwardEnabled : LibretroSettingDef(
        key = "fastForwardEnabled",
        section = "performance",
        title = "Enable Fast Forward",
        type = SettingType.Switch
    )

    data object FastForwardSpeed : LibretroSettingDef(
        key = "fastForwardSpeed",
        section = "performance",
        title = "Fast Forward Speed",
        type = SettingType.Cycle(listOf("2x", "4x", "8x"))
    )

    data object RewindEnabled : LibretroSettingDef(
        key = "rewindEnabled",
        section = "performance",
        title = "Enable Rewind",
        subtitle = "May cause slowdowns on heavier platforms",
        type = SettingType.Switch
    )

    data object RewindSpeed : LibretroSettingDef(
        key = "rewindSpeed",
        section = "performance",
        title = "Rewind Speed",
        type = SettingType.Cycle(listOf("1x", "2x", "4x"))
    )

    data object RewindBufferDuration : LibretroSettingDef(
        key = "rewindBufferDuration",
        section = "performance",
        title = "Rewind Buffer",
        subtitle = "Seconds of gameplay kept in memory",
        type = SettingType.Cycle(listOf("5s", "15s", "30s", "60s"))
    )

    data object SkipDuplicateFrames : LibretroSettingDef(
        key = "skipDuplicateFrames",
        section = "performance",
        title = "Skip Duplicate Frames",
        subtitle = "Reduce CPU usage by skipping unchanged frames",
        type = SettingType.Switch
    )

    data object LowLatencyAudio : LibretroSettingDef(
        key = "lowLatencyAudio",
        section = "performance",
        title = "Low Latency Audio",
        subtitle = "Reduce audio delay for better responsiveness",
        type = SettingType.Switch
    )

    data object VSync : LibretroSettingDef(
        key = "vsync",
        section = "display",
        title = "VSync",
        subtitle = "Sync to display refresh rate",
        type = SettingType.Switch
    )

    data object AutoSaveState : LibretroSettingDef(
        key = "autoSaveState",
        section = "saving",
        title = "Save State on Exit",
        type = SettingType.Switch
    )

    data object AutoRestoreState : LibretroSettingDef(
        key = "autoRestoreState",
        section = "saving",
        title = "Restore State on Launch",
        type = SettingType.Switch
    )

    companion object {
        val ALL: List<LibretroSettingDef> = listOf(
            Shader,
            Filter,
            Frame,
            AspectRatio,
            Rotation,
            OverscanCrop,
            BlackFrameInsertion,
            VSync,
            FastForwardEnabled,
            FastForwardSpeed,
            RewindEnabled,
            RewindSpeed,
            RewindBufferDuration,
            SkipDuplicateFrames,
            LowLatencyAudio,
            AutoSaveState,
            AutoRestoreState
        )

        val SECTIONS: Map<String, String> = mapOf(
            "shaders" to "Shaders",
            "display" to "Display",
            "performance" to "Performance",
            "saving" to "Saving"
        )

        val SECTION_ORDER: List<String> = listOf("shaders", "display", "performance", "saving")
    }
}
