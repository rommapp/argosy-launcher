package com.nendo.argosy.ui.screens.settings.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.core.emulator.LibretroSettingDef
import com.nendo.argosy.data.local.entity.PlatformLibretroSettingsEntity
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.screens.settings.BuiltinVideoState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.settingsRowItem
import com.nendo.argosy.ui.screens.settings.libretro.GlobalLibretroSettingsAccessor
import com.nendo.argosy.ui.screens.settings.libretro.LibretroSettingsScope
import com.nendo.argosy.ui.screens.settings.libretro.LibretroSettingsSection
import com.nendo.argosy.ui.screens.settings.libretro.PlatformLibretroSettingsAccessor
import com.nendo.argosy.ui.screens.settings.libretro.libretroSettingsItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.libretro.libretroSettingsMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.libretro.LibretroVisibilityState
import com.nendo.argosy.ui.screens.settings.libretro.isLibretroSettingShown
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.screens.settings.search.SearchableSetting

internal data class BuiltinSavesLayoutState(
    val visibility: LibretroVisibilityState,
    val showsPathRows: Boolean
) {
    companion object {
        fun from(
            state: BuiltinVideoState,
            platformSettings: Map<Long, PlatformLibretroSettingsEntity>
        ) = BuiltinSavesLayoutState(
            visibility = LibretroVisibilityState(
                platformSlug = state.currentPlatformContext?.platformSlug,
                canEnableBFI = state.canEnableBlackFrameInsertion,
                scope = LibretroSettingsScope.SAVES
            ),
            showsPathRows = builtinSavesHasPathRows(state, platformSettings)
        )

        fun global(state: BuiltinVideoState) = from(state.copy(platformContextIndex = 0), emptyMap())
    }
}

internal sealed class BuiltinSavesItem(
    val key: String,
    @StringRes override val labelRes: Int
) : SearchableSetting {
    data class Setting(val def: LibretroSettingDef) : BuiltinSavesItem(def.key, def.title)
    data object SavePath : BuiltinSavesItem("save_path", R.string.settings_shell_builtinvideo_save_path_title)
    data object StatePath : BuiltinSavesItem("state_path", R.string.settings_shell_builtinvideo_state_path_title)

    companion object {
        val ALL: List<BuiltinSavesItem>
            get() = LibretroSettingDef.ALL.map { Setting(it) } + listOf(SavePath, StatePath)
    }
}

internal val builtinSavesLayout = SettingsLayout<BuiltinSavesItem, BuiltinSavesLayoutState>(
    allItems = BuiltinSavesItem.ALL,
    isFocusable = { true },
    visibleWhen = { item, state ->
        when (item) {
            is BuiltinSavesItem.Setting -> isLibretroSettingShown(item.def, state.visibility)
            is BuiltinSavesItem.SavePath, is BuiltinSavesItem.StatePath -> state.showsPathRows
        }
    }
)

