package com.f0x1d.logfox.feature.database.impl.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.f0x1d.logfox.feature.database.impl.entity.QueryHistoryRoomEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface QueryHistoryDao {

    @Query("SELECT * FROM QueryHistory ORDER BY created_at DESC")
    fun getAllAsFlow(): Flow<List<QueryHistoryRoomEntity>>

    @Query("SELECT * FROM QueryHistory ORDER BY created_at DESC")
    suspend fun getAll(): List<QueryHistoryRoomEntity>

    @Query("SELECT * FROM QueryHistory WHERE id = :id")
    suspend fun getById(id: Long): QueryHistoryRoomEntity?

    @Insert
    suspend fun insert(queryHistory: QueryHistoryRoomEntity)

    @Delete
    suspend fun delete(queryHistory: QueryHistoryRoomEntity)

    @Query("DELETE FROM QueryHistory WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM QueryHistory")
    suspend fun deleteAll()
}