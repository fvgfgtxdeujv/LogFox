package com.f0x1d.logfox.mcp.impl.sse

data class SseEvent(
    val event: String? = null,
    val id: String? = null,
    val retry: Long? = null,
    val data: String,
)
