package com.f0x1d.logfox.mcp.impl

import com.f0x1d.logfox.feature.filters.api.domain.GetAllEnabledFiltersFlowUseCase
import com.f0x1d.logfox.feature.logging.api.domain.ClearLogsUseCase
import com.f0x1d.logfox.feature.logging.api.domain.GetLastLogUseCase
import com.f0x1d.logfox.feature.logging.api.domain.GetQueryFlowUseCase
import com.f0x1d.logfox.feature.logging.api.domain.StartLoggingUseCase
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

    private var server: Any? = null
    private var currentPort = McpServerManager.DEFAULT_PORT

    private val json = Json { ignoreUnknownKeys = true }

    private val tools: Map<String, McpTool> by lazy {
        buildToolsMap()
    }

    private fun buildToolsMap(): Map<String, McpTool> {
        val result = LinkedHashMap<String, McpTool>()
        val r1 = ReadLogsTool(
            startLoggingUseCase = startLoggingUseCase,
            getLastLogUseCase = getLastLogUseCase,
        )
        result[r1.name] = r1

        val r2 = SetQueryTool(updateQueryUseCase)
        result[r2.name] = r2

        val r3 = GetQueryTool(getQueryFlowUseCase)
        result[r3.name] = r3

        val r4 = ClearLogsTool(clearLogsUseCase)
        result[r4.name] = r4

        val r5 = GetFiltersTool(getAllEnabledFiltersFlowUseCase)
        result[r5.name] = r5

        return result
    }

    override suspend fun start(port: Int) {
        if (isRunning) {
            Timber.w("MCP server already running on port $port")
            return
        }

        val terminal = com.f0x1d.logfox.mcp.impl.McpServerDeps.selectedTerminal()

        server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            com.f0x1d.logfox.mcp.impl.McpRoutes(json).mcpRoutes(
                application = this,
                terminal = terminal,
                startLoggingUseCase = startLoggingUseCase,
                clearLogsUseCase = clearLogsUseCase,
                getQueryFlowUseCase = getQueryFlowUseCase,
                updateQueryUseCase = updateQueryUseCase,
                getAllEnabledFiltersFlowUseCase = getAllEnabledFiltersFlowUseCase,
                tools = tools,
            )
        }.start(wait = false)

        currentPort = port

        kotlinx.coroutines.delay(500)
        Timber.i("MCP server started on port $port")
    }

    override suspend fun stop() {
        (server as? io.ktor.server.engine.ApplicationEngine)?.stop()
        server = null
        currentPort = McpServerManager.DEFAULT_PORT
        Timber.i("MCP server stopped")
    }

    override val isRunning: Boolean
        get() = server != null

    override val port: Int
        get() = currentPort
}
