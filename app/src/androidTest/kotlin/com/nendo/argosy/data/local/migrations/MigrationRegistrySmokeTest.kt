package com.nendo.argosy.data.local.migrations

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.data.local.ALauncherDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "alauncher-migration-test.db"
private const val FIRST_VALIDATED_VERSION = 6
private const val CURRENT_VERSION = 128

@RunWith(AndroidJUnit4::class)
class MigrationRegistrySmokeTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ALauncherDatabase::class.java,
    )

    @Test
    fun registry_is_contiguous_and_covers_current_version() {
        MigrationRegistry.assertContiguous(CURRENT_VERSION)
    }

    @Test
    fun registry_contains_one_migration_per_step() {
        val expected = CURRENT_VERSION - 1
        check(MigrationRegistry.ALL.size == expected) {
            "Expected $expected migrations, found ${MigrationRegistry.ALL.size}"
        }
    }

    @Test
    fun migrate_all_versions_from_first_schema_to_current() {
        helper.createDatabase(TEST_DB, FIRST_VALIDATED_VERSION).close()

        val migrations = MigrationRegistry.ALL
            .filter { it.startVersion >= FIRST_VALIDATED_VERSION }
            .sortedBy { it.startVersion }

        migrations.forEach { migration ->
            helper.runMigrationsAndValidate(
                TEST_DB,
                migration.endVersion,
                true,
                migration,
            ).close()
        }
    }

    @Test
    fun room_can_open_after_full_migration_chain() {
        helper.createDatabase(TEST_DB, FIRST_VALIDATED_VERSION).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Room.databaseBuilder(context, ALauncherDatabase::class.java, TEST_DB)
            .addMigrations(*MigrationRegistry.ARRAY)
            .build()
            .apply {
                openHelper.writableDatabase
                close()
            }
    }
}
