# LogFox 开发文档

## 项目概述

LogFox 是一个 Android LogCat 阅读器，支持 Shizuku、Root 和 ADB 三种日志访问方式。本文档覆盖项目结构、MCP Server 功能开发全过程、构建方法及踩坑记录。

## 构建命令

所有 Gradle 任务统一使用 `--quiet` 标志：

```powershell
./gradlew :app:assembleDebug --quiet          # 构建 Debug APK
./gradlew testDebugUnitTest --quiet      # 运行单元测试
./gradlew verifyRoborazziDebug --quiet   # 运行快照测试（CI 使用此命令）
```

## 架构说明

### 模块组织

LogFox 采用 Clean Architecture 多模块架构：

```
feature/<name>/
  api/            # 领域接口、模型、仓库接口
  impl/           # 仓库实现、数据源（internal）、DTO（internal）
  presentation/   # ViewModel、Fragment/Composable、ViewState
```

```
core/
  tea/base        # 纯 Kotlin TEA 原语（Store、Reducer、EffectHandler）
  tea/android     # BaseStoreViewModel、BaseStoreFragment、ViewStateMapper
  ui/compose      # Compose 工具
  ui/view         # 基于 View 的 UI 工具
  di, io, preferences, context, ...
```

**依赖规则**：
- `presentation -> api` only（禁止导入 `impl`）
- `impl -> api`（仅依赖自身模块的 api）
- 只有 `:app` 聚合 `presentation` + `impl` 模块

### UI 模式

- **Container**（Fragment）：持有 ViewModel 生命周期，收集状态/副作用，处理导航
- **Passive views/composables**：渲染 ViewState，暴露回调，不含业务逻辑
- 导航由副作用驱动：Reducer 发射 `SideEffect.Navigate*`，Container Fragment 通过 Navigation Component 处理

### 命名规范

- 一个顶级类型对应一个文件；文件名与类型名一致
- Use Case 暴露 `operator fun invoke`；可能失败的操作返回 `Result<T>`
- Hilt 绑定返回接口（`@Binds`），而非实现类型
- 命名规范：`<Feature>ViewModel`、`<Feature>Reducer`、`<Feature>EffectHandler`、`<Feature>ViewStateMapper`

### Gradle 与依赖

- **版本目录**：所有依赖和插件通过 `libs` 访问（见 `gradle/libs.versions.toml`）
- **类型安全项目访问器**：通过 `TYPESAFE_PROJECT_ACCESSORS` 启用
- **Convention 插件**位于 `build-logic/conventions/`：
  - `logfox.android.feature` — 功能模块（Android Library + Hilt）
  - `logfox.android.feature.compose` — 启用 Compose 的功能模块
  - `logfox.android.library` — 标准 Android 库模块
  - `logfox.kotlin.jvm` — 纯 Kotlin 模块
  - `logfox.android.compose` — Compose 配置
  - `logfox.android.room` — Room 数据库配置

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
│       │       ├── McpRoutes.kt      # Ktor 路由（普通成员函数，非扩展函数）
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

## MCP 功能设计

### 核心设计

- **Server 位置**: `app/src/main/kotlin/com/f0x1d/logfox/mcp/`
- **运行方式**: Android 前台服务 (`McpServerService`)
- **网络**: 监听 `0.0.0.0:8765`
- **协议**: JSON-RPC 2.0（MCP stdio 协议映射到 HTTP POST `/mcp`）+ SSE（`/logs`）

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

### MCP 模块架构

```
McpServerService (Hilt @AndroidEntryPoint, Android 前台服务)
    └── McpServerManagerImpl (@Singleton, @Inject)
            ├── StartLoggingUseCase
            ├── GetLastLogUseCase
            ├── ClearLogsUseCase
            ├── GetQueryFlowUseCase
            ├── UpdateQueryUseCase
            └── GetAllEnabledFiltersFlowUseCase

McpRoutes (Ktor 路由配置)
    ├── /logs         GET  — SSE 日志流
    ├── /logs/clear   POST — 清空日志
    ├── /query        GET  — 获取过滤条件
    ├── /query        POST — 设置过滤条件
    ├── /filters      GET  — 获取所有过滤器
    ├── /tools        GET  — 工具列表
    ├── /tools/{name}/call POST — 调用指定工具
    ├── /mcp          POST — JSON-RPC 2.0 入口
    └── /health       GET  — 健康检查

McpServerDeps (单例，由 McpServerDepsModule 在启动时注入)
    └── GetSelectedTerminalUseCase → selectedTerminal()
```

### 模块依赖关系

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

### 修改记录

- `app/build.gradle.kts`: 添加 Ktor 和 kotlinx-serialization 依赖
- `app/src/main/AndroidManifest.xml`: 注册 McpServerService
- `feature/notifications/api/.../NotificationChannelIds.kt`: 添加 MCP 通知渠道 ID
- `strings/src/main/res/values/strings.xml`: 添加 MCP 通知相关字符串
- `app/src/main/kotlin/.../LogFoxApp.kt`: 创建 MCP 通知渠道

## 开发时间线与 CI/CD

### 开发时间线

