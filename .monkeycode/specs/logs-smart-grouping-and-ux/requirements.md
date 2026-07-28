# 需求文档 — 日志列表智能分组与交互优化

## 概述

为 LogFox 日志列表新增智能分组折叠、性能优化、快速导航和行级交互能力，改善大量日志场景下的阅读效率和操作体验。

## 术语表

- **日志行（LogLine）**：原始日志数据的领域模型
- **日志项（LogLineItem）**：UI 层的日志展示模型，含 displayText、expanded、selected 等 UI 状态
- **分组（Group）**：连续相似日志行折叠为一个可展开的分组条目
- **相似日志行**：content、tag、level 三者完全相同的连续日志行
- **FAB**：FloatingActionButton，悬浮操作按钮
- **DiffUtil**：Android RecyclerView 差异计算工具

---

## 需求

### R1 — 智能日志分组

**用户故事**：作为开发者，我希望连续的重复日志行能自动折叠为一组，以便在日志刷屏时快速定位关键信息，减少视觉噪音。

#### 验收标准

1. WHEN 日志列表中存在连续 N（N>=2）条 content、tag、level 三者完全相同的日志行，THE 系统 SHALL 将这些日志行折叠为一个分组条目展示。
2. THE 分组条目 SHALL 显示以下信息：第一条日志的 displayText、组内日志条数（如「×12」）、以及组内所有日志行的最高日志级别。
3. THE 每个分组默认最多包含 100 条日志行，超出部分另起新组。
4. THE 系统 SHALL 提供用户设置项（位于设置-UI 页面）允许修改每分组最大日志行数，可选值：50、100、200、500、不限。
5. WHEN 用户点击分组条目，THE 系统 SHALL 展开该分组，显示组内所有日志行。
6. WHEN 分组处于展开状态，用户再次点击分组头部，THE 系统 SHALL 折叠该分组。
7. THE 分组条目的背景色 SHALL 与其他日志行有明显视觉区分（如浅色背景 + 左侧级别指示带）。
8. WHEN 搜索结果中包含匹配行，被匹配行属于某个分组时，THE 系统 SHALL 自动展开该分组并将匹配行滚动到可见区域。
9. WHEN 用户选择「全部展开」时，THE 系统 SHALL 展开所有分组。
10. WHEN 用户选择「全部折叠」时，THE 系统 SHALL 折叠所有分组。
11. IF 分组中只有 1 条日志行，THE 系统 SHALL 不创建分组，直接展示该行。
12. WHEN 分组内的日志行被导出，THE 系统 SHALL 导出全部展开的日志行，分组本身的折叠状态不影响导出。

---

### R2 — RecyclerView 性能优化

**用户故事**：作为开发者，我希望日志列表在接收高频更新时（每秒数百条新日志）仍保持流畅滚动，避免 UI 卡顿。

#### 验收标准

1. THE 系统 SHALL 对 RecyclerView 启用 `setHasStableIds(true)`，通过 `LogLineItem.id` 提供稳定标识。
2. THE 系统 SHALL 使用 `ListAdapter` 的 `submitList()` 进行增量更新，由 DiffUtil 自动计算最小变更集。
3. THE RecyclerView SHALL 配置 `setItemViewCacheSize(20)` 以提高滚动复用效率。
4. WHEN `submitList` 提交的列表超过 5000 条，THE 系统 SHALL 在后台线程执行 DiffUtil 计算（通过 `AsyncListDiffer` 默认实现）。
5. THE ViewHolder 的 `bindTo` 方法 SHALL 避免在绑定过程中创建临时对象，复用已有资源。

---

### R3 — 快速回到顶部/底部

**用户故事**：作为开发者，我希望在翻阅大量日志时能一键回到顶部或跳到底部，以便快速定位日志流的首尾。

#### 验收标准

1. THE 系统 SHALL 在日志列表右下角显示一个 FAB（复用现有 `scroll_fab`）。
2. WHEN 列表滚动到距底部超过 5 个条目时，THE FAB SHALL 显示向下箭头图标（回到底部）。
3. WHEN 列表已在底部，THE FAB SHALL 切换为向上箭头图标（回到顶部）。
4. WHEN 用户点击 FAB（向下箭头模式），THE 系统 SHALL 平滑滚动到列表底部。
5. WHEN 用户点击 FAB（向上箭头模式），THE 系统 SHALL 平滑滚动到列表顶部。
6. IF 列表条目数少于 10 条，THE FAB SHALL 保持隐藏。
7. THE FAB 的显示和隐藏动画 SHALL 使用 Material Design 标准的 scale+fade 过渡（时长 200ms）。

