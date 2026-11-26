package com.nendo.argosy.ui.components.music

import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.data.music.library.MusicPlaylists
import com.nendo.argosy.domain.model.MusicPlaylistResult
import com.nendo.argosy.domain.model.MusicSourceFilter
import com.nendo.argosy.ui.screens.music.fixtureGroup
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicPlaylistPickerFlowTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val playlists = mockk<MusicPlaylists>()
    private val notifications = mockk<NotificationManager>(relaxed = true)
    private val addition = mockk<MusicPlaylistAddition>()
    private var picker: MusicPlaylistPickerState? = null
    private var finished = 0
    private var permissionPrompts = 0

    private val road = fixtureGroup("Road")
    private val shared = fixtureGroup("Friends", owned = false)
    private val night = fixtureGroup("Night")

    private fun flow(target: MusicPlaylistAddition? = addition) = MusicPlaylistPickerFlow(
        playlists = playlists,
        notifications = notifications,
        scope = scope,
        picker = { picker },
        updatePicker = { transform -> picker = transform(picker) },
        addition = { target },
        onFinished = { finished++ },
        onNeedsPermission = { permissionPrompts++ },
        io = dispatcher
    )

    private fun opened(): MusicPlaylistPickerFlow {
        coEvery { playlists.groups(MusicSourceFilter.ALL) } returns listOf(road, shared, night)
        val flow = flow()
        flow.open()
        scope.advanceUntilIdle()
        return flow
    }

    @Test
    fun `open lists owned playlists after the new playlist row`() {
        opened()
        val state = requireNotNull(picker)
        assertEquals(listOf(road, night), state.groups)
        assertEquals(3, state.rowCount)
        assertTrue(state.isNewPlaylistFocused)
    }

    @Test
    fun `focus wraps across the new playlist row`() {
        val flow = opened()
        flow.move(-1)
        assertEquals(night, picker?.focused)
        flow.move(1)
        assertTrue(picker?.isNewPlaylistFocused == true)
        flow.move(1)
        assertEquals(road, picker?.focused)
    }

    @Test
    fun `a picker with nothing to add lists nothing`() {
        flow(target = null).open()
        scope.advanceUntilIdle()
        assertEquals(0, picker?.rowCount)
        coVerify(exactly = 0) { playlists.groups(any()) }
    }

    @Test
    fun `confirming the new playlist row opens name entry`() {
        val flow = opened()
        flow.confirm()
        assertTrue(picker?.isNaming == true)
        assertFalse(picker?.ownsInput == true)
    }

    @Test
    fun `a taken name offers the existing playlist and adds to it on yes`() {
        val flow = opened()
        coEvery { addition.createNamed(playlists, "Road") } returns MusicPlaylistResult.NameTaken(road)
        coEvery { addition.addTo(playlists, road) } returns MusicPlaylistResult.Done(road)
        flow.confirm()
        flow.submitName("  Road ")
        scope.advanceUntilIdle()
        assertEquals(road, picker?.conflict)
        assertFalse(picker?.isNaming == true)

        flow.confirmConflict()
        scope.advanceUntilIdle()
        coVerify { addition.addTo(playlists, road) }
        assertNull(picker)
        assertEquals(1, finished)
    }

    @Test
    fun `declining the existing playlist returns to the list`() {
        val flow = opened()
        coEvery { addition.createNamed(playlists, "Road") } returns MusicPlaylistResult.NameTaken(road)
        flow.confirm()
        flow.submitName("Road")
        scope.advanceUntilIdle()
        flow.dismissConflict()
        assertNull(picker?.conflict)
        assertTrue(picker?.ownsInput == true)
        coVerify(exactly = 0) { addition.addTo(any(), any()) }
    }

    @Test
    fun `missing RomM permission closes the picker and asks for the permission`() {
        val flow = opened()
        coEvery { addition.addTo(playlists, road) } returns MusicPlaylistResult.NeedsRommPermission
        flow.move(1)
        flow.confirm()
        scope.advanceUntilIdle()
        assertNull(picker)
        assertEquals(1, permissionPrompts)
    }
}
