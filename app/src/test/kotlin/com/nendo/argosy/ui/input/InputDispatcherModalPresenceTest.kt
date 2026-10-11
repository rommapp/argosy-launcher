package com.nendo.argosy.ui.input

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputDispatcherModalPresenceTest {
    @Test
    fun touchModalPresenceInvalidatesOverlayVisibilityAndRetainsOtherModals() {
        val dispatcher = InputDispatcher()
        val first = Any()
        val second = Any()
        assertObservableChange(dispatcher) { dispatcher.markModalShown(first) }
        dispatcher.markModalShown(second)
        dispatcher.markModalHidden(first)
        assertTrue(dispatcher.hasCapturingOverlay())
        assertObservableChange(dispatcher) { dispatcher.markModalHidden(second) }
        assertFalse(dispatcher.hasCapturingOverlay())
    }

    @Test
    fun inputStackAndCriticalHandlerAlsoInvalidateOverlayVisibility() {
        val dispatcher = InputDispatcher()
        val handler = object : InputHandler {}
        assertObservableChange(dispatcher) { dispatcher.pushModal(handler) }
        assertObservableChange(dispatcher) { dispatcher.removeModal(handler) }
        assertObservableChange(dispatcher) { dispatcher.setCriticalHandler(handler) }
        assertObservableChange(dispatcher) { dispatcher.setCriticalHandler(null) }
        assertFalse(dispatcher.hasCapturingOverlay())
    }

    private fun assertObservableChange(dispatcher: InputDispatcher, change: () -> Unit) {
        var invalidated = false
        val observer = SnapshotStateObserver { it() }
        observer.start()
        try {
            observer.observeReads(Any(), { _: Any -> invalidated = true }) {
                dispatcher.hasCapturingOverlay()
            }
            change()
            Snapshot.sendApplyNotifications()
            assertTrue("Modal visibility must invalidate its Compose observer", invalidated)
        } finally {
            observer.stop()
            observer.clear()
        }
    }
}
