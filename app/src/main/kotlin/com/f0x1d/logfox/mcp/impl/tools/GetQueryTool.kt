package com.f0x1d.logfox.mcp.impl.tools

import com.f0x1d.logfox.feature.logging.api.domain.GetQueryFlowUseCase
import com.f0x1d.logfox.mcp.api.ContentBlock
import com.f0x1d.logfox.mcp.api.McpTool
import com.f0x1d.logfox.mcp.api.ToolResult
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber

class GetQueryTool(
    private val getQueryFlowUseCase: GetQueryFlowUseCase,
) : McpTool {

    override val name = "get_query"
    override val description = "Get the current log filter query."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }

    override suspend fun call(params: JsonObject): ToolResult {
        return try {
            val query = getQueryFlowUseCase().first().orEmpty()
            Timber.d("MCP get_query: query='$query'")
            ToolResult.Value(
                content = listOf(
                    ContentBlock(type = "text", text = query),
                ),
            )
        } catch (e: Exception) {
            Timber.e(e, "MCP get_query failed")
            ToolResult.Error("Failed to get query: ${e.message}")
        }
    }
}
