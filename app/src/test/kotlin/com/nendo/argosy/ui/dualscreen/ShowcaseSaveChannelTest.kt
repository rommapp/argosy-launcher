package com.nendo.argosy.ui.dualscreen

import com.nendo.argosy.hardware.SecondaryHomeBroadcastHelper
import com.nendo.argosy.ui.dualscreen.gamedetail.ActiveModal
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailUpperState
import com.nendo.argosy.ui.input.GamepadEvent
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test

class ShowcaseSaveChannelTest {
    @Test
    fun `delete confirmation honors cancel focus on the swapped display`() {
        val broadcasts = mockk<SecondaryHomeBroadcastHelper>(relaxed = true)
        val state = MutableStateFlow<DualGameDetailUpperState?>(
            DualGameDetailUpperState(modalType = ActiveModal.SAVE_DELETE)
        )
        val viewModel = ShowcaseViewModel(state, broadcasts) { true }

        viewModel.handleModalGamepadEvent(GamepadEvent.Confirm)
        verify(exactly = 1) { broadcasts.confirmSaveDelete(false) }
        verify(exactly = 0) { broadcasts.confirmSaveDelete(true) }

        state.value = state.value?.copy(saveDeleteFocusIndex = 1)
        viewModel.handleModalGamepadEvent(GamepadEvent.Confirm)
        verify(exactly = 1) { broadcasts.confirmSaveDelete(true) }
    }

    @Test
    fun `rename on the swapped display forwards the edited name and confirmation`() {
        val broadcasts = mockk<SecondaryHomeBroadcastHelper>(relaxed = true)
        val state = MutableStateFlow<DualGameDetailUpperState?>(
            DualGameDetailUpperState(modalType = ActiveModal.SAVE_NAME)
        )
        val viewModel = ShowcaseViewModel(state, broadcasts) { true }

        viewModel.onSaveNameTextChange("Renamed slot")
        viewModel.handleModalGamepadEvent(GamepadEvent.Confirm)

        verify(exactly = 1) { broadcasts.updateSaveName("Renamed slot") }
        verify(exactly = 1) { broadcasts.confirmSaveName() }
    }
}
