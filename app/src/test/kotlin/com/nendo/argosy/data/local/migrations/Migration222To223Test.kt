package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class Migration222To223Test {
    @Test
    fun `adds nullable checkpoints and zero retry default without rewriting legacy rows`() {
        val statements = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>()
        every { db.execSQL(any<String>()) } answers { statements += firstArg<String>() }
        Migration_222_223.migrate(db)
        assertEquals(
            listOf(
                "ALTER TABLE `download_queue` ADD COLUMN `resumeValidator` TEXT",
                "ALTER TABLE `download_queue` ADD COLUMN `responseTotalBytes` INTEGER",
                "ALTER TABLE `download_queue` ADD COLUMN `validatedBytes` INTEGER",
                "ALTER TABLE `download_queue` ADD COLUMN `retryAttempt` INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE `download_queue` ADD COLUMN `nextRetryAt` INTEGER",
                "ALTER TABLE `download_queue` ADD COLUMN `requestKey` TEXT",
                "ALTER TABLE `download_queue` ADD COLUMN `representationChecksum` TEXT"
            ),
            statements
        )
    }

    @Test
    fun `is registered and leaves no gap`() {
        assertEquals(Migration_222_223, MigrationRegistry.ALL.single { it.startVersion == 222 })
        MigrationRegistry.assertContiguous(MigrationRegistry.ALL.last().endVersion)
    }
}
