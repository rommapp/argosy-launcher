package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
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
            ) + GameArtChangeTriggers.dropStatements + GameArtChangeTriggers.statements + listOf(
                "ALTER TABLE `games` ADD COLUMN `changeSeq` INTEGER NOT NULL DEFAULT 0",
                "CREATE INDEX IF NOT EXISTS `index_games_changeSeq` ON `games` (`changeSeq`)"
            ) + GameChangeTriggers.dropStatements + GameChangeTriggers.statements,
            statements()
        )
    }

    @Test
    fun `is registered and leaves no gap`() {
        assertEquals(Migration_233_234, MigrationRegistry.ALL.single { it.startVersion == 233 })
        MigrationRegistry.assertContiguous(MigrationRegistry.ALL.last().endVersion)
    }
}