| 时间 | 事件 |
|------|------|
| 2026-07-05 03:24 | 开始逆向分析 LogFox APK，提取代码 |
| 2026-07-05 03:49 | 将提取代码推送到 fvgfgtxdeujv/LogFox 仓库 |
| 2026-07-05 05:12 | 修改 workflow 为 push 自动触发构建 |
| 2026-07-05 05:15 | 配置 GitHub Token（含 actions/workflows 权限） |
| 2026-07-05 05:22 | 第一次 CI 构建报错，开始修复编译问题 |
| 2026-07-05 06:26 | 修复 5 个编译错误，commit a8d3eb4 |
| 2026-07-05 06:36 | 修复 Ktor 3.x 扩展函数 + 协程上下文问题，commit eef172f |
| 2026-07-05 08:03 | 修复 launch deprecated 问题，commit da06a5d |

### CI/CD 流程

**Workflow 文件**: `.github/workflows/build_release.yml`

- **触发条件**: push 到 `master` 分支自动触发，或手动 `workflow_dispatch`
- **构建环境**: Ubuntu 24.04, JDK 21 (Temurin), Gradle
- **产物**: Release APK 上传为 GitHub Actions artifact
- **Release**: 手动触发时可选择创建 GitHub Release

**GitHub Token 配置**:
- 令牌名: `LogFox-Cli`（存储在 `~/.claude/.env`）
- 权限: `contents: write`, `actions: read`, `workflows: write`
- 工作流：push 触发 CI → 下载 LOG 分析报错 → 修复 → 重新 push

### 操作流程

1. 修改代码后 `git push` 触发 CI
2. 等待构建完成（约 4~5 分钟）
3. 通过 GitHub API 下载构建日志
4. 分析报错，本地修复
5. 重新 push，重复直到构建成功

## 编译错误修复记录

本次开发共遇到 5 个编译错误，全部解决：

### 1. McpRoutes.kt — 扩展函数无法解析（Ktor 3.x）

**错误**: `Unresolved reference 'mcpRoutes'`

**原因**: Ktor 3.x 的 `embeddedServer` lambda 中，扩展接收者 `Application` 无法正确解析外部定义的扩展函数。

**修复**: 将 `fun Application.mcpRoutes()` 改为 `fun mcpRoutes(application: Application)` 普通成员函数，`install` 和 `routing` 前加 `application.`。

### 2. McpServerManagerImpl.kt — 协程函数在非协程上下文中调用

**错误**: `Suspension functions can only be called within coroutine body`

**原因**: `McpServerDeps.selectedTerminal()` 是 `suspend` 函数，在 `embeddedServer` lambda 中直接调用。

**修复**: 在 `start()`（`suspend` 函数）中提前调用 `selectedTerminal()` 获取值，作为参数传入 `mcpRoutes`。

### 3. ReadLogsTool.kt — `buildJsonArray` 类型推断失败

**错误**: `Argument type mismatch`

**原因**: Kotlin 类型推断无法确定 `buildJsonArray` 中的元素类型。

**修复**: 改为显式 `JsonArray(listOf(JsonPrimitive("stream"), JsonPrimitive("dump")))`。

### 4. McpServerManagerImpl.kt — `mapOf` 类型推断失败

**错误**: `Type mismatch` / `Argument type mismatch`

**原因**: `mapOf` 的类型推断无法推断 `McpTool` 接口类型。

**修复**: 改用 `LinkedHashMap<String, McpTool>()` 并配合 `@Suppress("UNCHECKED_CAST")`。

### 5. McpServerManagerImpl.kt — `launch` deprecated

**错误**: `'fun launch(...)' is deprecated. 'launch' can not be called without the corresponding coroutine scope.`

**原因**: `embeddedServer` lambda 是 `Application.() -> Unit`，不是 `CoroutineScope.() -> Unit`，直接调用 `launch` 会走顶层 deprecated 函数。

**修复**: 将 `mcpRoutes` 改为非 suspend 函数，所有 `suspend` 调用在 `start()` 中提前 resolve。

## 构建要求

### JDK 版本

- **必需**: JDK 21 (Temurin 推荐)
- 项目配置 `JVM_VERSION = 21`，位于 `build-logic/conventions/src/main/kotlin/com/f0x1d/logfox/buildlogic/extensions/Constants.kt`

### 编译步骤

```powershell
# 设置 JDK 21
$env:JAVA_HOME = "C:\Users\q\.jwmv\candidates\java\21.35-tem"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# 清理并构建
./gradlew clean
./gradlew :app:assembleDebug --quiet
```

### 构建产物

- Debug APK: `app/build/outputs/apk/debug/LogFox-unknown-debug.apk`

## 注意事项

- MCP 服务需要 Root 设备才能正常工作（日志读取权限）
- 服务启动时会自动在 8765 端口监听
- 日志流通过 SSE (Server-Sent Events) 推送

## 已知构建问题

### Windows AIDL 编译编码错误

**现象**: 构建时 `:feature:terminals:presentation:compileDebugAidl` 报错：
```
java.nio.charset.MalformedInputException: Input length = 1
```

**原因**: AGP 9.2 + JDK 21 在 Windows 上编译 AIDL 时，遇到非 ASCII 字符（如俄语、中文 localized strings）会编码错误。这是**项目原有 issue**，与 MCP 功能无关——即使 `git stash` 后原项目也会报同样错误。

**临时绕过方案**:
1. 在 Windows 上使用 Android Studio 构建（其内置的 AIDL 编译器处理编码更健壮）
2. 或临时删除 `feature/terminals/impl/src/main/res/values-*` 下的非英文 strings.xml
3. 或升级 AGP 到修复此问题的版本

**注意**: 此错误不影响 MCP 代码本身——MCP 相关模块（`app`、`mcp/`）的代码逻辑是正确的。
