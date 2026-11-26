package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Stamps `games.changeSeq` with the next sequence on every insert and update. A delete restamps
 * the row holding the highest remaining sequence past the deleted one: the table's highest
 * sequence never decreases while it holds a row.
 */
object GameChangeTriggers {

    private const val CURRENT_MAX = "(SELECT IFNULL(MAX(`changeSeq`), 0) FROM `games`)"

    private const val NEXT_SEQ = "($CURRENT_MAX + 1)"

    val statements: List<String> = listOf(
        "CREATE TRIGGER IF NOT EXISTS `games_change_seq_insert` AFTER INSERT ON `games` " +
            "BEGIN UPDATE `games` SET `changeSeq` = $NEXT_SEQ " +
            "WHERE `id` = NEW.`id`; END",
        "CREATE TRIGGER IF NOT EXISTS `games_change_seq_update` AFTER UPDATE ON `games` " +
            "WHEN NEW.`changeSeq` <= OLD.`changeSeq` " +
            "BEGIN UPDATE `games` SET `changeSeq` = $NEXT_SEQ " +
            "WHERE `id` = NEW.`id`; END",
        "CREATE TRIGGER IF NOT EXISTS `games_change_seq_delete` AFTER DELETE ON `games` " +
            "BEGIN UPDATE `games` SET `changeSeq` = MAX(OLD.`changeSeq`, $CURRENT_MAX) + 1 " +
            "WHERE `id` = (SELECT `id` FROM `games` ORDER BY `changeSeq` DESC LIMIT 1); END"
    )

    fun create(db: SupportSQLiteDatabase) {
        statements.forEach(db::execSQL)
    }
}
