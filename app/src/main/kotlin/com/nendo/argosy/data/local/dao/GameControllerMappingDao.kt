package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nendo.argosy.data.local.entity.GameControllerMappingEntity
import java.time.Instant

@Dao
interface GameControllerMappingDao {

    @Query(
        "SELECT * FROM game_controller_mappings WHERE gameId = :gameId AND controllerId = :controllerId " +
            "AND profileKey = :profileKey LIMIT 1"
    )
    suspend fun getByGameAndController(
        gameId: Long,
        controllerId: String,
        profileKey: String
    ): GameControllerMappingEntity?

    @Query("SELECT COUNT(*) FROM game_controller_mappings WHERE gameId = :gameId")
    suspend fun countForGame(gameId: Long): Int

    @Upsert
    suspend fun upsert(mapping: GameControllerMappingEntity)

    @Query(
        "UPDATE game_controller_mappings SET mappingJson = :mappingJson, presetName = :presetName, " +
            "isAutoDetected = :isAutoDetected, updatedAt = :updatedAt " +
            "WHERE gameId = :gameId AND controllerId = :controllerId AND profileKey = :profileKey"
    )
    suspend fun updateMapping(
        gameId: Long,
        controllerId: String,
        profileKey: String,
        mappingJson: String,
        presetName: String?,
        isAutoDetected: Boolean,
        updatedAt: Instant = Instant.now()
    )

    @Query("DELETE FROM game_controller_mappings WHERE gameId = :gameId AND controllerId = :controllerId")
    suspend fun deleteForGameAndController(gameId: Long, controllerId: String)

    @Query("DELETE FROM game_controller_mappings WHERE gameId = :gameId")
    suspend fun deleteForGame(gameId: Long)
}
