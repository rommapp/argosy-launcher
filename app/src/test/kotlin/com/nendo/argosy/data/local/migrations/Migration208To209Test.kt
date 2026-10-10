package com.nendo.argosy.data.local.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Migration208To209Test {

    private data class Statement(val sql: String, val args: List<Any?> = emptyList())

    private fun statements(): List<Statement> {
        val captured = mutableListOf<Statement>()
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToNext() } returnsMany listOf(true, true, false)
        every { cursor.getLong(0) } returnsMany listOf(1L, 2L)
        every { cursor.getString(1) } returnsMany listOf("https://a, ,https://b", "https://x")
        every { cursor.isNull(2) } returnsMany listOf(false, true)
        every { cursor.getString(2) } returns "/cache/ss_1_0_a.jpg,,/cache/ss_1_1_b.jpg,/cache/ss_1_2_c.jpg"
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.query(any<String>()) } returns cursor
        every { db.execSQL(any<String>()) } answers { captured += Statement(firstArg()) }
        every { db.execSQL(any<String>(), any()) } answers {
            captured += Statement(firstArg(), secondArg<Array<out Any?>>().toList())
        }
        Migration_208_209.migrate(db)
        return captured
    }

    @Test
    fun `is the last registered migration and leaves no gap`() {
        assertEquals(Migration_208_209, MigrationRegistry.ALL.last())
        MigrationRegistry.assertContiguous(209)
    }

    @Test
    fun `each url becomes a row at its list position with the cached path of the same position`() {
        val rows = statements().filter { it.sql.startsWith("INSERT OR IGNORE INTO `game_screenshots`") }

        assertEquals(
            listOf(
                listOf(1L, 0, "https://a", "/cache/ss_1_0_a.jpg"),
                listOf(1L, 1, "https://b", "/cache/ss_1_1_b.jpg"),
                listOf(2L, 0, "https://x", null)
            ),
            rows.map { it.args }
        )
        assertTrue(rows.all { "VALUES (?, ?, ?, ?, NULL)" in it.sql })
    }

    @Test
    fun `blank entries are skipped and a cached path past the last url is dropped`() {
        assertEquals(
            listOf("u1" to "/p1", "u2" to "/p2"),
            legacyScreenshotRows("u1,,u2", "/p1, ,/p2,/p3")
        )
        assertEquals(listOf("u1" to null), legacyScreenshotRows("u1", null))
        assertTrue(legacyScreenshotRows("", "/p1").isEmpty())
        assertTrue(legacyScreenshotRows(null, null).isEmpty())
    }

    @Test
    fun `screenshots are copied before the old table is dropped`() {
        val sql = statements().map { it.sql }
        val create = sql.indexOfFirst { it.startsWith("CREATE TABLE IF NOT EXISTS `game_screenshots`") }
        val lastCopy = sql.indexOfLast { it.startsWith("INSERT OR IGNORE INTO `game_screenshots`") }
        val carry = sql.indexOfFirst { it.startsWith("INSERT INTO `games_new`") }
        val drop = sql.indexOf("DROP TABLE `games`")

        assertTrue(create in 0 until lastCopy)
        assertTrue(lastCopy < carry)
        assertTrue(carry < drop)
        assertTrue(drop < sql.indexOf("ALTER TABLE `games_new` RENAME TO `games`"))
    }

    @Test
    fun `the rebuilt games table has none of the image columns`() {
        val sql = statements().map { it.sql }
        val create = sql.single { it.startsWith("CREATE TABLE IF NOT EXISTS `games_new`") }
        val carry = sql.single { it.startsWith("INSERT INTO `games_new`") }

        listOf("screenshotPaths", "cachedScreenshotPaths", "boxBackPath", "boxSpinePath").forEach { column ->
            assertFalse(column, "`$column`" in create)
            assertFalse(column, "`$column`" in carry)
        }
        assertTrue("`isGroupVisible` INTEGER NOT NULL DEFAULT 1" in create)
    }

    @Test
    fun `all thirteen games indices are recreated after the rename`() {
        val sql = statements().map { it.sql }
        val rename = sql.indexOf("ALTER TABLE `games_new` RENAME TO `games`")
        val indices = sql.withIndex()
            .filter { (_, statement) -> statement.contains("INDEX IF NOT EXISTS `index_games_") }

        assertEquals(
            setOf(
                "platformId", "title", "lastPlayed", "source", "rommId", "steamAppId", "packageName",
                "regions", "gameModes", "franchises", "genres", "collections", "siblingGroupKey"
            ),
            indices.map { (_, statement) -> statement.substringAfter("`index_games_").substringBefore("`") }.toSet()
        )
        assertEquals(13, indices.size)
        assertTrue(indices.all { (position, _) -> position > rename })
        assertEquals(
            setOf("rommId", "steamAppId", "packageName"),
            indices.map { it.value }
                .filter { it.startsWith("CREATE UNIQUE INDEX") }
                .map { it.substringAfter("`index_games_").substringBefore("`") }
                .toSet()
        )
    }

    @Test
    fun `foreign keys are off for the rebuild and back on at the end`() {
        val sql = statements().map { it.sql }

        assertEquals("PRAGMA foreign_keys=OFF", sql.first())
        assertEquals("PRAGMA foreign_keys=ON", sql.last())
    }
}
