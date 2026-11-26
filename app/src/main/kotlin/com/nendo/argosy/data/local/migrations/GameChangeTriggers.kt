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

    private const val INSERT = "games_change_seq_insert"

    private const val UPDATE = "games_change_seq_update"

    private const val DELETE = "games_change_seq_delete"

    val createStatements: Map<String, String> = linkedMapOf(
        INSERT to "CREATE TRIGGER IF NOT EXISTS `$INSERT` AFTER INSERT ON `games` " +
            "BEGIN UPDATE `games` SET `changeSeq` = $NEXT_SEQ " +
            "WHERE `id` = NEW.`id`; END",
        UPDATE to "CREATE TRIGGER IF NOT EXISTS `$UPDATE` AFTER UPDATE ON `games` " +
            "WHEN NEW.`changeSeq` <= OLD.`changeSeq` " +
            "BEGIN UPDATE `games` SET `changeSeq` = MAX(OLD.`changeSeq`, $CURRENT_MAX) + 1 " +
            "WHERE `id` = NEW.`id`; END",
        DELETE to "CREATE TRIGGER IF NOT EXISTS `$DELETE` AFTER DELETE ON `games` " +
            "BEGIN UPDATE `games` SET `changeSeq` = MAX(OLD.`changeSeq`, $CURRENT_MAX) + 1 " +
            "WHERE `id` = (SELECT `id` FROM `games` ORDER BY `changeSeq` DESC LIMIT 1); END"
    )

    val statements: List<String> = createStatements.values.toList()

    val dropStatements: List<String> = createStatements.keys.map(ChangeStampTriggers::dropStatement)

    fun create(db: SupportSQLiteDatabase) {
        (dropStatements + statements).forEach(db::execSQL)
    }
}
