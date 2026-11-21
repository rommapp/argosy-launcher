package com.nendo.argosy.ui.screens.settings.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.local.entity.PlatformLibretroSettingsEntity
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import com.nendo.argosy.ui.screens.settings.BuiltinVideoState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.libretro.GlobalLibretroSettingsAccessor
import com.nendo.argosy.core.emulator.LibretroSettingDef
import com.nendo.argosy.ui.screens.settings.libretro.LibretroSettingsSection
import com.nendo.argosy.ui.screens.settings.libretro.PlatformLibretroSettingsAccessor
import com.nendo.argosy.ui.screens.settings.libretro.libretroSettingsItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.libretro.libretroSettingsMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.libretro.LibretroVisibilityState
import com.nendo.argosy.ui.screens.settings.libretro.isLibretroSettingShown
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.screens.settings.search.SearchableSetting
import com.nendo.argosy.ui.theme.Dimens

internal data class BuiltinVideoLayoutState(
    val visibility: LibretroVisibilityState,
    val graphicsRows: List<BuiltinGraphicsRow>,
    val showsResetAll: Boolean
) {
    companion object {
        fun from(
            state: BuiltinVideoState,
            platformSettings: Map<Long, PlatformLibretroSettingsEntity>
        ) = BuiltinVideoLayoutState(
            visibility = LibretroVisibilityState(
                platformSlug = state.currentPlatformContext?.platformSlug,
                canEnableBFI = state.canEnableBlackFrameInsertion
            ),
            graphicsRows = builtinGraphicsRows(state),
            showsResetAll = builtinVideoShowsResetAll(state, platformSettings)
        )

        fun global(state: BuiltinVideoState) = from(state.copy(platformContextIndex = 0), emptyMap())
    }
}

internal sealed class BuiltinVideoItem(
    val key: String,
    @StringRes override val labelRes: Int
) : SearchableSetting {
    data class Graphics(val row: BuiltinGraphicsRow) : BuiltinVideoItem("graphics_${row.name}", row.titleRes)
    data class Setting(val def: LibretroSettingDef) : BuiltinVideoItem(def.key, def.title)
    data object ResetAll :
        BuiltinVideoItem("resetAllToGlobal", R.string.settings_shell_builtinvideo_reset_all_to_global)

    companion object {
        val ALL: List<BuiltinVideoItem>
            get() = BuiltinGraphicsRow.entries.map { Graphics(it) } +
                LibretroSettingDef.ALL.map { Setting(it) } +
                ResetAll
    }
}

internal val builtinVideoLayout = SettingsLayout<BuiltinVideoItem, BuiltinVideoLayoutState>(
    allItems = BuiltinVideoItem.ALL,
    isFocusable = { true },
    visibleWhen = { item, state ->
        when (item) {
            is BuiltinVideoItem.Graphics -> item.row in state.graphicsRows
            is BuiltinVideoItem.Setting -> isLibretroSettingShown(item.def, state.visibility)
            is BuiltinVideoItem.ResetAll -> state.showsResetAll
        }
    }
)

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
    val showResetAll = builtinVideoShowsResetAll(videoState, uiState.platformLibretro.platformSettings)

    val graphicsRows = builtinGraphicsRows(videoState)
    val maxSettingsFocusIndex = libretroSettingsMaxFocusIndex(
        platformSlug = platformContext?.platformSlug,
        canEnableBFI = videoState.canEnableBlackFrameInsertion
    )
    val resetAllFocusIndex = graphicsRows.size + maxSettingsFocusIndex + 1

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
                        LibretroSettingDef.PortraitPosition -> viewModel.cycleBuiltinPortraitPosition(direction)
                        LibretroSettingDef.Rotation -> viewModel.cycleBuiltinRotation(direction)
                        LibretroSettingDef.OverscanCrop -> viewModel.cycleBuiltinOverscanCrop(direction)
                        LibretroSettingDef.FastForwardSpeed -> viewModel.cycleBuiltinFastForwardSpeed(direction)
                        LibretroSettingDef.AudioVolume -> viewModel.cycleBuiltinAudioVolume(direction)
                        LibretroSettingDef.AudioLatency -> viewModel.cycleBuiltinAudioLatency(direction)
                        LibretroSettingDef.RewindSpeed -> viewModel.cycleBuiltinRewindSpeed(direction)
                        LibretroSettingDef.RewindBufferDuration -> viewModel.cycleBuiltinRewindBufferDuration(direction)
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

    LibretroSettingsSection(
        accessor = accessor,
        focusedIndex = uiState.focusedIndex,
        platformSlug = platformContext?.platformSlug,
        canEnableBFI = videoState.canEnableBlackFrameInsertion,
        listState = listState,
        leadingHeader = stringResource(R.string.settings_builtin_graphics_header),
        leadingFocusCount = graphicsRows.size,
        leadingRow = { index, isFocused ->
            graphicsRows.getOrNull(index)?.let { row ->
                BuiltinGraphicsRowItem(row, uiState, viewModel, isFocused)
            }
        },
        pickerTokenFor = { setting ->
            if (uiState.enumPickerKey == setting.key) uiState.enumPickerToken else 0
        },
        trailingContent = if (showResetAll) {
            {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
                OptionItem(
                    label = stringResource(BuiltinVideoItem.ResetAll.labelRes),
                    isFocused = uiState.focusedIndex == resetAllFocusIndex,
                    isDangerous = true,
                    onClick = { viewModel.resetAllPlatformLibretroSettings() }
                )
            }
        } else null
    )
}

fun builtinVideoShowsResetAll(
    state: BuiltinVideoState,
    platformSettings: Map<Long, com.nendo.argosy.data.local.entity.PlatformLibretroSettingsEntity>
): Boolean {
    if (state.isGlobalContext) return false
    val platformId = state.currentPlatformContext?.platformId ?: return false
    return platformSettings[platformId]?.hasAnyVideoOverrides() == true
}

fun builtinVideoMaxFocusIndex(state: BuiltinVideoState, platformSettings: Map<Long, com.nendo.argosy.data.local.entity.PlatformLibretroSettingsEntity>): Int {
    val settingsMax = libretroSettingsMaxFocusIndex(
        platformSlug = state.currentPlatformContext?.platformSlug,
        canEnableBFI = state.canEnableBlackFrameInsertion
    )
    val resetAllItem = if (builtinVideoShowsResetAll(state, platformSettings)) 1 else 0
    return builtinGraphicsRows(state).size + settingsMax + resetAllItem
}

fun builtinVideoItemAtFocusIndex(
    index: Int,
    state: BuiltinVideoState
): LibretroSettingDef? =
    libretroSettingsItemAtFocusIndex(
        index = index - builtinGraphicsRows(state).size,
        platformSlug = state.currentPlatformContext?.platformSlug,
        canEnableBFI = state.canEnableBlackFrameInsertion
    )
