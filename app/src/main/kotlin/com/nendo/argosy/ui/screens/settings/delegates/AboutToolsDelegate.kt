package com.nendo.argosy.ui.screens.settings.delegates

import android.view.InputDevice
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.R
import com.nendo.argosy.core.input.ConnectedControllerTracker
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.core.input.actsAsGamepad
import com.nendo.argosy.core.input.controllerIdOf
import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.data.diagnostics.DiagnosticsExportResult
import com.nendo.argosy.data.diagnostics.DiagnosticsExporter
import com.nendo.argosy.data.diagnostics.DisplayDiagnostics
import com.nendo.argosy.data.model.DisplaySnapshot
import com.nendo.argosy.data.preferences.AppPreferencesRepository
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.github.UpdateState
import com.nendo.argosy.data.repository.InputConfigRepository
import com.nendo.argosy.data.update.AppUpdateManager
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.setup.SetupHandoff
import com.nendo.argosy.ui.screens.setup.SetupPage
import com.nendo.argosy.ui.screens.setup.SetupPosition
import com.nendo.argosy.ui.screens.setup.components.ButtonCheckController
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

/**
 * The pad the controller test reads. [seat] is the saved seat, zero-based, null when the pad has
 * none.
 */
data class ControllerTestPad(
    val deviceId: Int,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val controllerId: String,
    val seat: Int?
)

data class DisplayInfoState(
    val displays: List<DisplaySnapshot> = emptyList(),
    val loading: Boolean = false,
    val focusIndex: Int = 0
)

data class AboutToolsState(
    val controllerTestOpen: Boolean = false,
    val controllerTestPad: ControllerTestPad? = null,
    val displayInfo: DisplayInfoState? = null,
    val resetConfirmOpen: Boolean = false,
    val exporting: Boolean = false,
    val promptLoading: Boolean = false
)

/**
 * The tools in Settings > About > Debug: the controller test, display info, the diagnostics
 * export, running setup again, the update prompt on demand and the one-time notice reset. Each
 * open tool holds the input stack through its own handler.
 */
