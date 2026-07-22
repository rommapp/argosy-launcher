package com.nendo.argosy.libretro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RewindBudgetTest {

    private val gib = 1024L * 1024 * 1024

    @Test
    fun `32-bit budget never exceeds the ABI ceiling on a high-memory device`() {
        val budget = RewindBudget.budgetBytes(availableMemoryBytes = 8 * gib, is64Bit = false)
        assertEquals(RewindBudget.BUDGET_32BIT_BYTES, budget)
    }

    @Test
    fun `64-bit budget never exceeds the ABI ceiling on a high-memory device`() {
        val budget = RewindBudget.budgetBytes(availableMemoryBytes = 16 * gib, is64Bit = true)
        assertEquals(RewindBudget.BUDGET_64BIT_BYTES, budget)
    }

    @Test
    fun `available memory wins when it is the tighter constraint`() {
        val budget = RewindBudget.budgetBytes(availableMemoryBytes = 512L * 1024 * 1024, is64Bit = true)
        assertEquals(128L * 1024 * 1024, budget)
    }

    @Test
    fun `budget stays within a 32-bit address space`() {
        val budget = RewindBudget.budgetBytes(availableMemoryBytes = Long.MAX_VALUE, is64Bit = false)
        assertTrue(budget < 3 * gib)
    }

    @Test
    fun `budget is never negative when memory is unavailable`() {
        assertEquals(0L, RewindBudget.budgetBytes(availableMemoryBytes = 0, is64Bit = false))
        assertEquals(0L, RewindBudget.budgetBytes(availableMemoryBytes = -1, is64Bit = true))
    }

    @Test
    fun `32-bit clamps the requested rewind window`() {
        assertEquals(15, RewindBudget.durationSeconds(requestedSeconds = 60, is64Bit = false))
        assertEquals(15, RewindBudget.durationSeconds(requestedSeconds = 30, is64Bit = false))
        assertEquals(5, RewindBudget.durationSeconds(requestedSeconds = 5, is64Bit = false))
    }

    @Test
    fun `64-bit honours the requested rewind window`() {
        assertEquals(60, RewindBudget.durationSeconds(requestedSeconds = 60, is64Bit = true))
        assertEquals(5, RewindBudget.durationSeconds(requestedSeconds = 5, is64Bit = true))
    }

    @Test
    fun `the reported 4GB allocation cannot recur on 32-bit`() {
        val snesStateBytes = 1024L * 1024
        val slotsAt60Fps = RewindBudget.durationSeconds(60, is64Bit = false) * 60
        val budget = RewindBudget.budgetBytes(availableMemoryBytes = 4 * gib, is64Bit = false)
        val slots = minOf(slotsAt60Fps.toLong(), budget / snesStateBytes)

        assertTrue(slots * snesStateBytes <= RewindBudget.BUDGET_32BIT_BYTES)
        assertTrue(slots >= 4)
    }
}
