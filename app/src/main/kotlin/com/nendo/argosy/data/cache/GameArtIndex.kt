package com.nendo.argosy.data.cache

import com.nendo.argosy.data.local.entity.GameArtEntity
import com.nendo.argosy.data.local.entity.toResolvedArt
import com.nendo.argosy.data.model.ResolvedGameArt

internal class GameArtIndex(
    val byGame: Map<Long, ResolvedGameArt>,
    val rowsByGame: Map<Long, Int>,
    val rowCount: Int,
    val seq: Long
) {
    fun merge(changed: List<GameArtEntity>): GameArtIndex {
        if (changed.isEmpty()) return this
        val grouped = changed.groupBy { it.gameId }
        val rebuilt = grouped.mapValues { (_, rows) -> rows.toResolvedArt() }
        val artChanged = rebuilt.any { (gameId, art) -> byGame[gameId] != art }
        val counts = grouped.mapValues { (_, rows) -> rows.size }
        val replacedRows = counts.keys.sumOf { rowsByGame[it] ?: 0 }
        return GameArtIndex(
            byGame = if (artChanged) byGame + rebuilt else byGame,
            rowsByGame = rowsByGame + counts,
            rowCount = rowCount - replacedRows + counts.values.sum(),
            seq = maxOf(seq, changed.maxOf { it.changeSeq })
        )
    }

    fun retainGames(present: Set<Long>): GameArtIndex {
        val gone = rowsByGame.keys - present
        if (gone.isEmpty()) return this
        return GameArtIndex(
            byGame = byGame - gone,
            rowsByGame = rowsByGame - gone,
            rowCount = rowCount - gone.sumOf { rowsByGame.getValue(it) },
            seq = seq
        )
    }

    companion object {
        val EMPTY = GameArtIndex(emptyMap(), emptyMap(), 0, 0L)

        fun of(rows: List<GameArtEntity>): GameArtIndex = EMPTY.merge(rows)
    }
}
