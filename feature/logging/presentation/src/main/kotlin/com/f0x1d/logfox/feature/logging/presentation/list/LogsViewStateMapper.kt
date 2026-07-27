package com.f0x1d.logfox.feature.logging.presentation.list

import com.f0x1d.logfox.core.tea.ViewStateMapper
import com.f0x1d.logfox.feature.datetime.api.DateTimeFormatter
import com.f0x1d.logfox.feature.filters.api.model.filterAndSearch
import com.f0x1d.logfox.feature.logging.presentation.list.model.toPresentationModel
import javax.inject.Inject

internal class LogsViewStateMapper @Inject constructor(
    private val dateTimeFormatter: DateTimeFormatter,
) : ViewStateMapper<LogsState, LogsViewState> {

    override fun map(state: LogsState): LogsViewState {
        val filteredLogs = state.logs.filterAndSearch(
            filters = state.filters,
            query = state.query,
            caseSensitive = state.caseSensitive,
        )

        val lineItems = filteredLogs.map { line ->
            line.toPresentationModel(
                displayText = line.formatOriginal(
                    values = state.showLogValues,
                    formatDate = dateTimeFormatter::formatDate,
                    formatTime = dateTimeFormatter::formatTime,
                ),
                expanded = state.expandedOverrides.getOrElse(line.id) { state.logsExpanded },
                selected = line.id in state.selectedIds,
                textSize = state.textSize.toFloat(),
            )
        }

        val groupedItems = LogGrouper.group(lineItems, state.groupExpandStates)

        val scrollFabIcon = when {
            !state.paused && groupedItems.size < 10 -> ScrollFabIcon.HIDDEN
            state.isAtBottom -> ScrollFabIcon.SCROLL_UP
            else -> ScrollFabIcon.SCROLL_DOWN
        }

        return LogsViewState(
            items = groupedItems,
            logsChanged = state.logsChanged,
            paused = state.paused,
            query = state.query,
            filters = state.filters,
            selecting = state.selectedIds.isNotEmpty(),
            selectedCount = state.selectedIds.size,
            resumeLoggingWithBottomTouch = state.resumeLoggingWithBottomTouch,
            scrollFabIcon = scrollFabIcon,
        )
    }
}
