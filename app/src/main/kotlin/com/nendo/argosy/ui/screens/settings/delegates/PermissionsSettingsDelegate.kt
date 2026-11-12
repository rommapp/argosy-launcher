package com.nendo.argosy.ui.screens.settings.delegates

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import com.nendo.argosy.data.access.PrivilegedAccess
import com.nendo.argosy.data.permissions.PermissionGrant
import com.nendo.argosy.data.permissions.PermissionRegistry
import com.nendo.argosy.data.permissions.PermissionWatch
import com.nendo.argosy.data.preferences.DisplayPreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.domain.model.ArgosyPermission
import com.nendo.argosy.domain.model.PermissionAvailability
import com.nendo.argosy.hardware.ScreenCaptureManager
import com.nendo.argosy.ui.screens.settings.PermissionsState
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PermissionsSettingsDelegate @Inject constructor(
    private val application: Application,
    private val registry: PermissionRegistry,
    private val permissionWatch: PermissionWatch,
    private val screenCaptureManager: ScreenCaptureManager,
    private val displayPreferences: DisplayPreferencesRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val privilegedAccess: PrivilegedAccess
) {
    private val _state = MutableStateFlow(PermissionsState())
    val state: StateFlow<PermissionsState> = _state.asStateFlow()

    private val _grantRequests = MutableSharedFlow<ArgosyPermission>(extraBufferCapacity = 1)
    val grantRequests: SharedFlow<ArgosyPermission> = _grantRequests.asSharedFlow()

    @Volatile private var lockScreenArt = true
    private var scope: CoroutineScope? = null

    fun observeLockScreenArt(scope: CoroutineScope) {
        this.scope = scope
        scope.launch {
            combine(
                displayPreferences.preferences.map { it.lockScreenArt }.distinctUntilChanged(),
                screenCaptureManager.hasPermission
            ) { art, _ -> art }.collect {
                lockScreenArt = it
                refreshPermissions()
            }
        }
    }

    fun observePrivilegedAccess(scope: CoroutineScope) {
        scope.launch {
            privilegedAccess.state.collect { access ->
                _state.update { it.copy(privilegedRoute = access.route, privilegedReach = access.reach) }
                refreshPermissions()
            }
        }
    }

    fun refreshPermissions() {
        val target = scope ?: return
        val art = lockScreenArt
        target.launch {
            val snapshot = withContext(Dispatchers.IO) { registry.snapshot(art) }
            _state.update { it.copy(snapshot = snapshot) }
        }
    }

    fun isAccessibilityEnabled(): Boolean =
        registry.availabilityOf(ArgosyPermission.ACCESSIBILITY) == PermissionAvailability.GRANTED

    /**
     * Acting on a permission row. A granted sync-required row while save sync is on raises the
     * save sync warning first; anything else goes straight to its grant action.
     */
    fun act(permission: ArgosyPermission) {
        val target = scope ?: return
        when (_state.value.snapshot.of(permission)) {
            PermissionAvailability.CHECKING, PermissionAvailability.UNAVAILABLE -> return
            PermissionAvailability.MISSING -> grant(permission)
            PermissionAvailability.GRANTED -> target.launch {
                val saveSyncEnabled = preferencesRepository.preferences.first().saveSyncEnabled
                if (permission.syncRequired && saveSyncEnabled) {
                    _state.update { it.copy(saveSyncWarning = permission) }
                } else {
                    grant(permission)
                }
            }
        }
    }

    fun dismissSaveSyncWarning() {
        _state.update { it.copy(saveSyncWarning = null) }
    }

    fun takeSaveSyncWarning(): ArgosyPermission? {
        val permission = _state.value.saveSyncWarning ?: return null
        _state.update { it.copy(saveSyncWarning = null) }
        return permission
    }

    fun grant(permission: ArgosyPermission) {
        if (_state.value.snapshot.isGranted(permission)) permissionWatch.expectChange(permission)
        _grantRequests.tryEmit(permission)
    }

    fun grantFor(permission: ArgosyPermission): PermissionGrant =
        registry.grantFor(permission, _state.value.snapshot.of(permission))

    fun grantOnRoot(permission: ArgosyPermission) {
        val target = scope ?: return
        target.launch {
            withContext(Dispatchers.IO) { registry.grantOnRoot(permission) }
            refreshPermissions()
        }
    }

    fun enablePrivilegedAccess() = privilegedAccess.enableOffered()

    fun openAccessibilitySettings() {
        val intent = registry.accessibilityScreen() ?: return
        try {
            application.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Logger.warn(TAG, "Accessibility settings did not open", e)
        }
    }

    private companion object {
        const val TAG = "PermissionsSettings"
    }
}
