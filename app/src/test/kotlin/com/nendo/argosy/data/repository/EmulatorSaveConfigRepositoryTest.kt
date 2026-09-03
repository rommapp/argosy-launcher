package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao
import com.nendo.argosy.data.local.entity.EmulatorSaveConfigEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SHARED = "/storage/emulated/0/PSP/SAVEDATA"
private const val CHOSEN = "/sd/psp-saves"

/**
 * The evaluated default lives in the same row as the user's choice, told apart by the two flags.
 * These pin down that the flags never blur: a user choice is never overwritten by evaluation,
 * and clearing one kind of value never throws away the other.
 */
class EmulatorSaveConfigRepositoryTest {

    private val dao: EmulatorSaveConfigDao = mockk(relaxed = true)
    private val repo = EmulatorSaveConfigRepository(dao)

    private fun evaluatedRow(path: String = SHARED) = EmulatorSaveConfigEntity(
        emulatorId = "ppsspp", savePathPattern = path, isAutoDetected = true, isUserOverride = false
    )

    private fun userRow(path: String = CHOSEN) = EmulatorSaveConfigEntity(
        emulatorId = "ppsspp", savePathPattern = path, isAutoDetected = false, isUserOverride = true
    )

    private fun upserted(): EmulatorSaveConfigEntity {
        val captured = slot<EmulatorSaveConfigEntity>()
        coVerify { dao.upsert(capture(captured)) }
        return captured.captured
    }

    @Test
    fun `an evaluated row answers only when it is not a user choice`() = runTest {
        coEvery { dao.getByEmulator("ppsspp") } returns evaluatedRow()
        assertEquals(SHARED, repo.resolveEvaluatedSavePath("ppsspp"))

        coEvery { dao.getByEmulator("ppsspp") } returns userRow()
        assertNull(repo.resolveEvaluatedSavePath("ppsspp"))

        coEvery { dao.getByEmulator("ppsspp") } returns evaluatedRow(path = "")
        assertNull(repo.resolveEvaluatedSavePath("ppsspp"))
    }

    @Test
    fun `the effective path is the user choice first and the evaluated folder second`() = runTest {
        coEvery { dao.getByEmulator("ppsspp") } returns userRow()
        assertEquals(CHOSEN, repo.resolveEffectiveSavePath("ppsspp", "psp"))

        coEvery { dao.getByEmulator("ppsspp") } returns evaluatedRow()
        assertEquals(SHARED, repo.resolveEffectiveSavePath("ppsspp", "psp"))

        coEvery { dao.getByEmulator("ppsspp") } returns null
        coEvery { dao.getAll() } returns emptyList()
        assertNull(repo.resolveEffectiveSavePath("ppsspp", "psp"))
    }

    @Test
    fun `evaluation never overwrites a user choice`() = runTest {
        coEvery { dao.getByEmulator("ppsspp") } returns userRow()

        repo.setEvaluatedSavePath("ppsspp", SHARED)

        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test
    fun `evaluation writes an auto-detected row that keeps the other columns`() = runTest {
        coEvery { dao.getByEmulator("ppsspp") } returns EmulatorSaveConfigEntity(
            emulatorId = "ppsspp", savePathPattern = "", isAutoDetected = true,
            statePathPattern = "/sd/states", isUserStateOverride = true, selectedMemcardPath = "/sd/card"
        )

        repo.setEvaluatedSavePath("ppsspp", SHARED)

        val row = upserted()
        assertEquals(SHARED, row.savePathPattern)
        assertTrue(row.isAutoDetected)
        assertFalse(row.isUserOverride)
        assertEquals("/sd/states", row.statePathPattern)
        assertTrue(row.isUserStateOverride)
        assertEquals("/sd/card", row.selectedMemcardPath)
    }

    @Test
    fun `a user choice replaces the evaluated folder in place`() = runTest {
        coEvery { dao.getByEmulator("ppsspp") } returns evaluatedRow()

        repo.setSavePath("ppsspp", CHOSEN)

        val row = upserted()
        assertEquals(CHOSEN, row.savePathPattern)
        assertTrue(row.isUserOverride)
        assertFalse(row.isAutoDetected)
    }

    @Test
    fun `reset clears the evaluated folder and deletes the row when nothing else is in it`() = runTest {
        coEvery { dao.getByEmulator("ppsspp") } returns evaluatedRow()

        repo.resetSavePath("ppsspp")

        coVerify { dao.delete("ppsspp") }
    }

    @Test
    fun `reset keeps a state override alongside a cleared save folder`() = runTest {
        coEvery { dao.getByEmulator("ppsspp") } returns evaluatedRow().copy(
            statePathPattern = "/sd/states", isUserStateOverride = true
        )

        repo.resetSavePath("ppsspp")

        val row = upserted()
        assertEquals("", row.savePathPattern)
        assertFalse(row.isUserOverride)
        assertEquals("/sd/states", row.statePathPattern)
    }

    @Test
    fun `clearing the state override keeps the evaluated folder`() = runTest {
        coEvery { dao.getByEmulator("ppsspp") } returns evaluatedRow().copy(
            statePathPattern = "/sd/states", isUserStateOverride = true
        )

        repo.resetStatePath("ppsspp")

        coVerify(exactly = 0) { dao.delete(any()) }
        val row = upserted()
        assertEquals(SHARED, row.savePathPattern)
        assertNull(row.statePathPattern)
    }

    @Test
    fun `clearing the memory card keeps the evaluated folder`() = runTest {
        coEvery { dao.getByEmulator("ppsspp") } returns evaluatedRow().copy(selectedMemcardPath = "/sd/card")

        repo.clearMemcardPath("ppsspp")

        coVerify(exactly = 0) { dao.delete(any()) }
        val row = upserted()
        assertEquals(SHARED, row.savePathPattern)
        assertNull(row.selectedMemcardPath)
    }
}
