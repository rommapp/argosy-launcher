package com.nendo.argosy.ui.screens.settings.libretro

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SwitchPreference

@Composable
fun LibretroSettingItem(
    setting: LibretroSettingDef,
    accessor: LibretroSettingsAccessor,
    isFocused: Boolean,
    isPerPlatform: Boolean = false,
    modifier: Modifier = Modifier
) {
    val value = accessor.getDisplayValue(setting)
    val hasOverride = accessor.hasOverride(setting)
    val globalValue = accessor.getGlobalValue(setting)

    val subtitle = when {
        isPerPlatform && hasOverride -> "Global: ${formatGlobalHint(setting, globalValue)}"
        else -> setting.subtitle
    }

    if (accessor.isActionItem(setting)) {
        val (actionTitle, actionSubtitle) = when (setting.key) {
            "frame" -> setting.title to value
            else -> value to "Configure shader effects"
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
            title = setting.title,
            subtitle = subtitle,
            isEnabled = value.toBooleanStrictOrNull() ?: (value == "On"),
            isFocused = isFocused,
            isCustom = isPerPlatform && hasOverride,
            showResetButton = isPerPlatform && hasOverride && isFocused,
            onToggle = { accessor.toggle(setting) },
            onReset = { accessor.reset(setting) }
        )
    } else {
        CyclePreference(
            title = setting.title,
            subtitle = subtitle,
            value = value,
            isFocused = isFocused,
            isCustom = isPerPlatform && hasOverride,
            showResetButton = isPerPlatform && hasOverride && isFocused,
            onClick = { accessor.cycle(setting, 1) },
            onReset = { accessor.reset(setting) }
        )
    }
}

private fun formatGlobalHint(setting: LibretroSettingDef, globalValue: String): String {
    return when (setting.type) {
        LibretroSettingDef.SettingType.Switch -> if (globalValue == "true") "On" else "Off"
        is LibretroSettingDef.SettingType.Cycle -> globalValue
    }
}
