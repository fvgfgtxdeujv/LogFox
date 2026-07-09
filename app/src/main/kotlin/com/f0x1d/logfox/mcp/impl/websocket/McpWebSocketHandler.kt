package com.f0x1d.logfox.mcp.impl.websocket

import com.f0x1d.logfox.feature.logging.api.domain.ClearLogsUseCase
import com.f0x1d.logfox.feature.logging.api.domain.UpdateQueryUseCase
import com.f0x1d.logfox.feature.recordings.api.domain.EndRecordingUseCase
import com.f0x1d.logfox.feature.recordings.api.domain.StartRecordingUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

class McpWebSocketHandler(
    private val clearLogsUseCase: ClearLogsUseCase,
    private val updateQueryUseCase: UpdateQueryUseCase,
    private val startRecordingUseCase: StartRecordingUseCase,
    private val endRecordingUseCase: EndRecordingUseCase,
) {

    private companion object {
        private const val TAG = "[MCP-WS]"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val _events = MutableSharedFlow<WsMessage>(extraBufferCapacity = 100)
    val events = _events.asSharedFlow()

    suspend fun handleMessage(text: String) {
        try {
            val message = json.parseToJsonElement(text).jsonObject
            val type = message["type"]?.jsonPrimitive?.content ?: run {
                Timber.w("$TAG Missing message type")
                return
            }
            Timber.d("$TAG Received message: type=$type")

            when (type) {
                "set_query" -> handleSetQuery(message)
                "clear_logs" -> handleClearLogs()
                "start_recording" -> handleStartRecording()
                "stop_recording" -> handleStopRecording()
                "pong" -> {
                    Timber.d("$TAG Received pong")
                }
                else -> {
                    Timber.w("$TAG Unknown message type: $type")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG Failed to handle message")
        }
    }

    private suspend fun handleSetQuery(message: JsonObject) {
        val query = message["query"]?.jsonPrimitive?.content ?: ""
        Timber.d("$TAG Setting query: '$query'")
        updateQueryUseCase(query)
    }

    private suspend fun handleClearLogs() {
        Timber.d("$TAG Clearing logs")
        clearLogsUseCase()
    }

    private suspend fun handleStartRecording() {
        try {
            Timber.d("$TAG Starting recording")
            startRecordingUseCase()
            _events.emit(WsMessage(type = "recording_started"))
        } catch (e: Exception) {
            Timber.e(e, "$TAG Failed to start recording")
        }
    }

    private suspend fun handleStopRecording() {
        try {
            Timber.d("$TAG Stopping recording")
            val recording = endRecordingUseCase()
            if (recording != null) {
                val data = json.parseToJsonElement(
                    json.encodeToString(
                        com.f0x1d.logfox.mcp.api.model.LogRecordingInfo.serializer(),
                        com.f0x1d.logfox.mcp.api.model.LogRecordingInfo.from(recording),
                    ),
                ).jsonObject
                _events.emit(WsMessage(type = "recording_stopped", data = data))
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG Failed to stop recording")
        }
    }
}
