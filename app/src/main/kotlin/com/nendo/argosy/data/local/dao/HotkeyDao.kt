package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nendo.argosy.data.local.entity.HotkeyAction
import com.nendo.argosy.data.local.entity.HotkeyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HotkeyDao {

    @Query("SELECT * FROM hotkeys")
    suspend fun getAll(): List<HotkeyEntity>

    @Query("SELECT * FROM hotkeys")
    fun observeAll(): Flow<List<HotkeyEntity>>

    @Query("SELECT * FROM hotkeys WHERE isEnabled = 1")
    suspend fun getEnabled(): List<HotkeyEntity>

    @Query("SELECT * FROM hotkeys WHERE isEnabled = 1")
    fun observeEnabled(): Flow<List<HotkeyEntity>>

    @Query("SELECT * FROM hotkeys WHERE action = :action LIMIT 1")
    suspend fun getByAction(action: HotkeyAction): HotkeyEntity?

    @Query("SELECT * FROM hotkeys WHERE action = :action AND (controllerId IS NULL OR controllerId = :controllerId) LIMIT 1")
    suspend fun getByActionAndController(action: HotkeyAction, controllerId: String?): HotkeyEntity?

    @Upsert
    suspend fun upsert(hotkey: HotkeyEntity)

    @Query("UPDATE hotkeys SET buttonComboJson = :buttonComboJson, isEnabled = :isEnabled WHERE action = :action AND (controllerId IS NULL AND :controllerId IS NULL OR controllerId = :controllerId)")
    suspend fun updateCombo(action: HotkeyAction, controllerId: String?, buttonComboJson: String, isEnabled: Boolean)

    @Query("DELETE FROM hotkeys WHERE action = :action")
    suspend fun deleteByAction(action: HotkeyAction)

    @Query("DELETE FROM hotkeys WHERE action = :action AND controllerId = :controllerId")
    suspend fun deleteByActionAndController(action: HotkeyAction, controllerId: String)

    @Query("DELETE FROM hotkeys")
    suspend fun deleteAll()
}
