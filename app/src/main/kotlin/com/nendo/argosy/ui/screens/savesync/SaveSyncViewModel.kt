package com.nendo.argosy.ui.screens.savesync

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.R
import com.nendo.argosy.data.local.dao.SaveCountByDevice
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PendingConflictEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMDevice
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.sync.ConflictResolution
import com.nendo.argosy.data.sync.ConflictResolutionService
import com.nendo.argosy.data.sync.SaveAccessNotices
import com.nendo.argosy.data.sync.SyncDirection
import com.nendo.argosy.data.sync.SyncQueueManager
import com.nendo.argosy.data.sync.SyncStatus
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import com.nendo.argosy.data.model.GameActivitySnapshot
import com.nendo.argosy.data.repository.GameActivityRepository
import com.nendo.argosy.domain.model.DeviceKind
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

private const val JUST_SYNCED_THRESHOLD_MINUTES = 30L
private const val ACTIVITY_CALENDAR_DAYS = 84
private const val ACTIVITY_DEBOUNCE_MS = 150L

@HiltViewModel
class SaveSyncViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val syncQueueManager: SyncQueueManager,
    private val preferencesRepository: UserPreferencesRepository,
    private val romMRepository: RomMRepository,
    private val conflictResolutionService: ConflictResolutionService,
    private val saveSyncRepository: SaveSyncRepository,
    private val saveAccessNotices: SaveAccessNotices,
    private val gameActivityRepository: GameActivityRepository,
    private val syncCoordinator: com.nendo.argosy.data.sync.SyncCoordinator,
    private val gameRepository: com.nendo.argosy.data.repository.GameRepository
) : ViewModel() {

    private val _forceCheckStatus = MutableStateFlow<ForceSaveCheckUiState>(ForceSaveCheckUiState.Idle)
    val forceCheckStatus: StateFlow<ForceSaveCheckUiState> = _forceCheckStatus

    private val _focusedRowKey = MutableStateFlow<String?>(null)
    private val _attentionAction = MutableStateFlow(AttentionAction.SKIP)
    private val _registeredDevices = MutableStateFlow<List<RomMDevice>>(emptyList())
    private val _otherDevicesExpanded = MutableStateFlow(false)
    private var emptyFallbackJob: Job? = null

    init {
        viewModelScope.launch {
            _focusedRowKey.collect { _attentionAction.value = AttentionAction.SKIP }
        }
        viewModelScope.launch {
            romMRepository.connectionState.collect { state ->
                if (state is ConnectionState.Connected) {
                    _registeredDevices.value = romMRepository.getRegisteredDevices()
                } else {
                    _registeredDevices.value = emptyList()
                }
            }
        }
    }

    fun refreshDevices() {
        viewModelScope.launch {
            _registeredDevices.value = romMRepository.getRegisteredDevices()
        }
    }

    val uiState: StateFlow<SaveSyncUiState> = combine(
        listOf(
            ownedSaveRows(),
            ownedConflicts(),
            syncQueueManager.state,
            preferencesRepository.preferences,
            _focusedRowKey,
            _attentionAction,
            romMRepository.connectionState,
            ownedDeviceCounts(),
            _registeredDevices,
            saveAccessNotices.locations,
            _otherDevicesExpanded
        )
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val saveRows = values[0] as List<SaveSyncEntity>
        @Suppress("UNCHECKED_CAST")
        val conflicts = values[1] as List<PendingConflictEntity>
        val queueState = values[2] as com.nendo.argosy.data.sync.SyncQueueState
        val prefs = values[3] as com.nendo.argosy.data.preferences.UserPreferences
        val focusedKey = values[4] as String?
        val attentionAction = values[5] as AttentionAction
        val connection = values[6] as ConnectionState
        @Suppress("UNCHECKED_CAST")
        val deviceCounts = values[7] as List<SaveCountByDevice>
        @Suppress("UNCHECKED_CAST")
        val registeredDevices = values[8] as List<RomMDevice>
        @Suppress("UNCHECKED_CAST")
        val inaccessibleLocations = values[9] as List<SaveAccessNotices.InaccessibleLocation>
        val otherDevicesExpanded = values[10] as Boolean
        val gameIds = (saveRows.map { it.gameId } + conflicts.map { it.gameId } + queueState.operations.map { it.gameId })
            .distinct()
        val gameById = if (gameIds.isEmpty()) emptyMap() else gameRepository.getByIdsBatched(gameIds).associateBy { it.id }
        val artById = gameRepository.getArt(gameById.keys)

        val isConnected = connection is ConnectionState.Connected
        val serverVersion = (connection as? ConnectionState.Connected)?.version
        val currentDeviceId = prefs.rommDeviceId
        val currentDevice = registeredDevices.firstOrNull { it.id == currentDeviceId }
        val countsById = deviceCounts.associateBy { it.deviceId }
        val currentSaveCount = countsById[currentDeviceId]?.saveCount ?: 0

        val thisDeviceName = currentDevice?.name?.takeIf { it.isNotBlank() } ?: "${Build.MANUFACTURER} ${Build.MODEL}"
        val deviceCard = ThisDeviceCard(
            deviceName = if (isConnected) DeviceKind.withoutRepeatedManufacturer(thisDeviceName) else null,
            deviceIdShort = currentDeviceId?.takeLast(8)?.takeIf { isConnected },
            kind = DeviceKind.classify(thisDeviceName, currentDevice?.platform ?: "android", currentDevice?.client),
            client = currentDevice?.client,
            clientVersion = currentDevice?.clientVersion,
            serverVersion = serverVersion,
            saveCount = currentSaveCount,
            isConnected = isConnected
        )

        val allOtherDevices = buildList {
            registeredDevices
                .filter { it.id != currentDeviceId }
                .forEach { device ->
                    val info = countsById[device.id] ?: return@forEach
                    if (info.saveCount <= 0) return@forEach
                    add(
                        DeviceSummary(
                            deviceId = device.id,
                            deviceName = device.name?.takeIf { it.isNotBlank() }
                                ?.let(DeviceKind::withoutRepeatedManufacturer)
                                ?: context.getString(R.string.savesync_device_unnamed),
                            kind = DeviceKind.classify(device.name, device.platform, device.client),
                            client = device.client,
                            clientVersion = device.clientVersion,
                            saveCount = info.saveCount,
                            latestSyncAt = info.latestSyncAt
                        )
                    )
                }
            val webInfo = countsById[null]
            val webCount = webInfo?.saveCount ?: 0
            val untracked = deviceCounts
                .filter { it.deviceId != null && it.deviceId != currentDeviceId && registeredDevices.none { d -> d.id == it.deviceId } }
            val untrackedTotal = untracked.sumOf { it.saveCount }
            val untrackedLatest = untracked.mapNotNull { it.latestSyncAt }.maxOrNull()
            val webTotal = webCount + untrackedTotal
            if (webTotal > 0) {
                add(
                    DeviceSummary(
                        deviceId = null,
                        deviceName = context.getString(R.string.savesync_device_web),
                        kind = DeviceKind.WEB,
                        client = null,
                        clientVersion = null,
                        saveCount = webTotal,
                        latestSyncAt = listOfNotNull(webInfo?.latestSyncAt, untrackedLatest).maxOrNull()
                    )
                )
            }
        }.sortedWith(
            compareByDescending<DeviceSummary> { it.latestSyncAt ?: Instant.MIN }
                .thenByDescending { it.saveCount }
        )

        val attentionRows = conflicts.mapNotNull { conflict ->
            val game = gameById[conflict.gameId] ?: return@mapNotNull null
            buildAttentionRow(conflict, game, artById[game.id]?.coverPath, deviceCard.deviceName)
        }

        val attentionGameIds = attentionRows.map { it.gameId }.toSet()
        val inProgressRows = queueState.operations
            .filter { it.status == SyncStatus.PENDING || it.status == SyncStatus.IN_PROGRESS }
            .filter { it.gameId !in attentionGameIds }
            .map { op ->
                InProgressRow(
                    gameId = op.gameId,
                    title = op.gameName,
                    platformDisplayName = gameById[op.gameId]?.platformSlug ?: "",
                    coverPath = op.coverPath,
                    direction = op.direction,
                    progress = op.progress,
                    statusLabel = when (op.status) {
                        SyncStatus.IN_PROGRESS -> if (op.direction == SyncDirection.UPLOAD) {
                            context.getString(R.string.savesync_progress_status_uploading)
                        } else {
                            context.getString(R.string.savesync_progress_status_downloading)
                        }
                        SyncStatus.PENDING -> context.getString(R.string.savesync_progress_status_queued)
                        else -> op.status.name
                    }
                )
            }

        val gameRows = saveRows
            .groupBy { it.gameId to it.channelName }
            .map { (_, rows) -> rows.maxByOrNull { it.lastSyncedAt ?: Instant.MIN }!! }
            .filterNot { it.isConflicted() }
            .groupBy { it.gameId }
            .mapNotNull { (gameId, entities) ->
                val game = gameById[gameId] ?: return@mapNotNull null
                GameSaveRow(
                    gameId = gameId,
                    title = game.title,
                    platformDisplayName = game.platformSlug,
                    coverPath = artById[gameId]?.coverPath,
                    slots = entities
                        .map { buildSaveSlotEntry(it, prefs.rommDeviceId) }
                        .sortedByDescending { it.lastSyncedAt ?: Instant.MIN }
                )
            }
            .sortedByDescending { it.lastSyncedAt ?: Instant.MIN }

        val rows = listOfNotNull(OtherDevicesRow.takeIf { allOtherDevices.isNotEmpty() }) +
            attentionRows + inProgressRows + gameRows
        val resolvedFocus = resolveFocusKey(focusedKey, rows)

        val accessNotice = if (inaccessibleLocations.isNotEmpty()) {
            SaveAccessNoticeUi(
                count = inaccessibleLocations.size,
                emulatorNames = inaccessibleLocations
                    .map { EmulatorRegistry.getById(it.emulatorId)?.displayName ?: it.emulatorId }
                    .distinct()
            )
        } else null

        SaveSyncUiState(
            deviceCard = deviceCard,
            otherDevices = allOtherDevices,
            otherDevicesExpanded = otherDevicesExpanded,
            accessNotice = accessNotice,
            attentionRows = attentionRows,
            inProgressRows = inProgressRows,
            gameRows = gameRows,
            focusedRowKey = resolvedFocus,
            attentionAction = attentionAction,
            isLoading = false
        )
    }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000, replayExpirationMillis = Long.MAX_VALUE),
            SaveSyncUiState()
        )

    private val _presentationActive = MutableStateFlow(false)

    fun setPresentationActive(active: Boolean) {
        _presentationActive.value = active
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    val gameActivity: StateFlow<GameActivitySnapshot?> = combine(_presentationActive, uiState) { active, state ->
        if (!active) null else state.presentedGameId?.let { gameId -> gameId to state.focusedSyncStamp() }
    }
        .distinctUntilChanged()
        .debounce(ACTIVITY_DEBOUNCE_MS)
        .mapLatest { key -> key?.let { (gameId, _) -> gameActivityRepository.load(gameId, ACTIVITY_CALENDAR_DAYS) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), null)

    private fun SaveSyncUiState.focusedSyncStamp(): Instant? =
        (focusedRow as? GameSaveRow)?.lastSyncedAt

    fun createInputHandler(
        onBack: () -> Unit,
        onNavigateToGame: (Long) -> Unit
    ): InputHandler = object : InputHandler {
        override fun onUp(): InputResult {
            return if (moveFocus(-1)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
        }
        override fun onDown(): InputResult {
            return if (moveFocus(1)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
        }
        override fun onLeft(): InputResult {
            if (uiState.value.focusedRow is AttentionRow) {
                val prev = previousAttentionAction(uiState.value.attentionAction)
                if (prev != null) _attentionAction.value = prev
                return InputResult.HANDLED
            }
            return InputResult.UNHANDLED
        }
        override fun onRight(): InputResult {
            if (uiState.value.focusedRow is AttentionRow) {
                val next = nextAttentionAction(uiState.value.attentionAction)
                if (next != null) _attentionAction.value = next
                return InputResult.HANDLED
            }
            return InputResult.UNHANDLED
        }
        override fun onBack(): InputResult {
            onBack()
            return InputResult.HANDLED
        }
        override fun onConfirm(): InputResult {
            when (val row = uiState.value.focusedRow) {
                is AttentionRow -> resolveAttention(row.conflictId, uiState.value.attentionAction.toResolution())
                is GameSaveRow -> onNavigateToGame(row.gameId)
                OtherDevicesRow -> toggleOtherDevices()
                is InProgressRow, null -> Unit
            }
            return InputResult.HANDLED
        }
        override fun onSecondaryAction(): InputResult {
            forceSaveCheck()
            return InputResult.HANDLED
        }
    }

    fun moveFocus(delta: Int): Boolean {
        val rows = uiState.value.allRows
        if (rows.isEmpty()) return false
        val current = uiState.value.focusedIndex
        val next = (current + delta).coerceIn(0, rows.lastIndex)
        if (next == current) return false
        _focusedRowKey.value = rows[next].key
        return true
    }

    fun toggleOtherDevices() {
        _focusedRowKey.value = OtherDevicesRow.key
        _otherDevicesExpanded.update { !it }
    }

    fun tapGameRow(row: GameSaveRow, onNavigateToGame: (Long) -> Unit) {
        if (uiState.value.focusedRow?.key == row.key) onNavigateToGame(row.gameId)
        else _focusedRowKey.value = row.key
    }

    fun setAttentionAction(action: AttentionAction) {
        _attentionAction.value = action
    }

    fun forceSaveCheck() {
        if (_forceCheckStatus.value is ForceSaveCheckUiState.Running) return
        _forceCheckStatus.value = ForceSaveCheckUiState.Running
        viewModelScope.launch {
            val negotiates = (romMRepository.connectionState.value as? ConnectionState.Connected)
                ?.capabilities?.supportsSyncNegotiate == true
            val result = runCatching {
                if (negotiates) scanThroughNegotiate() else saveSyncRepository.forceSaveCheck()
            }
            _forceCheckStatus.value = result.fold(
                onSuccess = { r ->
                    ForceSaveCheckUiState.Complete(
                        inspected = r.inspected,
                        queued = r.queued,
                        downloaded = r.downloaded,
                        message = r.message
                    )
                },
                onFailure = {
                    ForceSaveCheckUiState.Failed(
                        it.message ?: context.getString(R.string.savesync_force_check_failed_fallback)
                    )
                }
            )
        }
    }

    private suspend fun scanThroughNegotiate(): com.nendo.argosy.data.repository.SaveSyncOrchestrator.ForceSaveCheckResult {
        val summary = syncCoordinator.reconcileAll(bypassCooldown = true)
        if (!summary.serverAnswered) throw java.io.IOException()
        val drained = syncCoordinator.processQueue()
        return com.nendo.argosy.data.repository.SaveSyncOrchestrator.ForceSaveCheckResult(
            inspected = summary.planGames,
            queued = summary.planApplied,
            message = null,
            downloaded = (drained as? com.nendo.argosy.data.sync.SyncCoordinator.ProcessResult.Completed)?.processed ?: 0
        )
    }

    fun dismissForceCheckStatus() {
        _forceCheckStatus.value = ForceSaveCheckUiState.Idle
    }

    private fun resolveFocusKey(focusedKey: String?, rows: List<SaveSyncRow>): String? {
        if (focusedKey == null) {
            emptyFallbackJob?.cancel()
            return (rows.firstOrNull { it != OtherDevicesRow } ?: rows.firstOrNull())?.key
        }
        if (rows.any { it.key == focusedKey }) {
            emptyFallbackJob?.cancel()
            return focusedKey
        }
        val prefix = focusedKey.substringBefore(':')
        val sectionRows = rows.filter { it.key.startsWith("$prefix:") }
        if (sectionRows.isNotEmpty()) {
            emptyFallbackJob?.cancel()
            return sectionRows.first().key
        }
        if (emptyFallbackJob?.isActive != true) {
            emptyFallbackJob = viewModelScope.launch {
                delay(500)
                if (_focusedRowKey.value == focusedKey) {
                    _focusedRowKey.value = null
                }
            }
        }
        return focusedKey
    }

    private fun previousAttentionAction(current: AttentionAction): AttentionAction? = when (current) {
        AttentionAction.KEEP_LOCAL -> null
        AttentionAction.KEEP_SERVER -> AttentionAction.KEEP_LOCAL
        AttentionAction.SKIP -> AttentionAction.KEEP_SERVER
    }

    private fun nextAttentionAction(current: AttentionAction): AttentionAction? = when (current) {
        AttentionAction.KEEP_LOCAL -> AttentionAction.KEEP_SERVER
        AttentionAction.KEEP_SERVER -> AttentionAction.SKIP
        AttentionAction.SKIP -> null
    }

    fun resolveFocusedAttention(action: AttentionAction, rowKey: String? = null) {
        val state = uiState.value
        val target = if (rowKey != null) state.allRows.firstOrNull { it.key == rowKey } else state.focusedRow
        val row = target as? AttentionRow ?: return
        val currentIndex = state.allRows.indexOfFirst { it.key == row.key }.coerceAtLeast(0)
        val remaining = state.allRows.filter { it.key != row.key }
        _focusedRowKey.value = when {
            remaining.isEmpty() -> null
            currentIndex < remaining.size -> remaining[currentIndex].key
            else -> remaining.last().key
        }
        resolveAttention(row.conflictId, action.toResolution())
    }

    private fun AttentionAction.toResolution(): ConflictResolution = when (this) {
        AttentionAction.KEEP_LOCAL -> ConflictResolution.KEEP_LOCAL
        AttentionAction.KEEP_SERVER -> ConflictResolution.KEEP_SERVER
        AttentionAction.SKIP -> ConflictResolution.SKIP
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun ownedConflicts() = preferencesRepository.preferences
        .map { it.rommUserId }
        .distinctUntilChanged()
        .flatMapLatest { conflictResolutionService.observeOpenConflicts(it) }

    private fun ownedSaveRows() = preferencesRepository.preferences
        .map { it.rommUserId }
        .distinctUntilChanged()
        .flatMapLatest { saveSyncRepository.observeSaveRows(it) }

    private fun ownedDeviceCounts() = preferencesRepository.preferences
        .map { it.rommUserId }
        .distinctUntilChanged()
        .flatMapLatest { saveSyncRepository.observeSaveCountsByDevice(it) }

    private fun resolveAttention(conflictId: Long, resolution: ConflictResolution) {
        viewModelScope.launch {
            conflictResolutionService.resolveById(conflictId, resolution)
        }
    }

    private fun buildAttentionRow(
        conflict: PendingConflictEntity,
        game: GameEntity,
        coverPath: String?,
        thisDeviceName: String?
    ): AttentionRow {
        val isLocalNewer = (conflict.localUpdatedAt ?: Instant.MIN).isAfter(conflict.serverUpdatedAt ?: Instant.MIN)
        return AttentionRow(
            conflictId = conflict.id,
            gameId = conflict.gameId,
            title = game.title,
            platformDisplayName = game.platformSlug,
            coverPath = coverPath,
            channelName = conflict.slot,
            channelDisplay = effectiveChannelLabel(conflict.slot),
            localTime = conflict.localUpdatedAt,
            serverTime = conflict.serverUpdatedAt,
            localDeviceName = thisDeviceName,
            serverDeviceName = null,
            isLocalNewer = isLocalNewer
        )
    }

    private fun SaveSyncEntity.isConflicted(): Boolean =
        syncStatus == SaveSyncEntity.STATUS_CONFLICT ||
            syncStatus == SaveSyncEntity.STATUS_NEEDS_HARDCORE_RESOLUTION

    private fun buildSaveSlotEntry(
        entity: SaveSyncEntity,
        thisDeviceId: String?
    ): SaveSlotEntry {
        val isThisDevice = entity.lastSyncDeviceId != null && entity.lastSyncDeviceId == thisDeviceId
        val justSynced = entity.lastSyncedAt
            ?.isAfter(Instant.now().minus(JUST_SYNCED_THRESHOLD_MINUTES, ChronoUnit.MINUTES)) == true
        return SaveSlotEntry(
            saveSyncId = entity.id,
            channelName = entity.channelName,
            channelDisplay = effectiveChannelLabel(entity.channelName),
            syncStatus = entity.syncStatus,
            lastSyncedAt = entity.lastSyncedAt,
            localUpdatedAt = entity.localUpdatedAt,
            serverUpdatedAt = entity.serverUpdatedAt,
            lastSyncDeviceName = entity.lastSyncDeviceName,
            isLastSyncThisDevice = isThisDevice,
            isJustSynced = justSynced
        )
    }

    private fun effectiveChannelLabel(channelName: String?): String =
        channelName?.takeUnless { it.isBlank() } ?: context.getString(R.string.savesync_channel_archived)
}

