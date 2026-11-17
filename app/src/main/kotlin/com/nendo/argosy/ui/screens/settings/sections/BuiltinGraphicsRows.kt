package com.nendo.argosy.ui.screens.settings.sections

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.emulator.GpuDriverToken
import com.nendo.argosy.ui.common.GpuDriverBlock
import com.nendo.argosy.ui.common.GpuDriverPickerEntry
import com.nendo.argosy.ui.common.VulkanDriverOption
import com.nendo.argosy.ui.common.gpuDriverBlock
import com.nendo.argosy.ui.common.gpuDriverPickerEntries
import com.nendo.argosy.ui.common.label
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.InfoPreference
import com.nendo.argosy.ui.screens.settings.BuiltinVideoState
import com.nendo.argosy.ui.screens.settings.GPU_DRIVER_PICKER_KEY
import com.nendo.argosy.ui.screens.settings.GRAPHICS_BACKEND_PICKER_KEY
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.currentGpuDriverToken
import com.nendo.argosy.ui.screens.settings.platformGpuDriverOverride
import com.nendo.argosy.ui.screens.settings.selectableGpuDriverTokens

enum class BuiltinGraphicsRow { BACKEND, GPU_DRIVER }

@get:StringRes
val BuiltinGraphicsRow.titleRes: Int
    get() = when (this) {
        BuiltinGraphicsRow.BACKEND -> R.string.settings_builtin_graphics_backend_title
        BuiltinGraphicsRow.GPU_DRIVER -> R.string.settings_builtin_gpu_driver_title
    }

private val BuiltinVideoState.graphicsLoaded: Boolean
    get() = graphics.isLoaded && graphics.loadedPlatformId == currentPlatformContext?.platformId

fun builtinGraphicsRows(state: BuiltinVideoState): List<BuiltinGraphicsRow> = buildList {
    if (state.currentPlatformContext != null && state.graphicsLoaded && state.graphics.backend != null) {
        add(BuiltinGraphicsRow.BACKEND)
    }
    add(BuiltinGraphicsRow.GPU_DRIVER)
}

fun builtinGraphicsRowAtFocusIndex(index: Int, state: BuiltinVideoState): BuiltinGraphicsRow? =
    builtinGraphicsRows(state).getOrNull(index)

/**
 * Why the GPU driver row is read-only here, or null when it can be changed. Until the graphics
 * for the current context have loaded the row stays read-only as [GpuDriverBlock.CORE_UNSUPPORTED].
 */
fun builtinGpuDriverBlock(state: BuiltinVideoState): GpuDriverBlock? {
    val graphics = state.graphics
    if (!state.graphicsLoaded) return GpuDriverBlock.CORE_UNSUPPORTED
    if (state.currentPlatformContext == null) return gpuDriverBlock(null, null, graphics.customDriversSupported)
    val backend = graphics.backend
    return gpuDriverBlock(
        backendSupportsVulkan = backend?.supportsVulkan ?: false,
        backendIsVulkan = backend?.isVulkan,
        customDriversSupported = graphics.customDriversSupported,
        coreAcceptsCustomDriver = backend?.acceptsCustomDriver ?: true
    )
}

fun builtinGraphicsRowHasOverride(row: BuiltinGraphicsRow, uiState: SettingsUiState): Boolean = when (row) {
    BuiltinGraphicsRow.BACKEND -> uiState.builtinVideo.graphics.backend?.isOverridden == true
    BuiltinGraphicsRow.GPU_DRIVER -> platformGpuDriverOverride(uiState) != null
}

@Composable
fun BuiltinGraphicsRowItem(
    row: BuiltinGraphicsRow,
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    isFocused: Boolean
) {
    when (row) {
        BuiltinGraphicsRow.BACKEND -> BackendRow(uiState, viewModel, isFocused)
        BuiltinGraphicsRow.GPU_DRIVER -> GpuDriverRow(uiState, viewModel, isFocused)
    }
}

@Composable
private fun BackendRow(uiState: SettingsUiState, viewModel: SettingsViewModel, isFocused: Boolean) {
    val backend = uiState.builtinVideo.graphics.backend ?: return
    val labels = backend.options.map { it.label() }
    val currentIndex = backend.options.indexOfFirst { it.token == backend.currentValue }
    val subtitle = if (backend.isCoreOption) {
        stringResource(R.string.settings_builtin_graphics_backend_core_subtitle, backend.coreName)
    } else {
        stringResource(R.string.settings_builtin_graphics_backend_platform_subtitle, backend.coreName)
    }
    CyclePreference(
        title = stringResource(BuiltinGraphicsRow.BACKEND.titleRes),
        subtitle = subtitle,
        value = labels.getOrNull(currentIndex) ?: backend.currentValue,
        isFocused = isFocused,
        isCustom = backend.isOverridden,
        showResetButton = backend.isOverridden && isFocused,
        onClick = { viewModel.cycleGraphicsBackend(1) },
        onPrev = { viewModel.cycleGraphicsBackend(-1) },
        onReset = { viewModel.resetGraphicsBackend() },
        options = labels.takeIf { it.size > 1 },
        onSelect = { index -> viewModel.selectGraphicsBackend(index) },
        pickerRequestToken = if (uiState.enumPickerKey == GRAPHICS_BACKEND_PICKER_KEY) uiState.enumPickerToken else 0
    )
}

