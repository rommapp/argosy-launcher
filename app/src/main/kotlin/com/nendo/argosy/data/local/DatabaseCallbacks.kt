package com.nendo.argosy.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nendo.argosy.data.local.migrations.ChangeStampTriggers

object DatabaseCallbacks : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        ChangeStampTriggers.createAll(db)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        ChangeStampTriggers.recreateStale(db)
    }
}
