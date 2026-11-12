package com.nendo.argosy.data.permissions

import com.nendo.argosy.domain.model.ArgosyPermission
import com.nendo.argosy.domain.model.PermissionSheetRequest
import com.nendo.argosy.domain.model.PermissionSnapshot
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one permission sheet on screen. A started surface attaches itself as a host; [show] answers
 * false straight away while no host is attached, so a caller never waits on a sheet nobody draws.
 */
@Singleton
class PermissionSheetController @Inject constructor(
    private val registry: PermissionRegistry
) {
    private val scope = SafeCoroutineScope(Dispatchers.IO, TAG)
    private val lock = Any()
    private val hosts = MutableStateFlow(0)
    private var pending: CompletableDeferred<Boolean>? = null

    private val _request = MutableStateFlow<PermissionSheetRequest?>(null)
    val request: StateFlow<PermissionSheetRequest?> = _request.asStateFlow()

    private val _snapshot = MutableStateFlow(PermissionSnapshot())
    val snapshot: StateFlow<PermissionSnapshot> = _snapshot.asStateFlow()

    private val _focusIndex = MutableStateFlow(0)
    val focusIndex: StateFlow<Int> = _focusIndex.asStateFlow()

    val hasHost: Boolean get() = hosts.value > 0

    fun attachHost() {
        hosts.update { it + 1 }
    }

    fun detachHost() {
        hosts.update { (it - 1).coerceAtLeast(0) }
    }

    /**
     * Shows [request] and returns true when the person goes on, false when they close the sheet
     * or no host is attached.
     */
    suspend fun show(request: PermissionSheetRequest): Boolean {
        if (!hasHost) return false
        val answer = CompletableDeferred<Boolean>()
        synchronized(lock) {
            pending?.complete(false)
            pending = answer
        }
        _snapshot.value = withContext(Dispatchers.IO) { registry.snapshot() }
        _focusIndex.value = 0
        _request.value = request
        return try {
            answer.await()
        } finally {
            synchronized(lock) {
                if (pending === answer) {
                    pending = null
                    _request.value = null
                }
            }
        }
    }

    fun answer(goesOn: Boolean) {
        synchronized(lock) { pending }?.complete(goesOn)
    }

    fun refresh() {
        scope.launch { _snapshot.value = registry.snapshot() }
    }

    fun moveFocus(delta: Int, targetCount: Int) {
        _focusIndex.update { (it + delta).coerceIn(0, (targetCount - 1).coerceAtLeast(0)) }
    }

    fun setFocus(index: Int) {
        _focusIndex.value = index.coerceAtLeast(0)
    }

    fun grantFor(permission: ArgosyPermission): PermissionGrant =
        registry.grantFor(permission, _snapshot.value.of(permission))

    fun grantOnRoot(permission: ArgosyPermission) {
        scope.launch {
            registry.grantOnRoot(permission)
            _snapshot.value = registry.snapshot()
        }
    }

    private companion object {
        const val TAG = "PermissionSheet"
    }
}
