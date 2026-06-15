package com.nendo.argosy.ui.screens.savesync

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingConflictDao
import com.nendo.argosy.data.local.dao.SaveCountByDevice
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.dao.getByIdsChunked
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PendingConflictEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMDevice
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.SaveSyncApiClient
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.sync.ConflictResolution
import com.nendo.argosy.data.sync.ConflictResolutionService
import com.nendo.argosy.data.sync.SyncDirection
import com.nendo.argosy.data.sync.SyncQueueManager
import com.nendo.argosy.data.sync.SyncStatus
import java.io.File
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

private const val JUST_SYNCED_THRESHOLD_MINUTES = 30L

@HiltViewModel
class SaveSyncViewModel @Inject constructor(
    private val saveSyncDao: SaveSyncDao,
    private val pendingConflictDao: PendingConflictDao,
    private val syncQueueManager: SyncQueueManager,
    private val gameDao: GameDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val romMRepository: RomMRepository,
    private val conflictResolutionService: ConflictResolutionService,
    private val saveSyncRepository: SaveSyncRepository
) : ViewModel() {

    private val _forceCheckStatus = MutableStateFlow<ForceSaveCheckUiState>(ForceSaveCheckUiState.Idle)
    val forceCheckStatus: StateFlow<ForceSaveCheckUiState> = _forceCheckStatus

    private val _focusedRowKey = MutableStateFlow<String?>(null)
    private val _attentionAction = MutableStateFlow(AttentionAction.SKIP)
    private val _registeredDevices = MutableStateFlow<List<RomMDevice>>(emptyList())
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
            saveSyncDao.observeAll(),
            pendingConflictDao.observeOpenConflicts(),
            syncQueueManager.state,
            preferencesRepository.preferences,
            _focusedRowKey,
            _attentionAction,
            romMRepository.connectionState,
            saveSyncDao.observeSaveCountsByDevice(),
            _registeredDevices
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
        val gameIds = (saveRows.map { it.gameId } + conflicts.map { it.gameId } + queueState.operations.map { it.gameId })
            .distinct()
        val gameById = if (gameIds.isEmpty()) emptyMap() else gameDao.getByIdsChunked(gameIds).associateBy { it.id }

        val isConnected = connection is ConnectionState.Connected
        val serverVersion = (connection as? ConnectionState.Connected)?.version
        val currentDeviceId = prefs.rommDeviceId
        val currentDevice = registeredDevices.firstOrNull { it.id == currentDeviceId }
        val countsById = deviceCounts.associateBy { it.deviceId }
        val currentSaveCount = countsById[currentDeviceId]?.saveCount ?: 0

        val deviceCard = ThisDeviceCard(
            deviceName = if (isConnected) (currentDevice?.name ?: Build.MODEL) else null,
            deviceIdShort = currentDeviceId?.takeLast(8)?.takeIf { isConnected },
            platform = currentDevice?.platform,
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
                            deviceName = device.name?.takeIf { it.isNotBlank() } ?: "Unnamed device",
                            platform = device.platform,
                            client = device.client,
                            clientVersion = device.clientVersion,
                            saveCount = info.saveCount,
                            latestSyncAt = info.latestSyncAt,
                            isWeb = false
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
                        deviceName = "Web",
                        platform = null,
                        client = "romm",
                        clientVersion = null,
                        saveCount = webTotal,
                        latestSyncAt = listOfNotNull(webInfo?.latestSyncAt, untrackedLatest).maxOrNull(),
                        isWeb = true
                    )
                )
            }
        }.sortedWith(
            compareByDescending<DeviceSummary> { it.latestSyncAt ?: Instant.MIN }
                .thenByDescending { it.saveCount }
        )
        val otherDevicesLimit = 5
        val otherDevices = allOtherDevices.take(otherDevicesLimit)
        val otherDevicesHidden = (allOtherDevices.size - otherDevices.size).coerceAtLeast(0)

        val attentionRows = conflicts.mapNotNull { conflict ->
            val game = gameById[conflict.gameId] ?: return@mapNotNull null
            buildAttentionRow(conflict, game, deviceCard.deviceName)
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
                        SyncStatus.IN_PROGRESS -> if (op.direction == SyncDirection.UPLOAD) "Uploading" else "Downloading"
                        SyncStatus.PENDING -> "Queued"
                        else -> op.status.name
                    }
                )
            }

        val gameRows = saveRows
            .groupBy { it.gameId to it.channelName }
            .map { (_, rows) -> rows.maxByOrNull { it.lastSyncedAt ?: Instant.MIN }!! }
            .mapNotNull { entity ->
                val game = gameById[entity.gameId] ?: return@mapNotNull null
                buildGameSaveRow(entity, game, prefs.rommDeviceId)
            }
            .groupBy { it.gameId }
            .toList()
            .sortedByDescending { (_, rows) -> rows.maxOfOrNull { it.lastSyncedAt ?: Instant.MIN } ?: Instant.MIN }
            .flatMap { (_, rows) -> rows.sortedByDescending { it.lastSyncedAt ?: Instant.MIN } }

        val rows = attentionRows + inProgressRows + gameRows
        val resolvedFocus = resolveFocusKey(focusedKey, rows)

        SaveSyncUiState(
            deviceCard = deviceCard,
            otherDevices = otherDevices,
            otherDevicesHidden = otherDevicesHidden,
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

    fun createInputHandler(
        onBack: () -> Unit,
        onNavigateToGame: (Long) -> Unit
    ): InputHandler = object : InputHandler {
        override fun onUp(): InputResult {
            moveFocus(-1)
            return InputResult.HANDLED
        }
        override fun onDown(): InputResult {
            moveFocus(1)
            return InputResult.HANDLED
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
                is GameSaveRow -> if (!row.hasConflict) onNavigateToGame(row.gameId)
                is InProgressRow, null -> Unit
            }
            return InputResult.HANDLED
        }
        override fun onSecondaryAction(): InputResult {
            forceSaveCheck()
            return InputResult.HANDLED
        }
    }

    fun moveFocus(delta: Int) {
        val rows = uiState.value.allRows
        if (rows.isEmpty()) return
        val current = uiState.value.focusedIndex
        val next = (current + delta).coerceIn(0, rows.lastIndex)
        _focusedRowKey.value = rows[next].key
    }

    fun setAttentionAction(action: AttentionAction) {
        _attentionAction.value = action
    }

    fun forceSaveCheck() {
        if (_forceCheckStatus.value is ForceSaveCheckUiState.Running) return
        _forceCheckStatus.value = ForceSaveCheckUiState.Running
        viewModelScope.launch {
            val result = runCatching { saveSyncRepository.forceSaveCheck() }
            _forceCheckStatus.value = result.fold(
                onSuccess = { r ->
                    ForceSaveCheckUiState.Complete(
                        inspected = r.inspected,
                        queued = r.queued,
                        downloaded = r.downloaded,
                        message = r.message
                    )
                },
                onFailure = { ForceSaveCheckUiState.Failed(it.message ?: "Save check failed") }
            )
        }
    }

    fun dismissForceCheckStatus() {
        _forceCheckStatus.value = ForceSaveCheckUiState.Idle
    }

    private fun resolveFocusKey(focusedKey: String?, rows: List<SaveSyncRow>): String? {
        if (focusedKey == null) {
            emptyFallbackJob?.cancel()
            return rows.firstOrNull()?.key
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

    fun resolveFocusedAttention(action: AttentionAction) {
        val state = uiState.value
        val row = state.focusedRow as? AttentionRow ?: return
        val currentIndex = state.focusedIndex
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

    private fun resolveAttention(conflictId: Long, resolution: ConflictResolution) {
        viewModelScope.launch {
            val conflict = pendingConflictDao.getById(conflictId) ?: return@launch
            conflictResolutionService.resolve(conflict, resolution)
        }
    }

    private fun buildAttentionRow(
        conflict: PendingConflictEntity,
        game: GameEntity,
        thisDeviceName: String?
    ): AttentionRow {
        val isLocalNewer = (conflict.localUpdatedAt ?: Instant.MIN).isAfter(conflict.serverUpdatedAt ?: Instant.MIN)
        return AttentionRow(
            conflictId = conflict.id,
            gameId = conflict.gameId,
            title = game.title,
            platformDisplayName = game.platformSlug,
            coverPath = game.coverPath,
            channelName = conflict.slot,
            channelDisplay = effectiveChannelLabel(conflict.slot, game),
            localTime = conflict.localUpdatedAt,
            serverTime = conflict.serverUpdatedAt,
            localDeviceName = thisDeviceName,
            serverDeviceName = null,
            isLocalNewer = isLocalNewer
        )
    }

    private fun buildGameSaveRow(
        entity: SaveSyncEntity,
        game: GameEntity,
        thisDeviceId: String?
    ): GameSaveRow {
        val isThisDevice = entity.lastSyncDeviceId != null && entity.lastSyncDeviceId == thisDeviceId
        val justSynced = entity.lastSyncedAt
            ?.isAfter(Instant.now().minus(JUST_SYNCED_THRESHOLD_MINUTES, ChronoUnit.MINUTES)) == true
        val hasConflict = entity.syncStatus == SaveSyncEntity.STATUS_CONFLICT ||
            entity.syncStatus == SaveSyncEntity.STATUS_NEEDS_HARDCORE_RESOLUTION
        return GameSaveRow(
            saveSyncId = entity.id,
            gameId = entity.gameId,
            title = game.title,
            platformDisplayName = game.platformSlug,
            coverPath = game.coverPath,
            channelName = entity.channelName,
            channelDisplay = effectiveChannelLabel(entity.channelName, game),
            syncStatus = entity.syncStatus,
            lastSyncedAt = entity.lastSyncedAt,
            localUpdatedAt = entity.localUpdatedAt,
            serverUpdatedAt = entity.serverUpdatedAt,
            lastSyncDeviceName = entity.lastSyncDeviceName,
            isLastSyncThisDevice = isThisDevice,
            isJustSynced = justSynced,
            hasConflict = hasConflict
        )
    }

    private fun effectiveChannelLabel(channelName: String?, game: GameEntity): String {
        if (channelName.isNullOrBlank()) return "Archived"
        if (channelName.equals(SaveSyncApiClient.AUTOSAVE_SLOT_NAME, ignoreCase = true)) return "Autosave"
        if (channelName.equals(SaveSyncApiClient.DEFAULT_SAVE_NAME, ignoreCase = true)) return "Autosave"
        if (channelName.equals(game.title, ignoreCase = true)) return "Autosave"
        val romBaseName = game.localPath?.let { File(it).nameWithoutExtension }
        if (romBaseName != null && channelName.equals(romBaseName, ignoreCase = true)) return "Autosave"
        return channelName
    }
}

