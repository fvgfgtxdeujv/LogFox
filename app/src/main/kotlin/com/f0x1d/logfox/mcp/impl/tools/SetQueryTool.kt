package com.f0x1d.logfox.mcp.impl.tools

import com.f0x1d.logfox.feature.logging.api.domain.UpdateQueryUseCase
import com.f0x1d.logfox.mcp.api.ContentBlock
import com.f0x1d.logfox.mcp.api.McpTool
import com.f0x1d.logfox.mcp.api.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber

class SetQueryTool(
    private val updateQueryUseCase: UpdateQueryUseCase,
) : McpTool {

    override val name = "set_query"
    override val description = "Set the log filter query."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("query", buildJsonObject {
                put("type", "string")
                put("description", "Filter query string")
            })
        })
    }

    override suspend fun call(params: JsonObject): ToolResult {
        val query = (params["query"] as? JsonPrimitive)?.content ?: ""
        return try {
            updateQueryUseCase(query)
            Timber.d("MCP set_query: query='$query'")
            ToolResult.Value(
                content = listOf(
                    ContentBlock(type = "text", text = "Query set to: $query"),
                ),
            )
        } catch (e: Exception) {
            Timber.e(e, "MCP set_query failed")
            ToolResult.Error("Failed to set query: ${e.message}")
        }
    }
}
