package com.nendo.argosy.data.local.migrations

import androidx.room.migration.Migration

object MigrationRegistry {

    val ALL: List<Migration> = listOf(
        Migration_1_2,
        Migration_2_3,
        Migration_3_4,
        Migration_4_5,
        Migration_5_6,
        Migration_6_7,
        Migration_7_8,
        Migration_8_9,
        Migration_9_10,
        Migration_10_11,
        Migration_11_12,
        Migration_12_13,
        Migration_13_14,
        Migration_14_15,
        Migration_15_16,
        Migration_16_17,
        Migration_17_18,
        Migration_18_19,
        Migration_19_20,
        Migration_20_21,
        Migration_21_22,
        Migration_22_23,
        Migration_23_24,
        Migration_24_25,
        Migration_25_26,
        Migration_26_27,
        Migration_27_28,
        Migration_28_29,
        Migration_29_30,
        Migration_30_31,
        Migration_31_32,
        Migration_32_33,
        Migration_33_34,
        Migration_34_35,
        Migration_35_36,
        Migration_36_37,
        Migration_37_38,
        Migration_38_39,
        Migration_39_40,
        Migration_40_41,
        Migration_41_42,
        Migration_42_43,
        Migration_43_44,
        Migration_44_45,
        Migration_45_46,
        Migration_46_47,
        Migration_47_48,
        Migration_48_49,
        Migration_49_50,
        Migration_50_51,
        Migration_51_52,
        Migration_52_53,
        Migration_53_54,
        Migration_54_55,
        Migration_55_56,
        Migration_56_57,
        Migration_57_58,
        Migration_58_59,
        Migration_59_60,
        Migration_60_61,
        Migration_61_62,
        Migration_62_63,
        Migration_63_64,
        Migration_64_65,
        Migration_65_66,
        Migration_66_67,
        Migration_67_68,
        Migration_68_69,
        Migration_69_70,
        Migration_70_71,
        Migration_71_72,
        Migration_72_73,
        Migration_73_74,
        Migration_74_75,
        Migration_75_76,
        Migration_76_77,
        Migration_77_78,
        Migration_78_79,
        Migration_79_80,
        Migration_80_81,
        Migration_81_82,
        Migration_82_83,
        Migration_83_84,
        Migration_84_85,
        Migration_85_86,
        Migration_86_87,
        Migration_87_88,
        Migration_88_89,
        Migration_89_90,
        Migration_90_91,
        Migration_91_92,
        Migration_92_93,
        Migration_93_94,
        Migration_94_95,
        Migration_95_96,
        Migration_96_97,
        Migration_97_98,
        Migration_98_99,
        Migration_99_100,
        Migration_100_101,
        Migration_101_102,
        Migration_102_103,
        Migration_103_104,
        Migration_104_105,
        Migration_105_106,
        Migration_106_107,
        Migration_107_108,
        Migration_108_109,
        Migration_109_110,
        Migration_110_111,
        Migration_111_112,
        Migration_112_113,
        Migration_113_114,
        Migration_114_115,
        Migration_115_116,
        Migration_116_117,
        Migration_117_118,
        Migration_118_119,
        Migration_119_120,
        Migration_120_121,
        Migration_121_122,
        Migration_122_123,
        Migration_123_124,
        Migration_124_125,
        Migration_125_126,
        Migration_126_127,
        Migration_127_128,
    )

    val ARRAY: Array<Migration> = ALL.toTypedArray()

    fun byKey(from: Int, to: Int): Migration? =
        ALL.firstOrNull { it.startVersion == from && it.endVersion == to }

    fun assertContiguous(targetVersion: Int) {
        val sorted = ALL.sortedBy { it.startVersion }
        sorted.forEachIndexed { index, migration ->
            val expectedFrom = index + 1
            require(migration.startVersion == expectedFrom) {
                "Migration gap: expected migration starting at $expectedFrom, found ${migration.startVersion}_${migration.endVersion}"
            }
            require(migration.endVersion == migration.startVersion + 1) {
                "Migration ${migration.startVersion}_${migration.endVersion} is not single-step"
            }
        }
        val last = sorted.last()
        require(last.endVersion == targetVersion) {
            "Migration registry ends at ${last.endVersion} but database version is $targetVersion"
        }
    }
}
