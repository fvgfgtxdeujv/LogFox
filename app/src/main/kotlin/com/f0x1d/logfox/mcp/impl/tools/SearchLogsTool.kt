package com.f0x1d.logfox.mcp.impl.tools

import com.f0x1d.logfox.feature.logging.api.domain.GetLogsSnapshotUseCase
import com.f0x1d.logfox.feature.logging.api.model.LogLevel
import com.f0x1d.logfox.mcp.api.McpTool
import com.f0x1d.logfox.mcp.api.ToolResult
import com.f0x1d.logfox.mcp.api.model.McpLogLine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SearchLogsTool(
    private val getLogsSnapshotUseCase: GetLogsSnapshotUseCase,
) : McpTool {

    override val name: String = "search_logs"

    override val description: String = "按条件搜索历史日志，支持关键词、标签、包名、级别过滤"

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("query", buildJsonObject {
                put("type", "string")
                put("description", "搜索关键词，匹配日志内容")
            })
            put("tag", buildJsonObject {
                put("type", "string")
                put("description", "按标签过滤")
            })
            put("package_name", buildJsonObject {
                put("type", "string")
                put("description", "按包名过滤")
            })
            put("level", buildJsonObject {
                put("type", "string")
                put("description", "按日志级别过滤 (V/D/I/W/E/F)")
            })
            put("limit", buildJsonObject {
                put("type", "number")
                put("description", "返回结果数量限制，默认1000")
            })
            put("offset", buildJsonObject {
                put("type", "number")
                put("description", "分页偏移量，默认0")
            })
        })
    }

    override suspend fun call(params: JsonObject): ToolResult {
        val query = params["query"]?.jsonPrimitive?.content
        val tag = params["tag"]?.jsonPrimitive?.content
        val packageName = params["package_name"]?.jsonPrimitive?.content
        val level = params["level"]?.jsonPrimitive?.content
        val limit = params["limit"]?.jsonPrimitive?.int ?: 1000
        val offset = params["offset"]?.jsonPrimitive?.int ?: 0

        val allLogs = getLogsSnapshotUseCase()

        val filtered = allLogs.filter { logLine ->
            tag?.let { logLine.tag.contains(it, ignoreCase = true) } ?: true &&
            packageName?.let { logLine.packageName?.contains(it, ignoreCase = true) ?: false } ?: true &&
            level?.let { logLine.level.letter.equals(it, ignoreCase = true) } ?: true &&
            query?.let { logLine.content.contains(it, ignoreCase = true) } ?: true
        }

        val paged = filtered.drop(offset).take(limit)
        val mcpLines = paged.map { McpLogLine.from(it) }

        val resultJson = buildJsonObject {
            put("total", filtered.size)
            put("limit", limit)
            put("offset", offset)
            putJsonArray("results") {
                mcpLines.forEach { line ->
                    add(
                        buildJsonObject {
                            put("id", line.id)
                            put("dateAndTime", line.dateAndTime)
                            put("uid", line.uid)
                            put("pid", line.pid)
                            put("tid", line.tid)
                            put("packageName", line.packageName ?: "")
                            put("level", line.level.name)
                            put("tag", line.tag)
                            put("content", line.content)
                        },
                    )
                }
            }
        }

        return ToolResult.Value(
            content = listOf(
                com.f0x1d.logfox.mcp.api.ContentBlock(
                    type = "text",
                    text = "找到 ${filtered.size} 条匹配日志，返回 ${mcpLines.size} 条",
                ),
                com.f0x1d.logfox.mcp.api.ContentBlock(
                    type = "data",
                    data = resultJson,
                ),
            ),
        )
    }
}
