package com.nendo.argosy.util

import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.ui.screens.settings.PlatformFilterItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformFilterLogicTest {

    private fun entity(
        id: Long, name: String, gameCount: Int,
        syncEnabled: Boolean = true, sortOrder: Int = 0
    ) = PlatformEntity(
        id = id, slug = name.lowercase().replace(" ", "-"), name = name,
        shortName = name.take(3), romExtensions = ".bin",
        gameCount = gameCount, syncEnabled = syncEnabled, sortOrder = sortOrder
    )

    private val platforms = listOf(
        entity(1, "PlayStation 5", 150, sortOrder = 1),
        entity(2, "PlayStation 2", 500, sortOrder = 2),
        entity(3, "Xbox Series X", 120, sortOrder = 3),
        entity(4, "Nintendo Switch", 300, sortOrder = 4),
        entity(5, "Game Boy Advance", 200, sortOrder = 5),
        entity(6, "Sega Genesis", 0, sortOrder = 6),
        entity(7, "Atari 2600", 0, sortOrder = 7)
    )

    @Test
    fun `DEFAULT sort uses sortOrder`() {
        val result = PlatformFilterLogic.filterAndSortPlatformEntities(
            platforms, "", PlatformFilterLogic.FilterMode.ALL, PlatformFilterLogic.SortMode.DEFAULT
        )
        assertEquals(platforms, result)
    }

    @Test
    fun `NAME_ASC and NAME_DESC sort alphabetically`() {
        val asc = PlatformFilterLogic.filterAndSortPlatformEntities(
            platforms, "", PlatformFilterLogic.FilterMode.ALL, PlatformFilterLogic.SortMode.NAME_ASC
        )
        assertEquals("Atari 2600", asc.first().name)
        assertEquals("Xbox Series X", asc.last().name)

        val desc = PlatformFilterLogic.filterAndSortPlatformEntities(
            platforms, "", PlatformFilterLogic.FilterMode.ALL, PlatformFilterLogic.SortMode.NAME_DESC
        )
        assertEquals("Xbox Series X", desc.first().name)
        assertEquals("Atari 2600", desc.last().name)
    }

    @Test
    fun `MOST_GAMES and LEAST_GAMES sort by count with name tiebreaker`() {
        val most = PlatformFilterLogic.filterAndSortPlatformEntities(
            platforms, "", PlatformFilterLogic.FilterMode.ALL, PlatformFilterLogic.SortMode.MOST_GAMES
        )
        assertEquals(listOf(500, 300, 200, 150, 120, 0, 0), most.map { it.gameCount })
        // Tied zeros should be alphabetical
        assertEquals("Atari 2600", most[5].name)
        assertEquals("Sega Genesis", most[6].name)

        val least = PlatformFilterLogic.filterAndSortPlatformEntities(
            platforms, "", PlatformFilterLogic.FilterMode.ALL, PlatformFilterLogic.SortMode.LEAST_GAMES
        )
        assertEquals(listOf(0, 0, 120, 150, 200, 300, 500), least.map { it.gameCount })
    }

    @Test
    fun `search is case-insensitive and matches substrings`() {
        val result = PlatformFilterLogic.filterAndSortPlatformEntities(
            platforms, "playstation", PlatformFilterLogic.FilterMode.ALL, PlatformFilterLogic.SortMode.DEFAULT
        )
        assertEquals(2, result.size)
        assertTrue(result.all { it.name.contains("PlayStation") })
    }

    @Test
    fun `whitespace-only search returns all`() {
        val result = PlatformFilterLogic.filterAndSortPlatformEntities(
            platforms, "   ", PlatformFilterLogic.FilterMode.ALL, PlatformFilterLogic.SortMode.DEFAULT
        )
        assertEquals(platforms.size, result.size)
    }

    @Test
    fun `HAS_GAMES filters out zero-count platforms`() {
        val result = PlatformFilterLogic.filterAndSortPlatformEntities(
            platforms, "", PlatformFilterLogic.FilterMode.HAS_GAMES, PlatformFilterLogic.SortMode.DEFAULT
        )
        assertEquals(5, result.size)
        result.forEach { assertTrue(it.gameCount > 0) }
    }

    @Test
    fun `ENABLED filters by syncEnabled`() {
        val mixed = listOf(
            entity(1, "Enabled", 10, syncEnabled = true),
            entity(2, "Disabled", 20, syncEnabled = false)
        )
        val result = PlatformFilterLogic.filterAndSortPlatformEntities(
            mixed, "", PlatformFilterLogic.FilterMode.ENABLED, PlatformFilterLogic.SortMode.DEFAULT
        )
        assertEquals(1, result.size)
        assertEquals("Enabled", result[0].name)
    }

    @Test
    fun `search combined with filter and sort`() {
        val result = PlatformFilterLogic.filterAndSortPlatformEntities(
            platforms, "play", PlatformFilterLogic.FilterMode.HAS_GAMES, PlatformFilterLogic.SortMode.MOST_GAMES
        )
        assertEquals(listOf("PlayStation 2", "PlayStation 5"), result.map { it.name })
    }

    @Test
    fun `empty list and no matches`() {
        val empty = PlatformFilterLogic.filterAndSortPlatformEntities(
            emptyList(), "test", PlatformFilterLogic.FilterMode.ALL, PlatformFilterLogic.SortMode.NAME_ASC
        )
        assertEquals(0, empty.size)

        val noMatch = PlatformFilterLogic.filterAndSortPlatformEntities(
            platforms, "zzz", PlatformFilterLogic.FilterMode.ALL, PlatformFilterLogic.SortMode.DEFAULT
        )
        assertEquals(0, noMatch.size)
    }

    // PlatformFilterItem uses the same underlying logic — just verify the wiring
    @Test
    fun `filterItems wires romCount and syncEnabled correctly`() {
        val items = listOf(
            PlatformFilterItem(id = 1, name = "PS2", slug = "ps2", romCount = 10, syncEnabled = true),
            PlatformFilterItem(id = 2, name = "GBA", slug = "gba", romCount = 0, syncEnabled = true),
            PlatformFilterItem(id = 3, name = "SNES", slug = "snes", romCount = 5, syncEnabled = false)
        )

        val hasGames = PlatformFilterLogic.filterAndSortPlatformFilterItems(
            items, "", PlatformFilterLogic.FilterMode.HAS_GAMES, PlatformFilterLogic.SortMode.MOST_GAMES
        )
        assertEquals(listOf("PS2", "SNES"), hasGames.map { it.name })

        val enabled = PlatformFilterLogic.filterAndSortPlatformFilterItems(
            items, "", PlatformFilterLogic.FilterMode.ENABLED, PlatformFilterLogic.SortMode.NAME_ASC
        )
        assertEquals(listOf("GBA", "PS2"), enabled.map { it.name })
    }
}
