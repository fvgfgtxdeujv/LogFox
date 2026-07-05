package com.f0x1d.logfox.mcp.api

interface McpServerManager {

    suspend fun start(port: Int = DEFAULT_PORT)

    suspend fun stop()

    val isRunning: Boolean

    val port: Int

    companion object {
        const val DEFAULT_PORT = 8765
    }
}
