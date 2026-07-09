# MCP Server 功能扩展计划

## 概要

在现有 MCP Server 基础上新增 5 大功能模块：日志搜索、日志导出、录制管理、API 认证、WebSocket 实时通信。

---

## 一、仓库调研结论

### 现有架构

- **MCP Server** 位于 `app/src/main/kotlin/com/f0x1d/logfox/mcp/`，采用 Ktor 3.x + CIO 引擎
- **路由层**：[McpRoutes.kt](file:///C:/Users/q/Desktop/工具/LogFox/app/src/main/kotlin/com/f0x1d/logfox/mcp/impl/McpRoutes.kt) - 纯函数式路由配置，通过构造参数注入 UseCase
- **管理层**：[McpServerManagerImpl.kt](file:///C:/Users/q/Desktop/工具/LogFox/app/src/main/kotlin/com/f0x1d/logfox/mcp/impl/McpServerManagerImpl.kt) - `@Singleton`，管理 Ktor 服务器生命周期，构建 tools map
- **工具层**：`McpTool` 接口 + 5 个实现（ReadLogsTool、SetQueryTool 等）
- **数据模型**：`McpLogLine`（日志行）、`ToolResult`（工具返回值）、`ContentBlock`（内容块）

### 现有 UseCase 可用清单

| 功能域 | UseCase | 位置 |
|--------|---------|------|
| 日志 | `GetLogsSnapshotUseCase` - 获取全部日志快照 | `feature/logging/api` |
| 日志 | `GetLogsFlowUseCase` - 日志流（Flow） | `feature/logging/api` |
| 日志 | `ExportLogsToUriUseCase` - 导出日志到 Uri | `feature/logging/api` |
| 日志 | `StartLoggingUseCase` - 启动日志采集 | `feature/logging/api` |
| 日志 | `ClearLogsUseCase` - 清空日志 | `feature/logging/api` |
| 日志 | `GetQueryFlowUseCase` / `UpdateQueryUseCase` - 查询条件 | `feature/logging/api` |
| 录制 | `StartRecordingUseCase` - 开始录制 | `feature/recordings/api` |
| 录制 | `EndRecordingUseCase` - 结束录制（返回 LogRecording） | `feature/recordings/api` |
| 录制 | `GetAllRecordingsFlowUseCase` - 全部录制列表（Flow） | `feature/recordings/api` |
| 录制 | `GetRecordingByIdFlowUseCase` - 单个录制详情（Flow） | `feature/recordings/api` |
| 录制 | `ExportRecordingFileUseCase` - 导出录制文件 | `feature/recordings/api` |
| 录制 | `DeleteRecordingUseCase` - 删除录制 | `feature/recordings/api` |
| 过滤器 | `GetAllEnabledFiltersFlowUseCase` - 启用的过滤器 | `feature/filters/api` |

### 技术栈

- **Ktor 3.1.3**（server-core, server-cio, content-negotiation, serialization-kotlinx-json）
- **kotlinx-serialization-json**
- **Hilt** 依赖注入
- **Kotlin Coroutines + Flow**

### 当前依赖缺口

- **WebSocket**：需要添加 `ktor-server-websockets` 依赖
- **API 认证**：需要添加 `ktor-server-auth` 依赖（或自行实现简单拦截器）

---

## 二、待修改文件与模块

### 新增文件

| 文件路径 | 说明 |
|----------|------|
| `app/src/main/kotlin/com/f0x1d/logfox/mcp/api/model/SearchRequest.kt` | 搜索请求数据模型 |
| `app/src/main/kotlin/com/f0x1d/logfox/mcp/api/model/SearchResponse.kt` | 搜索响应数据模型 |
| `app/src/main/kotlin/com/f0x1d/logfox/mcp/api/model/ExportRequest.kt` | 导出请求数据模型 |
| `app/src/main/kotlin/com/f0x1d/logfox/mcp/api/model/LogRecordingInfo.kt` | 录制信息 MCP 模型 |
| `app/src/main/kotlin/com/f0x1d/logfox/mcp/impl/auth/AuthConfig.kt` | API 认证配置 |
| `app/src/main/kotlin/com/f0x1d/logfox/mcp/impl/auth/ApiKeyAuthPlugin.kt` | API Key 认证插件（或拦截器） |
| `app/src/main/kotlin/com/f0x1d/logfox/mcp/impl/websocket/McpWebSocketSession.kt` | WebSocket 会话管理 |
| `app/src/main/kotlin/com/f0x1d/logfox/mcp/impl/websocket/McpWebSocketHandler.kt` | WebSocket 消息处理器 |
| `app/src/main/kotlin/com/f0x1d/logfox/mcp/impl/tools/SearchLogsTool.kt` | 搜索日志 MCP 工具 |
| `app/src/main/kotlin/com/f0x1d/logfox/mcp/impl/tools/ExportLogsTool.kt` | 导出日志 MCP 工具 |

### 修改文件

| 文件路径 | 修改内容 |
|----------|----------|
| `app/build.gradle.kts` | 添加 ktor-server-websockets 依赖 |
| `gradle/libs.versions.toml` | 添加 ktor-server-websockets 版本引用 |
| `app/src/main/kotlin/com/f0x1d/logfox/mcp/impl/McpRoutes.kt` | 新增搜索、导出、录制、认证、WebSocket 路由 |
| `app/src/main/kotlin/com/f0x1d/logfox/mcp/impl/McpServerManagerImpl.kt` | 注入新的 UseCase，构建新 tools，配置认证和 WebSocket |
| `DEVELOPMENT.md` | 更新 API 文档 |

---

## 三、功能详细设计与实现步骤

### 功能 1：日志搜索端点 (`POST /logs/search`)

**需求**：按关键词、标签、包名、级别、时间范围等条件搜索历史日志

**实现方案**：

1. **数据模型** (`SearchRequest.kt`, `SearchResponse.kt`)
   - `SearchRequest`：`query`（关键词）、`tag`、`packageName`、`level`、`limit`、`offset`
   - `SearchResponse`：`results`（`List<McpLogLine>`）、`total`、`limit`、`offset`

2. **搜索逻辑**：
   - 调用 `GetLogsSnapshotUseCase()` 获取当前所有日志
   - 在内存中按条件过滤（标签、包名、级别、关键词支持正则/包含匹配）
   - 分页返回结果

3. **路由实现**（在 `McpRoutes.kt` 中）：
   ```
   post("/logs/search") {
       val request = call.receive<SearchRequest>()
       val allLogs = getLogsSnapshotUseCase()
       val filtered = allLogs.filter { ... }
       val paged = filtered.drop(offset).take(limit)
       call.respond(SearchResponse(...))
   }
   ```

4. **MCP Tool** (`SearchLogsTool`)：
   - 工具名：`search_logs`
   - 参数：`query`, `tag`, `package_name`, `level`, `limit`
   - 返回：`ToolResult.Value`（搜索结果列表）

---

### 功能 2：日志导出端点 (`POST /logs/export`)

**需求**：将日志导出为文件（txt/log 格式），支持过滤条件

**实现方案**：

1. **数据模型** (`ExportRequest.kt`)
   - `format`：`txt` / `log`（纯文本格式）
   - 过滤条件：`query`, `tag`, `packageName`, `level`
   - `limit`：可选，限制条数

2. **导出逻辑**：
   - 调用 `GetLogsSnapshotUseCase()` 获取日志
   - 按条件过滤
   - 格式化为纯文本（每行一条，类似 logcat 输出格式）
   - 通过 HTTP 响应以文件形式返回（`Content-Disposition: attachment`）

3. **路由实现**（在 `McpRoutes.kt` 中）：
   ```
   post("/logs/export") {
       val request = call.receive<ExportRequest>()
       val logs = getLogsSnapshotUseCase().filter { ... }
       val text = logs.joinToString("\n") { formatLogLine(it) }
       call.respondText(
           text,
           contentType = ContentType.Text.Plain,
           status = HttpStatusCode.OK
       ) {
           headers.append(HttpHeaders.ContentDisposition, "attachment; filename=logs.txt")
       }
   }
   ```

4. **MCP Tool** (`ExportLogsTool`)：
   - 工具名：`export_logs`
   - 参数：`format`, `query`, `tag`, `package_name`, `level`
   - 返回：`ToolResult.Value`（导出的文件内容或文件信息）

---

### 功能 3：录制管理端点

**需求**：通过 HTTP API 控制日志录制，包括开始、停止、列表、详情

**实现方案**：

1. **数据模型** (`LogRecordingInfo.kt`)
   - `id`、`title`、`dateAndTime`、`fileSize`、`filePath`

2. **四个端点**：

   - **`POST /record/start`** - 开始录制
     - 调用 `StartRecordingUseCase()`
     - 返回：`{ "status": "started", "message": "Recording started" }`

   - **`POST /record/stop`** - 停止录制
     - 调用 `EndRecordingUseCase()`
     - 返回：`{ "status": "stopped", "recording": LogRecordingInfo }`

   - **`GET /record/list`** - 获取录制列表
     - 调用 `GetAllRecordingsFlowUseCase().first()`
     - 返回：`{ "recordings": [LogRecordingInfo, ...], "count": N }`

   - **`GET /record/{id}`** - 获取录制详情
     - 调用 `GetRecordingByIdFlowUseCase(id).first()`
     - 返回单个 `LogRecordingInfo`，包含文件大小、创建时间等

3. **路由实现**（在 `McpRoutes.kt` 中）：
   - 注意处理录制中状态（`StartRecordingUseCase` 可能因已在录制中而失败）

---

### 功能 4：API 认证机制

**需求**：添加 API Key 认证，防止未授权访问

**实现方案**：

1. **认证方式**：API Key 通过请求头 `X-API-Key` 传递

2. **实现选择**：使用 Ktor 自定义拦截器（更轻量，无需引入 `ktor-server-auth` 完整依赖）

3. **认证配置** (`AuthConfig.kt`)：
   - 从 SharedPreferences 或配置中读取 API Key
   - 默认不启用认证（向后兼容）
   - 通过设置开关启用

4. **实现方式**：
   - 在 `McpRoutes` 的 `routing` 块中添加自定义拦截器
   - 对所有 `/logs`, `/query`, `/filters`, `/tools`, `/record`, `/mcp` 端点进行认证
   - `/health` 和 `/help` 可选择跳过认证
   - 认证失败返回 401 Unauthorized

5. **`/auth/config` 端点**（可选）：
   - 获取/设置 API Key（需现有权限验证）
   - 暂不实现，保持简单

---

### 功能 5：WebSocket 实时通信

**需求**：替代 SSE，支持双向通信（日志推送 + 命令发送）

**实现方案**：

1. **依赖添加**：
   - `gradle/libs.versions.toml` 增加 `ktor-server-websockets`
   - `app/build.gradle.kts` 添加 `implementation(libs.ktor.server.websockets)`

2. **WebSocket 端点**：`/ws`

3. **消息协议**（JSON）：

   **服务端 → 客户端**：
   - `log` - 新日志行（`{ "type": "log", "data": McpLogLine }`）
   - `recording_started` - 录制开始通知
   - `recording_stopped` - 录制停止通知
   - `ping` - 心跳

   **客户端 → 服务端**：
   - `set_query` - 设置过滤条件
   - `clear_logs` - 清空日志
   - `start_recording` - 开始录制
   - `stop_recording` - 停止录制
   - `pong` - 心跳响应

4. **实现结构**：
   - `McpWebSocketSession`：管理单个 WebSocket 连接状态
   - `McpWebSocketHandler`：处理入站消息，协调 UseCase 调用
   - 日志流通过 `StartLoggingUseCase` 获取，转换后通过 WebSocket 发送

5. **路由实现**：
   ```
   webSocket("/ws") {
       // 建立连接
       // 启动日志采集并流式推送
       // 监听入站消息，分发处理
   }
   ```

6. **SSE 兼容性**：
   - 保留原有的 `/logs` SSE 端点
   - WebSocket 作为增强功能并存

---

## 四、实现顺序建议

按依赖关系和风险从低到高排序：

1. **日志搜索**（依赖简单：`GetLogsSnapshotUseCase`）
2. **日志导出**（依赖搜索过滤逻辑，复用搜索代码）
3. **录制管理**（依赖现有 recording UseCase）
4. **API 认证**（横切关注点，独立于业务逻辑）
5. **WebSocket**（最复杂，涉及双向通信、状态管理）

每完成一个功能即可独立推送验证。

---

## 五、潜在风险与注意事项

### 风险 1：日志搜索内存性能
- **风险**：当日志量很大（数万条）时，全量快照 + 内存过滤可能造成卡顿
- **缓解**：设置合理的默认 `limit`（如 1000 条），支持分页返回

### 风险 2：导出文件大小
- **风险**：全量日志导出可能生成很大的响应体
- **缓解**：默认限制最大导出条数（如 50000 条），超出时提示分批导出

### 风险 3：录制并发控制
- **风险**：`StartRecordingUseCase` 在已录制中调用的行为未明确
- **缓解**：在路由层检查录制状态，返回明确的错误信息

### 风险 4：WebSocket 内存泄漏
- **风险**：连接断开时未正确取消协程和 Flow 收集
- **缓解**：使用 `try-finally` 确保资源释放，`coroutineScope` 结构化并发

### 风险 5：认证默认值
- **风险**：启用认证后可能导致现有客户端无法连接
- **缓解**：默认关闭认证，通过设置或首次启动配置启用

### 风险 6：Ktor 3.x WebSocket API 兼容性
- **风险**：Ktor 3.x 的 WebSocket API 可能与 2.x 有差异
- **缓解**：参考 Ktor 3.x 官方文档，使用正确的 API

---

## 六、开发规范遵循

1. **架构边界**：MCP 功能仍在 `app/mcp/` 内，不破坏 Clean Architecture
   - `api/`：公开接口和模型（`McpServerManager`, `McpTool`, model 类）
   - `impl/`：内部实现（路由、工具实现、认证、WebSocket）

2. **命名规范**：
   - 数据类：`SearchRequest`, `ExportRequest`, `LogRecordingInfo`
   - 工具类：`SearchLogsTool`, `ExportLogsTool`（`McpTool` 实现）

3. **依赖注入**：
   - 新 UseCase 通过构造函数注入 `McpRoutes` 和 `McpServerManagerImpl`
   - 保持 `McpServerManagerImpl` 的 `buildToolsMap()` 模式

4. **日志**：统一使用 `[MCP]` TAG，遵循现有 Timber 日志风格

5. **错误处理**：所有端点 try-catch，返回统一的 `{ "error": "message" }` 格式
