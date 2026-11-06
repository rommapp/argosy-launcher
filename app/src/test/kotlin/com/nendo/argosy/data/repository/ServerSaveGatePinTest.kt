package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ServerSaveGatePinTest {

    private val gameId = 32831L
    private val romPath = "/roms/psx/Tony Hawk's Pro Skater 2 (USA).chd"
    private val romChannel = "Tony Hawk's Pro Skater 2 (USA)"
    private val oldServerTime = Instant.parse("2026-03-05T05:23:04Z")

    private val saveSyncDao: SaveSyncDao = mockk()
    private val activeSaves: ActiveSaveRepository = mockk()
    private val prefs: SyncPreferencesRepository = mockk()
    private val gate = ServerSaveGate(activeSaves, saveSyncDao, prefs)

    private fun row(channel: String, pinned: Boolean) = SaveSyncEntity(
        gameId = gameId,
        rommId = 8615,
        emulatorId = "argosy",
        channelName = channel,
        syncStatus = SaveSyncEntity.STATUS_SERVER_NEWER,
        userSelectedRestorePoint = pinned
    )

    private fun given(rows: List<SaveSyncEntity>, activeAt: Instant? = null) {
        coEvery { prefs.getRommUserId() } returns 1L
        coEvery { saveSyncDao.getByGame(gameId, 1L) } returns rows
        coEvery { activeSaves.getActiveRow(gameId) } returns activeAt?.let {
            SaveCacheEntity(id = 7L, gameId = gameId, emulatorId = "argosy", cachedAt = it, saveSize = 1, cachePath = "x", channelName = "autosave")
        }
    }

    @Test
    fun `a restore point in autosave holds the ROM-named slot that shares its bucket`() = runBlocking {
        given(listOf(row("autosave", pinned = true), row(romChannel, pinned = false)))

        val hold = gate.holdFor(gameId, romPath, romChannel, oldServerTime)

        assertEquals(ServerSaveHold.PinnedRestorePoint("autosave"), hold)
    }

    @Test
    fun `a restore point holds even when the server time is unknown and no save is active`() = runBlocking {
        given(listOf(row("autosave", pinned = true)))

        assertEquals(ServerSaveHold.PinnedRestorePoint("autosave"), gate.holdFor(gameId, romPath, "autosave", null))
    }

    @Test
    fun `a restore point in another slot does not hold this one`() = runBlocking {
        given(listOf(row("Boss rush", pinned = true)))

        assertNull(gate.holdFor(gameId, romPath, "autosave", Instant.parse("2026-11-01T00:00:00Z")))
    }

    @Test
    fun `without a restore point a newer server save is let through`() = runBlocking {
        given(listOf(row("autosave", pinned = false)), activeAt = Instant.parse("2026-10-27T01:42:39Z"))

        assertNull(gate.holdFor(gameId, romPath, "autosave", Instant.parse("2026-11-01T00:00:00Z")))
    }
}
