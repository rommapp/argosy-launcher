package com.nendo.argosy.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase

object GameArtChangeTriggers {

    private const val CURRENT_MAX = "(SELECT IFNULL(MAX(`changeSeq`), 0) FROM `game_art`)"

    private const val NEXT_SEQ = "($CURRENT_MAX + 1)"

    private const val SEQ_PAST_OLD = "(MAX(OLD.`changeSeq`, $CURRENT_MAX) + 1)"

    val statements: List<String> = listOf(
        "CREATE TRIGGER IF NOT EXISTS `game_art_change_seq_insert` AFTER INSERT ON `game_art` " +
            "BEGIN UPDATE `game_art` SET `changeSeq` = $NEXT_SEQ " +
            "WHERE `gameId` = NEW.`gameId` AND `slot` = NEW.`slot`; END",
        "CREATE TRIGGER IF NOT EXISTS `game_art_change_seq_update` AFTER UPDATE ON `game_art` " +
            "WHEN NEW.`changeSeq` <= OLD.`changeSeq` " +
            "BEGIN UPDATE `game_art` SET `changeSeq` = $SEQ_PAST_OLD " +
            "WHERE `gameId` = NEW.`gameId` AND `slot` = NEW.`slot`; END",
        "CREATE TRIGGER IF NOT EXISTS `game_art_change_seq_delete` AFTER DELETE ON `game_art` " +
            "BEGIN UPDATE `game_art` SET `changeSeq` = $SEQ_PAST_OLD " +
            "WHERE `gameId` = OLD.`gameId`; " +
            "UPDATE `game_art` SET `changeSeq` = $SEQ_PAST_OLD " +
            "WHERE `rowid` = (SELECT `rowid` FROM `game_art` ORDER BY `changeSeq` DESC LIMIT 1); END"
    )

    val dropStatements: List<String> =
        listOf("game_art_change_seq_insert", "game_art_change_seq_update", "game_art_change_seq_delete")
            .map { "DROP TRIGGER IF EXISTS `$it`" }

    fun create(db: SupportSQLiteDatabase) {
        (dropStatements + statements).forEach(db::execSQL)
    }
}
