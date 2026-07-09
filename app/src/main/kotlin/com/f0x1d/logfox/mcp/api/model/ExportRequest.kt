package com.f0x1d.logfox.mcp.api.model

import kotlinx.serialization.Serializable

@Serializable
data class ExportRequest(
    val format: String = "txt",
    val query: String? = null,
    val tag: String? = null,
    val packageName: String? = null,
    val level: String? = null,
    val limit: Int = 50000,
)
