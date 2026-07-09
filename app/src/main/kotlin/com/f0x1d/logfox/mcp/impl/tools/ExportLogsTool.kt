package com.f0x1d.logfox.mcp.impl.tools

import com.f0x1d.logfox.feature.logging.api.domain.GetLogsSnapshotUseCase
import com.f0x1d.logfox.mcp.api.McpTool
import com.f0x1d.logfox.mcp.api.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportLogsTool(
    private val getLogsSnapshotUseCase: GetLogsSnapshotUseCase,
) : McpTool {

    override val name: String = "export_logs"

    override val description: String = "导出日志为文本格式，支持按条件过滤"

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("format", buildJsonObject {
                put("type", "string")
                put("description", "导出格式 (txt/log)，默认 txt")
            })
            put("query", buildJsonObject {
                put("type", "string")
                put("description", "关键词过滤")
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
                put("description", "最大导出条数，默认50000")
            })
        })
    }

    override suspend fun call(params: JsonObject): ToolResult {
        val format = params["format"]?.jsonPrimitive?.content ?: "txt"
        val query = params["query"]?.jsonPrimitive?.content
        val tag = params["tag"]?.jsonPrimitive?.content
        val packageName = params["package_name"]?.jsonPrimitive?.content
        val level = params["level"]?.jsonPrimitive?.content
        val limit = params["limit"]?.jsonPrimitive?.int ?: 50000

        val allLogs = getLogsSnapshotUseCase()

        val filtered = allLogs.filter { logLine ->
            val tagMatch = tag?.let { logLine.tag.contains(it, ignoreCase = true) } ?: true
            val pkgMatch = packageName?.let { logLine.packageName?.contains(it, ignoreCase = true) ?: false } ?: true
            val levelMatch = level?.let { logLine.level.letter.equals(it, ignoreCase = true) } ?: true
            val queryMatch = query?.let { logLine.content.contains(it, ignoreCase = true) } ?: true
            tagMatch && pkgMatch && levelMatch && queryMatch
        }.take(limit)

        val sdf = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
        val content = filtered.joinToString("\n") { logLine ->
            val timeStr = sdf.format(Date(logLine.dateAndTime))
            "$timeStr ${logLine.level.letter}/${logLine.tag}: ${logLine.content}"
        }

        val lineCount = filtered.size
        val fileSize = content.toByteArray().size

        return ToolResult.Value(
            content = listOf(
                com.f0x1d.logfox.mcp.api.ContentBlock(
                    type = "text",
                    text = "导出完成，共 $lineCount 条日志，大小 $fileSize 字节，格式: $format",
                ),
                com.f0x1d.logfox.mcp.api.ContentBlock(
                    type = "text",
                    text = content,
                ),
            ),
        )
    }
}
