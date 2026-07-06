package com.f0x1d.logfox.di

import com.f0x1d.logfox.mcp.api.McpServerManager
import com.f0x1d.logfox.mcp.impl.McpServerManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface McpServerModule {

    @Binds
    @Singleton
    fun bindMcpServerManager(impl: McpServerManagerImpl): McpServerManager
}