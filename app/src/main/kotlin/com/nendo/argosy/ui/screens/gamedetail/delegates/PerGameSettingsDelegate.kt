package com.nendo.argosy.ui.screens.gamedetail.delegates

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.ExtensionOption
import com.nendo.argosy.data.emulator.LaunchConfig
import com.nendo.argosy.data.emulator.RetroArchPathResolver
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.repository.EmulatorSaveConfigRepository
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.domain.usecase.game.ConfigureEmulatorUseCase
import com.nendo.argosy.ui.input.InputDispatcher.Companion.computeWrappedIndex
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.util.DisplayAffinityHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class PerGameSettingsRow { EMULATOR, CORE, SAVE_PATH, DISPLAY_TARGET, EXTENSION, PLATFORM_SETTINGS }

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
    val savePath: String? = null,
    val isSavePathOverride: Boolean = false,
    val showDisplayTargetRow: Boolean = false,
    val displayTarget: EmulatorDisplayTarget? = null,
    val inheritedDisplayTarget: EmulatorDisplayTarget = EmulatorDisplayTarget.TOP,
    val extensionOptions: List<ExtensionOption> = emptyList(),
    val preferredExtension: String? = null,
    val inheritedExtension: String? = null
) {
    val rows: List<PerGameSettingsRow>
        get() = buildList {
            add(PerGameSettingsRow.EMULATOR)
            if (showCoreRow) add(PerGameSettingsRow.CORE)
            if (showSavePathRow) add(PerGameSettingsRow.SAVE_PATH)
            if (showDisplayTargetRow) add(PerGameSettingsRow.DISPLAY_TARGET)
            if (extensionOptions.isNotEmpty()) add(PerGameSettingsRow.EXTENSION)
            add(PerGameSettingsRow.PLATFORM_SETTINGS)
        }

    val focusedRow: PerGameSettingsRow? get() = rows.getOrNull(focusIndex)
}

