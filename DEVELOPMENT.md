# LogFox 开发文档

> **文档说明**：本文档覆盖 LogFox 项目结构、MCP Server 功能设计、CI/CD 操作流程及问题排查，遵循 CommonMark 与 GitHub Flavored Markdown 规范编写。

## 目录

- [项目概述](#项目概述)
- [环境变量](#环境变量)
- [构建命令](#构建命令)
- [架构说明](#架构说明)
- [项目结构](#项目结构)
- [MCP 功能设计](#mcp-功能设计)
- [CI/CD 操作流程](#cicd-操作流程)
- [问题修复记录](#问题修复记录)
- [构建要求](#构建要求)
- [已知问题](#已知问题)
- [常见问题排查](#常见问题排查)

---

## 项目概述

LogFox 是一个 Android LogCat 阅读器，支持 Shizuku、Root 和 ADB 三种日志访问方式。

本文档覆盖以下内容：

- 项目整体结构与模块组织
- MCP Server 功能设计与 API 端点说明
- CI/CD 操作流程与日志分析
- 已知问题及常见错误排查

---

## 环境变量

本机 `~/.claude/.env` 文件中已配置 `LogFox-Cli` token：

```powershell
$env:LOGFOX_TOKEN = (Get-Content "$HOME\.claude\.env" | Select-String "LogFox-Cli").ToString().Split('=')[1]
```

---

## 构建命令

```powershell
./gradlew :app:assembleDebug --quiet          # 构建 Debug APK
./gradlew testDebugUnitTest --quiet            # 运行单元测试
./gradlew verifyRoborazziDebug --quiet         # 快照测试（CI 使用）
```

---

## 架构说明

### 模块组织

项目采用 **Clean Architecture** 架构，每个 feature 模块遵循以下目录结构：

```text
feature/<name>/
  api/            # 领域接口、模型
  impl/           # 仓库实现、数据源
  presentation/   # ViewModel、Fragment/Composable
```

### 依赖规则

- `presentation -> api` only
- `impl -> api`
- 只有 `:app` 聚合 `presentation` + `impl`

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

## MCP 功能设计

### 核心设计

| 属性     | 说明                                          |
| -------- | --------------------------------------------- |
| 位置     | `app/src/main/kotlin/com/f0x1d/logfox/mcp/`   |
| 运行方式 | Android 前台服务 (`McpServerService`)         |
| 网络     | 监听 `0.0.0.0:8765`                           |
| 协议     | JSON-RPC 2.0 (`/mcp`) + SSE (`/logs`)         |

### API 端点

| 端点                 | 方法   | 说明                          |
| -------------------- | ------ | ----------------------------- |
| `/logs`              | GET    | SSE 日志流                    |
| `/logs/clear`        | POST   | 清空日志                      |
| `/logs/search`       | POST   | 搜索历史日志（关键词/标签/包名/级别） |
| `/logs/export`       | POST   | 导出日志为文件（txt/log）      |
| `/query`             | GET    | 获取过滤条件                  |
| `/query/set`         | POST   | 设置过滤条件                  |
| `/filters`           | GET    | 获取启用的过滤器              |
| `/record/start`      | POST   | 开始录制                      |
| `/record/stop`       | POST   | 停止录制                      |
| `/record/list`       | GET    | 获取录制列表                  |
| `/record/{id}`       | GET    | 获取录制详情                  |
| `/tools`             | GET    | 工具列表                      |
| `/tools/{name}/call` | POST   | 调用工具                      |
| `/mcp`               | POST   | JSON-RPC 2.0 入口             |
| `/health`            | GET    | 健康检查                      |
| `/help`              | GET    | 获取所有端点帮助文档          |
| `/ws`                | WS     | WebSocket 实时通信            |

### 可用工具

| 工具名         | 说明             | 参数                                       |
| -------------- | ---------------- | ------------------------------------------ |
| `read_logs`    | 读取日志流       | `mode`: stream/dump                        |
| `search_logs`  | 搜索历史日志     | `query`, `tag`, `package_name`, `level`, `limit` |
| `export_logs`  | 导出日志为文本   | `format`, `query`, `tag`, `level`           |
| `set_query`    | 设置过滤条件     | `query`: 过滤字符串                        |
| `get_query`    | 获取过滤条件     | 无                                         |
| `clear_logs`   | 清空日志         | 无                                         |
| `get_filters`  | 获取过滤器       | 无                                         |

### API 认证

- 默认关闭认证，向后兼容
- 通过 `X-API-Key` 请求头传递 API Key
- `/health` 和 `/help` 端点跳过认证
- 认证失败返回 401 Unauthorized

### WebSocket 通信

- **端点**: `/ws`**
- **服务端 → 客户端**：`log`（日志行）、`recording_started`、`recording_stopped`、`connected`
- **客户端 → 服务端**：`set_query`、`clear_logs`、`start_recording`、`stop_recording`、`pong`
- 保留 SSE `/logs` 端点继续可用

### 模块架构

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

### 修改记录

- `app/build.gradle.kts`：添加 Ktor 和 kotlinx-serialization、ktor-server-websockets
- `gradle/libs.versions.toml`：添加 ktor-server-websockets 依赖
- `AndroidManifest.xml`：注册 `McpServerService`
- `LogFoxApp.kt`：创建 MCP 通知渠道；注入 `McpServerDeps`
- `di/McpServerModule.kt`：Hilt `@Binds` 绑定
- `mcp/impl/McpServerManagerImpl.kt`：移除单例依赖，直接注入 UseCase；修复停止逻辑；添加录制、认证、WebSocket 支持
- `mcp/impl/McpRoutes.kt`：添加日志搜索、导出、录制管理、API 认证、WebSocket 路由
- `mcp/impl/tools/SearchLogsTool.kt`：日志搜索 MCP 工具
- `mcp/impl/tools/ExportLogsTool.kt`：日志导出 MCP 工具
- `mcp/impl/tools/ReadLogsTool.kt`：添加 `getSelectedTerminalUseCase` 参数
- `mcp/impl/auth/AuthConfig.kt`：API 认证配置
- `mcp/impl/websocket/`：WebSocket 会话管理和消息处理器
- `mcp/api/model/`：新增 SearchRequest、SearchResponse、ExportRequest、LogRecordingInfo 模型

---

## CI/CD 操作流程

### 提交代码

```powershell
git add <文件路径>
git commit -m "commit message"
git remote set-url origin "https://${env:LOGFOX_TOKEN}@github.com/fvgfgtxdeujv/LogFox.git"
git push origin master
git remote set-url origin "https://github.com/fvgfgtxdeujv/LogFox.git"
```

### 操作流程

1. 修改代码后，**等待用户检查确认**
2. 用户确认后执行 `git push` 触发 CI
3. 推送完成后**停止操作**，等待用户输入 **"1"**
4. 用户输入 **"1"** = 构建失败，下载构建日志分析报错
5. 修复代码后**直接推送**（不进行本地构建）
6. 重复步骤 3-5 直到构建成功

### 下载构建日志

```powershell
$token = (Get-Content "$HOME\.claude\.env" | Select-String "LogFox-Cli").ToString().Split('=')[1]
$runId = <run ID>

Invoke-WebRequest -Uri "https://api.github.com/repos/fvgfgtxdeujv/LogFox/actions/runs/$runId/logs" `
  -OutFile "C:\Users\q\Desktop\build_logs_$runId.zip" `
  -Headers @{Authorization = "token $token"}

Expand-Archive -Path "C:\Users\q\Desktop\build_logs_$runId.zip" -DestinationPath "C:\temp\build_logs_$runId" -Force
```

### 快速定位错误

```python
import os

for root, dirs, files in os.walk('C:/temp/build_logs'):
    for f in files:
        if f.endswith('.txt'):
            path = os.path.join(root, f)
            with open(path, 'r', encoding='utf-8', errors='replace') as fh:
                lines = fh.readlines()
            errors = [(i, l.rstrip()) for i, l in enumerate(lines) if l.strip().startswith('e:')]
            if errors:
                print('=== ' + path + ' ===')
                for idx, line in errors[:20]:
                    print('%d: %s' % (idx, line))
```

---

## 问题修复记录

| 时间       | 问题                          | 修复方式                                                            |
| ---------- | ----------------------------- | ------------------------------------------------------------------- |
| 2026-07-05 | Ktor 3.x 扩展函数无法解析     | `fun Application.mcpRoutes()` → `fun mcpRoutes(application: Application)` |
| 2026-07-05 | 协程函数在非协程上下文调用    | `start()` 中提前调用 `selectedTerminal()`                            |
| 2026-07-05 | `buildJsonArray` 类型推断失败 | 显式 `JsonArray(listOf(...))`                                       |
| 2026-07-05 | `mapOf` 类型推断失败          | 改用 `LinkedHashMap<String, McpTool>()`                             |
| 2026-07-05 | `launch` deprecated           | `mcpRoutes` 改为非 suspend 函数                                     |
| 2026-07-06 | Hilt MissingBinding           | 创建 `McpServerModule.kt`，使用 `@Binds`                             |
| 2026-07-06 | `McpServerDeps` 单例初始化延迟 | 重构为直接注入 UseCase                                              |
| 2026-07-06 | 服务器停止逻辑无效            | 添加 `(server as? ApplicationEngine)?.stop()`                       |
| 2026-07-06 | `ReadLogsTool` 构造函数参数缺失 | 添加 `getSelectedTerminalUseCase` 参数                             |

---

## 构建要求

| 属性 | 说明                                              |
| ---- | ------------------------------------------------- |
| JDK  | 21 (Temurin)                                      |
| 产物 | `app/build/outputs/apk/debug/LogFox-unknown-debug.apk` |

---

## 已知问题

### Windows AIDL 编译编码错误

AGP 9.2 + JDK 21 在 Windows 上编译 AIDL 时遇到非 ASCII 字符会报错：

```text
java.nio.charset.MalformedInputException: Input length = 1
```

**解决方案**：

1. 使用 Android Studio 构建
2. 或临时删除 `feature/terminals/impl/src/main/res/values-*` 下的非英文 strings.xml

---

## 常见问题排查

| 错误类型       | 典型信息                                       | 修复方式                              |
| -------------- | ---------------------------------------------- | ------------------------------------- |
| 导入路径错误   | `Unresolved reference`                         | 检查包路径                            |
| 依赖缺失       | `Unresolved reference: ContentNegotiation`     | 添加依赖                              |
| Ktor API 变更  | `embeddedServer` 返回类型不匹配                | 改用 `Any` 存储 server               |
| 类型推断失败   | `Cannot infer type`                            | 显式指定泛型                          |
| 序列化错误     | `Argument type mismatch: List<String>`         | `listOf` → `buildJsonArray`           |
| Hilt 绑定缺失  | `Dagger/MissingBinding`                        | 创建 `@Binds` 或 `@Provides`          |
| 单例初始化延迟 | `lateinit property ... not initialized`        | 直接注入 UseCase                      |
| 协程调用错误   | `Suspension functions can only be called within coroutine body` | 在 `suspend` 函数中调用 |
| 构造函数参数缺失 | `No value passed for parameter`              | 添加缺失参数                          |
