package com.nendo.argosy.data.local.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.data.local.ALauncherDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "download-checkpoint-migration-test.db"

@RunWith(AndroidJUnit4::class)
class Migration222To223DataTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ALauncherDatabase::class.java
    )

    @Test
    fun legacy_queue_values_survive_with_safe_checkpoint_defaults() {
        helper.createDatabase(TEST_DB, 222).use { db ->
            db.execSQL(
                "INSERT INTO download_queue (id, gameId, rommId, fileName, gameTitle, " +
                    "platformSlug, bytesDownloaded, totalBytes, state, tempFilePath, createdAt, " +
                    "isMultiFileRom, selectedFileIds, ownerUserId) VALUES " +
                    "(1,2,3,'test.zip','Test','test',123,456,'PAUSED','/synthetic/test.part',1000,1,'8,4',99)"
            )
        }
        helper.runMigrationsAndValidate(TEST_DB, 223, true, Migration_222_223).use { db ->
            db.query("SELECT * FROM download_queue WHERE id = 1").use { row ->
                assertTrue(row.moveToFirst())
                assertEquals("/synthetic/test.part", row.getString(row.getColumnIndexOrThrow("tempFilePath")))
                assertEquals(123L, row.getLong(row.getColumnIndexOrThrow("bytesDownloaded")))
                assertEquals(456L, row.getLong(row.getColumnIndexOrThrow("totalBytes")))
                assertEquals("PAUSED", row.getString(row.getColumnIndexOrThrow("state")))
                assertEquals("8,4", row.getString(row.getColumnIndexOrThrow("selectedFileIds")))
                assertEquals(99L, row.getLong(row.getColumnIndexOrThrow("ownerUserId")))
                assertEquals(0, row.getInt(row.getColumnIndexOrThrow("retryAttempt")))
                listOf("resumeValidator", "responseTotalBytes", "validatedBytes", "nextRetryAt", "requestKey", "representationChecksum")
                    .forEach { assertTrue(it, row.isNull(row.getColumnIndexOrThrow(it))) }
            }
            db.execSQL(
                "UPDATE download_queue SET resumeValidator = 'etag', responseTotalBytes = 456, " +
                    "validatedBytes = 123, retryAttempt = 2, nextRetryAt = 5000, requestKey = 'resource' WHERE id = 1"
            )
            db.query("SELECT retryAttempt, nextRetryAt FROM download_queue WHERE id = 1").use { row ->
                assertTrue(row.moveToFirst())
                assertEquals(2, row.getInt(0))
                assertEquals(5000L, row.getLong(1))
            }
        }
    }
}
