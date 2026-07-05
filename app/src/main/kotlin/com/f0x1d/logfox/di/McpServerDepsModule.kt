package com.f0x1d.logfox.di

import com.f0x1d.logfox.feature.filters.api.domain.GetAllEnabledFiltersFlowUseCase
import com.f0x1d.logfox.feature.logging.api.domain.StartLoggingUseCase
import com.f0x1d.logfox.feature.logging.api.domain.ClearLogsUseCase
import com.f0x1d.logfox.feature.logging.api.domain.GetQueryFlowUseCase
import com.f0x1d.logfox.feature.logging.api.domain.UpdateQueryUseCase
import com.f0x1d.logfox.mcp.impl.McpServerDeps
import com.f0x1d.logfox.feature.terminals.api.domain.GetSelectedTerminalUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object McpServerDepsModule {

    @Provides
    @Singleton
    fun provideMcpServerDeps(
        startLoggingUseCase: StartLoggingUseCase,
        clearLogsUseCase: ClearLogsUseCase,
        getQueryFlowUseCase: GetQueryFlowUseCase,
        updateQueryUseCase: UpdateQueryUseCase,
        getAllEnabledFiltersFlowUseCase: GetAllEnabledFiltersFlowUseCase,
        getSelectedTerminalUseCase: GetSelectedTerminalUseCase,
    ): McpServerDeps {
        McpServerDeps.startLoggingUseCase = startLoggingUseCase
        McpServerDeps.clearLogsUseCase = clearLogsUseCase
        McpServerDeps.getQueryFlowUseCase = getQueryFlowUseCase
        McpServerDeps.updateQueryUseCase = updateQueryUseCase
        McpServerDeps.getAllEnabledFiltersFlowUseCase = getAllEnabledFiltersFlowUseCase
        McpServerDeps.getSelectedTerminalUseCase = getSelectedTerminalUseCase
        return McpServerDeps
    }
}
