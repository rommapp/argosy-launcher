package com.nendo.argosy.ui.screens.setup

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import com.nendo.argosy.data.permissions.PermissionGrant
import com.nendo.argosy.data.permissions.PermissionRegistry
import com.nendo.argosy.data.setup.SetupStateRepository
import com.nendo.argosy.domain.model.ArgosyPermission
import com.nendo.argosy.domain.model.PermissionAvailability
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Live permission state for the permissions and accessibility pages, and the root route that
 * grants what it can on entry.
 */
class SetupAccessDelegate @Inject constructor(
    private val application: Application,
    private val registry: PermissionRegistry,
    private val setupStateRepository: SetupStateRepository
) {
    private val _state = MutableStateFlow(SetupAccessState())
    val state: StateFlow<SetupAccessState> = _state.asStateFlow()

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private lateinit var scope: CoroutineScope
    private var probed = false
    private var enteredPage: SetupPage? = null
    private var permissionsRootRun = false
    private var accessibilityRootRun = false
    private var awaitingUsage = false

    fun bind(scope: CoroutineScope) {
        this.scope = scope
        scope.launch {
            val probe = withContext(ioDispatcher) {
                Triple(registry.hasRootRoute(), registry.restrictedSettingsApply(), registry.accessibilityScreen() != null)
            }
            val (root, restricted, screen) = probe
            _state.update {
                it.copy(rootRoute = root, restrictedSettings = restricted, accessibilityOffered = root || screen)
            }
            probed = true
            when (enteredPage) {
                SetupPage.PERMISSIONS -> enterPermissions()
                SetupPage.ACCESSIBILITY -> enterAccessibility()
                else -> refresh()
            }
        }
    }

    fun refresh() {
        setupStateRepository.refresh()
        scope.launch {
            val snapshot = withContext(ioDispatcher) { registry.snapshot() }
            _state.update { it.copy(snapshot = snapshot) }
        }
    }

    fun grantFor(permission: ArgosyPermission): PermissionGrant =
        registry.grantFor(permission, _state.value.snapshot.of(permission))

    fun grantOnRoot(permission: ArgosyPermission) {
        runOnRoot(setOf(permission))
    }

    fun enterPermissions() {
        enteredPage = SetupPage.PERMISSIONS
        refresh()
        if (!probed || permissionsRootRun || !_state.value.rootRoute) return
        permissionsRootRun = true
        runOnRoot(SETUP_REQUIRED_PERMISSIONS.toSet(), markGranted = true)
    }

    fun enterAccessibility() {
        enteredPage = SetupPage.ACCESSIBILITY
        refresh()
        if (!probed || accessibilityRootRun || !_state.value.rootRoute) return
        accessibilityRootRun = true
        scope.launch {
            _state.update { it.copy(rootGranting = it.rootGranting + ArgosyPermission.ACCESSIBILITY) }
            val on = withContext(ioDispatcher) {
                registry.grantOnRoot(ArgosyPermission.ACCESSIBILITY)
                registry.availabilityOf(ArgosyPermission.ACCESSIBILITY) == PermissionAvailability.GRANTED
            }
            val snapshot = withContext(ioDispatcher) { registry.snapshot() }
            _state.update {
                it.copy(
                    snapshot = snapshot,
                    rootGranting = it.rootGranting - ArgosyPermission.ACCESSIBILITY,
                    rootAccessibilityFailed = !on
                )
            }
        }
    }

    fun leave() {
        enteredPage = null
    }

    fun noteUsageAttempt() {
        awaitingUsage = true
    }

    fun openStep(step: AccessibilityStepKind) {
        val intent = when (step) {
            AccessibilityStepKind.RESTRICTED_SETTINGS -> registry.appInfoScreen()
            AccessibilityStepKind.SERVICE -> registry.accessibilityScreen()
        } ?: return
        _state.update { it.copy(awaiting = step) }
        try {
            application.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Logger.warn(TAG, "Accessibility step screen did not open", e)
            _state.update { it.copy(awaiting = null) }
        }
    }

    /**
     * Records the return from an accessibility step's Android screen. Coming back from App info
     * marks the restricted settings step done; coming back from Accessibility with the service
     * still off on a device that restricts it reopens that step. Coming back from Usage access
     * still refused on such a device shows that step on the permissions page.
     */
    fun onResume() {
        val awaiting = _state.value.awaiting
        val usageAttempted = awaitingUsage
        awaitingUsage = false
        scope.launch {
            val snapshot = withContext(ioDispatcher) { registry.snapshot() }
            _state.update { base ->
                val usageRefused = usageAttempted && base.restrictedSettings && !base.rootRoute &&
                    !snapshot.isGranted(ArgosyPermission.USAGE_ACCESS)
                val current = if (usageRefused) base.copy(usageBlocked = true) else base
                val on = snapshot.isGranted(ArgosyPermission.ACCESSIBILITY)
                when (awaiting) {
                    AccessibilityStepKind.RESTRICTED_SETTINGS ->
                        current.copy(snapshot = snapshot, awaiting = null, restrictedStepDone = true, stillBlocked = false)
                    AccessibilityStepKind.SERVICE -> {
                        val blocked = !on && current.variant == AccessibilityVariant.TWO_STEP
                        current.copy(
                            snapshot = snapshot,
                            awaiting = null,
                            restrictedStepDone = current.restrictedStepDone && !blocked,
                            stillBlocked = blocked
                        )
                    }
                    null -> current.copy(snapshot = snapshot)
                }
            }
        }
        setupStateRepository.refresh()
    }

    private fun runOnRoot(permissions: Set<ArgosyPermission>, markGranted: Boolean = false) {
        scope.launch {
            val missing = permissions.filter { _state.value.snapshot.of(it) != PermissionAvailability.GRANTED }.toSet()
            if (missing.isEmpty()) return@launch
            _state.update { it.copy(rootGranting = it.rootGranting + missing) }
            val snapshot = withContext(ioDispatcher) {
                missing.forEach { registry.grantOnRoot(it) }
                registry.snapshot()
            }
            val granted = missing.any { snapshot.isGranted(it) }
            _state.update {
                it.copy(
                    snapshot = snapshot,
                    rootGranting = it.rootGranting - missing,
                    rootGranted = it.rootGranted || (markGranted && granted)
                )
            }
            setupStateRepository.refresh()
        }
    }

    private companion object {
        const val TAG = "SetupAccess"
    }
}
