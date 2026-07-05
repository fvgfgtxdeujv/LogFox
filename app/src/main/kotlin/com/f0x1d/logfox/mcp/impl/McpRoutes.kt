package com.f0x1d.logfox.mcp.impl

import com.f0x1d.logfox.feature.filters.api.domain.GetAllEnabledFiltersFlowUseCase
import com.f0x1d.logfox.feature.logging.api.data.StartLoggingUseCase
import com.f0x1d.logfox.feature.logging.api.domain.ClearLogsUseCase
import com.f0x1d.logfox.feature.logging.api.domain.GetQueryFlowUseCase
import com.f0x1d.logfox.feature.logging.api.domain.UpdateQueryUseCase
import com.f0x1d.logfox.mcp.api.McpLogLine
import com.f0x1d.logfox.mcp.api.McpTool
import com.f0x1d.logfox.mcp.impl.tools.ClearLogsTool
import com.f0x1d.logfox.mcp.impl.tools.GetFiltersTool
import com.f0x1d.logfox.mcp.impl.tools.GetQueryTool
import com.f0x1d.logfox.mcp.impl.tools.ReadLogsTool
import com.f0x1d.logfox.mcp.impl.tools.SetQueryTool
import com.f0x1d.logfox.feature.terminals.api.base.Terminal
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import timber.log.Timber

class McpRoutes(private val json: Json) {

