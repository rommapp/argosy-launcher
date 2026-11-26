package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase

object GameArtChangeTriggers {

    private const val CURRENT_MAX = "(SELECT IFNULL(MAX(`changeSeq`), 0) FROM `game_art`)"

    private const val NEXT_SEQ = "($CURRENT_MAX + 1)"

    private const val SEQ_PAST_OLD = "(MAX(OLD.`changeSeq`, $CURRENT_MAX) + 1)"

    private const val INSERT = "game_art_change_seq_insert"

    private const val UPDATE = "game_art_change_seq_update"

    private const val DELETE = "game_art_change_seq_delete"

    val createStatements: Map<String, String> = linkedMapOf(
        INSERT to "CREATE TRIGGER IF NOT EXISTS `$INSERT` AFTER INSERT ON `game_art` " +
            "BEGIN UPDATE `game_art` SET `changeSeq` = $NEXT_SEQ " +
            "WHERE `gameId` = NEW.`gameId` AND `slot` = NEW.`slot`; END",
        UPDATE to "CREATE TRIGGER IF NOT EXISTS `$UPDATE` AFTER UPDATE ON `game_art` " +
            "WHEN NEW.`changeSeq` <= OLD.`changeSeq` " +
            "BEGIN UPDATE `game_art` SET `changeSeq` = $SEQ_PAST_OLD " +
            "WHERE `gameId` = NEW.`gameId` AND `slot` = NEW.`slot`; END",
        DELETE to "CREATE TRIGGER IF NOT EXISTS `$DELETE` AFTER DELETE ON `game_art` " +
            "BEGIN UPDATE `game_art` SET `changeSeq` = $SEQ_PAST_OLD " +
            "WHERE `gameId` = OLD.`gameId`; " +
            "UPDATE `game_art` SET `changeSeq` = $SEQ_PAST_OLD " +
            "WHERE `rowid` = (SELECT `rowid` FROM `game_art` ORDER BY `changeSeq` DESC LIMIT 1); END"
    )

    val statements: List<String> = createStatements.values.toList()

    val dropStatements: List<String> = createStatements.keys.map(ChangeStampTriggers::dropStatement)

    fun create(db: SupportSQLiteDatabase) {
        (dropStatements + statements).forEach(db::execSQL)
    }
}
