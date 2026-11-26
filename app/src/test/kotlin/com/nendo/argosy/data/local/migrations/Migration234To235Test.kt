package com.nendo.argosy.data.local.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Migration234To235Test {

    private class Run(val statements: List<String>, val bindArgs: List<List<Any?>>)

    private fun run(activeAccount: Pair<Long, String>?): Run {
        val statements = mutableListOf<String>()
        val bindArgs = mutableListOf<List<Any?>>()
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToFirst() } returns (activeAccount != null)
        activeAccount?.let { (userId, baseUrl) ->
            every { cursor.getLong(0) } returns userId
            every { cursor.getString(1) } returns baseUrl
        }
        val db = mockk<SupportSQLiteDatabase>()
        every { db.query(any<String>()) } returns cursor
        every { db.execSQL(any<String>()) } answers { statements += firstArg<String>() }
        every { db.execSQL(any<String>(), any()) } answers {
            statements += firstArg<String>()
            bindArgs += secondArg<Array<out Any?>>().toList()
        }
        Migration_234_235.migrate(db)
        return Run(statements, bindArgs)
    }

    @Test
    fun `rebuilds music downloads keyed by server and track with an owner`() {
        val statements = run(7L to "https://RomM.example/").statements
        val create = statements.first()
        assertTrue(create.startsWith("CREATE TABLE IF NOT EXISTS `music_downloads_new`"))
        assertTrue(create.contains("`serverKey` TEXT NOT NULL, `ownerKey` INTEGER NOT NULL"))
        assertTrue(create.endsWith("PRIMARY KEY(`serverKey`, `trackKey`))"))
        assertEquals(
            listOf(
                "DROP TABLE `music_downloads`",
                "ALTER TABLE `music_downloads_new` RENAME TO `music_downloads`",
                "CREATE INDEX IF NOT EXISTS `index_music_downloads_source_albumId` " +
                    "ON `music_downloads` (`source`, `albumId`)",
                "CREATE INDEX IF NOT EXISTS `index_music_downloads_ownerKey` ON `music_downloads` (`ownerKey`)"
            ),
            statements.drop(2)
        )
    }

    @Test
    fun `backfills romm rows to the active account and its server`() {
        val result = run(7L to "https://RomM.example/")
        val copy = result.statements[1]
        assertTrue(copy.startsWith("INSERT INTO `music_downloads_new`"))
        assertTrue(copy.contains("CASE WHEN `source` = 'romm' THEN ? ELSE '' END, ?"))
        assertEquals(listOf(listOf<Any?>("romm:romm.example", 7L)), result.bindArgs)
    }

    @Test
    fun `leaves rows unscoped and unowned without an active account`() {
        assertEquals(listOf(listOf<Any?>("", 0L)), run(null).bindArgs)
    }

    @Test
    fun `is registered and leaves no gap`() {
        assertEquals(Migration_234_235, MigrationRegistry.ALL.single { it.startVersion == 234 })
        MigrationRegistry.assertContiguous(MigrationRegistry.ALL.last().endVersion)
    }
}
