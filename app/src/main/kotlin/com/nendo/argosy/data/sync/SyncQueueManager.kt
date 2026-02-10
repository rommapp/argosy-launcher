package com.nendo.argosy.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

enum class SyncDirection { UPLOAD, DOWNLOAD }
enum class SyncStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED, CONFLICT_PENDING }
enum class ConflictResolution { KEEP_LOCAL, KEEP_SERVER, SKIP }

data class ConflictInfo(
    val gameId: Long,
    val gameName: String,
    val channelName: String?,
    val localTimestamp: Instant,
    val serverTimestamp: Instant,
    val isHashConflict: Boolean
)

data class SyncOperation(
    val gameId: Long,
    val gameName: String,
    val channelName: String? = null,
    val coverPath: String?,
    val direction: SyncDirection,
    val status: SyncStatus,
    val progress: Float = 0f,
    val error: String? = null,
    val conflictInfo: ConflictInfo? = null
)

data class SyncQueueState(
    val operations: List<SyncOperation> = emptyList(),
    val isActive: Boolean = false,
    val currentOperation: SyncOperation? = null
) {
    val hasOperations: Boolean get() = operations.isNotEmpty()
    val pendingCount: Int get() = operations.count { it.status == SyncStatus.PENDING }
    val completedCount: Int get() = operations.count { it.status == SyncStatus.COMPLETED }
    val inProgressCount: Int get() = operations.count { it.status == SyncStatus.IN_PROGRESS }
}

@Singleton
class SyncQueueManager @Inject constructor() {
    private val _state = MutableStateFlow(SyncQueueState())
    val state: StateFlow<SyncQueueState> = _state.asStateFlow()

    private val _pendingConflicts = MutableStateFlow<List<ConflictInfo>>(emptyList())
    val pendingConflicts: StateFlow<List<ConflictInfo>> = _pendingConflicts.asStateFlow()

    private val conflictResolutions = MutableStateFlow<Map<Long, ConflictResolution>>(emptyMap())

    fun addOperation(operation: SyncOperation) {
        _state.update { state ->
            val existingIndex = state.operations.indexOfFirst { it.gameId == operation.gameId }
            val updatedOps = if (existingIndex >= 0) {
                state.operations.toMutableList().apply { set(existingIndex, operation) }
            } else {
                state.operations + operation
            }
            state.copy(operations = updatedOps, isActive = true)
        }
    }

    fun updateOperation(gameId: Long, transform: (SyncOperation) -> SyncOperation) {
        _state.update { state ->
            val updatedOps = state.operations.map { op ->
                if (op.gameId == gameId) transform(op) else op
            }
            val current = updatedOps.find { it.status == SyncStatus.IN_PROGRESS }
            state.copy(operations = updatedOps, currentOperation = current)
        }
    }

    fun completeOperation(gameId: Long, error: String? = null) {
        _state.update { state ->
            val updatedOps = state.operations.map { op ->
                if (op.gameId == gameId) {
                    op.copy(
                        status = if (error == null) SyncStatus.COMPLETED else SyncStatus.FAILED,
                        error = error,
                        progress = if (error == null) 1f else op.progress
                    )
                } else op
            }
            val remaining = updatedOps.filter { it.status != SyncStatus.COMPLETED && it.status != SyncStatus.FAILED }
            val current = updatedOps.find { it.status == SyncStatus.IN_PROGRESS }
            state.copy(
                operations = updatedOps,
                currentOperation = current,
                isActive = remaining.isNotEmpty()
            )
        }
    }

    fun clearCompletedOperations() {
        _state.update { state ->
            val remaining = state.operations.filter {
                it.status != SyncStatus.COMPLETED && it.status != SyncStatus.FAILED
            }
            state.copy(operations = remaining, isActive = remaining.isNotEmpty())
        }
    }

    fun addConflict(conflict: ConflictInfo) {
        _pendingConflicts.update { conflicts ->
            if (conflicts.any { it.gameId == conflict.gameId }) conflicts
            else conflicts + conflict
        }
    }

    suspend fun awaitResolution(gameId: Long): ConflictResolution {
        return conflictResolutions.first { it.containsKey(gameId) }[gameId]!!
    }

    fun resolveConflict(gameId: Long, resolution: ConflictResolution) {
        conflictResolutions.update { it + (gameId to resolution) }
        _pendingConflicts.update { conflicts -> conflicts.filter { it.gameId != gameId } }
    }

    fun clearResolutions() {
        conflictResolutions.value = emptyMap()
    }
}
