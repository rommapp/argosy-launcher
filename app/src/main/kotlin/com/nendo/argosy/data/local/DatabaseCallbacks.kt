package com.nendo.argosy.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nendo.argosy.data.local.migrations.GameArtChangeTriggers
import com.nendo.argosy.data.local.migrations.GameChangeTriggers

object DatabaseCallbacks : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        GameArtChangeTriggers.create(db)
        GameChangeTriggers.create(db)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        GameArtChangeTriggers.create(db)
        GameChangeTriggers.create(db)
    }
}
