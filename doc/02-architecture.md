# 架构说明

## 模块组织

项目采用 **Clean Architecture（整洁架构）**，每个 feature 模块遵循以下目录结构：

```text
feature/<name>/
  api/            # 领域接口、模型
  impl/           # 仓库实现、数据源
  presentation/   # ViewModel、Fragment/Composable
```

### 依赖规则

- `presentation` 层仅依赖 `api` 层
- `impl` 层依赖 `api` 层
- 只有 `:app` 模块聚合 `presentation` 和 `impl`

---

## 项目结构

```text
LogFox/
├── app/
│   └── src/main/kotlin/com/f0x1d/logfox/
│       ├── LogFoxApp.kt
│       ├── mcp/
│       │   ├── api/          # McpServerManager, McpTool, model/
│       │   └── impl/         # McpRoutes, McpServerManagerImpl, McpServerService
│       │       └── tools/    # ReadLogsTool, SetQueryTool, GetQueryTool, ClearLogsTool, GetFiltersTool
│       └── di/               # McpServerDepsModule, McpServerModule
├── feature/
├── core/
└── build-logic/
```

---

## MCP 模块架构

```text
McpServerService (@AndroidEntryPoint)
    └── McpServerManagerImpl (@Singleton)
            ├── StartLoggingUseCase
            ├── GetLastLogUseCase
            ├── ClearLogsUseCase
            ├── GetLogsSnapshotUseCase
            ├── GetQueryFlowUseCase
            ├── UpdateQueryUseCase
            ├── GetAllEnabledFiltersFlowUseCase
            ├── GetSelectedTerminalUseCase
            ├── StartRecordingUseCase
            ├── EndRecordingUseCase
            ├── GetAllRecordingsFlowUseCase
            ├── GetRecordingByIdFlowUseCase
            └── AuthConfig (API Key 认证)

ReadLogsTool / SearchLogsTool / ExportLogsTool (@Inject)
    └── 直接注入 UseCase，不依赖 McpServerDeps 单例

McpWebSocketHandler
    └── 处理 WebSocket 消息（set_query/clear_logs/start_recording/stop_recording）

McpServerModule (@Binds)
    └── McpServerManagerImpl → McpServerManager
```
