package com.f0x1d.logfox.mcp.api.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchRequest(
    val query: String? = null,
    val tag: String? = null,
    val packageName: String? = null,
    val level: String? = null,
    val limit: Int = 1000,
    val offset: Int = 0,
)
