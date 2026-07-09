package com.f0x1d.logfox.mcp.api.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchRequest(
    val include: FilterGroup? = null,
    val exclude: FilterGroup? = null,
    val levels: List<String>? = null,
    val limit: Int = 1000,
    val offset: Int = 0,
)