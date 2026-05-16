package com.nendo.argosy.data.sync.strategy

data class ReconcilePlan(
    val sessionId: Long? = null,
    val operations: List<ReconcileOperation>,
) {
    companion object {
        val EMPTY = ReconcilePlan(sessionId = null, operations = emptyList())
    }

    val uploadCount: Int get() = operations.count { it.action == ReconcileAction.UPLOAD }
    val downloadCount: Int get() = operations.count { it.action == ReconcileAction.DOWNLOAD }
    val conflictCount: Int get() = operations.count { it.action == ReconcileAction.CONFLICT }
    val noOpCount: Int get() = operations.count { it.action == ReconcileAction.NO_OP }
}

data class ReconcileOperation(
    val action: ReconcileAction,
    val romId: Long,
    val saveId: Long? = null,
    val fileName: String,
    val slot: String? = null,
    val emulator: String? = null,
    val reason: String = "",
    val serverUpdatedAt: String? = null,
    val serverContentHash: String? = null,
)

enum class ReconcileAction {
    UPLOAD,
    DOWNLOAD,
    CONFLICT,
    NO_OP,
}

data class LocalSaveState(
    val romId: Long,
    val fileName: String,
    val slot: String? = null,
    val emulator: String? = null,
    val contentHash: String? = null,
    val updatedAt: String,
    val fileSizeBytes: Long,
)
