package com.nendo.argosy.ui.screens.settings.libretro

import com.nendo.argosy.data.local.entity.PlatformLibretroSettingsEntity
import com.nendo.argosy.ui.screens.settings.BuiltinVideoState

interface LibretroSettingsAccessor {
    fun getValue(setting: LibretroSettingDef): String
    fun getGlobalValue(setting: LibretroSettingDef): String
    fun hasOverride(setting: LibretroSettingDef): Boolean
    fun cycle(setting: LibretroSettingDef, direction: Int)
    fun toggle(setting: LibretroSettingDef)
    fun reset(setting: LibretroSettingDef)
}

class GlobalLibretroSettingsAccessor(
    private val state: BuiltinVideoState,
    private val onCycle: (LibretroSettingDef, Int) -> Unit,
    private val onToggle: (LibretroSettingDef, Boolean) -> Unit
) : LibretroSettingsAccessor {

    override fun getValue(setting: LibretroSettingDef): String = getGlobalValue(setting)

    override fun getGlobalValue(setting: LibretroSettingDef): String = when (setting) {
        LibretroSettingDef.Shader -> state.shader
        LibretroSettingDef.Filter -> state.filter
        LibretroSettingDef.AspectRatio -> state.aspectRatio
        LibretroSettingDef.Rotation -> state.rotation
        LibretroSettingDef.OverscanCrop -> state.overscanCrop
        LibretroSettingDef.BlackFrameInsertion -> state.blackFrameInsertion.toString()
        LibretroSettingDef.FastForwardSpeed -> state.fastForwardSpeed
        LibretroSettingDef.RewindEnabled -> state.rewindEnabled.toString()
        LibretroSettingDef.SkipDuplicateFrames -> state.skipDuplicateFrames.toString()
        LibretroSettingDef.LowLatencyAudio -> state.lowLatencyAudio.toString()
    }

    override fun hasOverride(setting: LibretroSettingDef): Boolean = false

    override fun cycle(setting: LibretroSettingDef, direction: Int) {
        onCycle(setting, direction)
    }

    override fun toggle(setting: LibretroSettingDef) {
        val current = when (setting) {
            LibretroSettingDef.BlackFrameInsertion -> state.blackFrameInsertion
            LibretroSettingDef.RewindEnabled -> state.rewindEnabled
            LibretroSettingDef.SkipDuplicateFrames -> state.skipDuplicateFrames
            LibretroSettingDef.LowLatencyAudio -> state.lowLatencyAudio
            else -> return
        }
        onToggle(setting, !current)
    }

    override fun reset(setting: LibretroSettingDef) {
        // No-op for global settings
    }
}

class PlatformLibretroSettingsAccessor(
    private val platformSettings: PlatformLibretroSettingsEntity?,
    private val globalState: BuiltinVideoState,
    private val onUpdate: (LibretroSettingDef, String?) -> Unit
) : LibretroSettingsAccessor {

    override fun getValue(setting: LibretroSettingDef): String {
        val override = getOverrideValue(setting)
        return override ?: getGlobalValue(setting)
    }

    override fun getGlobalValue(setting: LibretroSettingDef): String = when (setting) {
        LibretroSettingDef.Shader -> globalState.shader
        LibretroSettingDef.Filter -> globalState.filter
        LibretroSettingDef.AspectRatio -> globalState.aspectRatio
        LibretroSettingDef.Rotation -> globalState.rotation
        LibretroSettingDef.OverscanCrop -> globalState.overscanCrop
        LibretroSettingDef.BlackFrameInsertion -> globalState.blackFrameInsertion.toString()
        LibretroSettingDef.FastForwardSpeed -> globalState.fastForwardSpeed
        LibretroSettingDef.RewindEnabled -> globalState.rewindEnabled.toString()
        LibretroSettingDef.SkipDuplicateFrames -> globalState.skipDuplicateFrames.toString()
        LibretroSettingDef.LowLatencyAudio -> globalState.lowLatencyAudio.toString()
    }

    override fun hasOverride(setting: LibretroSettingDef): Boolean =
        getOverrideValue(setting) != null

    override fun cycle(setting: LibretroSettingDef, direction: Int) {
        val type = setting.type as? LibretroSettingDef.SettingType.Cycle ?: return
        val options = type.options
        val current = getValue(setting)
        val currentIndex = options.indexOf(current).coerceAtLeast(0)
        val nextIndex = (currentIndex + direction + options.size) % options.size
        onUpdate(setting, options[nextIndex])
    }

    override fun toggle(setting: LibretroSettingDef) {
        val current = getValue(setting).toBooleanStrictOrNull() ?: false
        onUpdate(setting, (!current).toString())
    }

    override fun reset(setting: LibretroSettingDef) {
        onUpdate(setting, null)
    }

    private fun getOverrideValue(setting: LibretroSettingDef): String? {
        val ps = platformSettings ?: return null
        return when (setting) {
            LibretroSettingDef.Shader -> ps.shader
            LibretroSettingDef.Filter -> ps.filter
            LibretroSettingDef.AspectRatio -> ps.aspectRatio
            LibretroSettingDef.Rotation -> ps.rotation?.toRotationString()
            LibretroSettingDef.OverscanCrop -> ps.overscanCrop?.toOverscanString()
            LibretroSettingDef.BlackFrameInsertion -> ps.blackFrameInsertion?.toString()
            LibretroSettingDef.FastForwardSpeed -> ps.fastForwardSpeed?.let { "${it}x" }
            LibretroSettingDef.RewindEnabled -> ps.rewindEnabled?.toString()
            LibretroSettingDef.SkipDuplicateFrames -> ps.skipDuplicateFrames?.toString()
            LibretroSettingDef.LowLatencyAudio -> ps.lowLatencyAudio?.toString()
        }
    }

    private fun Int.toRotationString(): String = when (this) {
        -1 -> "Auto"
        0 -> "0°"
        90 -> "90°"
        180 -> "180°"
        270 -> "270°"
        else -> "Auto"
    }

    private fun Int.toOverscanString(): String = when (this) {
        0 -> "Off"
        else -> "${this}px"
    }
}

class InGameLibretroSettingsAccessor(
    private val getCurrentValue: (LibretroSettingDef) -> String,
    private val onCycle: (LibretroSettingDef, Int) -> Unit,
    private val onToggle: (LibretroSettingDef) -> Unit
) : LibretroSettingsAccessor {
    override fun getValue(setting: LibretroSettingDef): String = getCurrentValue(setting)
    override fun getGlobalValue(setting: LibretroSettingDef): String = getValue(setting)
    override fun hasOverride(setting: LibretroSettingDef): Boolean = false
    override fun cycle(setting: LibretroSettingDef, direction: Int) = onCycle(setting, direction)
    override fun toggle(setting: LibretroSettingDef) = onToggle(setting)
    override fun reset(setting: LibretroSettingDef) {}
}
