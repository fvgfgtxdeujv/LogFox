package com.f0x1d.logfox.mcp.api.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    val results: List<McpLogLine>,
    val total: Int,
    val limit: Int,
    val offset: Int,
)
