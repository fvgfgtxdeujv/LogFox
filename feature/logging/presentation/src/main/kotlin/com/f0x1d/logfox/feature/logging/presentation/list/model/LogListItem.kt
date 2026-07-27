package com.f0x1d.logfox.feature.logging.presentation.list.model

import com.f0x1d.logfox.feature.logging.api.model.LogLevel

sealed class LogListItem {

    data class Item(
        val data: LogLineItem,
    ) : LogListItem()

    data class Group(
        val header: LogLineItem,
        val children: List<LogLineItem>,
        val expanded: Boolean,
        val count: Int,
        val maxLevel: LogLevel,
    ) : LogListItem()
}