class PerGameSettingsDelegate @Inject constructor(
    private val emulatorConfigDao: EmulatorConfigDao,
    private val emulatorDetector: EmulatorDetector,
    private val emulatorResolver: EmulatorResolver,
    private val retroArchPathResolver: RetroArchPathResolver,
    private val emulatorSaveConfigRepository: EmulatorSaveConfigRepository,
    private val configureEmulatorUseCase: ConfigureEmulatorUseCase,
    private val preferencesRepository: UserPreferencesRepository,
    private val gameRepository: GameRepository,
    private val displayAffinityHelper: DisplayAffinityHelper,
    private val soundManager: SoundFeedbackManager
) {
    private val _state = MutableStateFlow(PerGameSettingsState())
    val state: StateFlow<PerGameSettingsState> = _state.asStateFlow()

    var menuWrapMode: MenuWrapMode = MenuWrapMode.HARD_STOP

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

    private suspend fun buildState(gameId: Long): PerGameSettingsState? {
        val game = gameRepository.getById(gameId) ?: return null
        if (emulatorDetector.installedEmulators.value.isEmpty()) {
            emulatorDetector.detectEmulators()
        }
        val prefs = preferencesRepository.userPreferences.first()

        val gameConfig = emulatorConfigDao.getByGameId(gameId)
        val platformConfig = emulatorConfigDao.getDefaultForPlatform(game.platformId)

        val emulatorName = gameConfig?.displayName
            ?: platformConfig?.displayName
            ?: emulatorDetector.getPreferredEmulator(game.platformSlug, prefs.builtinLibretroEnabled)?.def?.displayName

        val configuredPackage = gameConfig?.packageName ?: platformConfig?.packageName
        val emulatorDef = configuredPackage?.let { emulatorDetector.getByPackage(it) }
            ?: emulatorDetector.getPreferredEmulator(game.platformSlug, prefs.builtinLibretroEnabled)?.def
        val isBuiltIn = emulatorDef?.launchConfig is LaunchConfig.BuiltIn
        val isCoreSelectable = emulatorDef?.launchConfig?.isCoreSelectable == true
        val cores = EmulatorRegistry.getSelectableCores(game.platformSlug, isBuiltIn)
        val showCoreRow = isCoreSelectable && cores.size > 1
        val selectedCoreId = gameConfig?.coreName
            ?: platformConfig?.coreName
            ?: EmulatorRegistry.getDefaultSelectableCore(game.platformSlug, isBuiltIn)?.id
        val coreName = if (isCoreSelectable) cores.find { it.id == selectedCoreId }?.displayName else null

        val effectivePackage = emulatorResolver.getEmulatorPackageForGame(gameId, game.platformId, game.platformSlug)
        val effectiveEmulatorId = effectivePackage?.let { emulatorResolver.resolveEmulatorId(it) }
        val saveConfig = effectivePackage?.let { SavePathRegistry.getConfigForPlatformByPackage(it, game.platformSlug) }
            ?: effectiveEmulatorId?.let { SavePathRegistry.getConfigForPlatform(it, game.platformSlug) }
        val showSavePathRow = SavePathRegistry.supportsPerGameSavePath(saveConfig, game.platformSlug)

        val perGamePath = emulatorConfigDao.getSavePathForGame(gameId)?.takeIf { it.isNotBlank() }
        val isRetroArch = emulatorDef?.launchConfig is LaunchConfig.RetroArch
        val userSaveConfig = saveConfig?.let { emulatorSaveConfigRepository.getByEmulator(it.emulatorId) }
        val savePath = when {
            !showSavePathRow || saveConfig == null -> null
            perGamePath != null -> perGamePath
            isRetroArch -> {
                val request = RetroArchPathResolver.Request(
                    emulatorId = effectiveEmulatorId ?: saveConfig.emulatorId,
                    coreName = selectedCoreId,
                    romPath = game.localPath
                )
                when (val display = retroArchPathResolver.displaySavePath(request)) {
                    is RetroArchPathResolver.DisplayPath.ContentDirectory -> "(ROM directory)"
                    is RetroArchPathResolver.DisplayPath.Resolved -> display.path
                    RetroArchPathResolver.DisplayPath.Unknown -> null
                }
            }
            userSaveConfig?.isUserOverride == true -> userSaveConfig.savePathPattern
            else -> SavePathRegistry.resolvePathWithPackage(saveConfig, effectivePackage).firstOrNull()
        }

        val displayTarget = emulatorConfigDao.getDisplayTargetForGame(gameId)
            ?.let { raw -> EmulatorDisplayTarget.entries.find { it.name == raw } }
        val inheritedDisplayTarget = EmulatorDisplayTarget.fromString(
            emulatorConfigDao.getDisplayTargetForPlatform(game.platformId)
        )

        val extensionOptions = EmulatorRegistry.getExtensionOptionsForPlatform(game.platformSlug)
        val preferredExtension = emulatorConfigDao.getPreferredExtensionForGame(gameId)?.takeIf { it.isNotBlank() }
        val inheritedExtension = emulatorConfigDao.getPreferredExtension(game.platformId)?.takeIf { it.isNotBlank() }

        return PerGameSettingsState(
            emulatorName = emulatorName,
            isEmulatorOverride = gameConfig?.packageName != null,
            showCoreRow = showCoreRow,
            coreName = coreName,
            isCoreOverride = gameConfig?.coreName != null,
            showSavePathRow = showSavePathRow,
            savePath = savePath,
            isSavePathOverride = perGamePath != null,
            showDisplayTargetRow = displayAffinityHelper.hasSecondaryDisplay,
            displayTarget = displayTarget,
            inheritedDisplayTarget = inheritedDisplayTarget,
            extensionOptions = extensionOptions,
            preferredExtension = preferredExtension,
            inheritedExtension = inheritedExtension
        )
    }
}
