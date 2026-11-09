package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class Migration220To221Test {

    private fun statements(): List<String> {
        val captured = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers { captured += firstArg<String>() }
        Migration_220_221.migrate(db)
        return captured
    }

    @Test
    fun `unlocks only PSP Store releases and keeps their ids`() {
        assertEquals(
            listOf(
                "UPDATE `games` SET `titleIdLocked` = 0 WHERE `titleId` LIKE 'NP%' AND `platformId` IN " +
                    "(SELECT `id` FROM `platforms` WHERE `slug` = 'psp')"
            ),
            statements()
        )
    }

    @Test
    fun `is registered`() {
        assertEquals(Migration_220_221, MigrationRegistry.byKey(220, 221))
    }
}
