package com.nendo.argosy.ui.screens.settings.delegates

import com.nendo.argosy.data.preferences.SyncFilterPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.ui.screens.settings.SyncSettingsState
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SyncSettingsDelegateRegionPriorityTest {

    private val known = SyncFilterPreferences.ALL_KNOWN_REGIONS
    private val preferences: UserPreferencesRepository = mockk(relaxed = true)

    private fun delegate(): SyncSettingsDelegate = SyncSettingsDelegate(
        application = mockk(relaxed = true),
        preferencesRepository = preferences,
        saveSyncRepository = mockk(relaxed = true),
        saveCacheRepository = mockk(relaxed = true),
        stateCacheManager = mockk(relaxed = true),
        syncCoordinator = mockk(relaxed = true),
        platformRepository = mockk(relaxed = true),
        rommRepository = mockk(relaxed = true),
        imageCacheManager = mockk(relaxed = true),
        notificationManager = mockk(relaxed = true),
        attributionRepository = mockk(relaxed = true),
        retroAchievementsRepository = mockk(relaxed = true),
        saveSyncGate = mockk(relaxed = true)
    ).apply {
        updateState(SyncSettingsState(regionPriority = known))
        showRegionPriority()
    }

    @Test
    fun `lift move and drop persists the new order`() = runTest {
        val delegate = delegate()
        delegate.moveRegionPriorityFocus(2)

        delegate.liftRegionPriority()
        delegate.moveRegionPriorityFocus(-2)
        delegate.dropRegionPriority(this)
        advanceUntilIdle()

        val expected = listOf(known[2], known[0], known[1]) + known.drop(3)
        assertEquals(expected, delegate.state.value.regionPriority)
        assertEquals(0, delegate.state.value.regionPriorityFocusIndex)
        assertNull(delegate.state.value.regionPriorityHeld)
        coVerify(exactly = 1) { preferences.setRegionPriority(expected) }
    }

    @Test
    fun `moving a held region clamps at the ends of the list`() = runTest {
        val delegate = delegate()

        delegate.liftRegionPriority()
        delegate.moveRegionPriorityFocus(-1)

        assertEquals(known, delegate.state.value.regionPriority)
        assertEquals(0, delegate.state.value.regionPriorityFocusIndex)
    }

    @Test
    fun `focus wraps when nothing is held`() = runTest {
        val delegate = delegate()

        delegate.moveRegionPriorityFocus(-1)

        assertEquals(known.lastIndex, delegate.state.value.regionPriorityFocusIndex)
    }

    @Test
    fun `cancel restores the order and writes nothing`() = runTest {
        val delegate = delegate()
        delegate.liftRegionPriority()
        delegate.moveRegionPriorityFocus(3)

        delegate.cancelRegionPriorityHold()
        advanceUntilIdle()

        assertEquals(known, delegate.state.value.regionPriority)
        assertEquals(0, delegate.state.value.regionPriorityFocusIndex)
        assertFalse(delegate.isHoldingRegionPriority())
        coVerify(exactly = 0) { preferences.setRegionPriority(any()) }
    }

    @Test
    fun `dismissing while held restores the order`() = runTest {
        val delegate = delegate()
        delegate.liftRegionPriority()
        delegate.moveRegionPriorityFocus(4)

        delegate.dismissRegionPriority()

        assertEquals(known, delegate.state.value.regionPriority)
        assertFalse(delegate.state.value.showRegionPriority)
    }

    @Test
    fun `a touch drag moves the lifted region to the target slot`() = runTest {
        val delegate = delegate()
        val region = known[5]

        delegate.liftRegionPriorityAt(region)
        delegate.moveRegionPriorityTo(region, 1)
        delegate.dropRegionPriority(this)
        advanceUntilIdle()

        val expected = listOf(known[0], region) + known.filterNot { it == known[0] || it == region }
        assertEquals(expected, delegate.state.value.regionPriority)
        coVerify(exactly = 1) { preferences.setRegionPriority(expected) }
    }

    @Test
    fun `dropping without a move writes nothing`() = runTest {
        val delegate = delegate()

        delegate.liftRegionPriority()
        delegate.dropRegionPriority(this)
        advanceUntilIdle()

        coVerify(exactly = 0) { preferences.setRegionPriority(any()) }
    }

    @Test
    fun `toggling a filter region leaves the priority untouched`() = runTest {
        val delegate = delegate()

        delegate.toggleRegion(this, "Japan")
        delegate.toggleRegionMode(this)
        advanceUntilIdle()

        assertEquals(known, delegate.state.value.regionPriority)
        coVerify(exactly = 0) { preferences.setRegionPriority(any()) }
    }
}
