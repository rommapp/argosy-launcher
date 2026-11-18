package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class Migration224To225Test {
    @Test
    fun `adds a nullable folder parent to home tiles and indexes it`() {
        val statements = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>()
        every { db.execSQL(any<String>()) } answers { statements += firstArg<String>() }
        Migration_224_225.migrate(db)
        assertEquals(
            listOf(
                "ALTER TABLE `home_tiles` ADD COLUMN `parentTileId` INTEGER",
                "CREATE INDEX IF NOT EXISTS `index_home_tiles_parentTileId` ON `home_tiles` (`parentTileId`)"
            ),
            statements
        )
    }

    @Test
    fun `is registered and leaves no gap`() {
        assertEquals(Migration_224_225, MigrationRegistry.ALL.single { it.startVersion == 224 })
        MigrationRegistry.assertContiguous(MigrationRegistry.ALL.last().endVersion)
    }
}
