package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase

object ChangeStampTriggers {

    private val ifNotExists = Regex("IF\\s+NOT\\s+EXISTS\\s+", RegexOption.IGNORE_CASE)

    private val whitespace = Regex("\\s+")

    fun dropStatement(name: String): String = "DROP TRIGGER IF EXISTS `$name`"

    fun expected(): Map<String, String> =
        GameArtChangeTriggers.createStatements + GameChangeTriggers.createStatements

    fun normalise(sql: String): String = sql.replace(ifNotExists, "").replace(whitespace, " ").trim()

    /**
     * Names in [expected] whose definition is missing from [stored] or differs from it once both
     * sides are normalised, in [expected] order. Triggers absent from [expected] are ignored.
     */
    fun staleTriggers(expected: Map<String, String>, stored: Map<String, String>): List<String> =
        expected.filter { (name, statement) -> stored[name]?.let(::normalise) != normalise(statement) }
            .keys
            .toList()

    fun createAll(db: SupportSQLiteDatabase) {
        inTransaction(db) {
            GameArtChangeTriggers.create(db)
            GameChangeTriggers.create(db)
        }
    }

    fun recreateStale(db: SupportSQLiteDatabase) {
        inTransaction(db) {
            val expected = expected()
            staleTriggers(expected, storedTriggers(db)).forEach { name ->
                db.execSQL(dropStatement(name))
                db.execSQL(expected.getValue(name))
            }
        }
    }

    private fun storedTriggers(db: SupportSQLiteDatabase): Map<String, String> =
        db.query("SELECT name, sql FROM sqlite_master WHERE type = 'trigger'").use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    if (!cursor.isNull(0) && !cursor.isNull(1)) put(cursor.getString(0), cursor.getString(1))
                }
            }
        }

    private inline fun inTransaction(db: SupportSQLiteDatabase, block: () -> Unit) {
        db.beginTransaction()
        try {
            block()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
