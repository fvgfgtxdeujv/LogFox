package com.f0x1d.logfox.mcp.api.model

import kotlinx.serialization.Serializable

@Serializable
data class LogRecordingInfo(
    val id: Long,
    val title: String,
    val dateAndTime: Long,
    val fileSize: Long,
    val filePath: String,
) {
    companion object {
        fun from(recording: com.f0x1d.logfox.feature.recordings.api.model.LogRecording): LogRecordingInfo {
            return LogRecordingInfo(
                id = recording.id,
                title = recording.title,
                dateAndTime = recording.dateAndTime,
                fileSize = if (recording.file.exists()) recording.file.length() else 0,
                filePath = recording.file.absolutePath,
            )
        }
    }
}
