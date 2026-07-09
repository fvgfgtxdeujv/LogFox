package com.f0x1d.logfox.feature.database.impl.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "LogTag")
internal data class LogTagRoomEntity(
    @ColumnInfo(name = "log_id") val logId: Long,
    @ColumnInfo(name = "tag") val tag: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)