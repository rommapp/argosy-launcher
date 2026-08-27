package com.nendo.argosy.ui.screens.settings.libretro

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.core.emulator.LibretroSettingDef
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SwitchPreference

@Composable
fun LibretroSettingItem(
    setting: LibretroSettingDef,
    accessor: LibretroSettingsAccessor,
    isFocused: Boolean,
    isPerPlatform: Boolean = false,
    enablePicker: Boolean = true,
    pickerRequestToken: Int = 0,
    modifier: Modifier = Modifier
) {
    val displayValue = accessor.getDisplayValue(setting)
    val hasOverride = accessor.hasOverride(setting)
    val globalValue = accessor.getGlobalValue(setting)

    val subtitle = when {
        isPerPlatform && hasOverride -> stringResource(
            R.string.settings_shell_libretro_global_hint_template,
            formatGlobalHint(setting, globalValue)
        )
        else -> setting.subtitle?.let { stringResource(it) }
    }

    if (accessor.isActionItem(setting)) {
        val resolvedValue = displayValue.resolve()
        val (actionTitle, actionSubtitle) = when (setting.key) {
            "frame" -> stringResource(setting.title) to resolvedValue
            else -> resolvedValue to stringResource(R.string.settings_shell_libretro_configure_shader_effects_subtitle)
        }
        NavigationPreference(
            icon = Icons.Default.Tune,
            title = actionTitle,
            subtitle = actionSubtitle,
            isFocused = isFocused,
            onClick = { accessor.onAction(setting) }
        )
        return
    }

    if (accessor.isSwitch(setting)) {
        SwitchPreference(
            title = stringResource(setting.title),
            subtitle = subtitle,
            isEnabled = accessor.isSwitchEnabled(setting),
            isFocused = isFocused,
            isCustom = isPerPlatform && hasOverride,
            showResetButton = isPerPlatform && hasOverride && isFocused,
            onToggle = { accessor.toggle(setting) },
            onReset = { accessor.reset(setting) }
        )
    } else {
        val cycleType = setting.type as? LibretroSettingDef.SettingType.Cycle
        val cycleOptions = cycleType?.labels
            ?.takeIf { enablePicker && it.size > 1 }
            ?.map { stringResource(it) }
        val resolvedValue = displayValue.resolve()
        val cycleValue = cycleType?.labelResFor(resolvedValue)?.let { stringResource(it) } ?: resolvedValue
        CyclePreference(
            title = stringResource(setting.title),
            subtitle = subtitle,
            value = cycleValue,
            isFocused = isFocused,
            isCustom = isPerPlatform && hasOverride,
            showResetButton = isPerPlatform && hasOverride && isFocused,
            onClick = { accessor.cycle(setting, 1) },
            onPrev = { accessor.cycle(setting, -1) },
            onReset = { accessor.reset(setting) },
            options = cycleOptions,
            onSelect = cycleOptions?.let { _ ->
                { index: Int ->
                    val current = cycleType.options
                        .indexOf(accessor.getValue(setting)).coerceAtLeast(0)
                    accessor.cycle(setting, index - current)
                }
            },
            pickerRequestToken = pickerRequestToken
        )
    }
}

@Composable
private fun SettingDisplayValue.resolve(): String = when (this) {
    is SettingDisplayValue.Raw -> text
    is SettingDisplayValue.Resource -> stringResource(resId)
}

@Composable
private fun formatGlobalHint(setting: LibretroSettingDef, globalValue: String): String {
    return when (val type = setting.type) {
        LibretroSettingDef.SettingType.Switch -> if (globalValue == "true") {
            stringResource(R.string.settings_shell_libretro_on_label)
        } else {
            stringResource(R.string.settings_shell_libretro_off_label)
        }
        is LibretroSettingDef.SettingType.Cycle ->
            type.labelResFor(globalValue)?.let { stringResource(it) } ?: globalValue
    }
}
