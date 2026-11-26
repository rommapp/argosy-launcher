package com.nendo.argosy.domain.usecase.collection

import com.nendo.argosy.data.local.entity.GameCategoryInfo
import com.nendo.argosy.data.repository.categoryTokens
import org.junit.Assert.assertEquals
import org.junit.Test

class GameCategoryMatchTest {

    private val acaoLower = "A${Char(0xE7)}${Char(0xE3)}o"
    private val acaoUpper = "A${Char(0xC7)}${Char(0xC3)}O"
    private val noBreakSpace = Char(0xA0)
    private val longS = Char(0x17F)

    private val fixture = listOf(
        GameCategoryInfo(1, "Action", "Single player"),
        GameCategoryInfo(2, "Action, Adventure", "Single player,Multiplayer"),
        GameCategoryInfo(3, " ACTION ", null),
        GameCategoryInfo(4, null, " multiplayer "),
        GameCategoryInfo(5, "$acaoLower,RPG", "Co-op"),
        GameCategoryInfo(6, acaoUpper, "co-OP"),
        GameCategoryInfo(7, ",,", ""),
        GameCategoryInfo(8, "Action-Adventure", "Split screen"),
        GameCategoryInfo(9, "Shooter$noBreakSpace", "Single player\t"),
        GameCategoryInfo(10, "${longS}hooter", "Massively Multiplayer Online (MMO)")
    )

    private val names = listOf(
        "Action", "action", "Adventure", acaoLower, "RPG", "Shooter", "Single player", "Multiplayer",
        "Co-op", "Split screen", "", "Massively Multiplayer Online (MMO)", "Action-Adventure"
    )

    private fun legacyMatches(info: GameCategoryInfo, type: CategoryType, name: String): Boolean {
        val field = when (type) {
            CategoryType.GENRE -> info.genre
            CategoryType.GAME_MODE -> info.gameModes
            CategoryType.SERIES -> null
        }
        return field?.split(",")?.any { it.trim().equals(name, ignoreCase = true) } == true
    }

    @Test
    fun `token matching admits the same games as the per-row split`() {
        val tokens = fixture.associate { it.id to categoryTokens(it.id, it.genre, it.gameModes) }
        CategoryType.entries.forEach { type ->
            names.forEach { name ->
                val legacy = fixture.filter { legacyMatches(it, type, name) }.map { it.id }
                val indexed = fixture.filter { tokens.getValue(it.id).matches(type, name) }.map { it.id }
                assertEquals("$type/$name", legacy, indexed)
            }
        }
    }
}
