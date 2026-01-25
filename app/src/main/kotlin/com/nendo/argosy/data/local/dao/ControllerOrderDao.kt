package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nendo.argosy.data.local.entity.ControllerOrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ControllerOrderDao {

    @Query("SELECT * FROM controller_order ORDER BY port ASC")
    suspend fun getAll(): List<ControllerOrderEntity>

    @Query("SELECT * FROM controller_order ORDER BY port ASC")
    fun observeAll(): Flow<List<ControllerOrderEntity>>

    @Query("SELECT * FROM controller_order WHERE port = :port")
    suspend fun getByPort(port: Int): ControllerOrderEntity?

    @Query("SELECT * FROM controller_order WHERE controllerId = :controllerId")
    suspend fun getByControllerId(controllerId: String): ControllerOrderEntity?

    @Upsert
    suspend fun upsert(order: ControllerOrderEntity)

    @Upsert
    suspend fun upsertAll(orders: List<ControllerOrderEntity>)

    @Query("DELETE FROM controller_order WHERE port = :port")
    suspend fun deleteByPort(port: Int)

    @Query("DELETE FROM controller_order WHERE controllerId = :controllerId")
    suspend fun deleteByControllerId(controllerId: String)

    @Query("DELETE FROM controller_order")
    suspend fun deleteAll()
}
