package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class Migration225To226Test {
    @Test
    fun `adds a prerelease opt-in to managed installers that starts off`() {
        val statements = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>()
        every { db.execSQL(any<String>()) } answers { statements += firstArg<String>() }
        Migration_225_226.migrate(db)
        assertEquals(
            listOf(
                "ALTER TABLE `managed_installers` ADD COLUMN `includePrereleases` INTEGER NOT NULL DEFAULT 0"
            ),
            statements
        )
    }

    @Test
    fun `is registered and leaves no gap`() {
        assertEquals(Migration_225_226, MigrationRegistry.ALL.single { it.startVersion == 225 })
        MigrationRegistry.assertContiguous(MigrationRegistry.ALL.last().endVersion)
    }
}
