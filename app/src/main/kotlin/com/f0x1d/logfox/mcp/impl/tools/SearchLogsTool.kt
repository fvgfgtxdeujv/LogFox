package com.f0x1d.logfox.mcp.impl.tools

import com.f0x1d.logfox.feature.logging.api.domain.GetLogsSnapshotUseCase
import com.f0x1d.logfox.mcp.api.McpTool
import com.f0x1d.logfox.mcp.api.ToolResult
import com.f0x1d.logfox.mcp.api.model.McpLogLine
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SearchLogsTool(
    private val getLogsSnapshotUseCase: GetLogsSnapshotUseCase,
) : McpTool {

    override val name: String = "search_logs"

    override val description: String = "按条件搜索历史日志，支持包含/排除模式和多字段过滤"

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("include", buildJsonObject {
                put("type", "object")
                put("description", "包含条件组（所有条件必须满足）")
                put("properties", buildJsonObject {
                    put("uid", buildJsonObject {
                        put("type", "string")
                        put("description", "用户 ID 匹配")
                    })
                    put("pid", buildJsonObject {
                        put("type", "string")
                        put("description", "进程 ID 匹配")
                    })
                    put("tid", buildJsonObject {
                        put("type", "string")
                        put("description", "线程 ID 匹配")
                    })
                    put("package_name", buildJsonObject {
                        put("type", "string")
                        put("description", "包名匹配")
                    })
                    put("tag", buildJsonObject {
                        put("type", "string")
                        put("description", "标签匹配")
                    })
                    put("content", buildJsonObject {
                        put("type", "string")
                        put("description", "日志内容匹配")
                    })
                    put("case_sensitive", buildJsonObject {
                        put("type", "boolean")
                        put("description", "是否大小写敏感，默认 false")
                    })
                })
            })
            put("exclude", buildJsonObject {
                put("type", "object")
                put("description", "排除条件组（任何条件满足则排除）")
                put("properties", buildJsonObject {
                    put("uid", buildJsonObject {
                        put("type", "string")
                        put("description", "排除用户 ID")
                    })
                    put("pid", buildJsonObject {
                        put("type", "string")
                        put("description", "排除进程 ID")
                    })
                    put("tid", buildJsonObject {
                        put("type", "string")
                        put("description", "排除线程 ID")
                    })
                    put("package_name", buildJsonObject {
                        put("type", "string")
                        put("description", "排除包名")
                    })
                    put("tag", buildJsonObject {
                        put("type", "string")
                        put("description", "排除标签")
                    })
                    put("content", buildJsonObject {
                        put("type", "string")
                        put("description", "排除日志内容")
                    })
                    put("case_sensitive", buildJsonObject {
                        put("type", "boolean")
                        put("description", "是否大小写敏感，默认 false")
                    })
                })
            })
            put("levels", buildJsonObject {
                put("type", "array")
                put("description", "日志级别列表 (V/D/I/W/E/F)")
                put("items", buildJsonObject {
                    put("type", "string")
                    put("enum", JsonArray(listOf(
                        JsonPrimitive("V"),
                        JsonPrimitive("D"),
                        JsonPrimitive("I"),
                        JsonPrimitive("W"),
                        JsonPrimitive("E"),
                        JsonPrimitive("F"),
                    )))
                })
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
        val include = params["include"]?.jsonObject
        val exclude = params["exclude"]?.jsonObject
        val levels = params["levels"]?.jsonArray?.map { it.jsonPrimitive.content }
        val limit = params["limit"]?.jsonPrimitive?.int ?: 1000
        val offset = params["offset"]?.jsonPrimitive?.int ?: 0

        val includeCaseSensitive = include?.get("case_sensitive")?.jsonPrimitive?.boolean ?: false
        val excludeCaseSensitive = exclude?.get("case_sensitive")?.jsonPrimitive?.boolean ?: false

        val allLogs = getLogsSnapshotUseCase()

        val filtered = allLogs.filter { logLine ->
            val includeUid = include?.get("uid")?.jsonPrimitive?.content
            val includePid = include?.get("pid")?.jsonPrimitive?.content
            val includeTid = include?.get("tid")?.jsonPrimitive?.content
            val includePackageName = include?.get("package_name")?.jsonPrimitive?.content
            val includeTag = include?.get("tag")?.jsonPrimitive?.content
            val includeContent = include?.get("content")?.jsonPrimitive?.content

            val excludeUid = exclude?.get("uid")?.jsonPrimitive?.content
            val excludePid = exclude?.get("pid")?.jsonPrimitive?.content
            val excludeTid = exclude?.get("tid")?.jsonPrimitive?.content
            val excludePackageName = exclude?.get("package_name")?.jsonPrimitive?.content
            val excludeTag = exclude?.get("tag")?.jsonPrimitive?.content
            val excludeContent = exclude?.get("content")?.jsonPrimitive?.content

            val uidMatch = includeUid?.let { logLine.uid.contains(it, ignoreCase = !includeCaseSensitive) } ?: true
            val pidMatch = includePid?.let { logLine.pid.contains(it, ignoreCase = !includeCaseSensitive) } ?: true
            val tidMatch = includeTid?.let { logLine.tid.contains(it, ignoreCase = !includeCaseSensitive) } ?: true
            val pkgMatch = includePackageName?.let { logLine.packageName?.contains(it, ignoreCase = !includeCaseSensitive) ?: false } ?: true
            val tagMatch = includeTag?.let { logLine.tag.contains(it, ignoreCase = !includeCaseSensitive) } ?: true
            val contentMatch = includeContent?.let { logLine.content.contains(it, ignoreCase = !includeCaseSensitive) } ?: true

            val uidExclude = excludeUid?.let { !logLine.uid.contains(it, ignoreCase = !excludeCaseSensitive) } ?: true
            val pidExclude = excludePid?.let { !logLine.pid.contains(it, ignoreCase = !excludeCaseSensitive) } ?: true
            val tidExclude = excludeTid?.let { !logLine.tid.contains(it, ignoreCase = !excludeCaseSensitive) } ?: true
            val pkgExclude = excludePackageName?.let { !(logLine.packageName?.contains(it, ignoreCase = !excludeCaseSensitive) ?: false) } ?: true
            val tagExclude = excludeTag?.let { !logLine.tag.contains(it, ignoreCase = !excludeCaseSensitive) } ?: true
            val contentExclude = excludeContent?.let { !logLine.content.contains(it, ignoreCase = !excludeCaseSensitive) } ?: true

            val levelMatch = levels?.let { it.contains(logLine.level.letter, ignoreCase = true) } ?: true

            uidMatch && pidMatch && tidMatch && pkgMatch && tagMatch && contentMatch &&
            uidExclude && pidExclude && tidExclude && pkgExclude && tagExclude && contentExclude &&
            levelMatch
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