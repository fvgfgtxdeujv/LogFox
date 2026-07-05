# LogFox MCP 功能开发文档

## 项目概述

LogFox 是一个 Android LogCat 阅读器，支持 Shizuku、Root 和 ADB 三种日志访问方式。本开发文档覆盖了项目中新增的 MCP Server 功能的实现细节和构建方法。

## MCP 功能概述

在 LogFox Android 应用内嵌了一个 MCP（Model Context Protocol）服务器，允许外部 AI 通过 HTTP/SSE 方式读取设备日志、设置过滤条件、清空日志等。

### 核心设计

- **Server 位置**: `app/src/main/kotlin/com/f0x1d/logfox/mcp/`
- **运行方式**: Android 前台服务 (`McpServerService`)
- **网络**: 监听 `0.0.0.0:8765`
- **协议**: JSON-RPC 2.0 (MCP stdio 协议映射到 HTTP POST `/mcp`) + SSE (`/logs`)

### API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/logs` | GET | SSE 流式读取日志 |
| `/logs/clear` | POST | 清空日志缓冲区 |
| `/query` | GET | 获取当前过滤条件 |
| `/query` | POST | 设置过滤条件（body: `{"query":"..."}`） |
| `/filters` | GET | 获取所有启用的过滤器 |
| `/tools` | GET | 获取可用工具列表 |
| `/tools/{name}/call` | POST | 调用指定工具 |
| `/mcp` | POST | JSON-RPC 2.0 MCP 协议入口 |
| `/health` | GET | 健康检查 |

### 可用工具

| 工具名 | 说明 | 参数 |
|--------|------|------|
| `read_logs` | 读取日志流 | `mode`: stream(默认) / dump |
| `set_query` | 设置过滤条件 | `query`: 过滤字符串 |
| `get_query` | 获取过滤条件 | 无 |
| `clear_logs` | 清空日志 | 无 |
| `get_filters` | 获取所有过滤器 | 无 |

## 构建要求

### JDK 版本

- **必需**: JDK 21 (Temurin 推荐)
- 项目配置 `JVM_VERSION = 21`，在 `build-logic/conventions/src/main/kotlin/com/f0x1d/logfox/buildlogic/extensions/Constants.kt`

### 编译步骤

```bash
# 设置 JDK 21
$env:JAVA_HOME = "C:\Users\q\.jwmv\candidates\java\21.35-tem"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# 清理并构建
./gradlew clean
./gradlew :app:assembleDebug --quiet
```

### 构建产物

- Debug APK: `app/build/outputs/apk/debug/LogFox-unknown-debug.apk`

## 项目结构

```
LogFox/
├── app/                              # 应用主模块
│   ├── build.gradle.kts              # 应用级依赖（含 Ktor）
│   └── src/main/kotlin/com/f0x1d/logfox/
│       ├── LogFoxApp.kt              # Application 入口
│       ├── mcp/                      # MCP 功能包
│       │   ├── api/                  # 接口定义
│       │   │   ├── McpServerManager.kt
│       │   │   ├── McpTool.kt
│       │   │   └── model/
│       │   │       ├── ToolResult.kt
│       │   │       └── McpLogLine.kt
│       │   └── impl/                 # 实现
│       │       ├── McpRoutes.kt      # Ktor 路由
│       │       ├── McpServerManagerImpl.kt  # 服务器生命周期
│       │       ├── McpServerDeps.kt   # 单例依赖持有
│       │       ├── McpServerService.kt    # 前台服务
│       │       └── tools/            # MCP 工具
│       │           ├── ReadLogsTool.kt
│       │           ├── SetQueryTool.kt
│       │           ├── GetQueryTool.kt
│       │           ├── ClearLogsTool.kt
│       │           └── GetFiltersTool.kt
│       └── di/
│           └── McpServerDepsModule.kt  # Hilt 依赖注入
├── feature/                          # 功能模块（multi-module）
├── core/                             # 核心库
└── build-logic/                      # Gradle 构建逻辑
```

## MCP 模块依赖关系

```
McpServerService (Hilt @AndroidEntryPoint)
    └── McpServerManagerImpl (@Singleton, @Inject)
            ├── StartLoggingUseCase
            ├── GetLastLogUseCase
            ├── ClearLogsUseCase
            ├── GetQueryFlowUseCase
            ├── UpdateQueryUseCase
            └── GetAllEnabledFiltersFlowUseCase

McpServerDeps (单例)
    └── 由 McpServerDepsModule 在启动时注入 UseCase 引用
    └── 供 McpRoutes 在路由回调中使用
```

## 修改记录

- `app/build.gradle.kts`: 添加 Ktor 和 kotlinx-serialization 依赖
- `app/src/main/AndroidManifest.xml`: 注册 McpServerService
- `feature/notifications/api/.../NotificationChannelIds.kt`: 添加 MCP 通知渠道 ID
- `strings/src/main/res/values/strings.xml`: 添加 MCP 通知相关字符串
- `app/src/main/kotlin/.../LogFoxApp.kt`: 创建 MCP 通知渠道

## 注意事项

- MCP 服务需要 Root 设备才能正常工作（日志读取权限）
- 服务启动时会自动在 8765 端口监听
- 日志流通过 SSE (Server-Sent Events) 推送
