# 任务列表 — 日志列表智能分组与交互优化

## 1. 数据模型层变更

- [x] 1.1 新建 `LogListItem.kt` — sealed class 定义 Item / Group
- [x] 1.2 新建 `ScrollFabIcon.kt` — FAB 图标枚举
- [x] 1.3 修改 `LogsState.kt` — 新增 groupExpandStates、isAtBottom
- [x] 1.4 修改 `LogsViewState.kt` — logs → items（List&lt;LogListItem&gt;），新增 scrollFabIcon
- [x] 1.5 修改 `LogsCommand.kt` — 新增 GroupToggled、ExpandAllGroups、CollapseAllGroups、ScrollStateChanged

## 2. 分组算法实现

- [x] 2.1 新建 `LogGrouper.kt` — 分组算法 + 单元测试
- [x] 2.2 修改 `LogsViewStateMapper.kt` — 集成 LogGrouper

## 3. 多 ViewType Adapter

- [x] 3.1 修改 `BaseListAdapter.kt` — 支持多 ViewType（新增 createHolder 重载）
- [x] 3.2 修改 `LogsAdapter.kt` — 多 ViewType 支持、自定义 DiffCallback、setHasStableIds
- [x] 3.3 创建 `item_group_log.xml` — 分组条目布局
- [x] 3.4 新建 `GroupViewHolder.kt` — 分组条目 ViewHolder

## 4. Reducer 变更

- [x] 4.1 修改 `LogsReducer.kt` — 处理 GroupToggled、ExpandAllGroups、CollapseAllGroups、ScrollStateChanged

## 5. RecyclerView 性能优化

- [x] 5.1 修改 `LogsFragment.kt` — setHasStableIds、setItemViewCacheSize、多 ViewType RecycledViewPool

## 6. FAB 双向导航

- [x] 6.1 修改 `LogsFragment.kt` — FAB 双向滚动逻辑 + 滚动监听器

## 7. 点击高亮并复制

- [x] 7.1 修改 `LogViewHolder.kt` — 单击复制 + 高亮动画

## 8. 编译与测试验证

- [x] 8.1 全量编译验证（logging:presentation / core:recycler / crashes:presentation / filters:presentation 全部通过）
- [x] 8.2 数据库模块 schema 修复（18→19,19→20,20→21 合并为 18→21）

## 9. Bug 修复（P0）

- [x] 9.1 修复 `ExpandAllGroups` 逻辑反转 — `groupExpandStates = emptyMap()` 导致所有组折叠。需新增 `allGroupsExpanded: Boolean` 字段，由 ExpandAllGroups 设为 true、CollapseAllGroups 设为 false，LogGrouper 中默认值改为 `?: allGroupsExpanded`
- [x] 9.2 修复 `SelectAll` 遗漏分组子日志 — 折叠状态下 Group.children 不在 adapter.currentList 中，需遍历所有 `LogListItem.Group.children` 收集 logLineId

## 10. 功能补全（P1）

- [x] 10.1 添加 ExpandAll / CollapseAll 菜单项 — 在 `logs_menu.xml` 中添加两个菜单项，在 `LogsFragment.kt` 中连线 dispatch(ExpandAllGroups/CollapseAllGroups)
- [x] 10.2 添加字符串资源 — `expand_all_groups`、`collapse_all_groups`
- [x] 10.3 分组头部添加长按菜单 — GroupViewHolder 增加 `onGroupLongClick` 回调，弹出 PopupMenu（Select/Copy/CreateFilter），沿用现有 Command 链

## 11. 交互打磨（P2）

- [x] 11.1 搜索结果自动展开匹配分组 — LogsViewStateMapper 有查询时默认全展开，updateLogsList 有查询时滚动到顶部
- [x] 11.2 FAB 显示/隐藏过渡动画 — 使用 scaleX/Y + alpha 200ms 动画替代 show/hide
- [x] 11.3 分组背景色对齐设计文档 — 当前 `?attr/colorSurfaceVariant` 为 Material 主题属性，优于硬编码色值
