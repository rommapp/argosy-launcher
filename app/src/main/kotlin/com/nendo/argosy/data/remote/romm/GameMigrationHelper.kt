package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.entity.GameEntity
import java.io.File

object GameMigrationHelper {

    fun aggregateMultiDiscData(
        sources: List<GameEntity>,
        pathValidator: (String) -> Boolean = { File(it).exists() }
    ): GameEntity? {
        if (sources.isEmpty()) return null
        if (sources.size == 1) return sources.first()

        val base = sources.first()
        val validLocalPath = sources
            .mapNotNull { it.localPath }
            .firstOrNull { pathValidator(it) }

        return base.copy(
            id = 0,
            localPath = validLocalPath,
            playCount = sources.sumOf { it.playCount },
            playTimeMinutes = sources.sumOf { it.playTimeMinutes },
            userRating = sources.maxOf { it.userRating },
            userDifficulty = sources.maxOf { it.userDifficulty },
            completion = sources.maxOf { it.completion },
            isFavorite = sources.any { it.isFavorite },
            isHidden = sources.all { it.isHidden },
            addedAt = sources.minOfOrNull { it.addedAt } ?: base.addedAt,
            lastPlayed = sources.mapNotNull { it.lastPlayed }.maxOrNull(),
            achievementCount = sources.maxOf { it.achievementCount }
        )
    }
}
