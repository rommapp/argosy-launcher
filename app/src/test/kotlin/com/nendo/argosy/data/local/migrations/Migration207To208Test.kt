package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Migration207To208Test {

    private fun statements(): List<String> {
        val captured = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { captured += firstArg<String>() }
        Migration_207_208.migrate(db)
        return captured
    }

    @Test
    fun `is registered`() {
        assertTrue(Migration_207_208 in MigrationRegistry.ALL)
    }

    @Test
    fun `spine and back each copy their own column into their own slot`() {
        val copies = statements().filter { it.startsWith("INSERT OR IGNORE INTO `game_art`") }

        assertEquals(2, copies.size)
        assertTrue(copies.single { "'BOX_SPINE'" in it }.let { "`boxSpinePath`" in it && "`boxBackPath`" !in it })
        assertTrue(copies.single { "'BOX_BACK'" in it }.let { "`boxBackPath`" in it && "`boxSpinePath`" !in it })
    }

    @Test
    fun `a local path becomes the cached path and anything else the source url`() {
        val spine = statements().single { "'BOX_SPINE'" in it }

        assertTrue("CASE WHEN substr(NULLIF(`boxSpinePath`, ''), 1, 1) = '/' THEN NULL ELSE NULLIF(`boxSpinePath`, '') END" in spine)
        assertTrue("CASE WHEN substr(NULLIF(`boxSpinePath`, ''), 1, 1) = '/' THEN NULLIF(`boxSpinePath`, '') ELSE NULL END" in spine)
    }

    @Test
    fun `the old columns are emptied only after both copies`() {
        val sql = statements()
        val lastCopy = sql.indexOfLast { it.startsWith("INSERT OR IGNORE INTO `game_art`") }
        val clear = sql.indexOfFirst { it.startsWith("UPDATE `games` SET `boxSpinePath` = NULL") }

        assertTrue(clear > lastCopy)
    }

    @Test
    fun `only unoverridden covers of games with a 3d box source are forgotten`() {
        val reset = statements().single { it.startsWith("UPDATE `game_art`") }

        assertTrue("`slot` = 'COVER'" in reset)
        assertTrue("`overridePath` IS NULL" in reset)
        assertTrue("`slot` = 'BOX_3D' AND `sourceUrl` IS NOT NULL" in reset)
        assertTrue("`cachedPath` = NULL, `cachedFromUrl` = NULL" in reset)
    }
}
