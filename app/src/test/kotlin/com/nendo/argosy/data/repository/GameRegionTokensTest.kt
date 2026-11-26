package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.GameRegionChange
import com.nendo.argosy.data.local.dao.GameRegionChanges
import com.nendo.argosy.data.local.dao.GameScopeTally
import com.nendo.argosy.data.local.entity.GameRegionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameRegionTokensTest {

    private val fixture = listOf(
        GameRegionInfo(1, "USA"),
        GameRegionInfo(2, "USA, Europe"),
        GameRegionInfo(3, " Japan ,Europe "),
        GameRegionInfo(4, ", ,"),
        GameRegionInfo(5, "usa"),
        GameRegionInfo(6, "North America,Brazil"),
        GameRegionInfo(7, "World\t"),
        GameRegionInfo(8, "Europe (Beta)"),
        GameRegionInfo(9, "Korea,,Taiwan")
    )

    private val selections = listOf(
        setOf("USA"),
        setOf("Europe"),
        setOf("Japan", "Brazil"),
        setOf("World"),
        setOf("North America"),
        setOf("Taiwan"),
        setOf(""),
        setOf("usa", "Korea"),
        emptySet()
    )

    private fun legacyIdsInRegions(rows: List<GameRegionInfo>, regions: Set<String>): Set<Long> =
        rows.filter { row -> splitRegions(row.regions).any { it in regions } }.mapTo(HashSet()) { it.id }

    @Test
    fun `token rows admit the same games as splitting every row on each read`() {
        val tokens = fixture.map { regionTokens(it.id, it.regions) }
        selections.forEach { selection ->
            assertEquals(selection.toString(), legacyIdsInRegions(fixture, selection), tokens.idsInRegions(selection))
        }
    }

    @Test
    fun `a changed row outside the region scope leaves it`() {
        val changes = GameRegionChanges(
            rows = listOf(GameRegionChange(2, null, inScope = false), GameRegionChange(10, "USA", inScope = true)),
            tally = GameScopeTally(0, 0, 0),
            maxSeq = 1
        )
        val delta = changes.toDelta()
        assertNull(delta.changes.single { it.id == 2L }.rowInScope)
        assertEquals(setOf("USA"), delta.changes.single { it.id == 10L }.rowInScope?.regions)
    }
}
