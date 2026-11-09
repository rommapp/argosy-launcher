package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Migration221To222Test {

    private fun statements(): List<String> {
        val captured = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { captured += firstArg<String>() }
        Migration_221_222.migrate(db)
        return captured
    }

    @Test
    fun `is the last registered migration and leaves no gap`() {
        assertEquals(Migration_221_222, MigrationRegistry.ALL.last())
        MigrationRegistry.assertContiguous(222)
    }

    @Test
    fun `the rebuilt table keys rows by source so gallery and official rows share positions`() {
        val create = statements().single { it.startsWith("CREATE TABLE IF NOT EXISTS `game_screenshots_new`") }

        assertTrue("PRIMARY KEY(`gameId`, `source`, `position`)" in create)
        assertTrue("`source` TEXT NOT NULL DEFAULT 'official'" in create)
        assertTrue("`rommScreenshotId` INTEGER" in create)
        assertTrue("`ownerUsername` TEXT" in create)
        assertTrue("ON DELETE CASCADE" in create)
    }

    @Test
    fun `every existing row is carried as official before the old table is dropped`() {
        val sql = statements()
        val copy = sql.indexOfFirst { it.startsWith("INSERT INTO `game_screenshots_new`") }
        val drop = sql.indexOf("DROP TABLE `game_screenshots`")
        val rename = sql.indexOf("ALTER TABLE `game_screenshots_new` RENAME TO `game_screenshots`")
        val index = sql.indexOf(
            "CREATE INDEX IF NOT EXISTS `index_game_screenshots_gameId` ON `game_screenshots` (`gameId`)"
        )

        assertTrue("'official' FROM `game_screenshots`" in sql[copy])
        assertTrue("`cachedPath`, `cachedFromUrl`" in sql[copy])
        assertTrue(copy in 0 until drop)
        assertTrue(drop < rename)
        assertTrue(rename < index)
    }
}
