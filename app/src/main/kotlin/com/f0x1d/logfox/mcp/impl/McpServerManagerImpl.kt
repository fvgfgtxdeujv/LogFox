package com.f0x1d.logfox.mcp.impl

import com.f0x1d.logfox.feature.filters.api.domain.GetAllEnabledFiltersFlowUseCase
import com.f0x1d.logfox.feature.logging.api.domain.StartLoggingUseCase
import com.f0x1d.logfox.feature.logging.api.domain.ClearLogsUseCase
import com.f0x1d.logfox.feature.logging.api.domain.GetLastLogUseCase
import com.f0x1d.logfox.feature.logging.api.domain.GetQueryFlowUseCase
import com.f0x1d.logfox.feature.logging.api.domain.UpdateQueryUseCase
import com.f0x1d.logfox.mcp.api.McpServerManager
import com.f0x1d.logfox.mcp.api.McpTool
import com.f0x1d.logfox.mcp.impl.tools.ClearLogsTool
import com.f0x1d.logfox.mcp.impl.tools.GetFiltersTool
import com.f0x1d.logfox.mcp.impl.tools.GetQueryTool
import com.f0x1d.logfox.mcp.impl.tools.ReadLogsTool
import com.f0x1d.logfox.mcp.impl.tools.SetQueryTool
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpServerManagerImpl @Inject constructor(
    private val startLoggingUseCase: StartLoggingUseCase,
    private val getLastLogUseCase: GetLastLogUseCase,
    private val clearLogsUseCase: ClearLogsUseCase,
    private val getQueryFlowUseCase: GetQueryFlowUseCase,
    private val updateQueryUseCase: UpdateQueryUseCase,
    private val getAllEnabledFiltersFlowUseCase: GetAllEnabledFiltersFlowUseCase,
) : McpServerManager {

    private var server: io.ktor.server.engine.ApplicationEngine? = null

    private val json = Json { ignoreUnknownKeys = true }

    private val tools: Map<String, McpTool> by lazy {
        mapOf(
            ReadLogsTool.name to ReadLogsTool(
                startLoggingUseCase = startLoggingUseCase,
                getLastLogUseCase = getLastLogUseCase,
            ),
            SetQueryTool.name to SetQueryTool(updateQueryUseCase),
            GetQueryTool.name to GetQueryTool(getQueryFlowUseCase),
            ClearLogsTool.name to ClearLogsTool(clearLogsUseCase),
            GetFiltersTool.name to GetFiltersTool(getAllEnabledFiltersFlowUseCase),
        )
    }

    override suspend fun start(port: Int) {
        if (isRunning) {
            Timber.w("MCP server already running on port $port")
            return
        }

        server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            com.f0x1d.logfox.mcp.impl.McpRoutes(json).mcpRoutes(
                terminal = com.f0x1d.logfox.mcp.impl.McpServerDeps.selectedTerminal(),
                startLoggingUseCase = startLoggingUseCase,
                clearLogsUseCase = clearLogsUseCase,
                getQueryFlowUseCase = getQueryFlowUseCase,
                updateQueryUseCase = updateQueryUseCase,
                getAllEnabledFiltersFlowUseCase = getAllEnabledFiltersFlowUseCase,
                tools = tools,
            )
        }.start(wait = false)

        // Give the server a moment to bind
        kotlinx.coroutines.delay(500)
        Timber.i("MCP server started on port $port")
    }

    override suspend fun stop() {
        server?.stop(1000, 2000)
        server = null
        Timber.i("MCP server stopped")
    }

    override val isRunning: Boolean
        get() = server != null

    override val port: Int
        get() = server?.environment?.connectors?.firstOrNull()?.port
            ?: McpServerManager.DEFAULT_PORT
}
