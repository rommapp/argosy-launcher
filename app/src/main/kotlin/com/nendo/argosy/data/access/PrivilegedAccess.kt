package com.nendo.argosy.data.access

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.data.model.PrivilegedAccessState
import com.nendo.argosy.data.model.PrivilegedReach
import com.nendo.argosy.data.model.PrivilegedRoute
import com.nendo.argosy.data.preferences.PrivilegedAccessPreferencesRepository
import com.nendo.argosy.data.setup.SetupStateRepository
import com.nendo.argosy.domain.model.isComplete
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.RootCommandRunner
import com.nendo.argosy.util.RootShell
import com.nendo.argosy.util.ShizukuExecutor
import com.nendo.argosy.util.SuExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Magisk and Shizuku as routes to emulator save folders and root features on devices without a
 * vendor root daemon. A route is used only after the user enables it, and every enable and every
 * startup re-runs a read-only check whose result decides what the route backs.
 */
@Singleton
class PrivilegedAccess @Inject constructor(
    private val preferences: PrivilegedAccessPreferencesRepository,
    private val setupState: SetupStateRepository,
    private val notificationManager: NotificationManager
) {
    private val _state = MutableStateFlow(PrivilegedAccessState())
    val state: StateFlow<PrivilegedAccessState> = _state.asStateFlow()

    private val _prompt = MutableStateFlow<PrivilegedRoute?>(null)
    val prompt: StateFlow<PrivilegedRoute?> = _prompt.asStateFlow()

    private val checkLock = Mutex()
    private lateinit var scope: CoroutineScope

    /**
     * Restores an enabled route, then watches for Shizuku starting or stopping and for setup
     * completing, which decide when the one-time prompt appears.
     */
    suspend fun start(scope: CoroutineScope) {
        this.scope = scope
        withContext(Dispatchers.IO) { restoreMagisk() }
        Shizuku.addBinderReceivedListenerSticky { scope.launch(Dispatchers.IO) { onShizukuReceived() } }
        Shizuku.addBinderDeadListener { scope.launch(Dispatchers.IO) { onShizukuGone() } }
        scope.launch {
            setupState.state.map { it.isComplete }.distinctUntilChanged().collect { complete ->
                if (complete) {
                    delay(PROMPT_SETTLE_MS)
                    refreshOffer()
                }
            }
        }
    }

    suspend fun isMagiskApproved(): Boolean = preferences.isMagiskApproved()

    /**
     * Records a Magisk answer given elsewhere, such as the performance mode prompt, so the one
     * approval backs every root feature.
     */
    suspend fun recordMagiskApproval(granted: Boolean) {
        preferences.setMagiskApproved(granted)
        if (granted && RootShell.enabledRoot !== SuExecutor && !RootShell.hasVendorRoute) {
            withContext(Dispatchers.IO) { verify(PrivilegedRoute.MAGISK, notify = false) }
        }
    }

    fun answerPrompt(enable: Boolean) {
        val route = _prompt.value ?: return
        _prompt.value = null
        scope.launch {
            preferences.setPromptAnswered()
            if (enable) enable(route)
        }
    }

    /**
     * Asks the route's manager for access, then checks what it reaches and reports the result.
     */
    suspend fun enable(route: PrivilegedRoute) = withContext(Dispatchers.IO) {
        _prompt.value = null
        _state.update { it.copy(reach = PrivilegedReach.CHECKING) }
        val runner = runnerFor(route)
        if (!runner.requestGrant()) {
            persist(route, enabled = false)
            adopt(PrivilegedReach.REFUSED, route, runner)
            notify(route, PrivilegedReach.REFUSED)
            return@withContext
        }
        verify(route, notify = true)
    }

    fun enableOffered() {
        val route = _state.value.route ?: return
        scope.launch { enable(route) }
    }

    private suspend fun restoreMagisk() {
        if (RootShell.hasVendorRoute || !preferences.isMagiskApproved() || !SuExecutor.isInstalled) return
        if (!SuExecutor.requestGrant()) {
            Logger.warn(TAG, "Magisk no longer grants root")
            adopt(PrivilegedReach.REFUSED, PrivilegedRoute.MAGISK, SuExecutor)
            return
        }
        verify(PrivilegedRoute.MAGISK, notify = false)
    }

    private suspend fun onShizukuReceived() {
        refreshOffer()
        if (RootShell.hasVendorRoute || RootShell.enabledRoot === SuExecutor) return
        if (preferences.isShizukuEnabled() && ShizukuExecutor.isGranted) verify(PrivilegedRoute.SHIZUKU, notify = false)
    }

    private fun onShizukuGone() {
        if (RootShell.enabledRoot === ShizukuExecutor || RootShell.appDataShell === ShizukuExecutor) {
            Logger.info(TAG, "Shizuku stopped; its route is off until it starts again")
            RootShell.adopt(null, null)
            _state.update { it.copy(reach = PrivilegedReach.NONE) }
        }
        scope.launch { refreshOffer() }
    }

    private suspend fun verify(route: PrivilegedRoute, notify: Boolean) = checkLock.withLock {
        _state.update { it.copy(reach = PrivilegedReach.CHECKING) }
        val runner = runnerFor(route)
        val result = runner.runScript(PrivilegedAccessRules.checkScript(android.os.Process.myUid() / PER_USER_RANGE))
        val reach = PrivilegedAccessRules.reachOf(route, result)
        Logger.info(TAG, "check | route=$route reach=$reach exit=${result?.exitCode} output=${result?.output}")
        persist(route, enabled = reach.isReady)
        adopt(reach, route, runner)
        if (notify) notify(route, reach)
        refreshOffer()
    }

    private fun adopt(reach: PrivilegedReach, route: PrivilegedRoute, runner: RootCommandRunner) {
        when (reach) {
            PrivilegedReach.ROOT -> RootShell.adopt(runner, null)
            PrivilegedReach.APP_DATA -> RootShell.adopt(null, ShizukuExecutor)
            else -> if (RootShell.enabledRoot === runner || RootShell.appDataShell === runner) RootShell.adopt(null, null)
        }
        _state.update {
            it.copy(enabled = route.takeIf { reach.isReady }, reach = reach)
        }
    }

    private suspend fun persist(route: PrivilegedRoute, enabled: Boolean) {
        when (route) {
            PrivilegedRoute.MAGISK -> preferences.setMagiskApproved(enabled)
            PrivilegedRoute.SHIZUKU -> preferences.setShizukuEnabled(enabled)
        }
    }

    private suspend fun refreshOffer() {
        val detected = PrivilegedAccessRules.offeredRoute(
            vendorRoot = RootShell.hasVendorRoute,
            magiskInstalled = SuExecutor.isInstalled,
            shizukuRunning = ShizukuExecutor.isInstalled
        )
        _state.update { it.copy(detected = detected) }
        _prompt.value = PrivilegedAccessRules.promptRoute(
            setupComplete = setupState.state.first().isComplete,
            answered = preferences.isPromptAnswered(),
            enabled = _state.value.enabled,
            detected = detected
        )
    }

    private fun runnerFor(route: PrivilegedRoute): RootCommandRunner = when (route) {
        PrivilegedRoute.MAGISK -> SuExecutor
        PrivilegedRoute.SHIZUKU -> ShizukuExecutor
    }

    private fun notify(route: PrivilegedRoute, reach: PrivilegedReach) {
        val name = route.productName
        val (title, subtitle, type) = when (reach) {
            PrivilegedReach.ROOT -> Triple(
                NotificationText.Res(R.string.privileged_access_result_enabled_title, listOf(name)),
                NotificationText.Res(R.string.privileged_access_result_root_subtitle),
                NotificationType.SUCCESS
            )
            PrivilegedReach.APP_DATA -> Triple(
                NotificationText.Res(R.string.privileged_access_result_enabled_title, listOf(name)),
                NotificationText.Res(R.string.privileged_access_result_app_data_subtitle),
                NotificationType.SUCCESS
            )
            PrivilegedReach.REFUSED -> Triple(
                NotificationText.Res(R.string.privileged_access_result_refused_title, listOf(name)),
                null,
                NotificationType.WARNING
            )
            else -> Triple(
                NotificationText.Res(R.string.privileged_access_result_unreachable_title, listOf(name)),
                NotificationText.Res(R.string.privileged_access_result_unreachable_subtitle),
                NotificationType.WARNING
            )
        }
        notificationManager.show(title = title, subtitle = subtitle, type = type, duration = NotificationDuration.MEDIUM)
    }

    private companion object {
        const val TAG = "PrivilegedAccess"
        const val PROMPT_SETTLE_MS = 2_000L
        const val PER_USER_RANGE = 100_000
    }
}
