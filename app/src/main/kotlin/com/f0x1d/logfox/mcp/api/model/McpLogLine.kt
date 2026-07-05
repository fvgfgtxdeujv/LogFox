package com.f0x1d.logfox.mcp.api.model

import com.f0x1d.logfox.feature.logging.api.model.LogLevel
import kotlinx.serialization.Serializable

@Serializable
data class McpLogLine(
    val id: Long,
    val dateAndTime: Long,
    val uid: String,
    val pid: String,
    val tid: String,
    val packageName: String?,
    val level: LogLevel,
    val tag: String,
    val content: String,
) {
    companion object {
        fun from(logLine: com.f0x1d.logfox.feature.logging.api.model.LogLine): McpLogLine {
            return McpLogLine(
                id = logLine.id,
                dateAndTime = logLine.dateAndTime,
                uid = logLine.uid,
                pid = logLine.pid,
                tid = logLine.tid,
                packageName = logLine.packageName,
                level = logLine.level,
                tag = logLine.tag,
                content = logLine.content,
            )
        }
    }
}
