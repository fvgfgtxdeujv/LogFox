package com.f0x1d.logfox.mcp.impl

import com.f0x1d.logfox.feature.filters.api.domain.GetAllEnabledFiltersFlowUseCase
import com.f0x1d.logfox.feature.logging.api.domain.StartLoggingUseCase
import com.f0x1d.logfox.feature.logging.api.domain.ClearLogsUseCase
import com.f0x1d.logfox.feature.logging.api.domain.GetQueryFlowUseCase
import com.f0x1d.logfox.feature.logging.api.domain.UpdateQueryUseCase
import com.f0x1d.logfox.feature.terminals.api.base.Terminal
import com.f0x1d.logfox.feature.terminals.api.domain.GetSelectedTerminalUseCase

object McpServerDeps {

    lateinit var startLoggingUseCase: StartLoggingUseCase
    lateinit var clearLogsUseCase: ClearLogsUseCase
    lateinit var getQueryFlowUseCase: GetQueryFlowUseCase
    lateinit var updateQueryUseCase: UpdateQueryUseCase
    lateinit var getAllEnabledFiltersFlowUseCase: GetAllEnabledFiltersFlowUseCase
    lateinit var getSelectedTerminalUseCase: GetSelectedTerminalUseCase

    suspend fun selectedTerminal(): Terminal {
        return getSelectedTerminalUseCase()
    }
}
