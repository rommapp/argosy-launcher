package com.nendo.argosy.data.speedrun

import com.nendo.argosy.data.local.dao.SpeedrunDao
import com.nendo.argosy.data.local.entity.SpeedrunAttemptEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.json.JSONArray

/**
 * Offsets were only ever drawn against a completed personal best, so a runner who had never
 * finished the category saw no deltas at all while still seeing gold segments, which are derived
 * from every attempt.
 */
class SpeedrunComparisonTest {

    private val dao = mockk<SpeedrunDao>()
    private val repository = SpeedrunRepository(dao)

    private fun attempt(
        completed: Boolean,
        finalTimeMs: Long?,
        splits: List<Long?>
    ): SpeedrunAttemptEntity {
        val json = JSONArray().apply {
            splits.forEach { put(it ?: org.json.JSONObject.NULL) }
        }.toString()
        return SpeedrunAttemptEntity(
            categoryId = 1L,
            startedAt = 0L,
            completed = completed,
            finalTimeMs = finalTimeMs,
            splitTimesJson = json
        )
    }

    @Test
    fun `personal best splits are the comparison once a run has been completed`() = runTest {
        coEvery { dao.getAttemptsForCategory(1L) } returns listOf(
            attempt(completed = true, finalTimeMs = 300L, splits = listOf(100L, 200L, 300L)),
            attempt(completed = true, finalTimeMs = 360L, splits = listOf(110L, 240L, 360L))
        )

        val comparison = repository.getComparison(1L, 3)

        assertEquals(listOf(100L, 200L, 300L), comparison.pbSplitTimesMs)
        assertEquals(listOf(100L, 200L, 300L), comparison.comparisonSplitTimesMs)
        assertEquals(300L, comparison.pbTimeMs)
    }

    @Test
    fun `unfinished attempts still yield a comparison built from best segments`() = runTest {
        coEvery { dao.getAttemptsForCategory(1L) } returns listOf(
            attempt(completed = false, finalTimeMs = null, splits = listOf(100L, 250L, null)),
            attempt(completed = false, finalTimeMs = null, splits = listOf(120L, 220L, null))
        )

        val comparison = repository.getComparison(1L, 3)

        assertEquals(listOf(null, null, null), comparison.pbSplitTimesMs)
        assertNull(comparison.pbTimeMs)
        assertEquals(listOf(100L, 200L, null), comparison.comparisonSplitTimesMs)
    }

    @Test
    fun `the comparison stops at the first segment nobody has run`() = runTest {
        coEvery { dao.getAttemptsForCategory(1L) } returns listOf(
            attempt(completed = false, finalTimeMs = null, splits = listOf(100L, null, 400L))
        )

        val comparison = repository.getComparison(1L, 3)

        assertEquals(listOf(100L, null, null), comparison.comparisonSplitTimesMs)
    }

    @Test
    fun `no attempts leaves every comparison entry empty`() = runTest {
        coEvery { dao.getAttemptsForCategory(1L) } returns emptyList()

        val comparison = repository.getComparison(1L, 3)

        assertEquals(listOf(null, null, null), comparison.comparisonSplitTimesMs)
        assertEquals(0, comparison.attemptCount)
    }
}
