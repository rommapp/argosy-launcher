package com.nendo.argosy.ui.screens.settings.libretro

import com.nendo.argosy.data.local.entity.PlatformLibretroSettingsEntity
import com.nendo.argosy.ui.screens.settings.BuiltinVideoState

interface LibretroSettingsAccessor {
    fun getValue(setting: LibretroSettingDef): String
    fun getDisplayValue(setting: LibretroSettingDef): String = getValue(setting)
    fun getGlobalValue(setting: LibretroSettingDef): String
    fun hasOverride(setting: LibretroSettingDef): Boolean
    fun isActionItem(setting: LibretroSettingDef): Boolean = false
    fun isSwitch(setting: LibretroSettingDef): Boolean = setting.type == LibretroSettingDef.SettingType.Switch
    fun cycle(setting: LibretroSettingDef, direction: Int)
    fun toggle(setting: LibretroSettingDef)
    fun reset(setting: LibretroSettingDef)
    fun onAction(setting: LibretroSettingDef) { cycle(setting, 1) }
}

class GlobalLibretroSettingsAccessor(
    private val state: BuiltinVideoState,
    private val onCycle: (LibretroSettingDef, Int) -> Unit,
    private val onToggle: (LibretroSettingDef, Boolean) -> Unit,
    private val onActionCallback: (LibretroSettingDef) -> Unit = {}
) : LibretroSettingsAccessor {

    override fun getValue(setting: LibretroSettingDef): String = getGlobalValue(setting)

    override fun getDisplayValue(setting: LibretroSettingDef): String = when {
        setting == LibretroSettingDef.Shader -> state.shaderDisplayValue
        setting == LibretroSettingDef.Frame -> if (state.framesEnabled) "On" else "Off"
        setting.key == "filter" && state.shader == "Custom" -> "Configure Shader Chain"
        else -> getValue(setting)
    }

    override fun getGlobalValue(setting: LibretroSettingDef): String = when (setting) {
        LibretroSettingDef.Shader -> state.shader
        LibretroSettingDef.Filter -> state.filter
        LibretroSettingDef.AspectRatio -> state.aspectRatio
        LibretroSettingDef.Rotation -> state.rotation
        LibretroSettingDef.OverscanCrop -> state.overscanCrop
        LibretroSettingDef.Frame -> state.framesEnabled.toString()
        LibretroSettingDef.BlackFrameInsertion -> state.blackFrameInsertion.toString()
        LibretroSettingDef.FastForwardSpeed -> state.fastForwardSpeed
        LibretroSettingDef.RewindEnabled -> state.rewindEnabled.toString()
        LibretroSettingDef.SkipDuplicateFrames -> state.skipDuplicateFrames.toString()
        LibretroSettingDef.LowLatencyAudio -> state.lowLatencyAudio.toString()
        LibretroSettingDef.ForceSoftwareTiming -> state.forceSoftwareTiming.toString()
    }

    override fun hasOverride(setting: LibretroSettingDef): Boolean = false

    override fun isActionItem(setting: LibretroSettingDef): Boolean =
        setting.key == "filter" && state.shader == "Custom"

    override fun isSwitch(setting: LibretroSettingDef): Boolean =
        setting.type == LibretroSettingDef.SettingType.Switch || setting == LibretroSettingDef.Frame

    override fun cycle(setting: LibretroSettingDef, direction: Int) {
        onCycle(setting, direction)
    }

    override fun toggle(setting: LibretroSettingDef) {
        val current = when (setting) {
            LibretroSettingDef.Frame -> state.framesEnabled
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

    override fun onAction(setting: LibretroSettingDef) {
        onActionCallback(setting)
    }
}

class PlatformLibretroSettingsAccessor(
    private val platformSettings: PlatformLibretroSettingsEntity?,
    private val globalState: BuiltinVideoState,
    private val onUpdate: (LibretroSettingDef, String?) -> Unit,
    private val onActionCallback: (LibretroSettingDef) -> Unit = {}
) : LibretroSettingsAccessor {

    override fun getValue(setting: LibretroSettingDef): String {
        if (setting == LibretroSettingDef.Frame) {
            return platformSettings?.frame ?: "Auto"
        }
        val override = getOverrideValue(setting)
        return override ?: getGlobalValue(setting)
    }

    override fun getDisplayValue(setting: LibretroSettingDef): String {
        if (setting == LibretroSettingDef.Frame) {
            val value = platformSettings?.frame
            return when {
                value == null -> "Auto"
                value == "none" -> "None"
                else -> value.replaceFirstChar { it.uppercase() }
            }
        }
        if (setting == LibretroSettingDef.Filter && getEffectiveShader() == "Custom") {
            return "Configure Shader Chain"
        }
        val value = getValue(setting)
        return when (setting.type) {
            LibretroSettingDef.SettingType.Switch -> if (value == "true") "On" else "Off"
            is LibretroSettingDef.SettingType.Cycle -> value
        }
    }

    private fun getEffectiveShader(): String {
        return platformSettings?.shader ?: globalState.shader
    }

    override fun getGlobalValue(setting: LibretroSettingDef): String = when (setting) {
        LibretroSettingDef.Shader -> globalState.shader
        LibretroSettingDef.Filter -> globalState.filter
        LibretroSettingDef.AspectRatio -> globalState.aspectRatio
        LibretroSettingDef.Rotation -> globalState.rotation
        LibretroSettingDef.OverscanCrop -> globalState.overscanCrop
        LibretroSettingDef.Frame -> "Auto"
        LibretroSettingDef.BlackFrameInsertion -> globalState.blackFrameInsertion.toString()
        LibretroSettingDef.FastForwardSpeed -> globalState.fastForwardSpeed
        LibretroSettingDef.RewindEnabled -> globalState.rewindEnabled.toString()
        LibretroSettingDef.SkipDuplicateFrames -> globalState.skipDuplicateFrames.toString()
        LibretroSettingDef.LowLatencyAudio -> globalState.lowLatencyAudio.toString()
        LibretroSettingDef.ForceSoftwareTiming -> globalState.forceSoftwareTiming.toString()
    }

    override fun hasOverride(setting: LibretroSettingDef): Boolean {
        if (setting == LibretroSettingDef.Frame) {
            return platformSettings?.frame != null
        }
        return getOverrideValue(setting) != null
    }

    override fun isActionItem(setting: LibretroSettingDef): Boolean =
        setting == LibretroSettingDef.Frame ||
        (setting == LibretroSettingDef.Filter && getEffectiveShader() == "Custom")

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

    override fun onAction(setting: LibretroSettingDef) {
        onActionCallback(setting)
    }

    private fun getOverrideValue(setting: LibretroSettingDef): String? {
        val ps = platformSettings ?: return null
        return when (setting) {
            LibretroSettingDef.Shader -> ps.shader
            LibretroSettingDef.Filter -> ps.filter
            LibretroSettingDef.AspectRatio -> ps.aspectRatio
            LibretroSettingDef.Rotation -> ps.rotation?.toRotationString()
            LibretroSettingDef.OverscanCrop -> ps.overscanCrop?.toOverscanString()
            LibretroSettingDef.Frame -> ps.frame
            LibretroSettingDef.BlackFrameInsertion -> ps.blackFrameInsertion?.toString()
            LibretroSettingDef.FastForwardSpeed -> ps.fastForwardSpeed?.let { "${it}x" }
            LibretroSettingDef.RewindEnabled -> ps.rewindEnabled?.toString()
            LibretroSettingDef.SkipDuplicateFrames -> ps.skipDuplicateFrames?.toString()
            LibretroSettingDef.LowLatencyAudio -> ps.lowLatencyAudio?.toString()
            LibretroSettingDef.ForceSoftwareTiming -> null
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
    private val globalValue: (LibretroSettingDef) -> String,
    private val onCycle: (LibretroSettingDef, Int) -> Unit,
    private val onToggle: (LibretroSettingDef) -> Unit,
    private val onReset: (LibretroSettingDef) -> Unit,
    private val onActionCallback: (LibretroSettingDef) -> Unit = {}
) : LibretroSettingsAccessor {
    override fun getValue(setting: LibretroSettingDef): String = getCurrentValue(setting)
    override fun getDisplayValue(setting: LibretroSettingDef): String = when (setting) {
        LibretroSettingDef.Filter -> if (getCurrentValue(LibretroSettingDef.Shader) == "Custom") {
            "Configure Shader Chain"
        } else {
            getValue(setting)
        }
        LibretroSettingDef.Frame -> getValue(setting)
        else -> getValue(setting)
    }
    override fun getGlobalValue(setting: LibretroSettingDef): String = globalValue(setting)
    override fun hasOverride(setting: LibretroSettingDef): Boolean =
        getCurrentValue(setting) != globalValue(setting)
    override fun isActionItem(setting: LibretroSettingDef): Boolean =
        setting.key == "frame" ||
        (setting.key == "filter" && getCurrentValue(LibretroSettingDef.Shader) == "Custom")
    override fun cycle(setting: LibretroSettingDef, direction: Int) = onCycle(setting, direction)
    override fun toggle(setting: LibretroSettingDef) = onToggle(setting)
    override fun reset(setting: LibretroSettingDef) = onReset(setting)
    override fun onAction(setting: LibretroSettingDef) = onActionCallback(setting)
}
