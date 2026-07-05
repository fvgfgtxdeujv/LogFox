package com.f0x1d.logfox.mcp.impl.tools

import com.f0x1d.logfox.feature.logging.api.domain.ClearLogsUseCase
import com.f0x1d.logfox.mcp.api.ContentBlock
import com.f0x1d.logfox.mcp.api.McpTool
import com.f0x1d.logfox.mcp.api.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber

class ClearLogsTool(
    private val clearLogsUseCase: ClearLogsUseCase,
) : McpTool {

    override val name = "clear_logs"
    override val description = "Clear all logcat buffers."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }

    override suspend fun call(params: JsonObject): ToolResult {
        return try {
            clearLogsUseCase()
            Timber.d("MCP clear_logs: success")
            ToolResult.Value(
                content = listOf(
                    ContentBlock(type = "text", text = "Logs cleared successfully."),
                ),
            )
        } catch (e: Exception) {
            Timber.e(e, "MCP clear_logs failed")
            ToolResult.Error("Failed to clear logs: ${e.message}")
        }
    }
}
