# 需求实施计划

- [ ] 1. 数据层：新增 `mcpServerEnabled` 偏好（R1 开关持久化）
  - [ ] 1.1 在 `ServiceSettingsLocalDataSource` 接口中新增 `mcpServerEnabled(): Preference<Boolean>`
    - 文件：`feature/preferences/impl/data/service/ServiceSettingsLocalDataSource.kt`
  - [ ] 1.2 在 `ServiceSettingsLocalDataSourceImpl` 中实现，key=`pref_mcp_server_enabled`，默认 `false`
    - 文件：`feature/preferences/impl/data/service/ServiceSettingsLocalDataSourceImpl.kt`
  - [ ] 1.3 在 `ServiceSettingsRepository` 接口中新增 `mcpServerEnabled(): PreferenceStateFlow<Boolean>`
    - 文件：`feature/preferences/api/data/ServiceSettingsRepository.kt`
  - [ ] 1.4 在 `ServiceSettingsRepositoryImpl` 中新增代理实现
    - 文件：`feature/preferences/impl/data/service/ServiceSettingsRepositoryImpl.kt`

- [ ] 2. MCP API 层：新增 `toolsCount` 属性（R5 状态详情）
  - [ ] 2.1 在 `McpServerManager` 接口中新增 `val toolsCount: Int`
    - 文件：`mcp/api/McpServerManager.kt`
  - [ ] 2.2 在 `McpServerManagerImpl` 中实现 `toolsCount`
    - 文件：`mcp/impl/McpServerManagerImpl.kt`

- [ ] 3. 字符串资源：新增错误提示和详情标签（R3/R4/R5/R6）
  - [ ] 3.1 英文 strings.xml 新增 10 个字符串
    - 文件：`strings/src/main/res/values/strings.xml`
  - [ ] 3.2 中文 strings.xml 新增对应的 10 个字符串
    - 文件：`strings/src/main/res/values-zh-rCN/strings.xml`

- [ ] 4. 检查点：数据层编译验证
  - 确保 preferences 和 mcp 模块编译通过

- [ ] 5. XML 布局：新增内联详情 Preference（R4 状态展示 + R5 详情展开）
  - [ ] 5.1 在 `settings_service.xml` 的 MCP Category 中新增 4 个详情 Preference，默认 `isPreferenceVisible=false`
    - 文件：`feature/preferences/presentation/src/main/res/xml/settings_service.xml`
  - [ ] 5.2 将状态栏 Preference `selectable` 改为 `true`
    - 文件：同上

- [ ] 6. Fragment 重构：MCP 开关持久化 + 可见性控制（R1 + R2）
  - [ ] 6.1 在 `onCreatePreferences` 中从持久化读取初始开关状态，设置 `isChecked` 和初始 UI 可见性
    - 文件：`feature/preferences/presentation/.../PreferencesServiceFragment.kt`
  - [ ] 6.2 修改开关 `setOnPreferenceChangeListener`：开启时持久化并启动服务 + 显示 UI；关闭时弹出确认对话框
    - 文件：同上
  - [ ] 6.3 实现 `updateMcpUiVisibility()` 批量控制子 Preference 可见性
    - 文件：同上
  - [ ] 6.4 移除旧的独立 `pref_mcp_server_start`/`pref_mcp_server_stop` 点击监听（开关已接管启停）
    - 文件：同上

- [ ] 7. Fragment 重构：自检逻辑（R3 启动后自检）
  - [ ] 7.1 实现 `performHealthCheck(port)` 方法，HTTP GET `127.0.0.1:{port}/health`，超时 2s
    - 文件：`PreferencesServiceFragment.kt`
  - [ ] 7.2 修改 `startMcpServer()`：启动后 `delay(500ms)` 调用自检，更新状态栏 summary 和详情 pref
    - 文件：同上
  - [ ] 7.3 实现初始化时检查后台服务状态逻辑（如开关已开则自检并恢复状态显示）
    - 文件：同上

- [ ] 8. Fragment 重构：状态详情内联展开（R5 详情展开）
  - [ ] 8.1 为状态栏 Preference 设置点击监听，切换 `detailExpanded` 并调用 `toggleDetailPreferences()`
    - 文件：`PreferencesServiceFragment.kt`
  - [ ] 8.2 实现 `toggleDetailPreferences(expanded)` 控制 4 个详情 pref 的 `isVisible`
    - 文件：同上

- [ ] 9. Fragment 重构：关闭确认对话框（R6 关闭确认）
  - [ ] 9.1 实现 `showMcpDisableConfirmDialog()` 方法，确认后持久化 false + 停止服务 + 隐藏 UI + 重置 isChecked
    - 文件：`PreferencesServiceFragment.kt`

- [ ] 10. 检查点：全量编译验证
  - 确保 `assembleRelease` 编译通过
