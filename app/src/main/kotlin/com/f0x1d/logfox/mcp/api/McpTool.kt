package com.f0x1d.logfox.mcp.api

import kotlinx.serialization.json.JsonObject

interface McpTool {

    val name: String

    val description: String

    val inputSchema: JsonObject

    suspend fun call(params: JsonObject): ToolResult
}
