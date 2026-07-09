package com.f0x1d.logfox.mcp.api

interface McpServerManager {

    suspend fun start(port: Int = DEFAULT_PORT, host: String = DEFAULT_HOST)

    suspend fun stop()

    val isRunning: Boolean

    val port: Int

    val host: String

    val authConfig: AuthConfig

    companion object {
        const val DEFAULT_PORT = 8765
        const val DEFAULT_HOST = "0.0.0.0"
    }
}
