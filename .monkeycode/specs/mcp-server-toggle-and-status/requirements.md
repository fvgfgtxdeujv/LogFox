# Requirements Document

## Introduction

为 LogFox MCP 服务器添加启用开关持久化能力，开关关闭时隐藏所有 MCP 相关 UI，开启时自动启动服务并在启动后自检，在服务器状态栏展示运行结果，状态栏右侧添加展开箭头，点击后显示详细状态信息。

## Glossary

- **MCP Server**: Model Context Protocol 服务器，在 Android 设备上提供 HTTP/WebSocket 接入点
- **Self-Check**: 启动 MCP 服务器后，验证其是否真正在监听端口的自动化检测
- **Enable Toggle**: MCP 功能的总开关，位于 Service 设置页面
- **Status Detail**: 包含端口、绑定地址、认证状态、工具数量等完整运行信息的展示面板

## Requirements

### Requirement 1: 开关持久化

**User Story:** AS 用户, I want MCP 启用开关状态在重启应用后保持, so that 我不需要每次都重新开启

#### Acceptance Criteria

1. WHEN 用户切换 MCP 启用开关至开启, THE 系统 SHALL 将开启状态持久化到 SharedPreferences
2. WHEN 用户切换 MCP 启用开关至关闭, THE 系统 SHALL 将关闭状态持久化到 SharedPreferences，并停止 MCP 服务器
3. WHEN 用户重新进入 Service 设置页面, THE 系统 SHALL 从 SharedPreferences 读取开关状态并渲染
4. WHEN 开关开启且 MCP 服务器已在运行, THE 系统 SHALL 不再重复启动

### Requirement 2: 开关关闭时隐藏 MCP UI

**User Story:** AS 用户, I want 关闭 MCP 后看不到相关设置项, so that 界面简洁，不混淆

#### Acceptance Criteria

1. WHILE MCP 启用开关处于关闭状态, THE 系统 SHALL 隐藏以下设置项：服务器状态、端口、绑定地址、启动按钮、停止按钮
2. WHEN 开关从关闭切换至开启, THE 系统 SHALL 立即显示被隐藏的设置项
3. WHEN 开关从开启切换至关闭, THE 系统 SHALL 立即隐藏除开关本体外的所有 MCP 设置项

### Requirement 3: 启动后自检

**User Story:** AS 用户, I want 知道 MCP 服务器是否成功启动, so that 我能确认服务可用

#### Acceptance Criteria

1. WHEN MCP 服务器启动完成, THE 系统 SHALL 向 `http://127.0.0.1:{port}/health` 发起 HTTP GET 请求
2. WHEN 自检返回 200 OK, THE 系统 SHALL 在服务器状态栏显示运行中状态
3. IF 自检失败（连接被拒或非 200）, THE 系统 SHALL 在服务器状态栏显示启动失败信息

### Requirement 4: 服务器状态展示

**User Story:** AS 用户, I want 看到 MCP 服务器的运行状态, so that 我能了解服务是否正常

#### Acceptance Criteria

1. WHILE MCP 服务器正在运行, THE 状态栏 SHALL 显示当前端口号，格式为 "Running on port {port}"
2. WHILE MCP 服务器未运行, THE 状态栏 SHALL 显示 "Stopped"
3. WHEN 服务器状态栏的 summary 更新, THE 系统 SHALL 在 500ms 内完成渲染
4. WHEN 服务器状态栏右侧被点击（展开箭头), THE 系统 SHALL 弹出底部面板展示详细状态信息

### Requirement 5: 状态详情内联展开

**User Story:** AS 用户, I want 展开查看 MCP 服务器的详细运行信息, so that 我能了解完整的服务配置

#### Acceptance Criteria

1. WHEN 用户点击服务器状态栏的展开箭头, THE 系统 SHALL 在状态栏下方内联展开详情区域
2. THE 详情区域 SHALL 包含以下信息：端口号、绑定地址、认证状态（启用/关闭）、可用工具数量
3. WHEN 用户再次点击展开箭头, THE 系统 SHALL 折叠详情区域

### Requirement 6: 关闭确认对话框

**User Story:** AS 用户, I want 关闭 MCP 服务器前得到确认, so that 我不会误操作关闭服务

#### Acceptance Criteria

1. WHEN 用户将启用开关从开启切换至关闭, THE 系统 SHALL 弹出确认对话框
2. THE 确认对话框 SHALL 包含 "关闭 MCP 服务器将停止 AI 集成功能" 的提示文案
3. WHEN 用户点击确认, THE 系统 SHALL 关停服务器并隐藏 MCP UI
4. IF 用户点击取消, THE 系统 SHALL 保持开关开启且服务器继续运行
