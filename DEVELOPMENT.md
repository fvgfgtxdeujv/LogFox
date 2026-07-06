# LogFox 开发文档

## 项目概述

LogFox 是一个 Android LogCat 阅读器，支持 Shizuku、Root 和 ADB 三种日志访问方式。本文档覆盖项目结构、MCP Server 功能设计、CI/CD 操作流程及问题排查。

## 环境变量

本机 `~/.claude/.env` 文件中已配置 `LogFox-Cli` token：

```powershell
$env:LOGFOX_TOKEN = (Get-Content "$HOME\.claude\.env" | Select-String "LogFox-Cli").ToString().Split('=')[1]
```

## 构建命令

```powershell
./gradlew :app:assembleDebug --quiet          # 构建 Debug APK
./gradlew testDebugUnitTest --quiet            # 运行单元测试
./gradlew verifyRoborazziDebug --quiet         # 快照测试（CI 使用）
```

## 架构说明

**模块组织**（Clean Architecture）：
```
feature/<name>/
  api/            # 领域接口、模型
  impl/           # 仓库实现、数据源
  presentation/   # ViewModel、Fragment/Composable
```

**依赖规则**：
- `presentation -> api` only
- `impl -> api`
- 只有 `:app` 聚合 `presentation` + `impl`

## 项目结构

```
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

## MCP 功能设计

### 核心设计

- **位置**: `app/src/main/kotlin/com/f0x1d/logfox/mcp/`
- **运行方式**: Android 前台服务 (`McpServerService`)
- **网络**: 监听 `0.0.0.0:8765`
- **协议**: JSON-RPC 2.0 (`/mcp`) + SSE (`/logs`)

### API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/logs` | GET | SSE 日志流 |
| `/logs/clear` | POST | 清空日志 |
| `/query` | GET | 获取过滤条件 |
| `/query/set` | POST | 设置过滤条件 |
| `/help` | GET | 获取所有端点帮助文档 |
| `/filters` | GET | 获取启用的过滤器 |
| `/tools` | GET | 工具列表 |
| `/tools/{name}/call` | POST | 调用工具 |
| `/mcp` | POST | JSON-RPC 2.0 入口 |
| `/health` | GET | 健康检查 |

### 可用工具

| 工具名 | 说明 | 参数 |
|--------|------|------|
| `read_logs` | 读取日志流 | `mode`: stream/dump |
| `set_query` | 设置过滤条件 | `query`: 过滤字符串 |
| `get_query` | 获取过滤条件 | 无 |
| `clear_logs` | 清空日志 | 无 |
| `get_filters` | 获取过滤器 | 无 |

### 模块架构

```
McpServerService (@AndroidEntryPoint)
    └── McpServerManagerImpl (@Singleton)
            ├── StartLoggingUseCase
            ├── GetLastLogUseCase
            ├── ClearLogsUseCase
            ├── GetQueryFlowUseCase
            ├── UpdateQueryUseCase
            ├── GetAllEnabledFiltersFlowUseCase
            └── GetSelectedTerminalUseCase

ReadLogsTool (@Inject)
    └── 直接注入 UseCase，不依赖 McpServerDeps 单例

McpServerModule (@Binds)
    └── McpServerManagerImpl → McpServerManager
```

### 修改记录

- `app/build.gradle.kts`: 添加 Ktor 和 kotlinx-serialization
- `AndroidManifest.xml`: 注册 McpServerService
- `LogFoxApp.kt`: 创建 MCP 通知渠道；注入 `McpServerDeps`
- `di/McpServerModule.kt`: Hilt @Binds 绑定
- `mcp/impl/McpServerManagerImpl.kt`: 移除单例依赖，直接注入 UseCase；修复停止逻辑
- `mcp/impl/tools/ReadLogsTool.kt`: 添加 `getSelectedTerminalUseCase` 参数

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

```powershell
python -c "
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
"
```

## 问题修复记录

| 时间 | 问题 | 修复 |
|------|------|------|
| 2026-07-05 | Ktor 3.x 扩展函数无法解析 | `fun Application.mcpRoutes()` → `fun mcpRoutes(application: Application)` |
| 2026-07-05 | 协程函数在非协程上下文调用 | `start()` 中提前调用 `selectedTerminal()` |
| 2026-07-05 | `buildJsonArray` 类型推断失败 | 显式 `JsonArray(listOf(...))` |
| 2026-07-05 | `mapOf` 类型推断失败 | 改用 `LinkedHashMap<String, McpTool>()` |
| 2026-07-05 | `launch` deprecated | `mcpRoutes` 改为非 suspend 函数 |
| 2026-07-06 | Hilt MissingBinding | 创建 `McpServerModule.kt`，使用 `@Binds` |
| 2026-07-06 | `McpServerDeps` 单例初始化延迟 | 重构为直接注入 UseCase |
| 2026-07-06 | 服务器停止逻辑无效 | 添加 `(server as? ApplicationEngine)?.stop()` |
| 2026-07-06 | `ReadLogsTool` 构造函数参数缺失 | 添加 `getSelectedTerminalUseCase` 参数 |

## 构建要求

- **JDK**: 21 (Temurin)
- **产物**: `app/build/outputs/apk/debug/LogFox-unknown-debug.apk`

## 已知问题

### Windows AIDL 编译编码错误

AGP 9.2 + JDK 21 在 Windows 上编译 AIDL 时遇到非 ASCII 字符会报错：
```
java.nio.charset.MalformedInputException: Input length = 1
```

**解决方案**:
1. 使用 Android Studio 构建
2. 或临时删除 `feature/terminals/impl/src/main/res/values-*` 下的非英文 strings.xml

## 常见问题排查

| 错误类型 | 典型信息 | 修复方式 |
|----------|----------|----------|
| 导入路径错误 | `Unresolved reference` | 检查包路径 |
| 依赖缺失 | `Unresolved reference: ContentNegotiation` | 添加依赖 |
| Ktor API 变更 | `embeddedServer` 返回类型不匹配 | 改用 `Any` 存储 server |
| 类型推断失败 | `Cannot infer type` | 显式指定泛型 |
| 序列化错误 | `Argument type mismatch: List<String>` | `listOf` → `buildJsonArray` |
| Hilt 绑定缺失 | `Dagger/MissingBinding` | 创建 `@Binds` 或 `@Provides` |
| 单例初始化延迟 | `lateinit property ... not initialized` | 直接注入 UseCase |
| 协程调用错误 | `Suspension functions can only be called within coroutine body` | 在 `suspend` 函数中调用 |
| 构造函数参数缺失 | `No value passed for parameter` | 添加缺失参数 |
