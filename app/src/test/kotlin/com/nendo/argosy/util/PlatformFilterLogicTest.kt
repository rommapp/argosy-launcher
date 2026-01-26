package com.nendo.argosy.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformFilterLogicTest {

    data class TestPlatform(
        val name: String,
        val gameCount: Int,
        val sortOrder: Int = 0
    )

    private val testPlatforms = listOf(
        TestPlatform("PlayStation 5", 150, 1),
        TestPlatform("PlayStation 2", 500, 2),
        TestPlatform("PlayStation 10", 50, 3),
        TestPlatform("Xbox Series X", 120, 4),
        TestPlatform("Nintendo Switch", 300, 5),
        TestPlatform("Game Boy Advance", 200, 6),
        TestPlatform("Sega Genesis", 0, 7),
        TestPlatform("Atari 2600", 0, 8)
    )

    @Test
    fun `filterAndSort with DEFAULT sort mode preserves original order`() {
        val result = PlatformFilterLogic.filterAndSort(
            items = testPlatforms,
            searchQuery = "",
            hasGames = false,
            sortMode = PlatformFilterLogic.SortMode.DEFAULT,
            nameSelector = { it.name },
            countSelector = { it.gameCount },
            defaultSortSelector = { it.sortOrder }
        )

        assertEquals(testPlatforms, result)
    }

    @Test
    fun `filterAndSort with NAME_ASC sorts alphabetically case-insensitive`() {
        val result = PlatformFilterLogic.filterAndSort(
            items = testPlatforms,
            searchQuery = "",
            hasGames = false,
            sortMode = PlatformFilterLogic.SortMode.NAME_ASC,
            nameSelector = { it.name },
            countSelector = { it.gameCount }
        )

        val expected = listOf("Atari 2600", "Game Boy Advance", "Nintendo Switch",
            "PlayStation 10", "PlayStation 2", "PlayStation 5", "Sega Genesis", "Xbox Series X")
        assertEquals(expected, result.map { it.name })
    }

    @Test
    fun `filterAndSort with NAME_DESC sorts reverse alphabetically`() {
        val result = PlatformFilterLogic.filterAndSort(
            items = testPlatforms,
            searchQuery = "",
            hasGames = false,
            sortMode = PlatformFilterLogic.SortMode.NAME_DESC,
            nameSelector = { it.name },
            countSelector = { it.gameCount }
        )

        val expected = listOf("Xbox Series X", "Sega Genesis", "PlayStation 5",
            "PlayStation 2", "PlayStation 10", "Nintendo Switch", "Game Boy Advance", "Atari 2600")
        assertEquals(expected, result.map { it.name })
    }

    @Test
    fun `filterAndSort with MOST_GAMES sorts by game count descending`() {
        val result = PlatformFilterLogic.filterAndSort(
            items = testPlatforms,
            searchQuery = "",
            hasGames = false,
            sortMode = PlatformFilterLogic.SortMode.MOST_GAMES,
            nameSelector = { it.name },
            countSelector = { it.gameCount }
        )

        assertEquals(listOf(500, 300, 200, 150, 120, 50, 0, 0), result.map { it.gameCount })
    }

    @Test
    fun `filterAndSort with LEAST_GAMES sorts by game count ascending`() {
        val result = PlatformFilterLogic.filterAndSort(
            items = testPlatforms,
            searchQuery = "",
            hasGames = false,
            sortMode = PlatformFilterLogic.SortMode.LEAST_GAMES,
            nameSelector = { it.name },
            countSelector = { it.gameCount }
        )

        assertEquals(listOf(0, 0, 50, 120, 150, 200, 300, 500), result.map { it.gameCount })
    }

    @Test
    fun `filterAndSort filters by search query case-insensitive`() {
        val result = PlatformFilterLogic.filterAndSort(
            items = testPlatforms,
            searchQuery = "playstation",
            hasGames = false,
            sortMode = PlatformFilterLogic.SortMode.DEFAULT,
            nameSelector = { it.name },
            countSelector = { it.gameCount },
            defaultSortSelector = { it.sortOrder }
        )

        assertEquals(3, result.size)
        assertEquals(listOf("PlayStation 5", "PlayStation 2", "PlayStation 10"), result.map { it.name })
    }

    @Test
    fun `filterAndSort filters by partial search query`() {
        val result = PlatformFilterLogic.filterAndSort(
            items = testPlatforms,
            searchQuery = "boy",
            hasGames = false,
            sortMode = PlatformFilterLogic.SortMode.DEFAULT,
            nameSelector = { it.name },
            countSelector = { it.gameCount },
            defaultSortSelector = { it.sortOrder }
        )

        assertEquals(1, result.size)
        assertEquals("Game Boy Advance", result[0].name)
    }

    @Test
    fun `filterAndSort with hasGames filters out platforms with zero games`() {
        val result = PlatformFilterLogic.filterAndSort(
            items = testPlatforms,
            searchQuery = "",
            hasGames = true,
            sortMode = PlatformFilterLogic.SortMode.DEFAULT,
            nameSelector = { it.name },
            countSelector = { it.gameCount },
            defaultSortSelector = { it.sortOrder }
        )

        assertEquals(6, result.size)
        result.forEach { platform ->
            assert(platform.gameCount > 0) { "${platform.name} should have games" }
        }
    }

    @Test
    fun `filterAndSort combines search and hasGames filters`() {
        val result = PlatformFilterLogic.filterAndSort(
            items = testPlatforms,
            searchQuery = "play",
            hasGames = true,
            sortMode = PlatformFilterLogic.SortMode.DEFAULT,
            nameSelector = { it.name },
            countSelector = { it.gameCount },
            defaultSortSelector = { it.sortOrder }
        )

        assertEquals(3, result.size)
        assertEquals(listOf("PlayStation 5", "PlayStation 2", "PlayStation 10"), result.map { it.name })
    }

    @Test
    fun `filterAndSort combines all filters with sorting`() {
        val result = PlatformFilterLogic.filterAndSort(
            items = testPlatforms,
            searchQuery = "play",
            hasGames = true,
            sortMode = PlatformFilterLogic.SortMode.MOST_GAMES,
            nameSelector = { it.name },
            countSelector = { it.gameCount }
        )

        assertEquals(3, result.size)
        assertEquals(listOf("PlayStation 2", "PlayStation 5", "PlayStation 10"), result.map { it.name })
        assertEquals(listOf(500, 150, 50), result.map { it.gameCount })
    }

    @Test
    fun `filterAndSort handles empty search query`() {
        val result = PlatformFilterLogic.filterAndSort(
            items = testPlatforms,
            searchQuery = "   ",
            hasGames = false,
            sortMode = PlatformFilterLogic.SortMode.DEFAULT,
            nameSelector = { it.name },
            countSelector = { it.gameCount },
            defaultSortSelector = { it.sortOrder }
        )

        assertEquals(testPlatforms.size, result.size)
    }

    @Test
    fun `filterAndSort returns empty list when no matches`() {
        val result = PlatformFilterLogic.filterAndSort(
            items = testPlatforms,
            searchQuery = "nonexistent",
            hasGames = false,
            sortMode = PlatformFilterLogic.SortMode.DEFAULT,
            nameSelector = { it.name },
            countSelector = { it.gameCount }
        )

        assertEquals(0, result.size)
    }

    @Test
    fun `filterAndSort with numeric edge cases (1, 2, 10) sorts correctly`() {
        val platforms = listOf(
            TestPlatform("A", 1),
            TestPlatform("B", 10),
            TestPlatform("C", 2)
        )

        val mostGames = PlatformFilterLogic.filterAndSort(
            items = platforms,
            searchQuery = "",
            hasGames = false,
            sortMode = PlatformFilterLogic.SortMode.MOST_GAMES,
            nameSelector = { it.name },
            countSelector = { it.gameCount }
        )
        // Should be 10 (B), 2 (C), 1 (A)
        assertEquals(listOf(10, 2, 1), mostGames.map { it.gameCount })
        assertEquals(listOf("B", "C", "A"), mostGames.map { it.name })

        val leastGames = PlatformFilterLogic.filterAndSort(
            items = platforms,
            searchQuery = "",
            hasGames = false,
            sortMode = PlatformFilterLogic.SortMode.LEAST_GAMES,
            nameSelector = { it.name },
            countSelector = { it.gameCount }
        )
        // Should be 1 (A), 2 (C), 10 (B)
        assertEquals(listOf(1, 2, 10), leastGames.map { it.gameCount })
        assertEquals(listOf("A", "C", "B"), leastGames.map { it.name })
    }

    @Test
    fun `filterAndSort should use name as secondary sort for same game counts`() {
        val platforms = listOf(
            TestPlatform("B", 10),
            TestPlatform("A", 10),
            TestPlatform("C", 5)
        )

        val result = PlatformFilterLogic.filterAndSort(
            items = platforms,
            searchQuery = "",
            hasGames = false,
            sortMode = PlatformFilterLogic.SortMode.MOST_GAMES,
            nameSelector = { it.name },
            countSelector = { it.gameCount }
        )

        assertEquals(listOf("A", "B", "C"), result.map { it.name })
    }

    @Test
    fun `filterAndSort handles empty input list`() {
        val result = PlatformFilterLogic.filterAndSort(
            items = emptyList<TestPlatform>(),
            searchQuery = "test",
            hasGames = true,
            sortMode = PlatformFilterLogic.SortMode.NAME_ASC,
            nameSelector = { it.name },
            countSelector = { it.gameCount }
        )

        assertEquals(0, result.size)
    }
}
