package com.nendo.argosy.ui.screens.settings.delegates

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.data.emulator.CemuDriverChoice
import com.nendo.argosy.data.emulator.CemuDriverInstaller
import com.nendo.argosy.data.emulator.config.CemuConfigSpec
import com.nendo.argosy.data.emulator.config.ConfigFileRole
import com.nendo.argosy.data.emulator.config.ConfigFileState
import com.nendo.argosy.data.emulator.config.ConfigOption
import com.nendo.argosy.data.emulator.config.ConfigOptionDecoder
import com.nendo.argosy.data.emulator.config.ConfigTokens
import com.nendo.argosy.data.emulator.config.ConfigValueType
import com.nendo.argosy.data.emulator.config.ConfigWriteFailure
import com.nendo.argosy.data.emulator.config.ConfigWriteResult
import com.nendo.argosy.data.emulator.config.EmulatorConfigReader
import com.nendo.argosy.data.emulator.config.EmulatorConfigRegistry
import com.nendo.argosy.data.emulator.config.EmulatorConfigSnapshot
import com.nendo.argosy.data.emulator.config.EmulatorConfigWriter
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.ui.screens.settings.EmulatorDriverChoiceUi
import com.nendo.argosy.ui.screens.settings.EmulatorOptionChoiceUi
import com.nendo.argosy.ui.screens.settings.EmulatorOptionRangeUi
import com.nendo.argosy.ui.screens.settings.EmulatorOptionUi
import com.nendo.argosy.ui.screens.settings.EmulatorOptionsFileUi
import com.nendo.argosy.ui.screens.settings.EmulatorOptionsPhase
import com.nendo.argosy.ui.screens.settings.EmulatorOptionsState
import com.nendo.argosy.ui.screens.settings.EmulatorOtherKeyUi
import com.nendo.argosy.ui.screens.settings.EmulatorPendingEditUi
import com.nendo.argosy.ui.screens.settings.EmulatorPlayTimeUi
import com.nendo.argosy.ui.screens.settings.emulatorOptionId
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class EmulatorOptionAdjust { CHANGED, UNCHANGED, AT_BOUND, IGNORED }

