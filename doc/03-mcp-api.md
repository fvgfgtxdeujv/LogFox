# MCP 功能设计

## 核心设计

| 属性     | 说明                                          |
| -------- | --------------------------------------------- |
| 位置     | `app/src/main/kotlin/com/f0x1d/logfox/mcp/`   |
| 运行方式 | Android 前台服务 (`McpServerService`)         |
| 网络     | 监听 `0.0.0.0:8765`                           |
| 协议     | MCP 2025-11-25 / 2026-07-28 + JSON-RPC 2.0   |

---

## MCP 规范端点

遵循 Model Context Protocol (MCP) 官方规范，支持标准 MCP 客户端连接：

| 端点           | 方法   | 说明                                                  |
| -------------- | ------ | ----------------------------------------------------- |
| `/server/discover` | POST   | 服务器发现，返回名称、版本、协议版本、能力 |
| `/tools/list`   | GET/POST | 返回所有可用工具列表（含 JSON Schema）      |
| `/tools/call`   | POST   | 调用工具，支持 `_meta` 和 `resultType`     |

---

## 自定义 API 端点

| 端点                 | 方法   | 说明                          |
| -------------------- | ------ | ----------------------------- |
| `/logs`              | GET    | SSE 日志流                    |
| `/logs/clear`        | POST   | 清空日志                      |
| `/logs/search`       | POST   | 搜索历史日志（支持包含/排除过滤组、完整字段、大小写敏感、多级别筛选） |
| `/logs/export`       | POST   | 导出日志为文件（txt/log，支持包含/排除过滤组、完整字段、大小写敏感、多级别筛选） |
| `/query`             | GET    | 获取过滤条件                  |
| `/query/set`         | POST   | 设置过滤条件                  |
| `/filters`           | GET    | 获取启用的过滤器              |
| `/record/start`      | POST   | 开始录制                      |
| `/record/stop`       | POST   | 停止录制                      |
| `/record/list`       | GET    | 获取录制列表                  |
| `/record/{id}`       | GET    | 获取录制详情                  |
| `/tools`             | GET    | 工具列表（兼容模式）          |
| `/tools/{name}/call` | POST   | 调用工具（兼容模式）          |
| `/mcp`               | POST   | JSON-RPC 2.0 入口（兼容模式） |
| `/health`            | GET    | 健康检查                      |
| `/help`              | GET    | 获取所有端点帮助文档          |
| `/ws`                | WS     | WebSocket 实时通信            |

---

## 可用工具

| 工具名         | 说明             | 参数                                       |
| -------------- | ---------------- | ------------------------------------------ |
| `read_logs`    | 读取日志流       | `mode`: stream/dump                        |
| `search_logs`  | 搜索历史日志     | `include`, `exclude`, `levels`, `limit`, `offset` |
| `export_logs`  | 导出日志为文本   | `format`, `include`, `exclude`, `levels`, `limit` |
| `set_query`    | 设置过滤条件     | `query`: 过滤字符串                        |
| `get_query`    | 获取过滤条件     | 无                                         |
| `clear_logs`   | 清空日志         | 无                                         |
| `get_filters`  | 获取过滤器       | 无                                         |

---

## 过滤参数说明

### 过滤组（FilterGroup）

包含在 `include` 和 `exclude` 中：

| 字段 | 类型 | 说明 |
|------|------|------|
| `uid` | String | 用户 ID 匹配 |
| `pid` | String | 进程 ID 匹配 |
| `tid` | String | 线程 ID 匹配 |
| `packageName` | String | 包名匹配 |
| `tag` | String | 标签匹配 |
| `content` | String | 日志内容匹配 |
| `caseSensitive` | Boolean | 是否大小写敏感，默认 `false` |

### 日志级别

`levels` 数组，支持 V/D/I/W/E/F（详细/调试/信息/警告/错误/严重）

### 逻辑说明

- **include**：所有条件必须满足（AND）
- **exclude**：任何条件满足则排除（OR）
- **levels**：匹配任一指定级别（OR）
- **caseSensitive**：可在 include 和 exclude 中分别设置

### 示例请求

```json
{
  "include": {
    "tag": "AndroidRuntime",
    "content": "Exception",
    "caseSensitive": false
  },
  "exclude": {
    "packageName": "system",
    "caseSensitive": true
  },
  "levels": ["E", "W"],
  "limit": 100
}
```

---

## 请求/响应格式

### server/discover 请求

```json
{"jsonrpc":"2.0","id":1,"method":"server/discover"}
```

