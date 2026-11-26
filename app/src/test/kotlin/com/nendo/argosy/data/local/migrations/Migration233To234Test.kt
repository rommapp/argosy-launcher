package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Migration233To234Test {

    private fun statements(): List<String> {
        val statements = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>()
        every { db.execSQL(any<String>()) } answers { statements += firstArg<String>() }
        Migration_233_234.migrate(db)
        return statements
    }

    @Test
    fun `adds an indexed change sequence and triggers to game art and games`() {
        assertEquals(
            listOf(
                "ALTER TABLE `game_art` ADD COLUMN `changeSeq` INTEGER NOT NULL DEFAULT 0",
                "CREATE INDEX IF NOT EXISTS `index_game_art_changeSeq` ON `game_art` (`changeSeq`)"
            ) + GameArtChangeTriggers.statements + listOf(
                "ALTER TABLE `games` ADD COLUMN `changeSeq` INTEGER NOT NULL DEFAULT 0",
                "CREATE INDEX IF NOT EXISTS `index_games_changeSeq` ON `games` (`changeSeq`)"
            ) + GameChangeTriggers.statements,
            statements()
        )
    }

    @Test
    fun `games triggers are idempotent, keyed by id and skip their own stamp`() {
        assertEquals(3, GameChangeTriggers.statements.size)
        GameChangeTriggers.statements.forEach {
            assertTrue(it.startsWith("CREATE TRIGGER IF NOT EXISTS"))
            assertTrue("ON `games`" in it)
        }
        assertTrue(GameChangeTriggers.statements.any { "AFTER INSERT" in it && "NEW.`id`" in it })
        assertTrue(
            GameChangeTriggers.statements.any {
                "AFTER UPDATE" in it && "WHEN NEW.`changeSeq` <= OLD.`changeSeq`" in it && "NEW.`id`" in it
            }
        )
    }

    @Test
    fun `a games delete carries the deleted stamp onto the newest remaining row`() {
        val delete = GameChangeTriggers.statements.single { "AFTER DELETE" in it }
        assertTrue("MAX(OLD.`changeSeq`, " in delete)
        assertTrue("ORDER BY `changeSeq` DESC LIMIT 1" in delete)
    }

    @Test
    fun `every trigger is idempotent and the update trigger skips its own stamp`() {
        GameArtChangeTriggers.statements.forEach { assertTrue(it.startsWith("CREATE TRIGGER IF NOT EXISTS")) }
        assertTrue(GameArtChangeTriggers.statements.any { "AFTER UPDATE" in it && "WHEN NEW.`changeSeq` <= OLD.`changeSeq`" in it })
        assertTrue(GameArtChangeTriggers.statements.any { "AFTER DELETE" in it && "OLD.`gameId`" in it })
    }

    @Test
    fun `is registered and leaves no gap`() {
        assertEquals(Migration_233_234, MigrationRegistry.ALL.single { it.startVersion == 233 })
        MigrationRegistry.assertContiguous(MigrationRegistry.ALL.last().endVersion)
    }
}
