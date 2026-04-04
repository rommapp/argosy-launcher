package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import com.nendo.argosy.ui.screens.settings.BuiltinVideoState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.libretro.GlobalLibretroSettingsAccessor
import com.nendo.argosy.ui.screens.settings.libretro.LibretroSettingDef
import com.nendo.argosy.ui.screens.settings.libretro.LibretroSettingsSection
import com.nendo.argosy.ui.screens.settings.libretro.PlatformLibretroSettingsAccessor
import com.nendo.argosy.ui.screens.settings.libretro.libretroSettingsItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.libretro.libretroSettingsMaxFocusIndex
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun BuiltinVideoSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val listState = rememberLazyListState()
    val videoState = uiState.builtinVideo
    val isGlobal = videoState.isGlobalContext
    val platformContext = videoState.currentPlatformContext
    val platformSettings = platformContext?.let {
        uiState.platformLibretro.platformSettings[it.platformId]
    }
    val hasAnyOverrides = platformSettings?.hasAnyOverrides() == true

    val maxSettingsFocusIndex = libretroSettingsMaxFocusIndex(
        platformSlug = platformContext?.platformSlug,
        canEnableBFI = videoState.canEnableBlackFrameInsertion
    )
    val resetAllFocusIndex = maxSettingsFocusIndex + 1

    val accessor = remember(videoState, platformSettings) {
        if (isGlobal) {
            GlobalLibretroSettingsAccessor(
                state = videoState,
                onCycle = { setting, direction ->
                    when (setting) {
                        LibretroSettingDef.Shader -> viewModel.cycleBuiltinShader(direction)
                        LibretroSettingDef.Filter -> {
                            if (videoState.shader == "Custom") {
                                viewModel.openShaderChainConfig()
                            } else {
                                viewModel.cycleBuiltinFilter(direction)
                            }
                        }
                        LibretroSettingDef.AspectRatio -> viewModel.cycleBuiltinAspectRatio(direction)
                        LibretroSettingDef.Rotation -> viewModel.cycleBuiltinRotation(direction)
                        LibretroSettingDef.OverscanCrop -> viewModel.cycleBuiltinOverscanCrop(direction)
                        LibretroSettingDef.FastForwardSpeed -> viewModel.cycleBuiltinFastForwardSpeed(direction)
                        else -> {}
                    }
                },
                onToggle = { setting, enabled ->
                    when (setting) {
                        LibretroSettingDef.Frame -> viewModel.setBuiltinFramesEnabled(enabled)
                        LibretroSettingDef.BlackFrameInsertion -> viewModel.setBuiltinBlackFrameInsertion(enabled)
                        LibretroSettingDef.FastForwardEnabled -> viewModel.setBuiltinFastForwardEnabled(enabled)
                        LibretroSettingDef.RewindEnabled -> viewModel.setBuiltinRewindEnabled(enabled)
                        LibretroSettingDef.SkipDuplicateFrames -> viewModel.setBuiltinSkipDuplicateFrames(enabled)
                        LibretroSettingDef.LowLatencyAudio -> viewModel.setBuiltinLowLatencyAudio(enabled)
                        LibretroSettingDef.AutoSaveState -> viewModel.setBuiltinAutoSaveState(enabled)
                        LibretroSettingDef.AutoRestoreState -> viewModel.setBuiltinAutoRestoreState(enabled)
                        else -> {}
                    }
                },
                onActionCallback = { setting ->
                    if (setting.key == "filter") viewModel.openShaderChainConfig()
                }
            )
        } else {
            PlatformLibretroSettingsAccessor(
                platformSettings = platformSettings,
                globalState = videoState,
                onUpdate = { setting, value -> viewModel.updatePlatformLibretroSetting(setting, value) },
                onActionCallback = { setting ->
                    when (setting) {
                        LibretroSettingDef.Frame -> viewModel.openFrameConfig()
                        LibretroSettingDef.Filter -> viewModel.openShaderChainConfig()
                        else -> {}
                    }
                }
            )
        }
    }

    val showResetAll = !isGlobal && hasAnyOverrides
    // In platform context, Reset All is immediately after settings. Save/State paths come after that when shown.
    val resetAllExtra = if (showResetAll) 1 else 0
    val savePathFocusIndex = maxSettingsFocusIndex + 1 + resetAllExtra
    val statePathFocusIndex = maxSettingsFocusIndex + 2 + resetAllExtra

    // Effective path values depend on context. In platform context, platform override wins over global.
    val effectiveSavePath = if (isGlobal) videoState.savePath else (platformSettings?.savePath ?: videoState.savePath)
    val effectiveStatePath = if (isGlobal) videoState.statePath else (platformSettings?.statePath ?: videoState.statePath)
    val hasPlatformSaveOverride = !isGlobal && platformSettings?.savePath != null
    val hasPlatformStateOverride = !isGlobal && platformSettings?.statePath != null

    val showPathItems = effectiveSavePath.isNotEmpty()

    LibretroSettingsSection(
        accessor = accessor,
        focusedIndex = uiState.focusedIndex,
        platformSlug = platformContext?.platformSlug,
        canEnableBFI = videoState.canEnableBlackFrameInsertion,
        listState = listState,
        trailingContent = if (showResetAll) {
            {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
                OptionItem(
                    label = "Reset All to Global",
                    isFocused = uiState.focusedIndex == resetAllFocusIndex,
                    isDangerous = true,
                    onClick = { viewModel.resetAllPlatformLibretroSettings() }
                )
            }
        } else null,
        trailingItems = if (showPathItems) {
            {
                item(key = "save_path") {
                    CyclePreference(
                        title = "Save File Path",
                        value = formatStoragePath(effectiveSavePath),
                        subtitle = when {
                            isGlobal && videoState.isCustomSavePath -> "(custom)"
                            !isGlobal && hasPlatformSaveOverride -> "(custom for this platform)"
                            !isGlobal -> "(from global)"
                            else -> null
                        },
                        isFocused = uiState.focusedIndex == savePathFocusIndex,
                        onClick = {
                            if (isGlobal) viewModel.openBuiltinSavePathBrowser()
                            else platformContext?.let { viewModel.openPlatformBuiltinSavePathBrowser(it.platformId) }
                        },
                        showResetButton = if (isGlobal) videoState.isCustomSavePath else hasPlatformSaveOverride,
                        onReset = {
                            if (isGlobal) viewModel.resetBuiltinSavePath()
                            else platformContext?.let { viewModel.resetPlatformBuiltinSavePath(it.platformId) }
                        }
                    )
                }
                item(key = "state_path") {
                    CyclePreference(
                        title = "State Path",
                        value = formatStoragePath(effectiveStatePath),
                        subtitle = when {
                            isGlobal && videoState.isCustomStatePath -> "(custom)"
                            !isGlobal && hasPlatformStateOverride -> "(custom for this platform)"
                            !isGlobal -> "(from global)"
                            else -> null
                        },
                        isFocused = uiState.focusedIndex == statePathFocusIndex,
                        onClick = {
                            if (isGlobal) viewModel.openBuiltinStatePathBrowser()
                            else platformContext?.let { viewModel.openPlatformBuiltinStatePathBrowser(it.platformId) }
                        },
                        showResetButton = if (isGlobal) videoState.isCustomStatePath else hasPlatformStateOverride,
                        onReset = {
                            if (isGlobal) viewModel.resetBuiltinStatePath()
                            else platformContext?.let { viewModel.resetPlatformBuiltinStatePath(it.platformId) }
                        }
                    )
                }
            }
        } else null
    )
}

fun builtinVideoMaxFocusIndex(state: BuiltinVideoState, platformSettings: Map<Long, com.nendo.argosy.data.local.entity.PlatformLibretroSettingsEntity>): Int {
    val platformContext = state.currentPlatformContext
    val hasAnyOverrides = platformContext?.let { platformSettings[it.platformId]?.hasAnyOverrides() } == true
    val settingsMax = libretroSettingsMaxFocusIndex(
        platformSlug = platformContext?.platformSlug,
        canEnableBFI = state.canEnableBlackFrameInsertion
    )
    // Path items render in both global and platform contexts when the global save path is known.
    val pathItems = if (state.savePath.isNotEmpty()) 2 else 0
    val resetAllItem = if (!state.isGlobalContext && hasAnyOverrides) 1 else 0
    return settingsMax + resetAllItem + pathItems
}

fun builtinVideoItemAtFocusIndex(
    index: Int,
    state: BuiltinVideoState
): LibretroSettingDef? =
    libretroSettingsItemAtFocusIndex(
        index = index,
        platformSlug = state.currentPlatformContext?.platformSlug,
        canEnableBFI = state.canEnableBlackFrameInsertion
    )
