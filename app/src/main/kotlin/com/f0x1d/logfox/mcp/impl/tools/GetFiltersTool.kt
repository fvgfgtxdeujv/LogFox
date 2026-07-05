package com.f0x1d.logfox.mcp.impl.tools

import com.f0x1d.logfox.feature.filters.api.domain.GetAllEnabledFiltersFlowUseCase
import com.f0x1d.logfox.mcp.api.ContentBlock
import com.f0x1d.logfox.mcp.api.McpTool
import com.f0x1d.logfox.mcp.api.ToolResult
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import timber.log.Timber

class GetFiltersTool(
    private val getAllEnabledFiltersFlowUseCase: GetAllEnabledFiltersFlowUseCase,
) : McpTool {

    override val name = "get_filters"
    override val description = "Get all enabled log filters."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }

    override suspend fun call(params: JsonObject): ToolResult {
        return try {
            val filters = getAllEnabledFiltersFlowUseCase().first()
            Timber.d("MCP get_filters: ${filters.size} filters")

            val filterObjects = filters.map { filter ->
                buildJsonObject {
                    put("id", filter.id)
                    put("name", filter.name)
                    put("query", buildQueryFromFilter(filter))
                    put("including", filter.including)
                    put("enabled", filter.enabled)
                }
            }

            ToolResult.Value(
                content = listOf(
                    ContentBlock(
                        type = "text",
                        text = "Found ${filters.size} enabled filter(s).",
                    ),
                    ContentBlock(
                        type = "data",
                        data = buildJsonObject {
                            putJsonArray("filters") {
                                filterObjects.forEach { add(it) }
                            }
                        },
                    ),
                ),
            )
        } catch (e: Exception) {
            Timber.e(e, "MCP get_filters failed")
            ToolResult.Error("Failed to get filters: ${e.message}")
        }
    }

    private fun buildQueryFromFilter(
        filter: com.f0x1d.logfox.feature.filters.api.model.UserFilter,
    ): String {
        val parts = mutableListOf<String>()
        filter.uid?.let { parts.add("uid:$it") }
        filter.pid?.let { parts.add("pid:$it") }
        filter.tid?.let { parts.add("tid:$it") }
        filter.packageName?.let { parts.add("pkg:$it") }
        filter.tag?.let { parts.add("tag:$it") }
        filter.content?.let { parts.add("msg:$it") }
        return parts.joinToString(" ")
    }
}
