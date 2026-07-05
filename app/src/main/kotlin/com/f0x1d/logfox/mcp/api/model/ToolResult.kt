package com.f0x1d.logfox.mcp.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ContentBlock(
    val type: String,
    val text: String? = null,
    val data: JsonElement? = null,
)

sealed interface ToolResult {

    data class Value(val content: List<ContentBlock>) : ToolResult

    data class Stream(val flow: kotlinx.coroutines.flow.Flow<ContentBlock>) : ToolResult

    data class Error(val message: String) : ToolResult
}