class AboutToolsDelegate @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val inputConfigRepository: InputConfigRepository,
    private val controllerTracker: ConnectedControllerTracker,
    private val appUpdateManager: AppUpdateManager,
    private val syncPreferences: SyncPreferencesRepository,
    private val appPreferences: AppPreferencesRepository,
    private val setupHandoff: SetupHandoff,
    private val displayDiagnostics: DisplayDiagnostics,
    private val diagnosticsExporter: DiagnosticsExporter,
    private val notificationManager: NotificationManager
) {
    private val _state = MutableStateFlow(AboutToolsState())
    val state: StateFlow<AboutToolsState> = _state.asStateFlow()

    lateinit var buttonCheck: ButtonCheckController
        private set

    private lateinit var scope: CoroutineScope
    private var padLookup: Job? = null
    private var lookupDeviceId: Int? = null

    fun bind(scope: CoroutineScope) {
        this.scope = scope
        buttonCheck = ButtonCheckController(preferencesRepository, scope, showsBack = false)
        scope.launch {
            buttonCheck.state.collect { check ->
                if (_state.value.controllerTestOpen && !check.capturesKeys) closeControllerTest()
            }
        }
    }

    fun openControllerTest() {
        buttonCheck.reset()
        buttonCheck.startTest()
        lookupDeviceId = null
        _state.update { it.copy(controllerTestOpen = true, controllerTestPad = null) }
        controllerTracker.activeController.value?.deviceId?.let(::onPadInput)
    }

    fun onPadInput(deviceId: Int) {
        if (!_state.value.controllerTestOpen || deviceId == lookupDeviceId) return
        lookupDeviceId = deviceId
        padLookup?.cancel()
        padLookup = scope.launch {
            val pad = readPad(deviceId) ?: return@launch
            _state.update { if (it.controllerTestOpen) it.copy(controllerTestPad = pad) else it }
        }
    }

    fun closeControllerTest() {
        padLookup?.cancel()
        lookupDeviceId = null
        _state.update { it.copy(controllerTestOpen = false, controllerTestPad = null) }
        buttonCheck.reset()
    }

    private suspend fun readPad(deviceId: Int): ControllerTestPad? = withContext(Dispatchers.IO) {
        val device = InputDevice.getDevice(deviceId)?.takeIf { it.actsAsGamepad() } ?: return@withContext null
        val controllerId = controllerIdOf(device)
        ControllerTestPad(
            deviceId = deviceId,
            name = device.name.orEmpty(),
            vendorId = device.vendorId,
            productId = device.productId,
            controllerId = controllerId,
            seat = inputConfigRepository.getPortForController(controllerId)
        )
    }

    val controllerTestInput: InputHandler = object : InputHandler {
        override fun onBack(): InputResult {
            closeControllerTest()
            return InputResult.handled(SoundType.CLOSE_MODAL)
        }
        override fun onUp(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onDown(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onLeft(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onRight(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onConfirm(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onMenu(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onSecondaryAction(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onContextMenu(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onPrevSection(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onNextSection(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onPrevTrigger(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onNextTrigger(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onSelect(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onLongSelect(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onLeftStickClick(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onRightStickClick(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onRightStickUp(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onRightStickDown(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onRightStickLeft(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onRightStickRight(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onLongConfirm(): InputResult = InputResult.handled(SoundType.SILENT)
    }

    fun openDisplayInfo() {
        _state.update { it.copy(displayInfo = DisplayInfoState(loading = true)) }
        scope.launch {
            val displays = withLiveRoles(displayDiagnostics.read())
            _state.update { state ->
                if (state.displayInfo == null) state else state.copy(displayInfo = DisplayInfoState(displays))
            }
        }
    }

    fun focusDisplay(index: Int) {
        _state.update { state ->
            val info = state.displayInfo ?: return@update state
            state.copy(displayInfo = info.copy(focusIndex = index.coerceIn(0, (info.displays.size - 1).coerceAtLeast(0))))
        }
    }

    fun closeDisplayInfo() {
        _state.update { it.copy(displayInfo = null) }
    }

    private fun moveDisplayFocus(delta: Int): InputResult {
        val info = _state.value.displayInfo ?: return InputResult.HANDLED
        if (info.displays.size < 2) return InputResult.handled(SoundType.BOUNDARY)
        _state.update { state ->
            val current = state.displayInfo ?: return@update state
            state.copy(displayInfo = current.copy(focusIndex = (current.focusIndex + delta).mod(current.displays.size)))
        }
        return InputResult.HANDLED
    }

    val displayInfoInput: InputHandler = object : InputHandler {
        override fun onUp(): InputResult = moveDisplayFocus(-1)
        override fun onDown(): InputResult = moveDisplayFocus(1)
        override fun onBack(): InputResult {
            closeDisplayInfo()
            return InputResult.handled(SoundType.CLOSE_MODAL)
        }
        override fun onConfirm(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onLeft(): InputResult = InputResult.HANDLED
        override fun onRight(): InputResult = InputResult.HANDLED
        override fun onMenu(): InputResult = InputResult.HANDLED
        override fun onSecondaryAction(): InputResult = InputResult.HANDLED
        override fun onContextMenu(): InputResult = InputResult.HANDLED
        override fun onPrevSection(): InputResult = InputResult.HANDLED
        override fun onNextSection(): InputResult = InputResult.HANDLED
        override fun onPrevTrigger(): InputResult = InputResult.HANDLED
        override fun onNextTrigger(): InputResult = InputResult.HANDLED
        override fun onSelect(): InputResult = InputResult.HANDLED
        override fun onLongSelect(): InputResult = InputResult.HANDLED
        override fun onLeftStickClick(): InputResult = InputResult.HANDLED
        override fun onRightStickClick(): InputResult = InputResult.HANDLED
        override fun onLongConfirm(): InputResult = InputResult.HANDLED
    }

    private fun withLiveRoles(displays: List<DisplaySnapshot>): List<DisplaySnapshot> {
        val manager = DualScreenManagerHolder.instance ?: return displays
        return displays.map { it.copy(role = manager.liveRoleOf(it.displayId)) }
    }

    fun exportDiagnostics() {
        if (_state.value.exporting) return
        _state.update { it.copy(exporting = true) }
        scope.launch {
            val result = diagnosticsExporter.export(withLiveRoles(displayDiagnostics.read()))
            _state.update { it.copy(exporting = false) }
            when (result) {
                is DiagnosticsExportResult.Success -> notificationManager.show(
                    title = NotificationText.Res(R.string.about_tools_export_done_title),
                    subtitle = NotificationText.Res(R.string.about_tools_export_done_subtitle, listOf(result.path)),
                    type = NotificationType.SUCCESS,
                    duration = NotificationDuration.LONG
                )
                is DiagnosticsExportResult.Error ->
                    notificationManager.showError(NotificationText.Res(R.string.about_tools_export_failed))
            }
        }
    }

    /**
     * Arms the next setup run to open on its first page. Setup leaves every stored value alone and
     * shows each page in its current state.
     */
    fun prepareSetupRerun() {
        setupHandoff.record(SetupPosition(SetupPage.WELCOME, null))
    }

    fun showUpdatePromptNow() {
        if (_state.value.promptLoading) return
        _state.update { it.copy(promptLoading = true) }
        scope.launch {
            val result = appUpdateManager.showPromptNow()
            _state.update { it.copy(promptLoading = false) }
            when (result) {
                is UpdateState.UpdateAvailable -> Unit
                is UpdateState.Error -> notificationManager.showError(NotificationText.Res(R.string.about_tools_prompt_failed))
                else -> notificationManager.show(
                    title = NotificationText.Res(R.string.about_tools_prompt_none),
                    type = NotificationType.INFO
                )
            }
        }
    }

    fun requestResetNotices() {
        _state.update { it.copy(resetConfirmOpen = true) }
    }

    fun cancelResetNotices() {
        _state.update { it.copy(resetConfirmOpen = false) }
    }

    fun confirmResetNotices() {
        _state.update { it.copy(resetConfirmOpen = false) }
        scope.launch {
            syncPreferences.resetOneTimeNotices()
            appPreferences.resetOneTimeNotices()
            appUpdateManager.clearSkippedTag()
            notificationManager.show(
                title = NotificationText.Res(R.string.about_tools_reset_done_title),
                subtitle = NotificationText.Res(R.string.about_tools_reset_done_subtitle),
                type = NotificationType.SUCCESS,
                duration = NotificationDuration.MEDIUM
            )
        }
    }
}