### server/discover 响应

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "name": "LogFox MCP Server",
    "version": "1.0.0",
    "description": "LogCat reader MCP server for Android",
    "protocolVersions": ["2025-11-25", "2026-07-28"],
    "capabilities": {"tools": {}}
  }
}
```

### tools/call 请求

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "get_query",
    "arguments": {}
  },
  "_meta": {
    "io.modelcontextprotocol/protocolVersion": "2026-07-28"
  }
}
```

### tools/call 响应

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "resultType": "complete",
    "content": [{"type": "text", "text": "..."}]
  }
}
```

---

## API 认证

- 默认关闭认证，向后兼容
- 通过 `X-API-Key` 请求头传递 API Key
- `/health` 和 `/help` 端点跳过认证
- 认证失败返回 401 Unauthorized

---

## WebSocket 通信

- **端点**: `/ws`
- **服务端 → 客户端**：`log`（日志行）、`recording_started`、`recording_stopped`、`connected`
- **客户端 → 服务端**：`set_query`、`clear_logs`、`start_recording`、`stop_recording`、`pong`
- 保留 SSE `/logs` 端点继续可用

---

## 网络配置

### 端口配置

- 默认端口：`8765`
- 用户可在偏好设置中修改端口号
- 修改端口后自动重启服务应用新端口

### 绑定地址

- 默认地址：`0.0.0.0`（允许局域网访问）
- 可选地址：`127.0.0.1`（仅限本机访问，更安全）
- 用户可在偏好设置中选择绑定地址
- 修改绑定地址后自动重启服务应用新地址

---

## 修改记录

- `app/build.gradle.kts`：添加 Ktor 和 kotlinx-serialization、ktor-server-websockets
- `gradle/libs.versions.toml`：添加 ktor-server-websockets 依赖
- `AndroidManifest.xml`：注册 `McpServerService`
- `LogFoxApp.kt`：创建 MCP 通知渠道；注入 `McpServerDeps`
- `di/McpServerModule.kt`：Hilt `@Binds` 绑定
- `mcp/impl/McpServerManagerImpl.kt`：移除单例依赖，直接注入 UseCase；修复停止逻辑；添加录制、认证、WebSocket 支持
- `mcp/impl/McpRoutes.kt`：添加日志搜索、导出、录制管理、API 认证、WebSocket 路由；添加 MCP 标准端点；支持 `_meta` 参数和 `resultType` 返回字段；更新搜索/导出过滤逻辑
- `mcp/impl/tools/SearchLogsTool.kt`：日志搜索 MCP 工具；支持包含/排除过滤组、多级别筛选、大小写敏感匹配
- `mcp/impl/tools/ExportLogsTool.kt`：日志导出 MCP 工具；支持包含/排除过滤组、多级别筛选、大小写敏感匹配
- `mcp/impl/tools/ReadLogsTool.kt`：添加 `getSelectedTerminalUseCase` 参数；更新 inputSchema
- `mcp/impl/tools/SetQueryTool.kt`、`GetQueryTool.kt`、`ClearLogsTool.kt`、`GetFiltersTool.kt`：更新 inputSchema
- `mcp/impl/auth/AuthConfig.kt`：API 认证配置
- `mcp/impl/websocket/`：WebSocket 会话管理和消息处理器
- `mcp/api/model/`：新增 SearchRequest、SearchResponse、ExportRequest、LogRecordingInfo、FilterGroup 模型
- `feature/preferences/api/data/ServiceSettingsRepository.kt`：添加 `mcpServerPort()`、`mcpServerHost()` 方法
- `feature/preferences/impl/data/service/ServiceSettingsLocalDataSource.kt`：添加 `mcpServerPort()`、`mcpServerHost()` 方法
- `feature/preferences/impl/data/service/ServiceSettingsLocalDataSourceImpl.kt`：实现 `mcpServerPort()`（默认值 8765）、`mcpServerHost()`（默认值 0.0.0.0）
- `feature/preferences/impl/data/service/ServiceSettingsRepositoryImpl.kt`：实现 `mcpServerPort()`、`mcpServerHost()`
- `feature/preferences/presentation/service/ui/PreferencesServiceFragment.kt`：保存端口和绑定地址到 SharedPreferences，启动服务时传递，修改后自动重启服务；添加绑定地址选择对话框
- `mcp/impl/McpServerService.kt`：从 Intent 读取端口号和绑定地址，回退到 SharedPreferences，支持动态网络配置
- `mcp/api/McpServerManager.kt`：修改 `start()` 方法添加 `host` 参数
- `mcp/impl/McpServerManagerImpl.kt`：使用 `host` 参数绑定指定 IP 地址
