package com.f0x1d.logfox.mcp.api.model

import kotlinx.serialization.Serializable

@Serializable
data class FilterGroup(
    val uid: String? = null,
    val pid: String? = null,
    val tid: String? = null,
    val packageName: String? = null,
    val tag: String? = null,
    val content: String? = null,
    val caseSensitive: Boolean = false,
)