package com.nendo.argosy.data.local.migrations

import org.junit.Assert.assertEquals
import org.junit.Test

class ChangeStampTriggersTest {

    private val expected = ChangeStampTriggers.expected()

    private fun storedAsSqliteWrites(): Map<String, String> =
        expected.mapValues { (_, statement) -> statement.replace("IF NOT EXISTS ", "") }

    @Test
    fun `covers all six change stamp triggers`() {
        assertEquals(
            listOf(
                "game_art_change_seq_insert", "game_art_change_seq_update", "game_art_change_seq_delete",
                "games_change_seq_insert", "games_change_seq_update", "games_change_seq_delete"
            ),
            expected.keys.toList()
        )
    }

    @Test
    fun `recreates nothing when every stored definition matches`() {
        assertEquals(emptyList<String>(), ChangeStampTriggers.staleTriggers(expected, storedAsSqliteWrites()))
    }

    @Test
    fun `ignores whitespace and IF NOT EXISTS differences`() {
        val stored = expected.mapValues { (_, statement) ->
            statement.replace("IF NOT EXISTS ", "if  not\nexists   ").replace(" BEGIN ", "\n  BEGIN\n")
        }
        assertEquals(emptyList<String>(), ChangeStampTriggers.staleTriggers(expected, stored))
    }

    @Test
    fun `recreates a missing trigger`() {
        val stored = storedAsSqliteWrites() - "games_change_seq_update"
        assertEquals(listOf("games_change_seq_update"), ChangeStampTriggers.staleTriggers(expected, stored))
    }

    @Test
    fun `recreates a trigger whose stored body differs`() {
        val stored = storedAsSqliteWrites() +
            ("game_art_change_seq_delete" to "CREATE TRIGGER `game_art_change_seq_delete` AFTER DELETE ON `game_art` BEGIN SELECT 1; END")
        assertEquals(listOf("game_art_change_seq_delete"), ChangeStampTriggers.staleTriggers(expected, stored))
    }

    @Test
    fun `leaves triggers it does not own alone`() {
        val stored = storedAsSqliteWrites() + ("other_trigger" to "CREATE TRIGGER `other_trigger` AFTER INSERT ON `x` BEGIN SELECT 1; END")
        assertEquals(emptyList<String>(), ChangeStampTriggers.staleTriggers(expected, stored))
    }

    @Test
    fun `recreates all of them on an empty schema`() {
        assertEquals(expected.keys.toList(), ChangeStampTriggers.staleTriggers(expected, emptyMap()))
    }
}
