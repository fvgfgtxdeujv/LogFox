package com.f0x1d.logfox.feature.database.impl.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "AlertRule")
internal data class AlertRuleRoomEntity(
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "keyword") val keyword: String? = null,
    @ColumnInfo(name = "level_threshold") val levelThreshold: String? = null,
    @ColumnInfo(name = "enabled") val enabled: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)