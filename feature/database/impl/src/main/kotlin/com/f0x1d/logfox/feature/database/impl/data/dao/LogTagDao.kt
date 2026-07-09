package com.f0x1d.logfox.feature.database.impl.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.f0x1d.logfox.feature.database.impl.entity.LogTagRoomEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface LogTagDao {

    @Query("SELECT * FROM LogTag ORDER BY created_at DESC")
    fun getAllAsFlow(): Flow<List<LogTagRoomEntity>>

    @Query("SELECT * FROM LogTag ORDER BY created_at DESC")
    suspend fun getAll(): List<LogTagRoomEntity>

    @Query("SELECT * FROM LogTag WHERE id = :id")
    suspend fun getById(id: Long): LogTagRoomEntity?

    @Query("SELECT * FROM LogTag WHERE log_id = :logId")
    suspend fun getByLogId(logId: Long): List<LogTagRoomEntity>

    @Query("SELECT * FROM LogTag WHERE tag = :tag")
    suspend fun getByTag(tag: String): List<LogTagRoomEntity>

    @Insert
    suspend fun insert(logTag: LogTagRoomEntity)

    @Delete
    suspend fun delete(logTag: LogTagRoomEntity)

    @Query("DELETE FROM LogTag WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM LogTag WHERE log_id = :logId")
    suspend fun deleteByLogId(logId: Long)

    @Query("DELETE FROM LogTag")
    suspend fun deleteAll()

    @Query("SELECT DISTINCT tag FROM LogTag")
    suspend fun getAllTags(): List<String>
}