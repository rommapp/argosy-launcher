package com.nendo.argosy.ui.common.savechannel

import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncApiClient
import com.nendo.argosy.domain.model.UnifiedSaveEntry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class SaveChannelSavesDelegateTest {

    private lateinit var holder: SaveChannelStateHolder
    private lateinit var saveCacheManager: SaveCacheManager
    private lateinit var delegate: SaveChannelSavesDelegate

    @Before
    fun setup() {
        holder = SaveChannelStateHolder()
        saveCacheManager = mockk(relaxed = true)
        delegate = SaveChannelSavesDelegate(
            holder = holder,
            getUnifiedSavesUseCase = mockk(relaxed = true),
            restoreCachedSaveUseCase = mockk(relaxed = true),
            restoreCachedStatesUseCase = mockk(relaxed = true),
            saveCacheManager = saveCacheManager,
            saveSyncRepository = mockk(relaxed = true),
            stateCacheManager = mockk(relaxed = true),
            gameRepository = mockk(relaxed = true),
            notificationManager = mockk(relaxed = true),
            soundManager = mockk(relaxed = true),
            titleIdDownloadObserver = mockk(relaxed = true),
            syncCoordinator = mockk(relaxed = true)
        )
    }

    private fun entry(
        channelName: String?,
        userCreated: Boolean = false,
        archival: Boolean = false,
        ms: Long = 1L
    ) = UnifiedSaveEntry(
        timestamp = Instant.ofEpochMilli(ms),
        size = 100L,
        channelName = channelName,
        source = UnifiedSaveEntry.Source.LOCAL,
        isUserCreatedSlot = userCreated,
        isArchival = archival
    )

    @Test
    fun `channel colliding with autosave does not produce duplicate slot keys`() {
        val entries = listOf(entry(channelName = "Autosave", userCreated = true))

        val slots = delegate.buildSaveSlots(entries, activeChannel = "Autosave")

        val keys = slots.map { it.slotKey }
        assertEquals("slot keys must be unique", keys.size, keys.toSet().size)
        val names = slots.map { it.displayName }
        assertEquals("display names must be unique", names.size, names.toSet().size)
        assertEquals(1, slots.count { it.displayName == "Autosave" })

        val autosave = slots.first { it.displayName == "Autosave" }
        assertEquals(SaveSyncApiClient.AUTOSAVE_SLOT_NAME, autosave.channelName)
        assertEquals(1, autosave.saveCount)
    }

    @Test
    fun `slots sharing a display name still have distinct keys`() {
        val canonical = SaveSlotItem(
            channelName = SaveSyncApiClient.AUTOSAVE_SLOT_NAME,
            displayName = "Autosave",
            isActive = false,
            saveCount = 1,
            latestTimestamp = null
        )
        val colliding = SaveSlotItem(
            channelName = "Autosave",
            displayName = "Autosave",
            isActive = true,
            saveCount = 0,
            latestTimestamp = null
        )

        assertEquals(canonical.displayName, colliding.displayName)
        assertTrue(
            "slotKey must disambiguate slots with identical display names",
            canonical.slotKey != colliding.slotKey
        )
    }

    @Test
    fun `active channel with no saves still gets its own slot`() {
        val slots = delegate.buildSaveSlots(emptyList(), activeChannel = "flip2")

        assertTrue(slots.any { it.channelName == "flip2" && it.isActive })
        val keys = slots.map { it.slotKey }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `confirmRename rejects a reserved autosave name`() = runTest {
        holder.state.value = holder.state.value.copy(
            renameMode = RenameMode.NEW_SLOT,
            renameText = "Autosave"
        )

        delegate.confirmRename(this)
        advanceUntilIdle()

        coVerify(exactly = 0) { saveCacheManager.channelExists(any(), any()) }
    }

    @Test
    fun `confirmRename allows a normal slot name`() = runTest {
        coEvery { saveCacheManager.channelExists(any(), any()) } returns false
        holder.state.value = holder.state.value.copy(
            renameMode = RenameMode.NEW_SLOT,
            renameText = "flip3"
        )

        delegate.confirmRename(this)
        advanceUntilIdle()

        coVerify(exactly = 1) { saveCacheManager.channelExists(0L, "flip3") }
    }
}
