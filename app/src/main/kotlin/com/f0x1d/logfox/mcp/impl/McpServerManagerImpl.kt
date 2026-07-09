package com.f0x1d.logfox.mcp.impl

import com.f0x1d.logfox.feature.database.impl.data.dao.AlertRuleDao
import com.f0x1d.logfox.feature.database.impl.data.dao.LogTagDao
import com.f0x1d.logfox.feature.database.impl.data.dao.QueryHistoryDao
import com.f0x1d.logfox.feature.filters.api.domain.GetAllEnabledFiltersFlowUseCase
import com.f0x1d.logfox.feature.logging.api.domain.ClearLogsUseCase
import com.f0x1d.logfox.feature.logging.api.domain.GetLastLogUseCase
import com.f0x1d.logfox.feature.logging.api.domain.GetLogsSnapshotUseCase
import com.f0x1d.logfox.feature.logging.api.domain.GetQueryFlowUseCase
import com.f0x1d.logfox.feature.logging.api.domain.StartLoggingUseCase
import com.f0x1d.logfox.feature.logging.api.domain.UpdateQueryUseCase
import com.f0x1d.logfox.feature.recordings.api.domain.EndRecordingUseCase
import com.f0x1d.logfox.feature.recordings.api.domain.GetAllRecordingsFlowUseCase
import com.f0x1d.logfox.feature.recordings.api.domain.GetRecordingByIdFlowUseCase
import com.f0x1d.logfox.feature.recordings.api.domain.StartRecordingUseCase
import com.f0x1d.logfox.feature.terminals.api.domain.GetSelectedTerminalUseCase
import com.f0x1d.logfox.mcp.api.McpServerManager
import com.f0x1d.logfox.mcp.api.McpTool
import com.f0x1d.logfox.mcp.impl.auth.AuthConfig
import com.f0x1d.logfox.mcp.impl.tools.ClearLogsTool
import com.f0x1d.logfox.mcp.impl.tools.ExportLogsTool
import com.f0x1d.logfox.mcp.impl.tools.GetFiltersTool
import com.f0x1d.logfox.mcp.impl.tools.GetQueryTool
import com.f0x1d.logfox.mcp.impl.tools.ReadLogsTool
import com.f0x1d.logfox.mcp.impl.tools.SearchLogsTool
import com.f0x1d.logfox.mcp.impl.tools.SetQueryTool
import com.f0x1d.logfox.mcp.impl.websocket.McpWebSocketHandler
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
    private val getLogsSnapshotUseCase: GetLogsSnapshotUseCase,
    private val getQueryFlowUseCase: GetQueryFlowUseCase,
    private val updateQueryUseCase: UpdateQueryUseCase,
    private val getAllEnabledFiltersFlowUseCase: GetAllEnabledFiltersFlowUseCase,
    private val getSelectedTerminalUseCase: GetSelectedTerminalUseCase,
    private val startRecordingUseCase: StartRecordingUseCase,
    private val endRecordingUseCase: EndRecordingUseCase,
    private val getAllRecordingsFlowUseCase: GetAllRecordingsFlowUseCase,
    private val getRecordingByIdFlowUseCase: GetRecordingByIdFlowUseCase,
    private val queryHistoryDao: QueryHistoryDao,
    private val alertRuleDao: AlertRuleDao,
    private val logTagDao: LogTagDao,
) : McpServerManager {

    private var server: Any? = null
    private var currentPort = McpServerManager.DEFAULT_PORT
    private var currentHost = McpServerManager.DEFAULT_HOST

    private val json = Json { ignoreUnknownKeys = true }

    private var authConfig = AuthConfig(enabled = false, apiKey = null)

    private val webSocketHandler: McpWebSocketHandler by lazy {
        McpWebSocketHandler(
            clearLogsUseCase = clearLogsUseCase,
            updateQueryUseCase = updateQueryUseCase,
            startRecordingUseCase = startRecordingUseCase,
            endRecordingUseCase = endRecordingUseCase,
        )
    }

    fun setAuthConfig(enabled: Boolean, apiKey: String?) {
        authConfig = AuthConfig(enabled = enabled, apiKey = apiKey)
        Timber.d("$TAG Auth config updated: enabled=$enabled, hasKey=${apiKey != null}")
    }

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

        val r6 = SearchLogsTool(getLogsSnapshotUseCase)
        result[r6.name] = r6
        Timber.d("$TAG Added tool: ${r6.name}")

        val r7 = ExportLogsTool(getLogsSnapshotUseCase)
        result[r7.name] = r7
        Timber.d("$TAG Added tool: ${r7.name}")

        Timber.i("$TAG Tools map built, total ${result.size} tools")
        return result
    }

    override suspend fun start(port: Int, host: String) {
        Timber.d("$TAG Server start requested on $host:$port")

        if (isRunning) {
            Timber.w("$TAG Server already running on $currentHost:$currentPort, stopping first...")
            stop()
        }

        Timber.d("$TAG Checking if port $port is available...")
        if (!isPortAvailable(port)) {
            Timber.e("$TAG Port $port is already in use")
            throw IllegalStateException("Port $port is already in use")
        }

        Timber.d("$TAG Getting selected terminal...")
        val terminal = getSelectedTerminalUseCase()
        Timber.i("$TAG Selected terminal: ${terminal.type.key}")

        Timber.d("$TAG Creating embedded server on $host:$port...")
        try {
            server = embeddedServer(CIO, port = port, host = host) {
                Timber.d("$TAG Configuring Ktor routes...")
                com.f0x1d.logfox.mcp.impl.McpRoutes(json).mcpRoutes(
                    application = this,
                    terminal = terminal,
                    startLoggingUseCase = startLoggingUseCase,
                    clearLogsUseCase = clearLogsUseCase,
                    getLogsSnapshotUseCase = getLogsSnapshotUseCase,
                    getQueryFlowUseCase = getQueryFlowUseCase,
                    updateQueryUseCase = updateQueryUseCase,
                    getAllEnabledFiltersFlowUseCase = getAllEnabledFiltersFlowUseCase,
                    startRecordingUseCase = startRecordingUseCase,
                    endRecordingUseCase = endRecordingUseCase,
                    getAllRecordingsFlowUseCase = getAllRecordingsFlowUseCase,
                    getRecordingByIdFlowUseCase = getRecordingByIdFlowUseCase,
                    authConfig = authConfig,
                    webSocketHandler = webSocketHandler,
                    tools = tools,
                    mcpServerManager = this@McpServerManagerImpl,
                    queryHistoryDao = queryHistoryDao,
                    alertRuleDao = alertRuleDao,
                    logTagDao = logTagDao,
                )
                Timber.d("$TAG Ktor routes configured")
            }.start(wait = false)

            currentPort = port
            currentHost = host

            kotlinx.coroutines.delay(500)
            Timber.i("$TAG Server started successfully on $host:$port, isRunning=$isRunning")
        } catch (e: Exception) {
            Timber.e(e, "$TAG Failed to start server on port $port")
            server = null
            throw e
        }
    }

    private fun isPortAvailable(port: Int): Boolean {
        return try {
            val socket = java.net.ServerSocket(port)
            socket.close()
            true
        } catch (e: java.net.BindException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun stop() {
        Timber.d("$TAG Server stop requested")

        if (!isRunning) {
            Timber.w("$TAG Server not running, ignoring")
            return
        }

        Timber.d("$TAG Stopping embedded server...")
        (server as? io.ktor.server.engine.ApplicationEngine)?.stop(gracePeriodMillis = 500, timeoutMillis = 2000)
        server = null
        currentPort = McpServerManager.DEFAULT_PORT
        kotlinx.coroutines.delay(1000)
        Timber.i("$TAG Server stopped successfully")
    }

    override val isRunning: Boolean
        get() = server != null

    override val port: Int
        get() = currentPort

    override val host: String
        get() = currentHost

    override val authConfig: com.f0x1d.logfox.mcp.api.AuthConfig
        get() = com.f0x1d.logfox.mcp.api.AuthConfig(
            enabled = authConfig.enabled,
            apiKey = authConfig.apiKey,
        )
}
