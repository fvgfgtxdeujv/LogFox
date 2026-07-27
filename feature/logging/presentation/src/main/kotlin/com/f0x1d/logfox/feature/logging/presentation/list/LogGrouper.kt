package com.f0x1d.logfox.feature.logging.presentation.list

import com.f0x1d.logfox.feature.logging.presentation.list.model.LogLineItem
import com.f0x1d.logfox.feature.logging.presentation.list.model.LogListItem

internal object LogGrouper {

    const val DEFAULT_MAX_GROUP_SIZE = 100

    fun group(
        items: List<LogLineItem>,
        groupExpandStates: Map<Long, Boolean>,
        maxGroupSize: Int = DEFAULT_MAX_GROUP_SIZE,
    ): List<LogListItem> {
        if (items.isEmpty()) return emptyList()

        val result = mutableListOf<LogListItem>()
        var i = 0
        while (i < items.size) {
            val current = items[i]
            val groupStart = i
            while (i < items.size &&
                i - groupStart < maxGroupSize &&
                areSimilar(items[i], current)
            ) {
                i++
            }
            val count = i - groupStart
            if (count >= 2) {
                val children = items.subList(groupStart, i).toList()
                val maxLevel = children.maxBy { l -> l.level.ordinal }.level
                result.add(
                    LogListItem.Group(
                        header = current,
                        children = children,
                        expanded = groupExpandStates[current.logLineId] ?: false,
                        count = count,
                        maxLevel = maxLevel,
                    ),
                )
            } else {
                result.add(LogListItem.Item(current))
            }
        }
        return result
    }

    private fun areSimilar(a: LogLineItem, b: LogLineItem): Boolean =
        a.content == b.content && a.tag == b.tag && a.level == b.level
}