class EmulatorOptionsDelegate @Inject constructor(
    private val reader: EmulatorConfigReader,
    private val writer: EmulatorConfigWriter,
    private val cemuDrivers: CemuDriverInstaller,
    private val notificationManager: NotificationManager
) {
    private val _state = MutableStateFlow(EmulatorOptionsState())
    val state: StateFlow<EmulatorOptionsState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var editConfirmed = false
    private var driverChoices: List<CemuDriverChoice> = emptyList()

    private class Loaded(
        val snapshot: EmulatorConfigSnapshot,
        val files: List<EmulatorOptionsFileUi>,
        val driverOptionId: String?,
        val drivers: List<CemuDriverChoice>,
        val undoableIds: Set<String>
    )

    fun open(scope: CoroutineScope, emulatorId: String?, packageName: String?, emulatorName: String?) {
        loadJob?.cancel()
        _state.update {
            EmulatorOptionsState(
                emulatorId = emulatorId,
                packageName = packageName,
                emulatorName = emulatorName,
                phase = EmulatorOptionsPhase.LOADING
            )
        }
        load(scope)
    }

    fun retry(scope: CoroutineScope) {
        loadJob?.cancel()
        _state.update { it.copy(phase = EmulatorOptionsPhase.LOADING) }
        load(scope)
    }

    fun close() {
        loadJob?.cancel()
        loadJob = null
        _state.update { EmulatorOptionsState() }
    }

    fun toggleOtherKeys(fileId: String) {
        _state.update {
            val expanded = if (fileId in it.expandedFileIds) it.expandedFileIds - fileId else it.expandedFileIds + fileId
            it.copy(expandedFileIds = expanded)
        }
    }

    /**
     * Moves [optionId] one value in [direction]: the next registered value, one range step, or for
     * a boolean off (left) and on (right).
     */
    fun adjust(scope: CoroutineScope, optionId: String, direction: Int): EmulatorOptionAdjust {
        val row = editableRow(optionId) ?: return EmulatorOptionAdjust.IGNORED
        val option = optionFor(optionId) ?: return EmulatorOptionAdjust.IGNORED
        val next = when {
            optionId == _state.value.driverOptionId -> cycledDriver(row.token, direction)
            option.values.isNotEmpty() -> ConfigTokens.cycled(option, row.token, direction)
            option.type == ConfigValueType.BOOL -> {
                val on = direction > 0
                if (ConfigTokens.isOn(row.token) == on) return EmulatorOptionAdjust.UNCHANGED
                on.toString()
            }
            else -> ConfigTokens.stepped(option, row.token, direction)
        } ?: return EmulatorOptionAdjust.AT_BOUND
        if (!request(scope, optionId, next)) return EmulatorOptionAdjust.IGNORED
        return EmulatorOptionAdjust.CHANGED
    }

    /**
     * Flips a boolean option; the value it turns to, or null when the option cannot be toggled now.
     */
    fun toggle(scope: CoroutineScope, optionId: String): Boolean? {
        val row = editableRow(optionId)?.takeIf { it.type == ConfigValueType.BOOL } ?: return null
        val next = ConfigTokens.toggled(row.token)
        if (!request(scope, optionId, next)) return null
        return ConfigTokens.isOn(next)
    }

    fun select(scope: CoroutineScope, optionId: String, token: String) {
        editableRow(optionId) ?: return
        request(scope, optionId, token)
    }

    fun confirmPendingEdit(scope: CoroutineScope) {
        val pending = _state.value.pendingEdit ?: return
        if (_state.value.writing) return
        editConfirmed = true
        runWrite(scope, pending.optionId, pending.token)
    }

    fun cancelPendingEdit() {
        _state.update { if (it.writing) it else it.copy(pendingEdit = null) }
    }

    fun undo(scope: CoroutineScope, optionId: String) {
        val state = _state.value
        val emulatorId = state.emulatorId ?: return
        val packageName = state.packageName ?: return
        if (!state.canWrite(optionId) || state.writing || state.pendingEdit != null) return
        if (optionId !in state.undoableIds) return
        val option = optionFor(optionId) ?: return
        _state.update { it.copy(writing = true) }
        scope.launch {
            val result = runCatchingWrite { writer.undo(emulatorId, packageName, option) }
            if (result is ConfigWriteResult.Failed) notifyFailure(option, result.reason)
            finishWrite(emulatorId, packageName)
        }
    }

    private fun request(scope: CoroutineScope, optionId: String, token: String): Boolean {
        val state = _state.value
        if (!state.canWrite(optionId) || state.writing || state.pendingEdit != null) return false
        if (!editConfirmed) {
            val packageName = state.packageName ?: return false
            val option = optionFor(optionId) ?: return false
            scope.launch {
                if (withContext(Dispatchers.IO) { writer.holdsGame(packageName) }) {
                    notifyFailure(option, ConfigWriteFailure.GAME_RUNNING)
                    return@launch
                }
                _state.update {
                    if (it.writing || it.pendingEdit != null || it.packageName != packageName) it
                    else it.copy(pendingEdit = EmulatorPendingEditUi(optionId, token))
                }
            }
            return true
        }
        runWrite(scope, optionId, token)
        return true
    }

    private fun runWrite(scope: CoroutineScope, optionId: String, token: String) {
        val state = _state.value
        val emulatorId = state.emulatorId ?: return
        val packageName = state.packageName ?: return
        val option = optionFor(optionId) ?: return
        val isDriver = optionId == state.driverOptionId
        val driver = if (isDriver) driverChoices.firstOrNull { it.path == token }?.driver else null
        if (isDriver && token.isNotEmpty() && driver == null) {
            _state.update { it.copy(pendingEdit = null) }
            return
        }
        _state.update { it.copy(writing = true) }
        scope.launch {
            val result = runCatchingWrite {
                if (isDriver) {
                    cemuDrivers.select(emulatorId, packageName, driver)
                } else {
                    writer.write(emulatorId, packageName, option, token)
                }
            }
            if (result is ConfigWriteResult.Failed) notifyFailure(option, result.reason)
            finishWrite(emulatorId, packageName)
        }
    }

    private suspend fun runCatchingWrite(block: suspend () -> ConfigWriteResult): ConfigWriteResult =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG, "edit failed", e)
            ConfigWriteResult.Failed(ConfigWriteFailure.WRITE_FAILED)
        }

    private suspend fun finishWrite(emulatorId: String, packageName: String) {
        val loaded = try {
            loadSnapshot(emulatorId, packageName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG, "snapshot failed | emulator=$emulatorId", e)
            null
        }
        if (loaded != null && _state.value.packageName == packageName) driverChoices = loaded.drivers
        _state.update { current ->
            if (current.emulatorId != emulatorId || current.packageName != packageName) return@update current
            val refreshed = loaded?.let { applied(current, it) } ?: current
            refreshed.copy(writing = false, pendingEdit = null)
        }
    }

    private suspend fun loadSnapshot(emulatorId: String, packageName: String): Loaded? = withContext(Dispatchers.IO) {
        val snapshot = reader.snapshot(emulatorId, packageName) ?: return@withContext null
        val driverOption = EmulatorConfigRegistry.specFor(emulatorId)?.options
            ?.firstOrNull { it == CemuConfigSpec.CUSTOM_DRIVER_PATH }
        val dataRoot = snapshot.access.dataRoot
        val drivers = if (driverOption != null && dataRoot != null) cemuDrivers.choices(dataRoot) else emptyList()
        val driverOptionId = driverOption?.let { emulatorOptionId(it.file.id, it.section, it.key) }
        Loaded(snapshot, fileRows(snapshot, driverOptionId, drivers), driverOptionId, drivers, undoableIds(emulatorId, packageName, snapshot))
    }

    private fun undoableIds(emulatorId: String, packageName: String, snapshot: EmulatorConfigSnapshot): Set<String> =
        writer.pendingUndos(emulatorId, packageName).mapNotNull { (file, edit) ->
            val stored = snapshot.options.firstOrNull {
                it.option.file == file && it.option.section == edit.section && it.option.key == edit.key
            } ?: return@mapNotNull null
            val raw = stored.raw ?: return@mapNotNull null
            emulatorOptionId(file.id, edit.section, edit.key)
                .takeIf { ConfigOptionDecoder.same(stored.option.type, raw, edit.written) }
        }.toSet()

    private fun applied(current: EmulatorOptionsState, loaded: Loaded): EmulatorOptionsState {
        val access = loaded.snapshot.access
        return current.copy(
            phase = EmulatorOptionsPhase.READY,
            dataRoot = access.dataRoot,
            readRoute = access.readRoute,
            editRoute = access.editRoute,
            editBlock = access.editBlock,
            writeAppDataRoute = access.writeAppDataRoute,
            files = loaded.files,
            driverOptionId = loaded.driverOptionId,
            drivers = loaded.drivers.map { EmulatorDriverChoiceUi(it.driver.id, it.driver.name, it.path) },
            undoableIds = loaded.undoableIds
        )
    }

    private fun cycledDriver(current: String?, direction: Int): String? {
        val tokens = _state.value.driverTokens
        val index = tokens.indexOf(current.orEmpty())
        val next = when {
            index < 0 && direction > 0 -> 0
            index < 0 -> tokens.lastIndex
            else -> index + direction
        }
        return tokens.getOrNull(next)
    }

    private fun notifyFailure(option: ConfigOption, reason: ConfigWriteFailure) {
        notificationManager.show(
            title = NotificationText.Res(R.string.settings_emulator_options_edit_failed_title, listOf(option.key)),
            subtitle = NotificationText.Res(reason.labelRes),
            type = NotificationType.ERROR
        )
    }

    private fun editableRow(optionId: String): EmulatorOptionUi? =
        _state.value.files.asSequence().flatMap { it.options }.firstOrNull { it.id == optionId && it.editable }

    private fun optionFor(optionId: String): ConfigOption? {
        val emulatorId = _state.value.emulatorId ?: return null
        return EmulatorConfigRegistry.specFor(emulatorId)?.options
            ?.firstOrNull { emulatorOptionId(it.file.id, it.section, it.key) == optionId }
    }

    private fun load(scope: CoroutineScope) {
        val target = _state.value
        val emulatorId = target.emulatorId
        val packageName = target.packageName
        if (emulatorId == null || packageName == null) {
            _state.update { it.copy(phase = EmulatorOptionsPhase.UNSUPPORTED) }
            return
        }
        loadJob = scope.launch {
            val loaded = try {
                loadSnapshot(emulatorId, packageName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.warn(TAG, "snapshot failed | emulator=$emulatorId", e)
                _state.update { current ->
                    if (current.emulatorId != emulatorId) current else current.copy(phase = EmulatorOptionsPhase.FAILED)
                }
                return@launch
            }
            if (loaded != null) driverChoices = loaded.drivers
            _state.update { current ->
                when {
                    current.emulatorId != emulatorId || current.packageName != packageName -> current
                    loaded == null -> current.copy(phase = EmulatorOptionsPhase.UNSUPPORTED)
                    else -> applied(current, loaded)
                }
            }
        }
    }

    private fun fileRows(
        snapshot: EmulatorConfigSnapshot,
        driverOptionId: String?,
        drivers: List<CemuDriverChoice>
    ): List<EmulatorOptionsFileUi> {
        val playTimeFile = snapshot.files.firstOrNull { it.file.role == ConfigFileRole.PLAY_TIME }?.file
        return snapshot.files.map { found ->
            val options = snapshot.options
                .filter { it.option.file == found.file }
                .groupBy { it.option.section }
                .values
                .flatten()
                .map { decoded ->
                    val option = decoded.option
                    val isDriver = emulatorOptionId(option.file.id, option.section, option.key) == driverOptionId
                    val token = decoded.raw ?: option.defaultToken?.takeIf { decoded.isDefault }
                    val fileRead = found.state == ConfigFileState.READ
                    EmulatorOptionUi(
                        section = option.section,
                        key = option.key,
                        value = decoded.valueName
                            ?: decoded.raw
                            ?: option.defaultToken?.takeIf { decoded.isDefault },
                        isDefault = decoded.isDefault,
                        hidden = decoded.hidden,
                        fileId = option.file.id,
                        type = option.type,
                        token = token,
                        choices = option.values.map { EmulatorOptionChoiceUi(it.token, it.name) },
                        range = option.range?.let { EmulatorOptionRangeUi(it.min, it.max, it.step) },
                        editable = if (isDriver) {
                            fileRead && (drivers.isNotEmpty() || !token.isNullOrEmpty())
                        } else {
                            option.editable && ConfigTokens.isAdjustable(option) && !decoded.hidden && fileRead
                        }
                    )
                }
            val otherKeys = snapshot.otherKeys
                .filter { it.file == found.file }
                .map { EmulatorOtherKeyUi(it.section, it.key, it.value, it.hidden) }
            val playTimes = if (found.file == playTimeFile) {
                snapshot.playTimes.entries
                    .sortedByDescending { it.value }
                    .map { EmulatorPlayTimeUi(it.key, it.value) }
            } else {
                emptyList()
            }
            EmulatorOptionsFileUi(
                id = found.file.id,
                path = found.path,
                state = found.state,
                modifiedAt = found.modifiedAt,
                options = options,
                otherKeys = otherKeys,
                playTimes = playTimes
            )
        }
    }

    private companion object {
        const val TAG = "EmulatorOptionsDelegate"
    }
}
