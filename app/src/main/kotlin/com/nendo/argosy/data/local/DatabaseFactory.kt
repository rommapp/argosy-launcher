package com.nendo.argosy.data.local

import android.content.Context
import androidx.room.Room

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
            .addMigrations(
                ALauncherDatabase.MIGRATION_1_2,
                ALauncherDatabase.MIGRATION_2_3,
                ALauncherDatabase.MIGRATION_3_4,
                ALauncherDatabase.MIGRATION_4_5,
                ALauncherDatabase.MIGRATION_5_6,
                ALauncherDatabase.MIGRATION_6_7,
                ALauncherDatabase.MIGRATION_7_8,
                ALauncherDatabase.MIGRATION_8_9,
                ALauncherDatabase.MIGRATION_9_10,
                ALauncherDatabase.MIGRATION_10_11,
                ALauncherDatabase.MIGRATION_11_12,
                ALauncherDatabase.MIGRATION_12_13,
                ALauncherDatabase.MIGRATION_13_14,
                ALauncherDatabase.MIGRATION_14_15,
                ALauncherDatabase.MIGRATION_15_16,
                ALauncherDatabase.MIGRATION_16_17,
                ALauncherDatabase.MIGRATION_17_18,
                ALauncherDatabase.MIGRATION_18_19,
                ALauncherDatabase.MIGRATION_19_20,
                ALauncherDatabase.MIGRATION_20_21,
                ALauncherDatabase.MIGRATION_21_22,
                ALauncherDatabase.MIGRATION_22_23,
                ALauncherDatabase.MIGRATION_23_24,
                ALauncherDatabase.MIGRATION_24_25,
                ALauncherDatabase.MIGRATION_25_26,
                ALauncherDatabase.MIGRATION_26_27,
                ALauncherDatabase.MIGRATION_27_28,
                ALauncherDatabase.MIGRATION_28_29,
                ALauncherDatabase.MIGRATION_29_30,
                ALauncherDatabase.MIGRATION_30_31,
                ALauncherDatabase.MIGRATION_31_32,
                ALauncherDatabase.MIGRATION_32_33,
                ALauncherDatabase.MIGRATION_33_34,
                ALauncherDatabase.MIGRATION_34_35,
                ALauncherDatabase.MIGRATION_35_36,
                ALauncherDatabase.MIGRATION_36_37,
                ALauncherDatabase.MIGRATION_37_38,
                ALauncherDatabase.MIGRATION_38_39,
                ALauncherDatabase.MIGRATION_39_40,
                ALauncherDatabase.MIGRATION_40_41,
                ALauncherDatabase.MIGRATION_41_42,
                ALauncherDatabase.MIGRATION_42_43,
                ALauncherDatabase.MIGRATION_43_44,
                ALauncherDatabase.MIGRATION_44_45,
                ALauncherDatabase.MIGRATION_45_46,
                ALauncherDatabase.MIGRATION_46_47,
                ALauncherDatabase.MIGRATION_47_48,
                ALauncherDatabase.MIGRATION_48_49,
                ALauncherDatabase.MIGRATION_49_50,
                ALauncherDatabase.MIGRATION_50_51,
                ALauncherDatabase.MIGRATION_51_52,
                ALauncherDatabase.MIGRATION_52_53,
                ALauncherDatabase.MIGRATION_53_54,
                ALauncherDatabase.MIGRATION_54_55,
                ALauncherDatabase.MIGRATION_55_56,
                ALauncherDatabase.MIGRATION_56_57,
                ALauncherDatabase.MIGRATION_57_58,
                ALauncherDatabase.MIGRATION_58_59,
                ALauncherDatabase.MIGRATION_59_60,
                ALauncherDatabase.MIGRATION_60_61,
                ALauncherDatabase.MIGRATION_61_62,
                ALauncherDatabase.MIGRATION_62_63,
                ALauncherDatabase.MIGRATION_63_64,
                ALauncherDatabase.MIGRATION_64_65,
                ALauncherDatabase.MIGRATION_65_66,
                ALauncherDatabase.MIGRATION_66_67,
                ALauncherDatabase.MIGRATION_67_68,
                ALauncherDatabase.MIGRATION_68_69,
                ALauncherDatabase.MIGRATION_69_70,
                ALauncherDatabase.MIGRATION_70_71,
                ALauncherDatabase.MIGRATION_71_72,
                ALauncherDatabase.MIGRATION_72_73,
                ALauncherDatabase.MIGRATION_73_74,
                ALauncherDatabase.MIGRATION_74_75
            )
            .enableMultiInstanceInvalidation()
            .build()
    }
}
