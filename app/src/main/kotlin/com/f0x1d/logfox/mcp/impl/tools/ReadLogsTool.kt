package com.f0x1d.logfox.mcp.impl.tools

import com.f0x1d.logfox.feature.logging.api.domain.StartLoggingUseCase
import com.f0x1d.logfox.feature.logging.api.domain.GetLastLogUseCase
import com.f0x1d.logfox.feature.logging.api.model.LogLine
import com.f0x1d.logfox.feature.terminals.api.base.Terminal
import com.f0x1d.logfox.feature.terminals.api.domain.GetSelectedTerminalUseCase
import com.f0x1d.logfox.mcp.api.ContentBlock
import com.f0x1d.logfox.mcp.api.McpTool
import com.f0x1d.logfox.mcp.api.ToolResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ReadLogsTool(
    private val startLoggingUseCase: StartLoggingUseCase,
    private val getLastLogUseCase: GetLastLogUseCase,
    private val getSelectedTerminalUseCase: GetSelectedTerminalUseCase,
) : McpTool {

    override val name = "read_logs"
    override val description =
        "Read logcat logs. Returns a stream of log lines. Use mode='stream' for continuous or mode='dump' for a snapshot."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("mode", buildJsonObject {
                put("type", "string")
                put("enum", JsonArray(listOf(JsonPrimitive("stream"), JsonPrimitive("dump"))))
                put("description", "stream: continuous SSE, dump: single snapshot")
                put("default", JsonPrimitive("stream"))
            })
        })
    }

    override suspend fun call(params: JsonObject): ToolResult {
        val mode = (params["mode"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            ?: "stream"

        return when (mode) {
            "dump" -> {
                val lastLog = getLastLogUseCase()
                if (lastLog != null) {
                    ToolResult.Value(
                        content = listOf(
                            ContentBlock(
                                type = "text",
                                text = formatLogLine(lastLog),
                            ),
                        ),
                    )
                } else {
                    ToolResult.Value(
                        content = listOf(
                            ContentBlock(type = "text", text = "No logs available."),
                        ),
                    )
                }
            }
            else -> {
                val terminal = getSelectedTerminalUseCase()
                val logFlow: Flow<LogLine> = startLoggingUseCase(terminal = terminal)
                ToolResult.Stream(
                    flow = flow {
                        logFlow.collect { logLine ->
                            emit(
                                ContentBlock(
                                    type = "text",
                                    text = formatLogLine(logLine),
                                ),
                            )
                        }
                    },
                )
            }
        }
    }

    private fun formatLogLine(logLine: LogLine): String {
        return buildString {
            append(logLine.uid).append(" ")
            append(logLine.pid).append(" ")
            append(logLine.tid).append(" ")
            logLine.packageName?.let { append(it).append(" ") }
            append(logLine.level.name).append("/")
            append(logLine.tag).append(": ")
            append(logLine.content)
        }
    }
}