    fun Application.mcpRoutes(
        terminal: Terminal,
        startLoggingUseCase: StartLoggingUseCase,
        clearLogsUseCase: ClearLogsUseCase,
        getQueryFlowUseCase: GetQueryFlowUseCase,
        updateQueryUseCase: UpdateQueryUseCase,
        getAllEnabledFiltersFlowUseCase: GetAllEnabledFiltersFlowUseCase,
        tools: Map<String, McpTool>,
    ) {
        install(ContentNegotiation) {
            json(json)
        }

        routing {
            get("/logs") {
                try {
                    val flow = startLoggingUseCase(terminal = terminal)
                    call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
                    call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                        flow.collect { logLine ->
                            val mcpLine = McpLogLine.from(logLine)
                            val jsonStr = json.encodeToString(McpLogLine.serializer(), mcpLine)
                            write("data: $jsonStr\n\n")
                            flush()
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "MCP /logs error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            post("/logs/clear") {
                try {
                    clearLogsUseCase()
                    call.respond(mapOf("result" to "cleared"))
                } catch (e: Exception) {
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            get("/query") {
                try {
                    val query = getQueryFlowUseCase().first().orEmpty()
                    call.respond(mapOf("query" to query))
                } catch (e: Exception) {
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            post("/query") {
                try {
                    val body = call.receiveText()
                    val jsonElement = json.parseToJsonElement(body)
                    val query = (jsonElement as? JsonObject)?.get("query")?.let { el ->
                        (el as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                    } ?: ""
                    updateQueryUseCase(query)
                    call.respond(mapOf("result" to "ok", "query" to query))
                } catch (e: Exception) {
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            get("/filters") {
                try {
                    val filters = getAllEnabledFiltersFlowUseCase().first()
                    val filterObjects = filters.map { filter ->
                        buildJsonObject {
                            put("id", filter.id)
                            put("name", filter.name ?: "")
                            put("including", filter.including)
                            put("enabled", filter.enabled)
                            put("query", buildQueryFromFilter(filter))
                        }
                    }
                    call.respond(mapOf("filters" to filterObjects, "count" to filters.size))
                } catch (e: Exception) {
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            get("/tools") {
                val toolList = tools.values.map { tool ->
                    buildJsonObject {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("inputSchema", tool.inputSchema)
                    }
                }
                call.respond(mapOf("tools" to toolList))
            }

            post("/tools/{name}/call") {
                val toolName = call.parameters["name"] ?: run {
                    call.respond(mapOf("error" to "Missing tool name"))
                    return@post
                }
                val tool = tools[toolName]
                if (tool == null) {
                    call.respond(mapOf("error" to "Unknown tool: $toolName"))
                    return@post
                }

                val params: JsonObject = try { call.receive() } catch (e: Exception) { JsonObject(emptyMap()) }

                when (val result = tool.call(params)) {
                    is com.f0x1d.logfox.mcp.api.ToolResult.Value -> {
                        call.respond(mapOf("content" to result.content))
                    }
                    is com.f0x1d.logfox.mcp.api.ToolResult.Error -> {
                        call.respond(mapOf("error" to result.message))
                    }
                    is com.f0x1d.logfox.mcp.api.ToolResult.Stream -> {
                        call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
                        call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                            result.flow.collect { block ->
                                val contentJson = when (block.type) {
                                    "text" -> buildJsonObject { put("text", block.text) }
                                    "data" -> block.data ?: JsonObject(emptyMap())
                                    else -> buildJsonObject { put("text", block.text) }
                                }
                                val item = buildJsonObject {
                                    put("type", block.type)
                                    put("content", contentJson)
                                }
                                write("data: ${json.encodeToString(item)}\n\n")
                                flush()
                            }
                        }
                    }
                }
            }

            post("/mcp") {
                try {
                    val body = call.receiveText()
                    val request = json.parseToJsonElement(body).jsonObject
                    val method = request["method"]?.toString()?.trim('"') ?: ""
                    val id = request["id"]

                    when (method) {
                        "tools/list" -> {
                            val toolList = tools.values.map { tool ->
                                buildJsonObject {
                                    put("name", tool.name)
                                    put("description", tool.description)
                                    put("inputSchema", tool.inputSchema)
                                }
                            }
                            call.respond(
                                TextContent(
                                    json.encodeToString(
                                        buildJsonObject {
                                            put("jsonrpc", "2.0")
                                            put("id", id)
                                            put("result", buildJsonObject {
                                                putJsonArray("tools") { toolList.forEach { add(it) } }
                                            })
                                        },
                                    ),
                                    contentType = io.ktor.http.ContentType.Application.Json,
                                ),
                            )
                        }
                        "tools/call" -> {
                            val params = request["params"]?.jsonObject ?: JsonObject(emptyMap())
                            val toolName = params["name"]?.toString()?.trim('"') ?: ""
                            val tool = tools[toolName]
                            if (tool == null) {
                                call.respond(
                                    TextContent(
                                        json.encodeToString(
                                            buildJsonObject {
                                                put("jsonrpc", "2.0")
                                                put("id", id)
                                                put("error", buildJsonObject {
                                                    put("code", -32601)
                                                    put("message", "Tool not found: $toolName")
                                                })
                                            },
                                        ),
                                        contentType = io.ktor.http.ContentType.Application.Json,
                                    ),
                                )
                                return@post
                            }

                            val arguments = params["arguments"]?.jsonObject ?: JsonObject(emptyMap())
                            when (val result = tool.call(arguments)) {
                                is com.f0x1d.logfox.mcp.api.ToolResult.Value -> {
                                    call.respond(
                                        TextContent(
                                            json.encodeToString(
                                                buildJsonObject {
                                                    put("jsonrpc", "2.0")
                                                    put("id", id)
                                                    put("result", buildJsonObject {
                                                        putJsonArray("content") {
                                                            result.content.forEach { block ->
                                                                add(
                                                                    buildJsonObject {
                                                                        put("type", block.type)
                                                                        if (block.text != null) put("text", block.text)
                                                                        if (block.data != null) put("data", block.data)
                                                                    },
                                                                )
                                                            }
                                                        }
                                                    })
                                                },
                                            ),
                                            contentType = io.ktor.http.ContentType.Application.Json,
                                        ),
                                    )
                                }
                                is com.f0x1d.logfox.mcp.api.ToolResult.Error -> {
                                    call.respond(
                                        TextContent(
                                            json.encodeToString(
                                                buildJsonObject {
                                                    put("jsonrpc", "2.0")
                                                    put("id", id)
                                                    put("error", buildJsonObject {
                                                        put("code", -32000)
                                                        put("message", result.message)
                                                    })
                                                },
                                            ),
                                            contentType = io.ktor.http.ContentType.Application.Json,
                                        ),
                                    )
                                }
                                is com.f0x1d.logfox.mcp.api.ToolResult.Stream -> {
                                    call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
                                    call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                                        result.flow.collect { block ->
                                            val item = buildJsonObject {
                                                put("type", block.type)
                                                if (block.text != null) put("text", block.text)
                                                if (block.data != null) put("data", block.data)
                                            }
                                            write("data: ${json.encodeToString(item)}\n\n")
                                            flush()
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            call.respond(
                                TextContent(
                                    json.encodeToString(
                                        buildJsonObject {
                                            put("jsonrpc", "2.0")
                                            put("id", id)
                                            put("error", buildJsonObject {
                                                put("code", -32601)
                                                put("message", "Method not found: $method")
                                            })
                                        },
                                    ),
                                    contentType = io.ktor.http.ContentType.Application.Json,
                                ),
                            )
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "MCP JSON-RPC error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            get("/health") {
                call.respond(mapOf("status" to "ok"))
            }
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
