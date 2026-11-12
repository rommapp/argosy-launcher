package com.nendo.argosy.data.streaming

import com.nendo.argosy.data.remote.romm.RomMStreamSave
import com.nendo.argosy.data.remote.romm.RomMStreamSlotCapabilities
import com.nendo.argosy.data.remote.romm.RomMStreamState
import com.nendo.argosy.data.remote.romm.RomMStreamStatePicks
import com.nendo.argosy.data.remote.romm.RomMStreamingContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamPickerTest {

    private val duckstation = RomMStreamingContainer(
        platform = "psx",
        emulator = "duckstation",
        supportsSavePicker = true,
        capabilities = RomMStreamSlotCapabilities(maxSlots = 0, hasAutosave = true, autosaveSlot = 10)
    )
    private val retroarch = RomMStreamingContainer(
        platform = "snes",
        emulator = "retroarch",
        supportsSavePicker = true,
        importKinds = listOf("save")
    )

    private fun save(
        id: Long,
        fileName: String,
        emulator: String?,
        updatedAt: String,
        missing: Boolean = false
    ) = RomMStreamSave(id = id, fileName = fileName, emulator = emulator, updatedAt = updatedAt, missingFromFs = missing)

    private val olderNative = save(1, "old.zip", "duckstation", "2026-11-01T10:00:00+00:00")
    private val newerNative = save(2, "new.zip", "DuckStation", "2026-11-10T10:00:00+00:00")
    private val foreignSrm = save(3, "game.srm", "retroarch", "2026-11-12T10:00:00+00:00")
    private val nativeNotZip = save(4, "card.mcd", "duckstation", "2026-11-13T10:00:00+00:00")
    private val saves = listOf(olderNative, newerNative, foreignSrm, nativeNotZip)

    @Test
    fun `saves sort newest write first, id breaking ties`() {
        val tied = save(9, "tie.zip", "duckstation", "2026-11-10T10:00:00+00:00")
        assertEquals(listOf(9L, 2L, 1L), StreamPicker.allSaves(listOf(olderNative, newerNative, tied)).map { it.id })
    }

    @Test
    fun `a container that imports no saves offers only its own emulator's archives`() {
        assertEquals(listOf(2L, 1L), StreamPicker.pickableSaves(saves, duckstation).map { it.id })
    }

    @Test
    fun `a container that imports saves offers every save`() {
        assertEquals(listOf(4L, 3L, 2L, 1L), StreamPicker.pickableSaves(saves, retroarch).map { it.id })
    }

    @Test
    fun `the server default is the newest native archive whose file exists`() {
        val gone = newerNative.copy(missingFromFs = true)
        assertEquals(2L, StreamPicker.serverDefaultSave(saves, duckstation)?.id)
        assertEquals(1L, StreamPicker.serverDefaultSave(listOf(olderNative, gone), duckstation)?.id)
    }

    @Test
    fun `a RetroArch container that imports saves restores a newer bare srm`() {
        val raZip = save(5, "ra.zip", "retroarch", "2026-11-05T10:00:00+00:00")
        val raSrm = save(6, "ra.srm", "retroarch", "2026-11-06T10:00:00+00:00")
        assertEquals(6L, StreamPicker.serverDefaultSave(listOf(raZip, raSrm), retroarch)?.id)
        val olderSrm = raSrm.copy(updatedAt = "2026-11-04T10:00:00+00:00")
        assertEquals(5L, StreamPicker.serverDefaultSave(listOf(raZip, olderSrm), retroarch)?.id)
    }

    @Test
    fun `pre-5_5 servers preselect the claim's own default`() {
        assertEquals(2L, StreamPicker.defaultSave(saves, duckstation, activeChannelHeadSaveId = null)?.id)
    }

    @Test
    fun `5_5 servers preselect the active channel's head`() {
        assertEquals(1L, StreamPicker.defaultSave(saves, duckstation, activeChannelHeadSaveId = 1L)?.id)
    }

    @Test
    fun `a channel head the container cannot restore falls back to the claim's default`() {
        assertEquals(2L, StreamPicker.defaultSave(saves, duckstation, activeChannelHeadSaveId = 3L)?.id)
        assertEquals(2L, StreamPicker.defaultSave(saves, duckstation, activeChannelHeadSaveId = 404L)?.id)
    }

    @Test
    fun `the save picker shows only where the container has one and something can be picked`() {
        assertTrue(StreamPicker.showsSavePicker(saves, duckstation))
        assertFalse(StreamPicker.showsSavePicker(saves, duckstation.copy(supportsSavePicker = false)))
        assertFalse(StreamPicker.showsSavePicker(listOf(foreignSrm), duckstation))
    }

    @Test
    fun `save_id is never sent to a container without a save picker`() {
        val noPicker = duckstation.copy(supportsSavePicker = false)
        assertNull(StreamPicker.claimSaveId(saves, noPicker, olderNative))
        assertNull(StreamPicker.claimSaveId(saves, noPicker, newerNative))
    }

    @Test
    fun `save_id is sent for a pick other than the claim's default`() {
        assertEquals(1L, StreamPicker.claimSaveId(saves, duckstation, olderNative))
    }

    @Test
    fun `save_id is left off when the pick is the claim's default`() {
        assertNull(StreamPicker.claimSaveId(saves, duckstation, newerNative))
        assertNull(StreamPicker.claimSaveId(saves, duckstation, null))
    }

    @Test
    fun `save_id is never sent for a save the container cannot restore`() {
        assertNull(StreamPicker.claimSaveId(saves, duckstation, foreignSrm))
    }

    private fun state(id: Long) = RomMStreamState(id = id, fileName = "s$id.state")

    @Test
    fun `states come from the server's picks in the order RomM lists them`() {
        val states = listOf(state(30), state(20), state(10))
        val picks = RomMStreamStatePicks(native = listOf(10, 20), pickable = listOf(10, 20, 30))
        assertEquals(listOf(30L, 20L, 10L), StreamPicker.pickableStates(states, picks).map { it.id })
        assertEquals(listOf(20L, 10L), StreamPicker.nativeStates(states, picks).map { it.id })
    }

    @Test
    fun `the default state is the newest one the container loads natively`() {
        val states = listOf(state(30), state(20), state(10))
        val picks = RomMStreamStatePicks(native = listOf(10, 20), pickable = listOf(10, 20, 30))
        assertEquals(20L, StreamPicker.defaultState(states, picks)?.id)
    }

    @Test
    fun `no picks offers no state and boots from the save`() {
        val states = listOf(state(30))
        assertTrue(StreamPicker.pickableStates(states, null).isEmpty())
        assertNull(StreamPicker.defaultState(states, null))
        assertNull(StreamPicker.defaultState(states, RomMStreamStatePicks(native = emptyList(), pickable = listOf(30))))
    }

    @Test
    fun `states are offered only where the container has slots or an autosave`() {
        assertTrue(StreamPicker.supportsStates(duckstation))
        assertFalse(StreamPicker.supportsStates(retroarch))
    }
}