---

### R4 — 日志行点击高亮并复制

**用户故事**：作为开发者，我希望点击一条日志行后该行高亮并自动复制内容，以便快速分享日志片段。

#### 验收标准

1. WHEN 用户单击一条日志行，THE 系统 SHALL 将该行的原始内容复制到系统剪贴板。
2. WHEN 用户单击一条日志行，THE 行 SHALL 显示短暂的高亮动画（背景色变化，持续 300ms 后恢复）。
3. AFTER 复制成功，THE 系统 SHALL 显示 Toast 提示「已复制」。
4. WHEN 用户长按一条日志行，THE 系统 SHALL 保持现有行为（弹出操作菜单：Select/Copy/CreateFilter）。
5. WHEN 处于选择模式时，THE 单击行为 SHALL 保持现有的选中/取消选中逻辑，不触发复制。
6. IF 内容为空，THE 系统 SHALL 不执行复制操作。

---

## 与现有功能的交互

- **选择模式**：选择模式下单击行为不受影响（R4-5），分组在选择模式下仍可折叠/展开（R1 与选择模式独立）。
- **搜索功能**：搜索结果需考虑分组内内容匹配，自动展开（R1-8）。⚠ 当前实现为全部展开而非仅展开匹配分组（参见 design.md §15.1）。
- **导出功能**：导出时按展开状态输出全部日志行（R1-10）。
- **暂停/恢复**：FAB 行为与现有暂停逻辑保持一致（R3 复用现有 `scroll_fab`）。

---

## 验收状态

| 需求 | 状态 | 备注 |
|------|------|------|
| R1-1 连续相似日志行折叠展示 | ✅ | LogGrouper 实现 |
| R1-2 分组条目信息展示 | ✅ | GroupViewHolder 显示 displayText + count + maxLevel |
| R1-3 每组最大 100 条 | ✅ | DEFAULT_MAX_GROUP_SIZE = 100 |
| R1-4 用户设置项 | ⏭ | Setting 链路暂不实现（design §7.1） |
| R1-5 点击展开分组 | ✅ | GroupToggled Command |
| R1-6 折叠分组 | ✅ | GroupToggled toggle |
| R1-7 背景色视觉区分 | ✅ | ?attr/colorSurfaceVariant |
| R1-8 搜索结果自动展开匹配分组 | ⚠ | 实现为全部展开（design §15.1） |
| R1-9 全部展开 | ✅ | ExpandAllGroups Command + 菜单 |
| R1-10 全部折叠 | ✅ | CollapseAllGroups Command + 菜单 |
| R1-11 单条不分组 | ✅ | count >= 2 判定 |
| R1-12 导出不受折叠影响 | ✅ | Reducer 处理 |
| R2-1 setHasStableIds | ✅ | LogsAdapter.init |
| R2-2 ListAdapter + DiffUtil | ⚠ | submitList(null)+submitList(items) 绕过 DiffUtil（design §15.4） |
| R2-3 setItemViewCacheSize(20) | ✅ | LogsFragment:176 |
| R2-4 大列表后台 DiffUtil | ✅ | AsyncListDiffer 默认 |
| R2-5 bindTo 避免临时对象 | ⚠ | LogsViewStateMapper 每次 map 新建 lineItems 列表 |
| R3-1 FAB 显示/隐藏 | ✅ | processFAB + animateFAB |
| R3-2 离底部 >5 条显示向下箭头 | ✅ | ScrollFabIcon.SCROLL_DOWN |
| R3-3 已在底部显示向上箭头 | ✅ | ScrollFabIcon.SCROLL_UP |
| R3-4 点击向下箭头回到底部 | ✅ | scrollLogToBottom() |
| R3-5 点击向上箭头回到顶部 | ✅ | smoothScrollToPosition(0) |
| R3-6 <10 条隐藏 FAB | ✅ | groupedItems.size < 10 |
| R3-7 FAB scale+fade 200ms | ✅ | animateFAB() |
| R4-1 单击复制到剪贴板 | ⚠ | 双重复制（design §15.2） |
| R4-2 高亮动画 300ms | ✅ | 150ms alpha 0.5 + 150ms 恢复 |
| R4-3 Toast 提示 | ❌ | 缺失（design §15.2） |
| R4-4 长按弹出 PopupMenu | ✅ | log_menu.xml |
| R4-5 选择模式不触发复制 | ✅ | selected 时走 onSelectClick |
| R4-6 内容为空不复制 | ✅ | highlightAndCopy 中 content.isEmpty() 检查 |
