package com.f0x1d.logfox.mcp.api.model

import kotlinx.serialization.Serializable

@Serializable
data class ExportRequest(
    val format: String = "txt",
    val include: FilterGroup? = null,
    val exclude: FilterGroup? = null,
    val levels: List<String>? = null,
    val limit: Int = 50000,
)