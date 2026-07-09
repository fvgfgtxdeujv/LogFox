package com.f0x1d.logfox.mcp.impl

import com.f0x1d.logfox.feature.database.impl.data.dao.AlertRuleDao
import com.f0x1d.logfox.feature.database.impl.data.dao.LogTagDao
import com.f0x1d.logfox.feature.database.impl.data.dao.QueryHistoryDao
import com.f0x1d.logfox.feature.database.impl.entity.AlertRuleRoomEntity
import com.f0x1d.logfox.feature.database.impl.entity.LogTagRoomEntity
import com.f0x1d.logfox.feature.database.impl.entity.QueryHistoryRoomEntity
import com.f0x1d.logfox.feature.filters.api.domain.GetAllEnabledFiltersFlowUseCase
import com.f0x1d.logfox.feature.logging.api.domain.StartLoggingUseCase
import com.f0x1d.logfox.feature.logging.api.domain.ClearLogsUseCase
import com.f0x1d.logfox.feature.logging.api.domain.GetLogsSnapshotUseCase
import com.f0x1d.logfox.feature.logging.api.domain.GetQueryFlowUseCase
import com.f0x1d.logfox.feature.logging.api.domain.UpdateQueryUseCase
import com.f0x1d.logfox.feature.recordings.api.domain.EndRecordingUseCase
import com.f0x1d.logfox.feature.recordings.api.domain.GetAllRecordingsFlowUseCase
import com.f0x1d.logfox.feature.recordings.api.domain.GetRecordingByIdFlowUseCase
import com.f0x1d.logfox.feature.recordings.api.domain.StartRecordingUseCase
import com.f0x1d.logfox.mcp.api.McpServerManager
import com.f0x1d.logfox.mcp.api.McpTool
import com.f0x1d.logfox.mcp.api.model.ExportRequest
import com.f0x1d.logfox.mcp.api.model.LogRecordingInfo
import com.f0x1d.logfox.mcp.api.model.McpLogLine
import com.f0x1d.logfox.feature.logging.api.model.LogLevel
import com.f0x1d.logfox.mcp.api.model.BatchRequest
import com.f0x1d.logfox.mcp.api.model.SearchRequest
import com.f0x1d.logfox.mcp.api.model.SearchResponse
import com.f0x1d.logfox.mcp.impl.auth.AuthConfig
import com.f0x1d.logfox.mcp.impl.tools.ClearLogsTool
import com.f0x1d.logfox.mcp.impl.websocket.McpWebSocketHandler
import com.f0x1d.logfox.mcp.impl.websocket.McpWebSocketSession
import com.f0x1d.logfox.mcp.impl.websocket.WsMessage
import com.f0x1d.logfox.mcp.impl.tools.GetFiltersTool
import com.f0x1d.logfox.mcp.impl.tools.GetQueryTool
import com.f0x1d.logfox.mcp.impl.tools.ReadLogsTool
import com.f0x1d.logfox.mcp.impl.tools.SetQueryTool
import com.f0x1d.logfox.feature.terminals.api.base.Terminal
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class McpRoutes(private val json: Json) {

    private companion object {
        private const val TAG = "[MCP]"
    }

    fun mcpRoutes(
        application: Application,
        terminal: Terminal,
        startLoggingUseCase: StartLoggingUseCase,
        clearLogsUseCase: ClearLogsUseCase,
        getLogsSnapshotUseCase: GetLogsSnapshotUseCase,
        getQueryFlowUseCase: GetQueryFlowUseCase,
        updateQueryUseCase: UpdateQueryUseCase,
        getAllEnabledFiltersFlowUseCase: GetAllEnabledFiltersFlowUseCase,
        startRecordingUseCase: StartRecordingUseCase,
        endRecordingUseCase: EndRecordingUseCase,
        getAllRecordingsFlowUseCase: GetAllRecordingsFlowUseCase,
        getRecordingByIdFlowUseCase: GetRecordingByIdFlowUseCase,
        authConfig: AuthConfig,
        webSocketHandler: McpWebSocketHandler,
        tools: Map<String, McpTool>,
        mcpServerManager: McpServerManager? = null,
        queryHistoryDao: QueryHistoryDao? = null,
        alertRuleDao: AlertRuleDao? = null,
        logTagDao: LogTagDao? = null,
    ) {
        Timber.i("$TAG Configuring routes, terminal=${terminal.type.key}, authEnabled=${authConfig.enabled}, tools=${tools.keys.joinToString()}")

        application.install(ContentNegotiation) {
            json(json)
            Timber.d("$TAG Installed ContentNegotiation plugin")
        }

        application.install(WebSockets) {
            Timber.d("$TAG Installed WebSockets plugin")
        }

        if (authConfig.enabled && authConfig.apiKey != null) {
            val apiKey = authConfig.apiKey
            application.intercept(ApplicationCallPipeline.Plugins) {
                val path = call.request.uri
                val skipAuth = path == "/health" || path == "/help"
                if (!skipAuth) {
                    val providedKey = call.request.headers["X-API-Key"]
                    if (providedKey == null || providedKey != apiKey) {
                        Timber.w("$TAG Auth failed for path=$path, key provided=${providedKey != null}")
                        call.response.status(HttpStatusCode.Unauthorized)
                        call.respond(mapOf("error" to "Unauthorized"))
                        finish()
                    }
                }
            }
            Timber.i("$TAG API Key authentication enabled")
        }

        application.routing {

            get("/logs") {
                Timber.i("$TAG Received GET /logs request")
                try {
                    Timber.d("$TAG Starting logging with terminal: ${terminal.type.key}")
                    val flow = startLoggingUseCase(terminal = terminal)
                    Timber.d("$TAG Got log flow, streaming...")
                    call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
                    call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                        flow.collect { logLine ->
                            val mcpLine = McpLogLine.from(logLine)
                            val jsonStr = json.encodeToString(McpLogLine.serializer(), mcpLine)
                            write("data: $jsonStr\n\n")
                            flush()
                        }
                    }
                    Timber.d("$TAG /logs stream completed")
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /logs error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            get("/logs/tail") {
                Timber.i("$TAG Received GET /logs/tail request")
                try {
                    val linesParam = call.parameters["lines"]?.toIntOrNull() ?: 100
                    val follow = call.parameters["follow"]?.toBooleanStrictOrNull() ?: false

                    Timber.d("$TAG tail: lines=$linesParam, follow=$follow")

                    val snapshot = getLogsSnapshotUseCase()
                    val tailLogs = snapshot.takeLast(linesParam)

                    call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
                    call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                        tailLogs.forEach { logLine ->
                            val mcpLine = McpLogLine.from(logLine)
                            val jsonStr = json.encodeToString(McpLogLine.serializer(), mcpLine)
                            write("data: $jsonStr\n\n")
                            flush()
                        }

                        if (follow) {
                            Timber.d("$TAG Starting live tail stream")
                            val flow = startLoggingUseCase(terminal = terminal)
                            flow.collect { logLine ->
                                val mcpLine = McpLogLine.from(logLine)
                                val jsonStr = json.encodeToString(McpLogLine.serializer(), mcpLine)
                                write("data: $jsonStr\n\n")
                                flush()
                            }
                        }
                    }

                    Timber.d("$TAG /logs/tail completed")
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /logs/tail error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            post("/logs/clear") {
                Timber.i("$TAG Received POST /logs/clear request")
                try {
                    clearLogsUseCase()
                    Timber.d("$TAG Logs cleared successfully")
                    call.respond(mapOf("result" to "cleared"))
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /logs/clear error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            get("/logs/stats") {
                Timber.i("$TAG Received GET /logs/stats request")
                try {
                    val packageNameFilter = call.parameters["package_name"]
                    val tagFilter = call.parameters["tag"]

                    val allLogs = getLogsSnapshotUseCase()
                    Timber.d("$TAG Total logs in snapshot: ${allLogs.size}")

                    val filtered = allLogs.filter { logLine ->
                        val pkgMatch = packageNameFilter?.let { logLine.packageName?.contains(it, ignoreCase = true) ?: false } ?: true
                        val tagMatch = tagFilter?.let { logLine.tag.contains(it, ignoreCase = true) } ?: true
                        pkgMatch && tagMatch
                    }

                    val levelCounts = mutableMapOf<String, Int>().apply {
                        LogLevel.values().filter { it != LogLevel.SILENT }.forEach { put(it.letter, 0) }
                    }

                    var lastUpdated = 0L
                    filtered.forEach { logLine ->
                        levelCounts[logLine.level.letter] = levelCounts.getOrDefault(logLine.level.letter, 0) + 1
                        if (logLine.dateAndTime > lastUpdated) {
                            lastUpdated = logLine.dateAndTime
                        }
                    }

                    val response = buildJsonObject {
                        put("total", filtered.size)
                        putJsonObject("levels") {
                            levelCounts.forEach { (level, count) ->
                                put(level, count)
                            }
                        }
                        put("lastUpdated", lastUpdated)
                    }

                    Timber.d("$TAG Stats calculated: total=${filtered.size}, levels=$levelCounts, lastUpdated=$lastUpdated")
                    call.respond(response)
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /logs/stats error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            post("/logs/batch") {
                Timber.i("$TAG Received POST /logs/batch request")
                try {
                    val request = call.receive<BatchRequest>()
                    Timber.d("$TAG Batch operation=${request.operation}, limit=${request.limit}")

                    val allLogs = getLogsSnapshotUseCase()

                    val filtered = allLogs.filter { logLine ->
                        val include = request.include
                        val exclude = request.exclude
                        val levels = request.levels

                        val includeCaseSensitive = include?.caseSensitive ?: false
                        val excludeCaseSensitive = exclude?.caseSensitive ?: false

                        val includeUid = include?.uid
                        val includePid = include?.pid
                        val includeTid = include?.tid
                        val includePackageName = include?.packageName
                        val includeTag = include?.tag
                        val includeContent = include?.content

                        val excludeUid = exclude?.uid
                        val excludePid = exclude?.pid
                        val excludeTid = exclude?.tid
                        val excludePackageName = exclude?.packageName
                        val excludeTag = exclude?.tag
                        val excludeContent = exclude?.content

                        val uidMatch = includeUid?.let { logLine.uid.contains(it, ignoreCase = !includeCaseSensitive) } ?: true
                        val pidMatch = includePid?.let { logLine.pid.contains(it, ignoreCase = !includeCaseSensitive) } ?: true
                        val tidMatch = includeTid?.let { logLine.tid.contains(it, ignoreCase = !includeCaseSensitive) } ?: true
                        val pkgMatch = includePackageName?.let { logLine.packageName?.contains(it, ignoreCase = !includeCaseSensitive) ?: false } ?: true
                        val tagMatch = includeTag?.let { logLine.tag.contains(it, ignoreCase = !includeCaseSensitive) } ?: true
                        val contentMatch = includeContent?.let { logLine.content.contains(it, ignoreCase = !includeCaseSensitive) } ?: true

                        val uidExclude = excludeUid?.let { !logLine.uid.contains(it, ignoreCase = !excludeCaseSensitive) } ?: true
                        val pidExclude = excludePid?.let { !logLine.pid.contains(it, ignoreCase = !excludeCaseSensitive) } ?: true
                        val tidExclude = excludeTid?.let { !logLine.tid.contains(it, ignoreCase = !excludeCaseSensitive) } ?: true
                        val pkgExclude = excludePackageName?.let { !(logLine.packageName?.contains(it, ignoreCase = !excludeCaseSensitive) ?: false) } ?: true
                        val tagExclude = excludeTag?.let { !logLine.tag.contains(it, ignoreCase = !excludeCaseSensitive) } ?: true
                        val contentExclude = excludeContent?.let { !logLine.content.contains(it, ignoreCase = !excludeCaseSensitive) } ?: true

                        val levelMatch = levels?.let { it.contains(logLine.level.letter, ignoreCase = true) } ?: true

                        uidMatch && pidMatch && tidMatch && pkgMatch && tagMatch && contentMatch &&
                        uidExclude && pidExclude && tidExclude && pkgExclude && tagExclude && contentExclude &&
                        levelMatch
                    }.take(request.limit)

                    when (request.operation.lowercase()) {
                        "delete" -> {
                            val count = filtered.size
                            clearLogsUseCase()
                            Timber.d("$TAG Batch delete completed: $count logs deleted")
                            call.respond(buildJsonObject {
                                put("operation", "delete")
                                put("count", count)
                                put("result", "success")
                            })
                        }
                        "export" -> {
                            val lineCount = filtered.size

                            when (request.format.lowercase()) {
                                "csv" -> {
                                    val content = buildString {
                                        appendLine("id,dateAndTime,uid,pid,tid,packageName,level,tag,content")
                                        filtered.forEach { logLine ->
                                            val mcpLine = McpLogLine.from(logLine)
                                            appendLine("${mcpLine.id},${mcpLine.dateAndTime},${mcpLine.uid},${mcpLine.pid},${mcpLine.tid},${mcpLine.packageName ?: ""},${mcpLine.level.name},${mcpLine.tag},${mcpLine.content.replace(",", "\\,").replace("\n", "\\n")}")
                                        }
                                    }
                                    Timber.d("$TAG Batch export completed: $lineCount logs, format=csv")
                                    call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=\"logs_${System.currentTimeMillis()}.csv\"")
                                    call.respondText(content, contentType = ContentType.Text.Csv)
                                }
                                "xml" -> {
                                    val content = buildString {
                                        appendLine("<logs>")
                                        appendLine("  <total>$lineCount</total>")
                                        filtered.forEach { logLine ->
                                            val mcpLine = McpLogLine.from(logLine)
                                            appendLine("  <log>")
                                            appendLine("    <id>${mcpLine.id}</id>")
                                            appendLine("    <dateAndTime>${mcpLine.dateAndTime}</dateAndTime>")
                                            appendLine("    <uid>${mcpLine.uid}</uid>")
                                            appendLine("    <pid>${mcpLine.pid}</pid>")
                                            appendLine("    <tid>${mcpLine.tid}</tid>")
                                            appendLine("    <packageName>${mcpLine.packageName ?: ""}</packageName>")
                                            appendLine("    <level>${mcpLine.level.name}</level>")
                                            appendLine("    <tag>${mcpLine.tag}</tag>")
                                            appendLine("    <content>${mcpLine.content.replace("<", "&lt;").replace(">", "&gt;").replace("&", "&amp;")}</content>")
                                            appendLine("  </log>")
                                        }
                                        appendLine("</logs>")
                                    }
                                    Timber.d("$TAG Batch export completed: $lineCount logs, format=xml")
                                    call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=\"logs_${System.currentTimeMillis()}.xml\"")
                                    call.respondText(content, contentType = ContentType.Text.Xml)
                                }
                                else -> {
                                    val sdf = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
                                    val content = filtered.joinToString("\n") { logLine ->
                                        val timeStr = sdf.format(Date(logLine.dateAndTime))
                                        "$timeStr ${logLine.level.letter}/${logLine.tag}: ${logLine.content}"
                                    }
                                    val fileSize = content.toByteArray().size
                                    Timber.d("$TAG Batch export completed: $lineCount logs, $fileSize bytes, format=txt")
                                    call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=\"logs_${System.currentTimeMillis()}.txt\"")
                                    call.respondText(content, contentType = ContentType.Text.Plain)
                                }
                            }
                        }
                        else -> {
                            Timber.w("$TAG Unknown batch operation: ${request.operation}")
                            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Unknown operation: ${request.operation}"))
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /logs/batch error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            post("/logs/search") {
                Timber.i("$TAG Received POST /logs/search request")
                try {
                    val request = call.receive<SearchRequest>()
                    Timber.d("$TAG Search include=${request.include}, exclude=${request.exclude}, levels=${request.levels}, limit=${request.limit}, offset=${request.offset}")

                    val allLogs = getLogsSnapshotUseCase()
                    Timber.d("$TAG Total logs in snapshot: ${allLogs.size}")

                    val filtered = allLogs.filter { logLine ->
                        val include = request.include
                        val exclude = request.exclude
                        val levels = request.levels

                        val includeCaseSensitive = include?.caseSensitive ?: false
                        val excludeCaseSensitive = exclude?.caseSensitive ?: false

                        val includeUid = include?.uid
                        val includePid = include?.pid
                        val includeTid = include?.tid
                        val includePackageName = include?.packageName
                        val includeTag = include?.tag
                        val includeContent = include?.content

                        val excludeUid = exclude?.uid
                        val excludePid = exclude?.pid
                        val excludeTid = exclude?.tid
                        val excludePackageName = exclude?.packageName
                        val excludeTag = exclude?.tag
                        val excludeContent = exclude?.content

                        val uidMatch = includeUid?.let { logLine.uid.contains(it, ignoreCase = !includeCaseSensitive) } ?: true
                        val pidMatch = includePid?.let { logLine.pid.contains(it, ignoreCase = !includeCaseSensitive) } ?: true
                        val tidMatch = includeTid?.let { logLine.tid.contains(it, ignoreCase = !includeCaseSensitive) } ?: true
                        val pkgMatch = includePackageName?.let { logLine.packageName?.contains(it, ignoreCase = !includeCaseSensitive) ?: false } ?: true
                        val tagMatch = includeTag?.let { logLine.tag.contains(it, ignoreCase = !includeCaseSensitive) } ?: true
                        val contentMatch = includeContent?.let { logLine.content.contains(it, ignoreCase = !includeCaseSensitive) } ?: true

                        val uidExclude = excludeUid?.let { !logLine.uid.contains(it, ignoreCase = !excludeCaseSensitive) } ?: true
                        val pidExclude = excludePid?.let { !logLine.pid.contains(it, ignoreCase = !excludeCaseSensitive) } ?: true
                        val tidExclude = excludeTid?.let { !logLine.tid.contains(it, ignoreCase = !excludeCaseSensitive) } ?: true
                        val pkgExclude = excludePackageName?.let { !(logLine.packageName?.contains(it, ignoreCase = !excludeCaseSensitive) ?: false) } ?: true
                        val tagExclude = excludeTag?.let { !logLine.tag.contains(it, ignoreCase = !excludeCaseSensitive) } ?: true
                        val contentExclude = excludeContent?.let { !logLine.content.contains(it, ignoreCase = !excludeCaseSensitive) } ?: true

                        val levelMatch = levels?.let { it.contains(logLine.level.letter, ignoreCase = true) } ?: true

                        uidMatch && pidMatch && tidMatch && pkgMatch && tagMatch && contentMatch &&
                        uidExclude && pidExclude && tidExclude && pkgExclude && tagExclude && contentExclude &&
                        levelMatch
                    }

                    val paged = filtered.drop(request.offset).take(request.limit)
                    val mcpLines = paged.map { McpLogLine.from(it) }

                    Timber.d("$TAG Search found ${filtered.size} matches, returning ${mcpLines.size}, format=${request.format}")

                    when (request.format.lowercase()) {
                        "csv" -> {
                            val csvContent = buildString {
                                appendLine("id,dateAndTime,uid,pid,tid,packageName,level,tag,content")
                                mcpLines.forEach { line ->
                                    appendLine("${line.id},${line.dateAndTime},${line.uid},${line.pid},${line.tid},${line.packageName ?: ""},${line.level.name},${line.tag},${line.content.replace(",", "\\,").replace("\n", "\\n")}")
                                }
                            }
                            call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=\"logs_search_${System.currentTimeMillis()}.csv\"")
                            call.respondText(csvContent, contentType = ContentType.Text.Csv)
                        }
                        "xml" -> {
                            val xmlContent = buildString {
                                appendLine("<logs>")
                                appendLine("  <total>${filtered.size}</total>")
                                appendLine("  <limit>${request.limit}</limit>")
                                appendLine("  <offset>${request.offset}</offset>")
                                mcpLines.forEach { line ->
                                    appendLine("  <log>")
                                    appendLine("    <id>${line.id}</id>")
                                    appendLine("    <dateAndTime>${line.dateAndTime}</dateAndTime>")
                                    appendLine("    <uid>${line.uid}</uid>")
                                    appendLine("    <pid>${line.pid}</pid>")
                                    appendLine("    <tid>${line.tid}</tid>")
                                    appendLine("    <packageName>${line.packageName ?: ""}</packageName>")
                                    appendLine("    <level>${line.level.name}</level>")
                                    appendLine("    <tag>${line.tag}</tag>")
                                    appendLine("    <content>${line.content.replace("<", "&lt;").replace(">", "&gt;").replace("&", "&amp;")}</content>")
                                    appendLine("  </log>")
                                }
                                appendLine("</logs>")
                            }
                            call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=\"logs_search_${System.currentTimeMillis()}.xml\"")
                            call.respondText(xmlContent, contentType = ContentType.Text.Xml)
                        }
                        else -> {
                            call.respond(
                                SearchResponse(
                                    results = mcpLines,
                                    total = filtered.size,
                                    limit = request.limit,
                                    offset = request.offset,
                                ),
                            )
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /logs/search error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            post("/logs/export") {
                Timber.i("$TAG Received POST /logs/export request")
                try {
                    val request = call.receive<ExportRequest>()
                    Timber.d("$TAG Export format='${request.format}', include=${request.include}, exclude=${request.exclude}, levels=${request.levels}, limit=${request.limit}")

                    val allLogs = getLogsSnapshotUseCase()
                    Timber.d("$TAG Total logs in snapshot: ${allLogs.size}")

                    val filtered = allLogs.filter { logLine ->
                        val include = request.include
                        val exclude = request.exclude
                        val levels = request.levels

                        val includeCaseSensitive = include?.caseSensitive ?: false
                        val excludeCaseSensitive = exclude?.caseSensitive ?: false

                        val includeUid = include?.uid
                        val includePid = include?.pid
                        val includeTid = include?.tid
                        val includePackageName = include?.packageName
                        val includeTag = include?.tag
                        val includeContent = include?.content

                        val excludeUid = exclude?.uid
                        val excludePid = exclude?.pid
                        val excludeTid = exclude?.tid
                        val excludePackageName = exclude?.packageName
                        val excludeTag = exclude?.tag
                        val excludeContent = exclude?.content

                        val uidMatch = includeUid?.let { logLine.uid.contains(it, ignoreCase = !includeCaseSensitive) } ?: true
                        val pidMatch = includePid?.let { logLine.pid.contains(it, ignoreCase = !includeCaseSensitive) } ?: true
                        val tidMatch = includeTid?.let { logLine.tid.contains(it, ignoreCase = !includeCaseSensitive) } ?: true
                        val pkgMatch = includePackageName?.let { logLine.packageName?.contains(it, ignoreCase = !includeCaseSensitive) ?: false } ?: true
                        val tagMatch = includeTag?.let { logLine.tag.contains(it, ignoreCase = !includeCaseSensitive) } ?: true
                        val contentMatch = includeContent?.let { logLine.content.contains(it, ignoreCase = !includeCaseSensitive) } ?: true

                        val uidExclude = excludeUid?.let { !logLine.uid.contains(it, ignoreCase = !excludeCaseSensitive) } ?: true
                        val pidExclude = excludePid?.let { !logLine.pid.contains(it, ignoreCase = !excludeCaseSensitive) } ?: true
                        val tidExclude = excludeTid?.let { !logLine.tid.contains(it, ignoreCase = !excludeCaseSensitive) } ?: true
                        val pkgExclude = excludePackageName?.let { !(logLine.packageName?.contains(it, ignoreCase = !excludeCaseSensitive) ?: false) } ?: true
                        val tagExclude = excludeTag?.let { !logLine.tag.contains(it, ignoreCase = !excludeCaseSensitive) } ?: true
                        val contentExclude = excludeContent?.let { !logLine.content.contains(it, ignoreCase = !excludeCaseSensitive) } ?: true

                        val levelMatch = levels?.let { it.contains(logLine.level.letter, ignoreCase = true) } ?: true

                        uidMatch && pidMatch && tidMatch && pkgMatch && tagMatch && contentMatch &&
                        uidExclude && pidExclude && tidExclude && pkgExclude && tagExclude && contentExclude &&
                        levelMatch
                    }.take(request.limit)

                    val sdf = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
                    val content = filtered.joinToString("\n") { logLine ->
                        val timeStr = sdf.format(Date(logLine.dateAndTime))
                        "$timeStr ${logLine.level.letter}/${logLine.tag}: ${logLine.content}"
                    }

                    val fileName = "logs_${System.currentTimeMillis()}.${request.format}"
                    Timber.d("$TAG Exporting ${filtered.size} lines, file=$fileName, size=${content.toByteArray().size} bytes")

                    call.response.headers.append(
                        HttpHeaders.ContentDisposition,
                        "attachment; filename=\"$fileName\"",
                    )
                    call.respondText(
                        text = content,
                        contentType = ContentType.Text.Plain,
                        status = HttpStatusCode.OK,
                    )
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /logs/export error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            get("/query") {
                Timber.i("$TAG Received GET /query request")
                try {
                    val query = getQueryFlowUseCase().first().orEmpty()
                    Timber.d("$TAG Current query: '$query'")
                    call.respond(mapOf("query" to query))
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /query error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            post("/query/set") {
                Timber.i("$TAG Received POST /query/set request")
                try {
                    val body = call.receiveText()
                    val jsonElement = json.parseToJsonElement(body)
                    val query = (jsonElement as? JsonObject)?.get("query")?.let { el ->
                        (el as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                    } ?: ""
                    Timber.d("$TAG Setting query to: '$query'")
                    updateQueryUseCase(query)
                    call.respond(mapOf("result" to "ok", "query" to query))
                } catch (e: Exception) {
                    Timber.e(e, "$TAG POST /query/set error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            get("/filters") {
                Timber.i("$TAG Received GET /filters request")
                try {
                    val filters = getAllEnabledFiltersFlowUseCase().first()
                    Timber.d("$TAG Found ${filters.size} enabled filters")
                    val filterObjects = filters.map { filter ->
                        buildJsonObject {
                            put("id", filter.id)
                            put("name", filter.name ?: "")
                            put("including", filter.including)
                            put("enabled", filter.enabled)
                            put("query", buildQueryFromFilter(filter))
                        }
                    }
                    call.respond(mapOf("filters" to filterObjects, "count" to filters.size))
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /filters error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            post("/record/start") {
                Timber.i("$TAG Received POST /record/start request")
                try {
                    startRecordingUseCase()
                    Timber.d("$TAG Recording started")
                    call.respond(mapOf("status" to "started", "message" to "Recording started"))
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /record/start error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            post("/record/stop") {
                Timber.i("$TAG Received POST /record/stop request")
                try {
                    val recording = endRecordingUseCase()
                    if (recording != null) {
                        val info = LogRecordingInfo.from(recording)
                        Timber.d("$TAG Recording stopped: ${recording.title}")
                        call.respond(mapOf("status" to "stopped", "recording" to info))
                    } else {
                        Timber.w("$TAG No active recording to stop")
                        call.respond(mapOf("status" to "no_recording", "message" to "No active recording"))
                    }
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /record/stop error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            get("/record/list") {
                Timber.i("$TAG Received GET /record/list request")
                try {
                    val recordings = getAllRecordingsFlowUseCase().first()
                    val infoList = recordings.map { LogRecordingInfo.from(it) }
                    Timber.d("$TAG Found ${infoList.size} recordings")
                    call.respond(mapOf("recordings" to infoList, "count" to infoList.size))
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /record/list error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            get("/record/{id}") {
                val idStr = call.parameters["id"]
                Timber.i("$TAG Received GET /record/$idStr request")
                try {
                    val id = idStr?.toLongOrNull() ?: run {
                        call.respond(mapOf("error" to "Invalid recording id"))
                        return@get
                    }
                    val recording = getRecordingByIdFlowUseCase(id).first()
                    if (recording != null) {
                        val info = LogRecordingInfo.from(recording)
                        Timber.d("$TAG Found recording: ${recording.title}")
                        call.respond(info)
                    } else {
                        Timber.w("$TAG Recording not found: $id")
                        call.respond(mapOf("error" to "Recording not found"))
                    }
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /record/{id} error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            get("/tools") {
                Timber.i("$TAG Received GET /tools request, returning ${tools.size} tools")
                val toolList = tools.values.map { tool ->
                    buildJsonObject {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("inputSchema", tool.inputSchema)
                    }
                }
                call.respond(mapOf("tools" to toolList))
            }

            post("/tools/{name}/call") {
                val toolName = call.parameters["name"] ?: run {
                    Timber.w("$TAG POST /tools/call without tool name")
                    call.respond(mapOf("error" to "Missing tool name"))
                    return@post
                }
                Timber.i("$TAG Received POST /tools/$toolName/call request")
                val tool = tools[toolName]
                if (tool == null) {
                    Timber.w("$TAG Unknown tool: $toolName")
                    call.respond(mapOf("error" to "Unknown tool: $toolName"))
                    return@post
                }

                val params: JsonObject = try { call.receive() } catch (e: Exception) {
                    Timber.w("$TAG Failed to parse params, using empty: ${e.message}")
                    JsonObject(emptyMap())
                }

                Timber.d("$TAG Calling tool $toolName with params: $params")
                when (val result = tool.call(params)) {
                    is com.f0x1d.logfox.mcp.api.ToolResult.Value -> {
                        Timber.d("$TAG Tool $toolName returned Value result")
                        call.respond(mapOf("content" to result.content))
                    }
                    is com.f0x1d.logfox.mcp.api.ToolResult.Error -> {
                        Timber.e("$TAG Tool $toolName returned Error: ${result.message}")
                        call.respond(mapOf("error" to result.message))
                    }
                    is com.f0x1d.logfox.mcp.api.ToolResult.Stream -> {
                        Timber.d("$TAG Tool $toolName returned Stream result")
                        call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
                        call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                            result.flow.collect { block ->
                                val contentJson = when (block.type) {
                                    "text" -> buildJsonObject { put("text", block.text) }
                                    "data" -> block.data ?: JsonObject(emptyMap())
                                    else -> buildJsonObject { put("text", block.text) }
                                }
                                val item = buildJsonObject {
                                    put("type", block.type)
                                    put("content", contentJson)
                                }
                                write("data: ${json.encodeToString(item)}\n\n")
                                flush()
                            }
                        }
                    }
                }
            }

            post("/mcp") {
                try {
                    val body = call.receiveText()
                    val request = json.parseToJsonElement(body).jsonObject
                    val method = request["method"]?.toString()?.trim('"') ?: ""
                    val id = request["id"] ?: JsonNull
                    Timber.i("$TAG Received JSON-RPC request: method=$method, id=$id")

                    when (method) {
                        "server/discover" -> {
                            Timber.d("$TAG Processing server/discover")
                            call.respond(
                                TextContent(
                                    json.encodeToString(
                                        buildJsonObject {
                                            put("jsonrpc", "2.0")
                                            put("id", id)
                                            put("result", buildJsonObject {
                                                put("name", "LogFox MCP Server")
                                                put("version", "1.0.0")
                                                put("description", "LogCat reader MCP server for Android")
                                                putJsonArray("protocolVersions") {
                                                    add("2025-11-25")
                                                    add("2026-07-28")
                                                }
                                                put("capabilities", buildJsonObject {
                                                    put("tools", buildJsonObject {})
                                                })
                                            })
                                        },
                                    ),
                                    contentType = io.ktor.http.ContentType.Application.Json,
                                ),
                            )
                        }
                        "tools/list" -> {
                            Timber.d("$TAG Processing tools/list")
                            val toolList = tools.values.map { tool ->
                                buildJsonObject {
                                    put("name", tool.name)
                                    put("description", tool.description)
                                    put("inputSchema", tool.inputSchema)
                                }
                            }
                            call.respond(
                                TextContent(
                                    json.encodeToString(
                                        buildJsonObject {
                                            put("jsonrpc", "2.0")
                                            put("id", id)
                                            put("result", buildJsonObject {
                                                putJsonArray("tools") { toolList.forEach { add(it) } }
                                            })
                                        },
                                    ),
                                    contentType = io.ktor.http.ContentType.Application.Json,
                                ),
                            )
                        }
                        "tools/call" -> {
                            val params = request["params"]?.jsonObject ?: JsonObject(emptyMap())
                            val toolName = params["name"]?.toString()?.trim('"') ?: ""
                            Timber.d("$TAG Processing tools/call: $toolName")
                            val tool = tools[toolName]
                            if (tool == null) {
                                Timber.w("$TAG Tool not found: $toolName")
                                call.respond(
                                    TextContent(
                                        json.encodeToString(
                                            buildJsonObject {
                                                put("jsonrpc", "2.0")
                                                put("id", id)
                                                put("error", buildJsonObject {
                                                    put("code", -32601)
                                                    put("message", "Tool not found: $toolName")
                                                })
                                            },
                                        ),
                                        contentType = io.ktor.http.ContentType.Application.Json,
                                    ),
                                )
                                return@post
                            }

                            val arguments = params["arguments"]?.jsonObject ?: JsonObject(emptyMap())
                            when (val result = tool.call(arguments)) {
                                is com.f0x1d.logfox.mcp.api.ToolResult.Value -> {
                                    Timber.d("$TAG tools/call returned Value")
                                    call.respond(
                                        TextContent(
                                            json.encodeToString(
                                                buildJsonObject {
                                                    put("jsonrpc", "2.0")
                                                    put("id", id)
                                                    put("result", buildJsonObject {
                                                        put("resultType", "complete")
                                                        putJsonArray("content") {
                                                            result.content.forEach { block ->
                                                                add(
                                                                    buildJsonObject {
                                                                        put("type", block.type)
                                                                        if (block.text != null) put("text", block.text)
                                                                        if (block.data != null) put("data", block.data)
                                                                    },
                                                                )
                                                            }
                                                        }
                                                    })
                                                },
                                            ),
                                            contentType = io.ktor.http.ContentType.Application.Json,
                                        ),
                                    )
                                }
                                is com.f0x1d.logfox.mcp.api.ToolResult.Error -> {
                                    Timber.e("$TAG tools/call returned Error: ${result.message}")
                                    call.respond(
                                        TextContent(
                                            json.encodeToString(
                                                buildJsonObject {
                                                    put("jsonrpc", "2.0")
                                                    put("id", id)
                                                    put("error", buildJsonObject {
                                                        put("code", -32000)
                                                        put("message", result.message)
                                                    })
                                                },
                                            ),
                                            contentType = io.ktor.http.ContentType.Application.Json,
                                        ),
                                    )
                                }
                                is com.f0x1d.logfox.mcp.api.ToolResult.Stream -> {
                                    Timber.d("$TAG tools/call returned Stream")
                                    call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
                                    call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                                        result.flow.collect { block ->
                                            val item = buildJsonObject {
                                                put("type", block.type)
                                                if (block.text != null) put("text", block.text)
                                                if (block.data != null) put("data", block.data)
                                            }
                                            write("data: ${json.encodeToString(item)}\n\n")
                                            flush()
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            Timber.w("$TAG Unknown JSON-RPC method: $method")
                            call.respond(
                                TextContent(
                                    json.encodeToString(
                                        buildJsonObject {
                                            put("jsonrpc", "2.0")
                                            put("id", id)
                                            put("error", buildJsonObject {
                                                put("code", -32601)
                                                put("message", "Method not found: $method")
                                            })
                                        },
                                    ),
                                    contentType = io.ktor.http.ContentType.Application.Json,
                                ),
                            )
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "$TAG JSON-RPC error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            get("/help") {
                Timber.i("$TAG Received GET /help request")
                val helpDoc = buildJsonObject {
                    put("server", "LogFox MCP Server")
                    put("port", 8765)
                    put("description", "LogFox MCP Server API documentation")
                    putJsonArray("endpoints") {
                        add(buildJsonObject {
                            put("path", "/logs")
                            put("method", "GET")
                            put("description", "SSE 日志流，实时返回日志数据")
                            put("example", "curl http://localhost:8765/logs")
                        })
                        add(buildJsonObject {
                            put("path", "/logs/clear")
                            put("method", "POST")
                            put("description", "清空所有日志")
                            put("example", "curl -X POST http://localhost:8765/logs/clear")
                        })
                        add(buildJsonObject {
                            put("path", "/logs/search")
                            put("method", "POST")
                            put("description", "搜索历史日志，支持关键词、标签、包名、级别过滤")
                            put("body", "{\"query\": \"error\", \"level\": \"E\", \"limit\": 100}")
                            put("example", "curl -X POST -H 'Content-Type: application/json' -d '{\"query\": \"error\"}' http://localhost:8765/logs/search")
                        })
                        add(buildJsonObject {
                            put("path", "/logs/export")
                            put("method", "POST")
                            put("description", "导出日志为文本文件")
                            put("body", "{\"format\": \"txt\", \"level\": \"E\"}")
                            put("example", "curl -X POST -H 'Content-Type: application/json' -d '{\"format\": \"txt\"}' http://localhost:8765/logs/export")
                        })
                        add(buildJsonObject {
                            put("path", "/query")
                            put("method", "GET")
                            put("description", "获取当前过滤条件")
                            put("example", "curl http://localhost:8765/query")
                        })
                        add(buildJsonObject {
                            put("path", "/query/set")
                            put("method", "POST")
                            put("description", "设置过滤条件")
                            put("body", "{\"query\": \"tag:LogFox\"}")
                            put("example", "curl -X POST -H 'Content-Type: application/json' -d '{\"query\": \"tag:LogFox\"}' http://localhost:8765/query/set")
                        })
                        add(buildJsonObject {
                            put("path", "/filters")
                            put("method", "GET")
                            put("description", "获取所有启用的过滤器")
                            put("example", "curl http://localhost:8765/filters")
                        })
                        add(buildJsonObject {
                            put("path", "/record/start")
                            put("method", "POST")
                            put("description", "开始录制日志")
                            put("example", "curl -X POST http://localhost:8765/record/start")
                        })
                        add(buildJsonObject {
                            put("path", "/record/stop")
                            put("method", "POST")
                            put("description", "停止录制日志")
                            put("example", "curl -X POST http://localhost:8765/record/stop")
                        })
                        add(buildJsonObject {
                            put("path", "/record/list")
                            put("method", "GET")
                            put("description", "获取录制列表")
                            put("example", "curl http://localhost:8765/record/list")
                        })
                        add(buildJsonObject {
                            put("path", "/record/{id}")
                            put("method", "GET")
                            put("description", "获取录制详情")
                            put("example", "curl http://localhost:8765/record/1")
                        })
                        add(buildJsonObject {
                            put("path", "/tools")
                            put("method", "GET")
                            put("description", "获取可用工具列表")
                            put("example", "curl http://localhost:8765/tools")
                        })
                        add(buildJsonObject {
                            put("path", "/tools/{name}/call")
                            put("method", "POST")
                            put("description", "调用指定工具")
                            put("body", "{\"param1\": \"value1\"}")
                            put("example", "curl -X POST -H 'Content-Type: application/json' -d '{\"mode\": \"stream\"}' http://localhost:8765/tools/read_logs/call")
                        })
                        add(buildJsonObject {
                            put("path", "/server/discover")
                            put("method", "POST")
                            put("description", "MCP 服务器发现端点，返回服务器信息和支持的协议版本")
                            put("body", "{\"jsonrpc\": \"2.0\", \"method\": \"server/discover\", \"id\": 1}")
                            put("example", "curl -X POST -H 'Content-Type: application/json' -d '{\"jsonrpc\": \"2.0\", \"method\": \"server/discover\", \"id\": 1}' http://localhost:8765/server/discover")
                        })
                        add(buildJsonObject {
                            put("path", "/tools/list")
                            put("method", "GET/POST")
                            put("description", "MCP 标准工具列表端点，返回所有可用工具")
                            put("body", "{\"jsonrpc\": \"2.0\", \"method\": \"tools/list\", \"id\": 1}")
                            put("example", "curl -X GET http://localhost:8765/tools/list")
                        })
                        add(buildJsonObject {
                            put("path", "/tools/call")
                            put("method", "POST")
                            put("description", "MCP 标准工具调用端点，支持 _meta 参数")
                            put("body", "{\"jsonrpc\": \"2.0\", \"method\": \"tools/call\", \"id\": 1, \"params\": {\"name\": \"get_query\", \"arguments\": {}}}")
                            put("example", "curl -X POST -H 'Content-Type: application/json' -d '{\"jsonrpc\": \"2.0\", \"method\": \"tools/call\", \"id\": 1, \"params\": {\"name\": \"get_query\", \"arguments\": {}}}' http://localhost:8765/tools/call")
                        })
                        add(buildJsonObject {
                            put("path", "/mcp")
                            put("method", "POST")
                            put("description", "JSON-RPC 2.0 入口（兼容模式）")
                            put("body", "{\"jsonrpc\": \"2.0\", \"method\": \"tools/list\", \"id\": 1}")
                            put("example", "curl -X POST -H 'Content-Type: application/json' -d '{\"jsonrpc\": \"2.0\", \"method\": \"tools/list\", \"id\": 1}' http://localhost:8765/mcp")
                        })
                        add(buildJsonObject {
                            put("path", "/health")
                            put("method", "GET")
                            put("description", "健康检查")
                            put("example", "curl http://localhost:8765/health")
                        })
                        add(buildJsonObject {
                            put("path", "/help")
                            put("method", "GET")
                            put("description", "获取此帮助文档")
                            put("example", "curl http://localhost:8765/help")
                        })
                        add(buildJsonObject {
                            put("path", "/ws")
                            put("method", "WS")
                            put("description", "WebSocket 实时通信端点，支持日志推送和命令发送")
                            put("example", "wscat -c ws://localhost:8765/ws")
                        })
                    }
                    putJsonArray("tools") {
                        add(buildJsonObject {
                            put("name", "read_logs")
                            put("description", "读取日志流")
                            put("params", "{\"mode\": \"stream/dump\"}")
                        })
                        add(buildJsonObject {
                            put("name", "search_logs")
                            put("description", "搜索历史日志")
                            put("params", "{\"query\": \"关键词\", \"tag\": \"标签\", \"level\": \"级别\"}")
                        })
                        add(buildJsonObject {
                            put("name", "export_logs")
                            put("description", "导出日志为文本")
                            put("params", "{\"format\": \"txt\", \"query\": \"关键词\"}")
                        })
                        add(buildJsonObject {
                            put("name", "set_query")
                            put("description", "设置过滤条件")
                            put("params", "{\"query\": \"过滤字符串\"}")
                        })
                        add(buildJsonObject {
                            put("name", "get_query")
                            put("description", "获取过滤条件")
                            put("params", "无")
                        })
                        add(buildJsonObject {
                            put("name", "clear_logs")
                            put("description", "清空日志")
                            put("params", "无")
                        })
                        add(buildJsonObject {
                            put("name", "get_filters")
                            put("description", "获取过滤器")
                            put("params", "无")
                        })
                    }
                }
                call.respond(helpDoc)
            }

            get("/config") {
                Timber.i("$TAG Received GET /config request")
                try {
                    val config = buildJsonObject {
                        put("port", mcpServerManager?.port ?: McpServerManager.DEFAULT_PORT)
                        put("host", mcpServerManager?.host ?: McpServerManager.DEFAULT_HOST)
                        put("authEnabled", mcpServerManager?.authConfig?.enabled ?: false)
                        put("hasApiKey", mcpServerManager?.authConfig?.apiKey != null)
                    }
                    call.respond(config)
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /config error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            get("/history") {
                Timber.i("$TAG Received GET /history request")
                try {
                    val histories = queryHistoryDao?.getAll() ?: emptyList()
                    val response = buildJsonObject {
                        putJsonArray("items") {
                            histories.forEach { item ->
                                add(
                                    buildJsonObject {
                                        put("id", item.id)
                                        put("name", item.name)
                                        put("query", item.query)
                                        put("createdAt", item.createdAt)
                                    }
                                )
                            }
                        }
                        put("total", histories.size)
                    }
                    call.respond(response)
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /history error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            post("/history") {
                Timber.i("$TAG Received POST /history request")
                try {
                    val body = call.receiveText()
                    val request = json.parseToJsonElement(body).jsonObject
                    val name = request["name"]?.jsonPrimitive?.content ?: ""
                    val query = request["query"]?.jsonPrimitive?.content ?: ""

                    if (name.isEmpty()) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "name is required"))
                        return@post
                    }

                    queryHistoryDao?.insert(
                        QueryHistoryRoomEntity(
                            name = name,
                            query = query,
                            createdAt = System.currentTimeMillis(),
                        )
                    )

                    call.respond(buildJsonObject {
                        put("result", "success")
                        put("message", "Query history saved")
                    })
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /history POST error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            delete("/history/{id}") {
                Timber.i("$TAG Received DELETE /history request")
                try {
                    val id = call.parameters["id"]?.toLongOrNull()
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "id is required"))
                        return@delete
                    }

                    queryHistoryDao?.deleteById(id)
                    call.respond(buildJsonObject {
                        put("result", "success")
                        put("message", "Query history deleted")
                    })
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /history DELETE error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            get("/alerts") {
                Timber.i("$TAG Received GET /alerts request")
                try {
                    val rules = alertRuleDao?.getAll() ?: emptyList()
                    val response = buildJsonObject {
                        putJsonArray("rules") {
                            rules.forEach { rule ->
                                add(
                                    buildJsonObject {
                                        put("id", rule.id)
                                        put("name", rule.name)
                                        put("type", rule.type)
                                        put("keyword", rule.keyword ?: "")
                                        put("levelThreshold", rule.levelThreshold ?: "")
                                        put("enabled", rule.enabled)
                                        put("createdAt", rule.createdAt)
                                    }
                                )
                            }
                        }
                        put("total", rules.size)
                    }
                    call.respond(response)
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /alerts error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            post("/alerts") {
                Timber.i("$TAG Received POST /alerts request")
                try {
                    val body = call.receiveText()
                    val request = json.parseToJsonElement(body).jsonObject
                    val name = request["name"]?.jsonPrimitive?.content ?: ""
                    val type = request["type"]?.jsonPrimitive?.content ?: "keyword"
                    val keyword = request["keyword"]?.jsonPrimitive?.content
                    val levelThreshold = request["levelThreshold"]?.jsonPrimitive?.content
                    val enabled = request["enabled"]?.jsonPrimitive?.boolean ?: true

                    if (name.isEmpty()) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "name is required"))
                        return@post
                    }

                    if (type != "keyword" && type != "level") {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "type must be 'keyword' or 'level'"))
                        return@post
                    }

                    alertRuleDao?.insert(
                        AlertRuleRoomEntity(
                            name = name,
                            type = type,
                            keyword = keyword,
                            levelThreshold = levelThreshold,
                            enabled = enabled,
                            createdAt = System.currentTimeMillis(),
                        )
                    )

                    call.respond(buildJsonObject {
                        put("result", "success")
                        put("message", "Alert rule created")
                    })
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /alerts POST error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            delete("/alerts/{id}") {
                Timber.i("$TAG Received DELETE /alerts request")
                try {
                    val id = call.parameters["id"]?.toLongOrNull()
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "id is required"))
                        return@delete
                    }

                    alertRuleDao?.deleteById(id)
                    call.respond(buildJsonObject {
                        put("result", "success")
                        put("message", "Alert rule deleted")
                    })
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /alerts DELETE error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            get("/tags") {
                Timber.i("$TAG Received GET /tags request")
                try {
                    val tags = logTagDao?.getAllTags() ?: emptyList()
                    val response = buildJsonObject {
                        putJsonArray("tags") {
                            tags.forEach { add(it) }
                        }
                        put("total", tags.size)
                    }
                    call.respond(response)
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /tags error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            post("/tags") {
                Timber.i("$TAG Received POST /tags request")
                try {
                    val body = call.receiveText()
                    val request = json.parseToJsonElement(body).jsonObject
                    val logId = request["logId"]?.jsonPrimitive?.long ?: 0
                    val tag = request["tag"]?.jsonPrimitive?.content ?: ""

                    if (tag.isEmpty()) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "tag is required"))
                        return@post
                    }

                    logTagDao?.insert(
                        LogTagRoomEntity(
                            logId = logId,
                            tag = tag,
                            createdAt = System.currentTimeMillis(),
                        )
                    )

                    call.respond(buildJsonObject {
                        put("result", "success")
                        put("message", "Tag created")
                    })
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /tags POST error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            delete("/tags/{id}") {
                Timber.i("$TAG Received DELETE /tags request")
                try {
                    val id = call.parameters["id"]?.toLongOrNull()
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "id is required"))
                        return@delete
                    }

                    logTagDao?.deleteById(id)
                    call.respond(buildJsonObject {
                        put("result", "success")
                        put("message", "Tag deleted")
                    })
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /tags DELETE error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            post("/config") {
                Timber.i("$TAG Received POST /config request")
                try {
                    val body = call.receiveText()
                    val request = json.parseToJsonElement(body).jsonObject
                    val authEnabled = request["authEnabled"]?.jsonPrimitive?.boolean
                    val apiKey = request["apiKey"]?.jsonPrimitive?.content

                    if (mcpServerManager is McpServerManagerImpl) {
                        mcpServerManager.setAuthConfig(
                            enabled = authEnabled ?: false,
                            apiKey = apiKey,
                        )
                    }

                    val config = buildJsonObject {
                        put("port", mcpServerManager?.port ?: McpServerManager.DEFAULT_PORT)
                        put("host", mcpServerManager?.host ?: McpServerManager.DEFAULT_HOST)
                        put("authEnabled", mcpServerManager?.authConfig?.enabled ?: false)
                        put("hasApiKey", mcpServerManager?.authConfig?.apiKey != null)
                    }
                    call.respond(config)
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /config POST error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            get("/health") {
                Timber.i("$TAG Received GET /health request")
                call.respond(mapOf("status" to "ok"))
            }

            post("/server/discover") {
                Timber.i("$TAG Received POST /server/discover request")
                try {
                    val body = call.receiveText()
                    val request = json.parseToJsonElement(body).jsonObject
                    val id = request["id"] ?: JsonNull

                    val response = buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("id", id)
                        put("result", buildJsonObject {
                            put("name", "LogFox MCP Server")
                            put("version", "1.0.0")
                            put("description", "LogCat reader MCP server for Android")
                            putJsonArray("protocolVersions") {
                                add("2025-11-25")
                                add("2026-07-28")
                            }
                            put("capabilities", buildJsonObject {
                                put("tools", buildJsonObject {})
                            })
                        })
                    }

                    call.respond(
                        TextContent(
                            json.encodeToString(response),
                            contentType = ContentType.Application.Json,
                        ),
                    )
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /server/discover error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            get("/tools/list") {
                Timber.i("$TAG Received GET /tools/list request")
                try {
                    val toolList = tools.values.map { tool ->
                        buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("inputSchema", tool.inputSchema)
                        }
                    }

                    val response = buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("id", 0)
                        put("result", buildJsonObject {
                            putJsonArray("tools") {
                                toolList.forEach { add(it) }
                            }
                        })
                    }

                    call.respond(
                        TextContent(
                            json.encodeToString(response),
                            contentType = ContentType.Application.Json,
                        ),
                    )
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /tools/list error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            post("/tools/list") {
                Timber.i("$TAG Received POST /tools/list request")
                try {
                    val body = call.receiveText()
                    val request = json.parseToJsonElement(body).jsonObject
                    val id = request["id"] ?: JsonNull

                    val toolList = tools.values.map { tool ->
                        buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("inputSchema", tool.inputSchema)
                        }
                    }

                    val response = buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("id", id)
                        put("result", buildJsonObject {
                            putJsonArray("tools") {
                                toolList.forEach { add(it) }
                            }
                        })
                    }

                    call.respond(
                        TextContent(
                            json.encodeToString(response),
                            contentType = ContentType.Application.Json,
                        ),
                    )
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /tools/list error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            post("/tools/call") {
                Timber.i("$TAG Received POST /tools/call request")
                try {
                    val body = call.receiveText()
                    val request = json.parseToJsonElement(body).jsonObject
                    val id = request["id"] ?: JsonNull

                    val params = request["params"]?.jsonObject ?: JsonObject(emptyMap())
                    val toolName = params["name"]?.jsonPrimitive?.content ?: ""
                    val arguments = params["arguments"]?.jsonObject ?: JsonObject(emptyMap())

                    val meta = request["_meta"]?.jsonObject

                    Timber.d("$TAG tools/call: tool=$toolName, meta=$meta")

                    val tool = tools[toolName]
                    if (tool == null) {
                        Timber.w("$TAG Tool not found: $toolName")
                        call.respond(
                            TextContent(
                                json.encodeToString(
                                    buildJsonObject {
                                        put("jsonrpc", "2.0")
                                        put("id", id)
                                        put("error", buildJsonObject {
                                            put("code", -32601)
                                            put("message", "Tool not found: $toolName")
                                        })
                                    },
                                ),
                                contentType = ContentType.Application.Json,
                            ),
                        )
                        return@post
                    }

                    when (val result = tool.call(arguments)) {
                        is com.f0x1d.logfox.mcp.api.ToolResult.Value -> {
                            Timber.d("$TAG tools/call returned Value")
                            call.respond(
                                TextContent(
                                    json.encodeToString(
                                        buildJsonObject {
                                            put("jsonrpc", "2.0")
                                            put("id", id)
                                            put("result", buildJsonObject {
                                                put("resultType", "complete")
                                                putJsonArray("content") {
                                                    result.content.forEach { block ->
                                                        add(
                                                            buildJsonObject {
                                                                put("type", block.type)
                                                                if (block.text != null) put("text", block.text)
                                                                if (block.data != null) put("data", block.data)
                                                            },
                                                        )
                                                    }
                                                }
                                            })
                                        },
                                    ),
                                    contentType = ContentType.Application.Json,
                                ),
                            )
                        }
                        is com.f0x1d.logfox.mcp.api.ToolResult.Error -> {
                            Timber.e("$TAG tools/call returned Error: ${result.message}")
                            call.respond(
                                TextContent(
                                    json.encodeToString(
                                        buildJsonObject {
                                            put("jsonrpc", "2.0")
                                            put("id", id)
                                            put("error", buildJsonObject {
                                                put("code", -32000)
                                                put("message", result.message)
                                            })
                                        },
                                    ),
                                    contentType = ContentType.Application.Json,
                                ),
                            )
                        }
                        is com.f0x1d.logfox.mcp.api.ToolResult.Stream -> {
                            Timber.d("$TAG tools/call returned Stream")
                            call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
                            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                                result.flow.collect { block ->
                                    val item = buildJsonObject {
                                        put("type", block.type)
                                        if (block.text != null) put("text", block.text)
                                        if (block.data != null) put("data", block.data)
                                    }
                                    write("data: ${json.encodeToString(item)}\n\n")
                                    flush()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "$TAG /tools/call error")
                    call.respond(mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            webSocket("/ws") {
                Timber.i("$TAG WebSocket connection established")
                val session = McpWebSocketSession(this)

                try {
                    session.send(WsMessage(type = "connected", data = buildJsonObject { put("message", "Connected to LogFox MCP WebSocket") }))

                    val logFlow = startLoggingUseCase(terminal = terminal)
                    val logJob = launch(Dispatchers.IO) {
                        try {
                            logFlow.collect { logLine ->
                                val mcpLine = McpLogLine.from(logLine)
                                session.sendLog(mcpLine)
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "$TAG WebSocket log flow error")
                        }
                    }

                    val eventJob = launch(Dispatchers.IO) {
                        try {
                            webSocketHandler.events.collect { event ->
                                session.send(event)
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "$TAG WebSocket event flow error")
                        }
                    }

                    for (frame in incoming) {
                        frame as? Frame.Text ?: continue
                        val text = frame.readText()
                        Timber.d("$TAG WebSocket received: $text")
                        webSocketHandler.handleMessage(text)
                    }

                    logJob.cancel()
                    eventJob.cancel()
                } catch (e: Exception) {
                    Timber.e(e, "$TAG WebSocket error")
                } finally {
                    Timber.i("$TAG WebSocket connection closed")
                }
            }
        }

        Timber.i("$TAG Routes configured successfully")
    }

    private fun buildQueryFromFilter(
        filter: com.f0x1d.logfox.feature.filters.api.model.UserFilter,
    ): String {
        val parts = mutableListOf<String>()
        filter.uid?.let { parts.add("uid:$it") }
        filter.pid?.let { parts.add("pid:$it") }
        filter.tid?.let { parts.add("tid:$it") }
        filter.packageName?.let { parts.add("pkg:$it") }
        filter.tag?.let { parts.add("tag:$it") }
        filter.content?.let { parts.add("msg:$it") }
        return parts.joinToString(" ")
    }
}
