package com.nendo.argosy.ui.screens.gamedetail.delegates

import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showError
import com.nendo.argosy.ui.notification.showSuccess
import com.nendo.argosy.ui.screens.gamedetail.RatingType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class RatingsStatusState(
    val showRatingPicker: Boolean = false,
    val ratingPickerType: RatingType = RatingType.OPINION,
    val ratingPickerValue: Int = 0,
    val showStatusPicker: Boolean = false,
    val statusPickerValue: String? = null,
    val showRatingsStatusMenu: Boolean = false,
    val ratingsStatusFocusIndex: Int = 0
)

@Singleton
class RatingsStatusDelegate @Inject constructor(
    private val romMRepository: RomMRepository,
    private val notificationManager: NotificationManager,
    private val soundManager: SoundFeedbackManager
) {
    private val _state = MutableStateFlow(RatingsStatusState())
    val state: StateFlow<RatingsStatusState> = _state.asStateFlow()

    fun reset() {
        _state.value = RatingsStatusState()
    }

    fun showRatingPicker(type: RatingType, currentValue: Int) {
        _state.update {
            it.copy(
                showRatingsStatusMenu = false,
                showRatingPicker = true,
                ratingPickerType = type,
                ratingPickerValue = currentValue
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissRatingPicker() {
        _state.update { it.copy(showRatingPicker = false, showRatingsStatusMenu = true) }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun showRatingsStatusMenu() {
        _state.update {
            it.copy(
                showRatingsStatusMenu = true,
                ratingsStatusFocusIndex = 0
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissRatingsStatusMenu() {
        _state.update {
            it.copy(showRatingsStatusMenu = false)
        }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun changeRatingsStatusFocus(delta: Int) {
        _state.update { state ->
            val newIndex = (state.ratingsStatusFocusIndex + delta).coerceIn(0, 2)
            state.copy(ratingsStatusFocusIndex = newIndex)
        }
    }

    fun confirmRatingsStatusSelection() {
        when (_state.value.ratingsStatusFocusIndex) {
            0 -> {} // caller should pass current game values
            1 -> {}
            2 -> {}
        }
    }

    fun getRatingsStatusAction(): Int = _state.value.ratingsStatusFocusIndex

    fun changeRatingValue(delta: Int) {
        _state.update { state ->
            val newValue = (state.ratingPickerValue + delta).coerceIn(0, 10)
            state.copy(ratingPickerValue = newValue)
        }
    }

    fun setRatingValue(value: Int) {
        _state.update { it.copy(ratingPickerValue = value.coerceIn(0, 10)) }
    }

    fun confirmRating(scope: CoroutineScope, gameId: Long, onSuccess: () -> Unit) {
        val state = _state.value
        val value = state.ratingPickerValue
        val type = state.ratingPickerType

        scope.launch {
            val result = when (type) {
                RatingType.OPINION -> romMRepository.updateUserRating(gameId, value)
                RatingType.DIFFICULTY -> romMRepository.updateUserDifficulty(gameId, value)
            }

            when (result) {
                is com.nendo.argosy.data.remote.romm.RomMResult.Success -> {
                    val label = if (type == RatingType.OPINION) "Rating" else "Difficulty"
                    notificationManager.showSuccess("$label saved")
                    onSuccess()
                }
                is com.nendo.argosy.data.remote.romm.RomMResult.Error -> {
                    notificationManager.showError(result.message)
                }
            }
            _state.update { it.copy(showRatingPicker = false, showRatingsStatusMenu = true) }
        }
    }

    fun showStatusPicker(currentStatus: String?) {
        _state.update {
            it.copy(
                showRatingsStatusMenu = false,
                showStatusPicker = true,
                statusPickerValue = currentStatus
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissStatusPicker() {
        _state.update { it.copy(showStatusPicker = false, showRatingsStatusMenu = true) }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun changeStatusValue(delta: Int) {
        _state.update { state ->
            val newValue = if (delta > 0) {
                com.nendo.argosy.domain.model.CompletionStatus.cycleNext(state.statusPickerValue)
            } else {
                com.nendo.argosy.domain.model.CompletionStatus.cyclePrev(state.statusPickerValue)
            }
            state.copy(statusPickerValue = newValue)
        }
    }

    fun selectStatus(value: String) {
        _state.update { it.copy(statusPickerValue = value) }
    }

    fun confirmStatus(scope: CoroutineScope, gameId: Long, onSuccess: () -> Unit) {
        val value = _state.value.statusPickerValue

        scope.launch {
            val result = romMRepository.updateUserStatus(gameId, value)

            when (result) {
                is com.nendo.argosy.data.remote.romm.RomMResult.Success -> {
                    notificationManager.showSuccess("Status saved")
                    onSuccess()
                }
                is com.nendo.argosy.data.remote.romm.RomMResult.Error -> {
                    notificationManager.showError(result.message)
                }
            }
            _state.update { it.copy(showStatusPicker = false, showRatingsStatusMenu = true) }
        }
    }
}