@Composable
fun BuiltinSavesSection(
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

    val maxSettingsFocusIndex = builtinSavesSettingsMaxFocusIndex(videoState)
    val savePathFocusIndex = maxSettingsFocusIndex + 1
    val statePathFocusIndex = maxSettingsFocusIndex + 2

    val accessor = remember(videoState, platformSettings) {
        if (isGlobal) {
            GlobalLibretroSettingsAccessor(
                state = videoState,
                onCycle = { _, _ -> },
                onToggle = { setting, enabled ->
                    when (setting) {
                        LibretroSettingDef.AutoSaveState -> viewModel.setBuiltinAutoSaveState(enabled)
                        LibretroSettingDef.AutoRestoreState -> viewModel.setBuiltinAutoRestoreState(enabled)
                        LibretroSettingDef.PreferNewerServerSave -> viewModel.setBuiltinPreferNewerServerSave(enabled)
                        LibretroSettingDef.HwCoreSaveStates -> viewModel.setBuiltinHwCoreSaveStates(enabled)
                        else -> {}
                    }
                }
            )
        } else {
            PlatformLibretroSettingsAccessor(
                platformSettings = platformSettings,
                globalState = videoState,
                onUpdate = { setting, value -> viewModel.updatePlatformLibretroSetting(setting, value) }
            )
        }
    }

    val effectiveSavePath = if (isGlobal) videoState.savePath else (platformSettings?.savePath ?: videoState.savePath)
    val effectiveStatePath = if (isGlobal) videoState.statePath else (platformSettings?.statePath ?: videoState.statePath)
    val hasPlatformSaveOverride = !isGlobal && platformSettings?.savePath != null
    val hasPlatformStateOverride = !isGlobal && platformSettings?.statePath != null

    val showPathItems = builtinSavesHasPathRows(videoState, uiState.platformLibretro.platformSettings)

    LibretroSettingsSection(
        accessor = accessor,
        focusedIndex = uiState.focusedIndex,
        platformSlug = platformContext?.platformSlug,
        canEnableBFI = videoState.canEnableBlackFrameInsertion,
        scope = LibretroSettingsScope.SAVES,
        listState = listState,
        pickerTokenFor = { setting ->
            if (uiState.enumPickerKey == setting.key) uiState.enumPickerToken else 0
        },
        trailingItems = if (showPathItems) {
            {
                settingsRowItem(key = BuiltinSavesItem.SavePath.key, focusIndex = savePathFocusIndex) {
                    ActionPreference(
                        title = stringResource(BuiltinSavesItem.SavePath.labelRes),
                        subtitle = formatStoragePath(effectiveSavePath),
                        trailingText = when {
                            isGlobal && videoState.isCustomSavePath ->
                                stringResource(R.string.settings_shell_builtinvideo_save_trailing_custom)
                            !isGlobal && hasPlatformSaveOverride ->
                                stringResource(R.string.settings_shell_builtinvideo_save_trailing_custom_platform)
                            !isGlobal -> stringResource(R.string.settings_shell_builtinvideo_save_trailing_from_global)
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
                settingsRowItem(key = BuiltinSavesItem.StatePath.key, focusIndex = statePathFocusIndex) {
                    ActionPreference(
                        title = stringResource(BuiltinSavesItem.StatePath.labelRes),
                        subtitle = formatStoragePath(effectiveStatePath),
                        trailingText = when {
                            isGlobal && videoState.isCustomStatePath ->
                                stringResource(R.string.settings_shell_builtinvideo_state_trailing_custom)
                            !isGlobal && hasPlatformStateOverride ->
                                stringResource(R.string.settings_shell_builtinvideo_state_trailing_custom_platform)
                            !isGlobal -> stringResource(R.string.settings_shell_builtinvideo_state_trailing_from_global)
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

internal fun builtinSavesSettingsMaxFocusIndex(state: BuiltinVideoState): Int =
    libretroSettingsMaxFocusIndex(
        platformSlug = state.currentPlatformContext?.platformSlug,
        canEnableBFI = state.canEnableBlackFrameInsertion,
        scope = LibretroSettingsScope.SAVES
    )

internal fun builtinSavesHasPathRows(
    state: BuiltinVideoState,
    platformSettings: Map<Long, PlatformLibretroSettingsEntity>
): Boolean {
    val platformOverride = if (state.isGlobalContext) {
        null
    } else {
        state.currentPlatformContext?.let { platformSettings[it.platformId]?.savePath }
    }
    return (platformOverride ?: state.savePath).isNotEmpty()
}

fun builtinSavesMaxFocusIndex(
    state: BuiltinVideoState,
    platformSettings: Map<Long, PlatformLibretroSettingsEntity>
): Int {
    val pathItems = if (builtinSavesHasPathRows(state, platformSettings)) 2 else 0
    return builtinSavesSettingsMaxFocusIndex(state) + pathItems
}

fun builtinSavesItemAtFocusIndex(
    index: Int,
    state: BuiltinVideoState
): LibretroSettingDef? =
    libretroSettingsItemAtFocusIndex(
        index = index,
        platformSlug = state.currentPlatformContext?.platformSlug,
        canEnableBFI = state.canEnableBlackFrameInsertion,
        scope = LibretroSettingsScope.SAVES
    )
