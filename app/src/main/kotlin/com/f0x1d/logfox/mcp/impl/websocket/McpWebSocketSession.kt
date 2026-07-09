package com.f0x1d.logfox.mcp.impl.websocket

import com.f0x1d.logfox.mcp.api.model.McpLogLine
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.serialization.json.Json

class McpWebSocketSession(
    val session: DefaultWebSocketServerSession,
) {

    private companion object {
        private const val TAG = "[MCP-WS]"
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun sendLog(logLine: McpLogLine) {
        val data = json.encodeToJsonElement(McpLogLine.serializer(), logLine)
        send(WsMessage(type = "log", data = data))
    }

    suspend fun send(message: WsMessage) {
        val text = json.encodeToString(WsMessage.serializer(), message)
        session.send(Frame.Text(text))
    }
}
