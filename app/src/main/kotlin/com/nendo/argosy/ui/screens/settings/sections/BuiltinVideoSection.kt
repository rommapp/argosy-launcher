package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
                        LibretroSettingDef.RewindEnabled -> viewModel.setBuiltinRewindEnabled(enabled)
                        LibretroSettingDef.SkipDuplicateFrames -> viewModel.setBuiltinSkipDuplicateFrames(enabled)
                        LibretroSettingDef.LowLatencyAudio -> viewModel.setBuiltinLowLatencyAudio(enabled)
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
                    if (setting == LibretroSettingDef.Frame) viewModel.openFrameConfig()
                }
            )
        }
    }

    LibretroSettingsSection(
        accessor = accessor,
        focusedIndex = uiState.focusedIndex,
        platformSlug = platformContext?.platformSlug,
        canEnableBFI = videoState.canEnableBlackFrameInsertion,
        listState = listState,
        trailingContent = if (!isGlobal && hasAnyOverrides) {
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
    return if (!state.isGlobalContext && hasAnyOverrides) settingsMax + 1 else settingsMax
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
