package com.f0x1d.logfox.feature.database.impl.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "QueryHistory")
internal data class QueryHistoryRoomEntity(
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "query") val query: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)