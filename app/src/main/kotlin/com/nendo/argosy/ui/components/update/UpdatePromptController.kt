package com.nendo.argosy.ui.components.update

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.model.AppUpdateDownload
import com.nendo.argosy.data.model.AppUpdateRelease
import com.nendo.argosy.data.update.AppUpdateManager
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sign

enum class UpdatePromptAction { INSTALL, SKIP, LATER }

/**
 * The open update prompt. [bodyScrolls] counts note scroll requests and [bodyScrollDirection] is
 * the latest one's direction. [downloadProgress] is set while the update downloads.
 */
data class UpdatePromptState(
    val release: AppUpdateRelease,
    val focusIndex: Int = UpdatePromptAction.INSTALL.ordinal,
    val bodyScrolls: Int = 0,
    val bodyScrollDirection: Int = 0,
    val downloadProgress: Int? = null,
    val failed: Boolean = false
) {
    val focusedAction: UpdatePromptAction get() = UpdatePromptAction.entries[focusIndex]
}

/**
 * The automatic update prompt on the control surface: when it opens, the button focus, the notes
 * scroll and what each button does.
 */
@Singleton
class UpdatePromptController @Inject constructor(
    private val manager: AppUpdateManager
) {
    private val scope = SafeCoroutineScope(Dispatchers.Main.immediate, TAG)

    val pending: StateFlow<AppUpdateRelease?> = manager.pendingPrompt

    private val _state = MutableStateFlow<UpdatePromptState?>(null)
    val state: StateFlow<UpdatePromptState?> = _state.asStateFlow()

    init {
        scope.launch {
            manager.download.collect { download -> applyDownload(download) }
        }
    }

    /**
     * Opens the prompt for [release] when the control surface is [idle] and nothing is open, and
     * closes it when no release is pending any more.
     */
    fun onPending(release: AppUpdateRelease?, idle: Boolean) {
        val open = _state.value
        if (release == null) {
            if (open != null && open.downloadProgress == null) close()
        } else if (open == null && idle) {
            _state.value = UpdatePromptState(release)
        }
    }

    fun close() {
        _state.value = null
    }

    fun focus(action: UpdatePromptAction) {
        _state.update { it?.copy(focusIndex = action.ordinal) }
    }

    fun activate(action: UpdatePromptAction): InputResult {
        val open = _state.value ?: return InputResult.HANDLED
        return when (action) {
            UpdatePromptAction.INSTALL -> {
                if (open.downloadProgress != null) return InputResult.handled(SoundType.SILENT)
                _state.update { it?.copy(failed = false) }
                manager.install(open.release)
                InputResult.HANDLED
            }
            UpdatePromptAction.SKIP -> {
                manager.skip(open.release.tag)
                close()
                InputResult.handled(SoundType.CLOSE_MODAL)
            }
            UpdatePromptAction.LATER -> {
                manager.later(open.release.tag)
                close()
                InputResult.handled(SoundType.CLOSE_MODAL)
            }
        }
    }

    fun scrollNotes(direction: Int): InputResult {
        if (direction == 0) return InputResult.HANDLED
        _state.update { it?.copy(bodyScrolls = it.bodyScrolls + 1, bodyScrollDirection = direction.sign) }
        return InputResult.handled(SoundType.SILENT)
    }

    private fun moveFocus(delta: Int): InputResult {
        if (_state.value == null) return InputResult.HANDLED
        val count = UpdatePromptAction.entries.size
        _state.update { it?.copy(focusIndex = (it.focusIndex + delta).mod(count)) }
        return InputResult.HANDLED
    }

    private fun applyDownload(download: AppUpdateDownload) {
        val open = _state.value ?: return
        when (download) {
            is AppUpdateDownload.Downloading -> if (download.tag == open.release.tag) {
                _state.update { it?.copy(downloadProgress = download.progress, failed = false) }
            }
            is AppUpdateDownload.HandedOff -> if (download.tag == open.release.tag) close()
            is AppUpdateDownload.Failed -> if (download.tag == open.release.tag) {
                _state.update { it?.copy(downloadProgress = null, failed = true) }
            }
            AppUpdateDownload.Idle -> Unit
        }
    }

    val inputHandler: InputHandler = object : InputHandler {
        override fun onLeft(): InputResult = moveFocus(-1)
        override fun onRight(): InputResult = moveFocus(1)
        override fun onUp(): InputResult = scrollNotes(-1)
        override fun onDown(): InputResult = scrollNotes(1)
        override fun onRightStickUp(): InputResult = scrollNotes(-1)
        override fun onRightStickDown(): InputResult = scrollNotes(1)
        override fun onConfirm(): InputResult {
            val open = _state.value ?: return InputResult.HANDLED
            return activate(open.focusedAction)
        }
        override fun onBack(): InputResult = activate(UpdatePromptAction.LATER)
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
        override fun onRightStickLeft(): InputResult = InputResult.HANDLED
        override fun onRightStickRight(): InputResult = InputResult.HANDLED
        override fun onLongConfirm(): InputResult = InputResult.HANDLED
    }

    private companion object {
        const val TAG = "UpdatePrompt"
    }
}
