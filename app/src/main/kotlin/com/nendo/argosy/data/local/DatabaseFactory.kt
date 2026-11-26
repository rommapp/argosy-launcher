package com.nendo.argosy.data.local

import android.content.Context
import androidx.room.Room
import com.nendo.argosy.data.local.migrations.MigrationRegistry

object DatabaseFactory {

    @Volatile
    private var instance: ALauncherDatabase? = null

    fun getDatabase(context: Context): ALauncherDatabase {
        return instance ?: synchronized(this) {
            instance ?: buildDatabase(context).also { instance = it }
        }
    }

    private fun buildDatabase(context: Context): ALauncherDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            ALauncherDatabase::class.java,
            "alauncher.db"
        )
            .addMigrations(*MigrationRegistry.ARRAY)
            .addCallback(DatabaseCallbacks)
            .enableMultiInstanceInvalidation()
            .build()
    }
}
