package com.f0x1d.logfox.feature.logging.presentation.list

import com.f0x1d.logfox.feature.filters.api.model.UserFilter
import com.f0x1d.logfox.feature.logging.presentation.list.model.LogListItem

internal data class LogsViewState(
    val items: List<LogListItem>,
    val logsChanged: Boolean,
    val paused: Boolean,
    val query: String?,
    val filters: List<UserFilter>,
    val selecting: Boolean,
    val selectedCount: Int,
    val resumeLoggingWithBottomTouch: Boolean,
    val scrollFabIcon: ScrollFabIcon,
)
