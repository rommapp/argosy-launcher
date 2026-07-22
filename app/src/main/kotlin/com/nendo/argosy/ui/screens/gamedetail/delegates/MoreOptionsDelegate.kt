package com.nendo.argosy.ui.screens.gamedetail.delegates

import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.ui.input.InputDispatcher.Companion.computeWrappedIndex
import com.nendo.argosy.ui.screens.gamedetail.GameDownloadStatus
import com.nendo.argosy.ui.screens.gamedetail.MoreOptionAction
import com.nendo.argosy.ui.screens.gamedetail.MoreOptionsContext
import com.nendo.argosy.ui.screens.gamedetail.buildMoreOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class MoreOptionsState(
    val showMoreOptions: Boolean = false,
    val moreOptionsFocusIndex: Int = 0
)

class MoreOptionsDelegate @Inject constructor(
    private val soundManager: SoundFeedbackManager
) {
    private val _state = MutableStateFlow(MoreOptionsState())
    val state: StateFlow<MoreOptionsState> = _state.asStateFlow()

    var menuWrapMode: MenuWrapMode = MenuWrapMode.HARD_STOP

    fun reset() {
        _state.value = MoreOptionsState()
    }

    fun toggleMoreOptions() {
        val wasShowing = _state.value.showMoreOptions
        _state.update {
            it.copy(
                showMoreOptions = !it.showMoreOptions,
                moreOptionsFocusIndex = 0
            )
        }
        if (!wasShowing) {
            soundManager.play(SoundType.OPEN_MODAL)
        } else {
            soundManager.play(SoundType.CLOSE_MODAL)
        }
    }

    fun moveOptionsFocus(delta: Int, context: MoreOptionsContext) {
        val maxIndex = buildMoreOptions(context).lastIndex
        _state.update { state ->
            val newIndex = computeWrappedIndex(state.moreOptionsFocusIndex, delta, maxIndex, menuWrapMode)
            state.copy(moreOptionsFocusIndex = newIndex)
        }
    }

    fun resolveOptionAction(context: MoreOptionsContext): MoreOptionAction? =
        buildMoreOptions(context).getOrNull(_state.value.moreOptionsFocusIndex)

}
