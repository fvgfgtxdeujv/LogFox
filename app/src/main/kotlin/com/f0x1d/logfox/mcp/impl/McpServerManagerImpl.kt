package com.f0x1d.logfox.mcp.impl

import com.f0x1d.logfox.feature.filters.api.domain.GetAllEnabledFiltersFlowUseCase
import com.f0x1d.logfox.feature.logging.api.domain.ClearLogsUseCase
import com.f0x1d.logfox.feature.logging.api.domain.GetLastLogUseCase
import com.f0x1d.logfox.feature.logging.api.domain.GetQueryFlowUseCase
import com.f0x1d.logfox.feature.logging.api.domain.StartLoggingUseCase
import com.f0x1d.logfox.feature.logging.api.domain.UpdateQueryUseCase
import com.f0x1d.logfox.feature.terminals.api.domain.GetSelectedTerminalUseCase
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

private const val TAG = "[MCP]"

@Singleton
class McpServerManagerImpl @Inject constructor(
    private val startLoggingUseCase: StartLoggingUseCase,
    private val getLastLogUseCase: GetLastLogUseCase,
    private val clearLogsUseCase: ClearLogsUseCase,
    private val getQueryFlowUseCase: GetQueryFlowUseCase,
    private val updateQueryUseCase: UpdateQueryUseCase,
    private val getAllEnabledFiltersFlowUseCase: GetAllEnabledFiltersFlowUseCase,
    private val getSelectedTerminalUseCase: GetSelectedTerminalUseCase,
) : McpServerManager {

    private var server: Any? = null
    private var currentPort = McpServerManager.DEFAULT_PORT

    private val json = Json { ignoreUnknownKeys = true }

    init {
        Timber.d("$TAG McpServerManagerImpl initialized")
    }

    private val tools: Map<String, McpTool> by lazy {
        buildToolsMap()
    }

    private fun buildToolsMap(): Map<String, McpTool> {
        Timber.d("$TAG Building tools map...")
        val result = LinkedHashMap<String, McpTool>()

        val r1 = ReadLogsTool(
            startLoggingUseCase = startLoggingUseCase,
            getLastLogUseCase = getLastLogUseCase,
            getSelectedTerminalUseCase = getSelectedTerminalUseCase,
        )
        result[r1.name] = r1
        Timber.d("$TAG Added tool: ${r1.name}")

        val r2 = SetQueryTool(updateQueryUseCase)
        result[r2.name] = r2
        Timber.d("$TAG Added tool: ${r2.name}")

        val r3 = GetQueryTool(getQueryFlowUseCase)
        result[r3.name] = r3
        Timber.d("$TAG Added tool: ${r3.name}")

        val r4 = ClearLogsTool(clearLogsUseCase)
        result[r4.name] = r4
        Timber.d("$TAG Added tool: ${r4.name}")

        val r5 = GetFiltersTool(getAllEnabledFiltersFlowUseCase)
        result[r5.name] = r5
        Timber.d("$TAG Added tool: ${r5.name}")

        Timber.i("$TAG Tools map built, total ${result.size} tools")
        return result
    }

    override suspend fun start(port: Int) {
        Timber.d("$TAG Server start requested on port $port")

        if (isRunning) {
            Timber.w("$TAG Server already running on port $currentPort, ignoring")
            return
        }

        Timber.d("$TAG Getting selected terminal...")
        val terminal = getSelectedTerminalUseCase()
        Timber.i("$TAG Selected terminal: ${terminal.name}")

        Timber.d("$TAG Creating embedded server on port $port...")
        server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            Timber.d("$TAG Configuring Ktor routes...")
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
            Timber.d("$TAG Ktor routes configured")
        }.start(wait = false)

        currentPort = port

        kotlinx.coroutines.delay(500)
        Timber.i("$TAG Server started successfully on port $port, isRunning=$isRunning")
    }

    override suspend fun stop() {
        Timber.d("$TAG Server stop requested")

        if (!isRunning) {
            Timber.w("$TAG Server not running, ignoring")
            return
        }

        Timber.d("$TAG Stopping embedded server...")
        (server as? io.ktor.server.engine.ApplicationEngine)?.stop()
        server = null
        currentPort = McpServerManager.DEFAULT_PORT
        Timber.i("$TAG Server stopped successfully")
    }

    override val isRunning: Boolean
        get() = server != null

    override val port: Int
        get() = currentPort
}