@Composable
private fun GpuDriverRow(uiState: SettingsUiState, viewModel: SettingsViewModel, isFocused: Boolean) {
    val video = uiState.builtinVideo
    val graphics = video.graphics
    val title = stringResource(BuiltinGraphicsRow.GPU_DRIVER.titleRes)
    val current = currentGpuDriverToken(uiState)
    val currentLabel = gpuDriverLabel(current, graphics.installedDrivers)
    val block = builtinGpuDriverBlock(video)
    if (block != null) {
        val loadedCoreName = graphics.coreName.takeIf { video.graphicsLoaded }
        InfoPreference(
            title = title,
            value = currentLabel,
            isFocused = isFocused,
            subtitle = gpuDriverBlockReason(block, loadedCoreName)
        )
        return
    }

    val entries = gpuDriverPickerEntries(selectableGpuDriverTokens(graphics), graphics.installablePackages)
    val entryLabels = entries.map { entry ->
        when (entry) {
            is GpuDriverPickerEntry.Value -> gpuDriverLabel(entry.token ?: GpuDriverToken.SYSTEM, graphics.installedDrivers)
            is GpuDriverPickerEntry.Install -> stringResource(R.string.settings_builtin_gpu_driver_install, entry.fileName)
        }
    }
    val override = platformGpuDriverOverride(uiState)
    val subtitle = when {
        graphics.installingPackage != null ->
            stringResource(R.string.settings_builtin_gpu_driver_installing, graphics.installingPackage)
        !video.isGlobalContext && override != null -> stringResource(
            R.string.settings_builtin_gpu_driver_global_hint,
            gpuDriverLabel(video.gpuDriver, graphics.installedDrivers)
        )
        else -> stringResource(R.string.settings_builtin_gpu_driver_subtitle)
    }
    CyclePreference(
        title = title,
        subtitle = subtitle,
        value = currentLabel,
        isFocused = isFocused,
        isCustom = override != null,
        showResetButton = override != null && isFocused,
        onClick = { viewModel.cycleGpuDriver(1) },
        onPrev = { viewModel.cycleGpuDriver(-1) },
        onReset = { viewModel.resetGpuDriver() },
        options = entryLabels,
        onSelect = { index ->
            when (val entry = entries.getOrNull(index)) {
                is GpuDriverPickerEntry.Value -> viewModel.setGpuDriver(entry.token ?: GpuDriverToken.SYSTEM)
                is GpuDriverPickerEntry.Install -> viewModel.installVulkanDriver(entry.fileName)
                null -> {}
            }
        },
        pickerRequestToken = if (uiState.enumPickerKey == GPU_DRIVER_PICKER_KEY) uiState.enumPickerToken else 0
    )
}

@Composable
private fun gpuDriverLabel(token: String, installed: List<VulkanDriverOption>): String {
    val customId = GpuDriverToken.customIdOf(token)
        ?: return stringResource(R.string.settings_builtin_gpu_driver_system)
    val driver = installed.firstOrNull { it.id == customId }
        ?: return stringResource(R.string.settings_builtin_gpu_driver_missing)
    return driver.version?.let { stringResource(R.string.settings_builtin_gpu_driver_named_version, driver.name, it) }
        ?: driver.name
}

@Composable
private fun gpuDriverBlockReason(block: GpuDriverBlock, coreName: String?): String? =
    when (block) {
        GpuDriverBlock.CORE_UNSUPPORTED -> coreName
            ?.let { stringResource(R.string.settings_builtin_gpu_driver_reason_core_unsupported, it) }
        GpuDriverBlock.CORE_SYSTEM_DRIVER_ONLY -> coreName
            ?.let { stringResource(R.string.settings_builtin_gpu_driver_reason_core_system_only, it) }
        GpuDriverBlock.BACKEND_NOT_VULKAN -> stringResource(R.string.settings_builtin_gpu_driver_reason_backend)
        GpuDriverBlock.NO_CUSTOM_DRIVERS -> stringResource(R.string.settings_builtin_gpu_driver_reason_device)
    }
