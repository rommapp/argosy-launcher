package com.nendo.argosy.ui.dualscreen

import com.nendo.argosy.domain.model.CompletionStatus
import com.nendo.argosy.hardware.SecondaryHomeBroadcastHelper
import com.nendo.argosy.ui.dualscreen.gamedetail.ActiveModal
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailUpperState
import com.nendo.argosy.ui.input.GamepadEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class ShowcaseViewModel(
    private val detailState: MutableStateFlow<DualGameDetailUpperState?>,
    private val broadcasts: SecondaryHomeBroadcastHelper,
    private val isControlActive: () -> Boolean
) {

    fun onModalRatingSelect(value: Int) {
        val modal = detailState.value?.modalType ?: return
        detailState.update {
            when (modal) {
                ActiveModal.RATING -> it?.copy(
                    modalType = ActiveModal.NONE,
                    rating = value.takeIf { v -> v > 0 }
                )
                ActiveModal.DIFFICULTY -> it?.copy(
                    modalType = ActiveModal.NONE,
                    userDifficulty = value
                )
                else -> it?.copy(modalType = ActiveModal.NONE)
            }
        }
        if (isControlActive()) {
            broadcasts.broadcastModalConfirmResult(modal, value, null)
        }
    }

    fun onModalStatusSelect(value: String) {
        detailState.update {
            it?.copy(modalType = ActiveModal.NONE, status = value)
        }
        if (isControlActive()) {
            broadcasts.broadcastModalConfirmResult(ActiveModal.STATUS, 0, value)
        }
    }

    fun onModalEmulatorSelect(index: Int) {
        val state = detailState.value ?: return
        detailState.update {
            it?.copy(
                modalType = ActiveModal.NONE,
                emulatorCurrentName = if (index == 0) null
                else state.emulatorNames.getOrNull(index - 1)
            )
        }
        if (isControlActive()) {
            broadcasts.broadcastInlineUpdate("emulator_confirm", index)
        }
    }

    fun onModalCollectionToggle(collectionId: Long) {
        detailState.update { s ->
            s?.copy(
                collectionItems = s.collectionItems.map {
                    if (it.id == collectionId)
                        it.copy(isInCollection = !it.isInCollection)
                    else it
                }
            )
        }
    }

    fun onModalCollectionShowCreate() {
        detailState.update { it?.copy(showCreateDialog = true) }
    }

    fun onModalCollectionCreate(name: String) {
        detailState.update { it?.copy(showCreateDialog = false) }
    }

    fun onModalCollectionCreateDismiss() {
        detailState.update { it?.copy(showCreateDialog = false) }
    }

    fun onSaveNameTextChange(text: String) {
        detailState.update { it?.copy(saveNameText = text) }
    }

    fun onSaveNameConfirm() {
        detailState.update { it?.copy(modalType = ActiveModal.NONE) }
    }

    fun onModalDismiss() {
        detailState.update { it?.copy(modalType = ActiveModal.NONE) }
        if (isControlActive()) broadcasts.broadcastModalClose()
    }

    fun adjustModalRating(delta: Int) {
        detailState.update { state ->
            state?.copy(
                modalRatingValue = (state.modalRatingValue + delta).coerceIn(0, 10)
            )
        }
    }

    fun moveModalStatus(delta: Int) {
        detailState.update { state ->
            if (state == null) return@update null
            val entries = CompletionStatus.entries
            val current = CompletionStatus.fromApiValue(
                state.modalStatusSelected
            ) ?: entries.first()
            val next = entries[(current.ordinal + delta).mod(entries.size)]
            state.copy(modalStatusSelected = next.apiValue)
        }
    }

    fun moveEmulatorFocus(delta: Int) {
        detailState.update { state ->
            val max = state?.emulatorNames?.size ?: 0
            state?.copy(
                emulatorFocusIndex = (state.emulatorFocusIndex + delta).coerceIn(0, max)
            )
        }
    }

    fun moveCollectionFocus(delta: Int) {
        detailState.update { state ->
            val max = state?.collectionItems?.size ?: 0
            state?.copy(
                collectionFocusIndex = (state.collectionFocusIndex + delta).coerceIn(0, max)
            )
        }
    }

    fun isModalActive(): Boolean {
        val modal = detailState.value?.modalType
        return modal != null && modal != ActiveModal.NONE
    }

    fun handleModalGamepadEvent(event: GamepadEvent): Boolean {
        val state = detailState.value ?: return false
        val modal = state.modalType
        if (modal == ActiveModal.NONE) return false
        when (event) {
            is GamepadEvent.Left -> {
                if (modal == ActiveModal.RATING || modal == ActiveModal.DIFFICULTY)
                    adjustModalRating(-1)
            }
            is GamepadEvent.Right -> {
                if (modal == ActiveModal.RATING || modal == ActiveModal.DIFFICULTY)
                    adjustModalRating(1)
            }
            is GamepadEvent.Up -> {
                if (state.showCreateDialog) return true
                when (modal) {
                    ActiveModal.STATUS -> moveModalStatus(-1)
                    ActiveModal.EMULATOR -> moveEmulatorFocus(-1)
                    ActiveModal.COLLECTION -> moveCollectionFocus(-1)
                    else -> {}
                }
            }
            is GamepadEvent.Down -> {
                if (state.showCreateDialog) return true
                when (modal) {
                    ActiveModal.STATUS -> moveModalStatus(1)
                    ActiveModal.EMULATOR -> moveEmulatorFocus(1)
                    ActiveModal.COLLECTION -> moveCollectionFocus(1)
                    else -> {}
                }
            }
            is GamepadEvent.Confirm -> {
                if (state.showCreateDialog) return true
                when (modal) {
                    ActiveModal.RATING, ActiveModal.DIFFICULTY ->
                        onModalRatingSelect(state.modalRatingValue)
                    ActiveModal.STATUS ->
                        onModalStatusSelect(state.modalStatusSelected ?: return true)
                    ActiveModal.EMULATOR ->
                        onModalEmulatorSelect(state.emulatorFocusIndex)
                    ActiveModal.COLLECTION -> {
                        val idx = state.collectionFocusIndex
                        if (idx < state.collectionItems.size) {
                            onModalCollectionToggle(state.collectionItems[idx].id)
                        } else {
                            onModalCollectionShowCreate()
                        }
                    }
                    ActiveModal.SAVE_NAME -> onSaveNameConfirm()
                    else -> {}
                }
            }
            is GamepadEvent.Back -> {
                if (state.showCreateDialog) {
                    onModalCollectionCreateDismiss()
                    return true
                }
                onModalDismiss()
            }
            else -> {}
        }
        return true
    }
}
