package com.nendo.argosy.data.social.uploader

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlaySessionEntity
import com.nendo.argosy.data.model.GameSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class PlaySessionMapperTest {

    private val gameDao: GameDao = mockk()

    private fun gameEntity(id: Long, rommId: Long?): GameEntity = GameEntity(
        id = id,
        title = "Test Game",
        sortTitle = "test game",
        platformId = 1L,
        platformSlug = "snes",
        localPath = null,
        rommId = rommId,
        igdbId = null,
        source = GameSource.LOCAL_ONLY
    )

    private fun session(
        gameId: Long = 1L,
        start: Instant = Instant.parse("2026-05-15T12:00:00Z"),
        end: Instant = Instant.parse("2026-05-15T12:30:00Z"),
        activePlayMs: Long = 1_800_000L
    ) = PlaySessionEntity(
        id = 0,
        userId = "user-1",
        gameId = gameId,
        igdbId = 42L,
        gameTitle = "Test Game",
        platformSlug = "snes",
        startTime = start,
        endTime = end,
        continued = false,
        deviceId = "device-x",
        deviceManufacturer = "Acme",
        deviceModel = "ZX-90",
        activePlayMs = activePlayMs,
        standbyMs = 0
    )

    @Test
    fun `maps active session with rommId`() = runTest {
        coEvery { gameDao.getById(1L) } returns gameEntity(1L, rommId = 99L)

        val entry = PlaySessionMapper.toRomMEntry(session(), gameDao)

        assertEquals(99L, entry?.romId)
        assertEquals("2026-05-15T12:00:00Z", entry?.startTime)
        assertEquals("2026-05-15T12:30:00Z", entry?.endTime)
        assertEquals(1_800_000L, entry?.durationMs)
    }

    @Test
    fun `null rommId is preserved (local-only game)`() = runTest {
        coEvery { gameDao.getById(2L) } returns gameEntity(2L, rommId = null)

        val entry = PlaySessionMapper.toRomMEntry(session(gameId = 2L), gameDao)

        assertNull(entry?.romId)
    }

    @Test
    fun `filters degenerate session where end equals start`() = runTest {
        val t = Instant.parse("2026-05-15T12:00:00Z")
        val degenerate = session(start = t, end = t)

        val entry = PlaySessionMapper.toRomMEntry(degenerate, gameDao)

        assertNull(entry)
    }

    @Test
    fun `filters session where end is before start`() = runTest {
        val degenerate = session(
            start = Instant.parse("2026-05-15T12:30:00Z"),
            end = Instant.parse("2026-05-15T12:00:00Z")
        )

        val entry = PlaySessionMapper.toRomMEntry(degenerate, gameDao)

        assertNull(entry)
    }

    @Test
    fun `unknown game returns entry with null romId`() = runTest {
        coEvery { gameDao.getById(99L) } returns null

        val entry = PlaySessionMapper.toRomMEntry(session(gameId = 99L), gameDao)

        assertNull(entry?.romId)
    }
}
