package com.nendo.argosy.ui.screens.gamedetail.delegates

import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.emulator.BuiltinCoreResolver
import com.nendo.argosy.data.emulator.BuiltinGraphicsResolver
import com.nendo.argosy.data.emulator.GpuDriverToken
import com.nendo.argosy.libretro.GraphicsBackendRegistry
import com.nendo.argosy.libretro.LibretroCoreRegistry
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.data.emulator.VulkanDriverRepository
import com.nendo.argosy.ui.common.GpuDriverBlock
import com.nendo.argosy.ui.common.GpuDriverPickerEntry
import com.nendo.argosy.ui.common.GraphicsBackendOptionUi
import com.nendo.argosy.ui.common.VulkanDriverOption
import com.nendo.argosy.ui.common.gpuDriverBlock
import com.nendo.argosy.ui.common.gpuDriverPickerEntries
import com.nendo.argosy.ui.common.optionsUi
import com.nendo.argosy.ui.common.toOption
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.EmulatorSettingScope
import com.nendo.argosy.data.emulator.ExtensionOption
import com.nendo.argosy.data.emulator.LaunchConfig
import com.nendo.argosy.data.emulator.RetroArchPathResolver
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import com.nendo.argosy.data.repository.EmulatorSaveConfigRepository
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.sync.platform.MemcardInfo
import com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
import com.nendo.argosy.domain.usecase.game.ConfigureEmulatorUseCase
import com.nendo.argosy.ui.input.InputDispatcher.Companion.computeWrappedIndex
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.util.DisplayAffinityHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import com.nendo.argosy.ui.screens.common.EmulatorLaunchTargetResolver
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class PerGameSettingsRow { EMULATOR, CORE, SAVE_PATH, SAVE_BASE_PATH, MEMCARD, DISPLAY_TARGET, EXTENSION, BACKEND, GPU_DRIVER, PLATFORM_SETTINGS }

data class PerGameSettingsState(
    val visible: Boolean = false,
    val focusIndex: Int = 0,
    val pathButtonIndex: Int = 0,
    val showPathBrowser: Boolean = false,
    val emulatorName: String? = null,
    val isEmulatorOverride: Boolean = false,
    val showCoreRow: Boolean = false,
    val coreName: String? = null,
    val isCoreOverride: Boolean = false,
    val showSavePathRow: Boolean = false,
    val showSaveBasePathRow: Boolean = false,
    val saveBasePath: String? = null,
    val saveBasePathIsInherited: Boolean = false,
    val savePath: String? = null,
    val isSavePathOverride: Boolean = false,
    val showDisplayTargetRow: Boolean = false,
    val displayTarget: EmulatorDisplayTarget? = null,
    val inheritedDisplayTarget: EmulatorDisplayTarget = EmulatorDisplayTarget.PRESENTATION,
    val extensionOptions: List<ExtensionOption> = emptyList(),
    val preferredExtension: String? = null,
    val inheritedExtension: String? = null,
    val showMemcardRow: Boolean = false,
    val memcardCards: List<MemcardInfo> = emptyList(),
    val selectedMemcardPath: String? = null,
    val inheritedMemcardName: String? = null,
    val showMemcardPicker: Boolean = false,
    val memcardPickerFocusIndex: Int = 0,
    val showGpuDriverRow: Boolean = false,
    val gpuDriver: String? = null,
    val inheritedGpuDriver: String = GpuDriverToken.SYSTEM,
    val gpuDrivers: List<VulkanDriverOption> = emptyList(),
    val gpuDriverBlock: GpuDriverBlock? = null,
    val graphicsCoreName: String? = null,
    val gpuDriverInstallable: List<String> = emptyList(),
    val installingGpuDriver: String? = null,
    val showGpuDriverPicker: Boolean = false,
    val backendOptions: List<GraphicsBackendOptionUi> = emptyList(),
    val gameBackend: String? = null,
    val inheritedBackend: String? = null
) {
    val rows: List<PerGameSettingsRow>
        get() = buildList {
            add(PerGameSettingsRow.EMULATOR)
            if (showCoreRow) add(PerGameSettingsRow.CORE)
            if (showSavePathRow) add(PerGameSettingsRow.SAVE_PATH)
            if (showSaveBasePathRow) add(PerGameSettingsRow.SAVE_BASE_PATH)
            if (showMemcardRow) add(PerGameSettingsRow.MEMCARD)
            if (showDisplayTargetRow) add(PerGameSettingsRow.DISPLAY_TARGET)
            if (extensionOptions.isNotEmpty()) add(PerGameSettingsRow.EXTENSION)
            if (backendOptions.isNotEmpty()) add(PerGameSettingsRow.BACKEND)
            if (showGpuDriverRow) add(PerGameSettingsRow.GPU_DRIVER)
            add(PerGameSettingsRow.PLATFORM_SETTINGS)
        }

    val gpuDriverCycle: List<String?>
        get() = listOf(null, GpuDriverToken.SYSTEM) + gpuDrivers.map { GpuDriverToken.custom(it.id) }

    val gpuDriverPicker: List<GpuDriverPickerEntry>
        get() = gpuDriverPickerEntries(gpuDriverCycle, gpuDriverInstallable)

    val backendCycle: List<String?>
        get() = listOf<String?>(null) + backendOptions.map { it.token }

    val focusedRow: PerGameSettingsRow? get() = rows.getOrNull(focusIndex)

    val memcardPickerCards: List<MemcardInfo>
        get() = listOf(
            MemcardInfo(
                name = inheritedMemcardName ?: "",
                path = "",
                gameFolderCount = 0,
                lastModified = 0L,
                isDefault = true
            )
        ) + memcardCards
}

