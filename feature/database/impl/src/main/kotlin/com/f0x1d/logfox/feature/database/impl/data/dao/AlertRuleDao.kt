package com.f0x1d.logfox.feature.database.impl.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.f0x1d.logfox.feature.database.impl.entity.AlertRuleRoomEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface AlertRuleDao {

    @Query("SELECT * FROM AlertRule ORDER BY created_at DESC")
    fun getAllAsFlow(): Flow<List<AlertRuleRoomEntity>>

    @Query("SELECT * FROM AlertRule ORDER BY created_at DESC")
    suspend fun getAll(): List<AlertRuleRoomEntity>

    @Query("SELECT * FROM AlertRule WHERE id = :id")
    suspend fun getById(id: Long): AlertRuleRoomEntity?

    @Query("SELECT * FROM AlertRule WHERE enabled = 1")
    suspend fun getEnabled(): List<AlertRuleRoomEntity>

    @Insert
    suspend fun insert(alertRule: AlertRuleRoomEntity)

    @Update
    suspend fun update(alertRule: AlertRuleRoomEntity)

    @Delete
    suspend fun delete(alertRule: AlertRuleRoomEntity)

    @Query("DELETE FROM AlertRule WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM AlertRule")
    suspend fun deleteAll()
}