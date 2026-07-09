package com.f0x1d.logfox.mcp.impl.websocket

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class WsMessage(
    val type: String,
    val data: JsonElement? = null,
)