class PerGameSettingsDelegate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val emulatorDetector: EmulatorDetector,
    private val emulatorResolver: EmulatorResolver,
    private val builtinCoreResolver: BuiltinCoreResolver,
    private val builtinGraphicsResolver: BuiltinGraphicsResolver,
    private val vulkanDriverRepository: VulkanDriverRepository,
    private val notificationManager: NotificationManager,
    private val retroArchPathResolver: RetroArchPathResolver,
    private val emulatorSaveConfigRepository: EmulatorSaveConfigRepository,
    private val builtinSaveBase: com.nendo.argosy.data.emulator.BuiltinSaveBase,
    private val configureEmulatorUseCase: ConfigureEmulatorUseCase,
    private val preferencesRepository: UserPreferencesRepository,
    private val gameRepository: GameRepository,
    private val saveHandlerRegistry: PlatformSaveHandlerRegistry,
    private val resolveGameEmulatorContext:
        com.nendo.argosy.domain.usecase.emulator.ResolveGameEmulatorContextUseCase,
    private val displayAffinityHelper: DisplayAffinityHelper,
    private val soundManager: SoundFeedbackManager
) {
    private val _state = MutableStateFlow(PerGameSettingsState())
    val state: StateFlow<PerGameSettingsState> = _state.asStateFlow()

    var menuWrapMode: MenuWrapMode = MenuWrapMode.HARD_STOP

    /**
     * The emulator's current name, preferring what the registry says now over the name stored
     * when the override was made. A stored name is a snapshot, and a variant whose label has since
     * been corrected would otherwise show its old one until the user reselects the emulator.
     */
    private fun liveEmulatorName(config: EmulatorConfigEntity?): String? {
        val stored = config?.displayName
        val pkg = config?.packageName ?: return stored
        return emulatorDetector.getByPackage(pkg)?.displayName ?: stored
    }

    suspend fun show(gameId: Long) {
        val loaded = buildState(gameId) ?: return
        _state.value = loaded.copy(visible = true)
        soundManager.play(SoundType.OPEN_MODAL)
    }

    suspend fun refresh(gameId: Long) {
        val current = _state.value
        if (!current.visible) return
        val loaded = buildState(gameId) ?: return
        _state.value = loaded.copy(
            visible = true,
            focusIndex = current.focusIndex.coerceIn(0, loaded.rows.size - 1)
        )
    }

    fun dismiss() {
        if (!_state.value.visible) return
        _state.value = PerGameSettingsState()
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun reset() {
        _state.value = PerGameSettingsState()
    }

    fun moveFocus(delta: Int) {
        _state.update { st ->
            val maxIndex = st.rows.size - 1
            st.copy(
                focusIndex = computeWrappedIndex(st.focusIndex, delta, maxIndex, menuWrapMode),
                pathButtonIndex = 0
            )
        }
    }

    fun movePathButton(delta: Int) {
        _state.update { st ->
            if (st.focusedRow != PerGameSettingsRow.SAVE_PATH) return@update st
            val maxIndex = if (st.isSavePathOverride) 1 else 0
            st.copy(pathButtonIndex = (st.pathButtonIndex + delta).coerceIn(0, maxIndex))
        }
    }

    fun openPathBrowser() {
        _state.update { it.copy(showPathBrowser = true) }
    }

    fun dismissPathBrowser() {
        _state.update { it.copy(showPathBrowser = false) }
    }

    suspend fun setSavePath(gameId: Long, path: String) {
        configureEmulatorUseCase.setSavePathForGame(gameId, path)
        refresh(gameId)
    }

    suspend fun clearSavePath(gameId: Long) {
        configureEmulatorUseCase.clearSavePathForGame(gameId)
        _state.update { it.copy(pathButtonIndex = 0) }
        refresh(gameId)
    }

    fun openMemcardPicker() {
        if (!_state.value.showMemcardRow) return
        _state.update { it.copy(showMemcardPicker = true, memcardPickerFocusIndex = 0) }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissMemcardPicker() {
        if (!_state.value.showMemcardPicker) return
        _state.update { it.copy(showMemcardPicker = false, memcardPickerFocusIndex = 0) }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun moveMemcardPickerFocus(delta: Int) {
        _state.update { st ->
            if (!st.showMemcardPicker) return@update st
            val maxIndex = (st.memcardPickerCards.size - 1).coerceAtLeast(0)
            st.copy(memcardPickerFocusIndex = (st.memcardPickerFocusIndex + delta).coerceIn(0, maxIndex))
        }
    }

    suspend fun selectMemcard(gameId: Long, path: String) {
        if (path.isBlank()) {
            configureEmulatorUseCase.clearMemcardForGame(gameId)
        } else {
            configureEmulatorUseCase.setMemcardForGame(gameId, path)
        }
        _state.update { it.copy(showMemcardPicker = false, memcardPickerFocusIndex = 0) }
        soundManager.play(SoundType.CLOSE_MODAL)
        refresh(gameId)
    }

    suspend fun cycleDisplayTarget(gameId: Long, direction: Int) {
        val cycle: List<EmulatorDisplayTarget?> = listOf(null) + EmulatorDisplayTarget.entries
        val currentIndex = cycle.indexOf(_state.value.displayTarget).coerceAtLeast(0)
        val next = cycle[(currentIndex + direction).mod(cycle.size)]
        configureEmulatorUseCase.setDisplayTargetForGame(gameId, next?.name)
        _state.update { it.copy(displayTarget = next) }
    }

    suspend fun cycleExtension(gameId: Long, direction: Int) {
        val options = _state.value.extensionOptions
        if (options.isEmpty()) return
        val cycle: List<String?> = listOf(null) + options.map { it.extension }
        val currentIndex = cycle.indexOf(_state.value.preferredExtension).coerceAtLeast(0)
        val next = cycle[(currentIndex + direction).mod(cycle.size)]
        configureEmulatorUseCase.setExtensionForGame(gameId, next)
        _state.update { it.copy(preferredExtension = next) }
    }

    suspend fun cycleGpuDriver(gameId: Long, direction: Int) {
        val state = _state.value
        if (!state.showGpuDriverRow || state.gpuDriverBlock != null) return
        val cycle = state.gpuDriverCycle
        val currentIndex = cycle.indexOf(state.gpuDriver).coerceAtLeast(0)
        val next = cycle[(currentIndex + direction).mod(cycle.size)]
        configureEmulatorUseCase.setGpuDriverForGame(gameId, next)
        _state.update { it.copy(gpuDriver = next) }
    }

    fun openGpuDriverPicker() {
        val state = _state.value
        if (!state.showGpuDriverRow || state.gpuDriverBlock != null) return
        _state.update { it.copy(showGpuDriverPicker = true) }
    }

    fun dismissGpuDriverPicker() {
        _state.update { it.copy(showGpuDriverPicker = false) }
    }

    suspend fun applyGpuDriverEntry(gameId: Long, entry: GpuDriverPickerEntry) {
        _state.update { it.copy(showGpuDriverPicker = false) }
        when (entry) {
            is GpuDriverPickerEntry.Value -> {
                configureEmulatorUseCase.setGpuDriverForGame(gameId, entry.token)
                _state.update { it.copy(gpuDriver = entry.token) }
            }
            is GpuDriverPickerEntry.Install -> installGpuDriver(gameId, entry.fileName)
        }
    }

    private suspend fun installGpuDriver(gameId: Long, fileName: String) {
        if (_state.value.installingGpuDriver != null) return
        _state.update { it.copy(installingGpuDriver = fileName) }
        vulkanDriverRepository.installFromDownloads(fileName)
            .onSuccess { driver ->
                configureEmulatorUseCase.setGpuDriverForGame(gameId, GpuDriverToken.custom(driver.id))
                notificationManager.show(
                    title = NotificationText.Res(R.string.gamedetail_per_game_gpu_driver_installed, listOf(driver.name)),
                    type = NotificationType.SUCCESS
                )
            }
            .onFailure {
                notificationManager.show(
                    title = NotificationText.Res(R.string.gamedetail_per_game_gpu_driver_install_failed, listOf(fileName)),
                    type = NotificationType.ERROR
                )
            }
        _state.update { it.copy(installingGpuDriver = null) }
        refresh(gameId)
    }

    suspend fun cycleBackend(gameId: Long, direction: Int) {
        val state = _state.value
        if (state.backendOptions.isEmpty()) return
        val cycle = state.backendCycle
        val currentIndex = cycle.indexOf(state.gameBackend).coerceAtLeast(0)
        val next = cycle[(currentIndex + direction).mod(cycle.size)]
        configureEmulatorUseCase.setGraphicsBackendForGame(gameId, next)
        refresh(gameId)
    }

    private suspend fun buildState(gameId: Long): PerGameSettingsState? {
        val game = gameRepository.getById(gameId) ?: return null
        if (emulatorDetector.installedEmulators.value.isEmpty()) {
            emulatorDetector.detectEmulators()
        }
        val prefs = preferencesRepository.userPreferences.first()

        val gameConfig = emulatorConfigDao.getByGameId(gameId)
        val platformConfig = emulatorConfigDao.getDefaultForPlatform(game.platformId)

        val emulatorName = liveEmulatorName(gameConfig) ?: liveEmulatorName(platformConfig)
            ?: emulatorDetector.getPreferredEmulator(game.platformSlug, prefs.builtinLibretroEnabled)?.def?.displayName

        val emulatorDef = emulatorResolver.resolveEmulatorForGame(gameId, game.platformId, game.platformSlug)
        val isBuiltIn = emulatorDef?.launchConfig is LaunchConfig.BuiltIn
        val isCoreSelectable = emulatorDef?.launchConfig?.isCoreSelectable == true
        val cores = EmulatorRegistry.getSelectableCores(game.platformSlug, isBuiltIn)
        val showCoreRow = EmulatorSettingScope.showsCoreSelection(isCoreSelectable, cores.size)
        val selectedCoreId = if (isBuiltIn) {
            builtinCoreResolver.resolveCoreId(
                gameId = gameId,
                platformId = game.platformId,
                platformSlug = game.platformSlug
            )
        } else {
            gameConfig?.coreName
                ?: platformConfig?.coreName
                ?: EmulatorRegistry.getDefaultSelectableCore(game.platformSlug, isBuiltIn)?.id
        }
        val coreName = if (isCoreSelectable) cores.find { it.id == selectedCoreId }?.displayName else null

        val emulatorContext = resolveGameEmulatorContext(gameId, game.platformId, game.platformSlug)
        val effectivePackage = emulatorContext.effectivePackage
        val effectiveEmulatorId = emulatorContext.effectiveEmulatorId
        val saveConfig = emulatorContext.saveConfig
        val showSavePathRow = emulatorContext.supportsPerGameSavePath

        val perGamePath = emulatorConfigDao.getSavePathForGame(gameId)?.takeIf { it.isNotBlank() }
        val isRetroArch = emulatorDef?.launchConfig is LaunchConfig.RetroArch
        val userSaveConfig = saveConfig?.let { emulatorSaveConfigRepository.getByEmulator(it.emulatorId) }
        val besideRomDir = saveConfig?.takeIf { !isRetroArch }
            ?.let { emulatorSaveConfigRepository.besideRomDir(it, game.platformSlug, game.localPath) }
        val savePath = when {
            !showSavePathRow || saveConfig == null -> null
            isBuiltIn -> builtinSaveBase.forGame(game)
            perGamePath != null -> perGamePath
            besideRomDir != null -> besideRomDir
            isRetroArch -> {
                val request = RetroArchPathResolver.Request(
                    emulatorId = effectiveEmulatorId ?: saveConfig.emulatorId,
                    coreName = selectedCoreId,
                    romPath = game.localPath
                )
                when (val display = retroArchPathResolver.displaySavePath(request)) {
                    is RetroArchPathResolver.DisplayPath.ContentDirectory ->
                        context.getString(R.string.gamedetail_per_game_save_path_content_dir)
                    is RetroArchPathResolver.DisplayPath.Resolved -> display.path
                    RetroArchPathResolver.DisplayPath.Unknown -> null
                }
            }
            userSaveConfig?.isUserOverride == true ->
                SavePathRegistry.pathForPackage(userSaveConfig.savePathPattern, effectivePackage)
            else -> emulatorSaveConfigRepository.resolveEvaluatedSavePath(saveConfig.emulatorId, effectivePackage)
                ?: SavePathRegistry.resolvePathWithPackage(saveConfig, effectivePackage, context.filesDir.absolutePath).firstOrNull()
        }

        val showSaveBasePathRow = !showSavePathRow && saveConfig != null && saveConfig.isUsable
        val saveBasePath = if (!showSaveBasePathRow || saveConfig == null) {
            null
        } else {
            emulatorContext.memcardCardsDir
                ?: emulatorSaveConfigRepository.resolveEffectiveSavePath(saveConfig.emulatorId, game.platformSlug, effectivePackage)
                ?: SavePathRegistry.resolvePathWithPackage(saveConfig, effectivePackage, context.filesDir.absolutePath).firstOrNull()
        }
        val saveBasePathIsInherited = saveBasePath != null &&
            userSaveConfig?.takeIf { it.isUserOverride }?.savePathPattern.isNullOrBlank()

        val displayTarget = emulatorConfigDao.getDisplayTargetForGame(gameId)
            ?.let { raw -> EmulatorDisplayTarget.fromString(raw) }
        val inheritedDisplayTarget = EmulatorLaunchTargetResolver.resolvedTarget(
            stored = EmulatorDisplayTarget.fromString(emulatorConfigDao.getDisplayTargetForPlatform(game.platformId)),
            gamesDefault = preferencesRepository.userPreferences.first().gamesDisplayTarget
        )

        val extensionOptions = EmulatorRegistry.getExtensionOptionsForPlatform(game.platformSlug)
        val preferredExtension = emulatorConfigDao.getPreferredExtensionForGame(gameId)?.takeIf { it.isNotBlank() }
        val inheritedExtension = emulatorConfigDao.getPreferredExtension(game.platformId)?.takeIf { it.isNotBlank() }

        val memcardCanonicalId = emulatorContext.memcardCanonicalId
        val memcardUserConfig = memcardCanonicalId?.let { emulatorSaveConfigRepository.getByEmulator(it) }
        val memcardCards = emulatorContext.memcards
        val showMemcardRow = emulatorContext.showMemcardRow
        val perGameMemcardPath = emulatorConfigDao.getSelectedMemcardForGame(gameId)?.takeIf { it.isNotBlank() }
        val inheritedMemcardPath = memcardUserConfig?.selectedMemcardPath?.takeIf { it.isNotBlank() }
        val inheritedMemcardName = inheritedMemcardPath
            ?.let { p -> memcardCards.find { it.path == p }?.name ?: File(p).name }
            ?: memcardCards.firstOrNull()?.name

        val graphics = if (isBuiltIn) {
            builtinGraphicsResolver.resolveForGame(
                game = game,
                coreId = selectedCoreId,
                globalDriver = preferencesRepository.getBuiltinEmulatorSettings().first().gpuDriver
            )
        } else {
            null
        }

        return PerGameSettingsState(
            showGpuDriverRow = graphics != null,
            gpuDriver = graphics?.gameDriver,
            inheritedGpuDriver = graphics?.inheritedDriver ?: GpuDriverToken.SYSTEM,
            gpuDrivers = graphics
                ?.takeIf { it.customDriversSupported }
                ?.installedDrivers
                ?.map { it.toOption() }
                .orEmpty(),
            gpuDriverBlock = graphics?.let {
                gpuDriverBlock(
                    backendSupportsVulkan = GraphicsBackendRegistry.supportsVulkan(it.backend),
                    backendIsVulkan = it.preferVulkan,
                    customDriversSupported = it.customDriversSupported,
                    coreAcceptsCustomDriver = it.coreAcceptsCustomDriver
                )
            },
            graphicsCoreName = graphics?.coreId?.let { LibretroCoreRegistry.displayNameFor(it) },
            gpuDriverInstallable = if (graphics?.customDriversSupported == true) {
                vulkanDriverRepository.listInstallablePackages().map { it.name }
            } else {
                emptyList()
            },
            backendOptions = graphics?.backend?.optionsUi().orEmpty(),
            gameBackend = graphics?.gameBackend,
            inheritedBackend = graphics?.inheritedBackend,
            emulatorName = emulatorName,
            isEmulatorOverride = gameConfig?.packageName != null,
            showCoreRow = showCoreRow,
            coreName = coreName,
            isCoreOverride = gameConfig?.coreName != null,
            showSavePathRow = showSavePathRow,
            showSaveBasePathRow = showSaveBasePathRow,
            saveBasePath = saveBasePath,
            saveBasePathIsInherited = saveBasePathIsInherited,
            savePath = savePath,
            isSavePathOverride = perGamePath != null,
            showDisplayTargetRow = displayAffinityHelper.hasSecondaryDisplay,
            displayTarget = displayTarget,
            inheritedDisplayTarget = inheritedDisplayTarget,
            extensionOptions = extensionOptions,
            preferredExtension = preferredExtension,
            inheritedExtension = inheritedExtension,
            showMemcardRow = showMemcardRow,
            memcardCards = memcardCards,
            selectedMemcardPath = perGameMemcardPath,
            inheritedMemcardName = inheritedMemcardName
        )
    }
}
