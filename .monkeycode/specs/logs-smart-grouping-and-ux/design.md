# 技术设计文档 — 日志列表智能分组与交互优化

## 概述

为 LogFox 日志列表新增智能分组折叠、RecyclerView 性能优化、双向 FAB 导航和行级点击复制交互，采用 TEA 架构 + 多 ViewType Adapter 方案。

---

## 1. 数据模型变更

### 1.1 引入分组列表项 sealed class

**新建文件**：`feature/logging/presentation/.../list/model/LogListItem.kt`

```kotlin
sealed class LogListItem {

    data class Item(
        val data: LogLineItem,
    ) : LogListItem()

    data class Group(
        val header: LogLineItem,
        val children: List<LogLineItem>,
        val expanded: Boolean,
        val count: Int,
        val maxLevel: LogLevel,
    ) : LogListItem()
}
```

### 1.2 LogsViewState 变更

**修改文件**：`feature/logging/presentation/.../list/LogsViewState.kt`

```kotlin
internal data class LogsViewState(
    val items: List<LogListItem>,
    val logsChanged: Boolean,
    val paused: Boolean,
    val query: String?,
    val filters: List<UserFilter>,
    val selecting: Boolean,
    val selectedCount: Int,
    val resumeLoggingWithBottomTouch: Boolean,
    val scrollFabIcon: ScrollFabIcon,
)
```

关键变更：
- `logs: List<LogLineItem>` → `items: List<LogListItem>` — 列表项从单一类型变为 sealed class
- 新增 `scrollFabIcon: ScrollFabIcon` — 控制 FAB 图标和方向
- 移除 `logs` 字段（替代为 `items`）

### 1.3 LogsState 变更

**修改文件**：`feature/logging/presentation/.../list/LogsState.kt`

```kotlin
internal data class LogsState(
    val logs: List<LogLine>,
    val paused: Boolean,
    val query: String?,
    val caseSensitive: Boolean,
    val filters: List<UserFilter>,
    val showLogValues: ShowLogValues,
    val selectedIds: Set<Long>,
    val expandedOverrides: Map<Long, Boolean>,
    val groupExpandStates: Map<Long, Boolean>,
    val allGroupsExpanded: Boolean,
    val logsExpanded: Boolean,
    val textSize: Int,
    val logsChanged: Boolean,
    val resumeLoggingWithBottomTouch: Boolean,
    val isAtBottom: Boolean,
)
```

关键变更：
- 新增 `groupExpandStates: Map<Long, Boolean>` — key 为分组第一条日志行 ID，控制单个分组的展开/折叠覆盖状态（优先级高于 `allGroupsExpanded`）
- 新增 `allGroupsExpanded: Boolean` — 全局展开/折叠默认值，由 ExpandAllGroups / CollapseAllGroups 控制，LogGrouper 中 `groupExpandStates[id] ?: allGroupsExpanded` 作为展开判定
- 新增 `isAtBottom: Boolean` — 标记列表是否滚动到底部

### 1.4 ScrollFabIcon 枚举

**新建文件**：`feature/logging/presentation/.../list/ScrollFabIcon.kt`

```kotlin
enum class ScrollFabIcon {
    HIDDEN,
    SCROLL_DOWN,
    SCROLL_UP,
}
```

---

## 2. 分组算法

### 2.1 分组逻辑位置

在 Reducer 中将 `List<LogLine>` 映射为 `List<LogLineItem>` 之后、输出 `LogsViewState` 之前执行分组。

**新建文件**：`feature/logging/presentation/.../list/LogGrouper.kt`

```kotlin
internal object LogGrouper {

    fun group(
        items: List<LogLineItem>,
        groupExpandStates: Map<Long, Boolean>,
        allGroupsExpanded: Boolean,
        maxGroupSize: Int,
    ): List<LogListItem> {
        if (items.isEmpty()) return emptyList()

        val result = mutableListOf<LogListItem>()
        var i = 0
        while (i < items.size) {
            val current = items[i]
            val groupStart = i
            while (i < items.size &&
                i - groupStart < maxGroupSize &&
                areSimilar(items[i], current)) {
                i++
            }
            val count = i - groupStart
            if (count >= 2) {
                val children = items.subList(groupStart, i).toList()
                val maxLevel = children.maxBy { it.level.ordinal }.level
                result.add(LogListItem.Group(
                    header = current,
                    children = children,
                    expanded = groupExpandStates[current.logLineId] ?: allGroupsExpanded,
                    count = count,
                    maxLevel = maxLevel,
                ))
            } else {
                result.add(LogListItem.Item(current))
            }
        }
        return result
    }

    private fun areSimilar(a: LogLineItem, b: LogLineItem): Boolean =
        a.content == b.content && a.tag == b.tag && a.level == b.level
}
```

分组规则：
- 仅折叠连续且 content + tag + level 三者完全相同的日志行
- 每组上限 `maxGroupSize`（默认 100，可配置），超出另起新组
- 少于 2 条不分组

### 2.2 集成到 ViewModel / Reducer

在 `LogsReducer` 中 `reduce` 函数的 `LogsAdded` 或其他日志变更命令处理后，调用 `LogGrouper.group()`：

**设计修正（初版实现 Bug）**：`ExpandAllGroups` 原设计 `groupExpandStates = emptyMap()` 因 `LogGrouper` 中默认 `?: false` 导致行为反转——实际效果为全部折叠。修正方案：

1. 在 `LogsState` 中新增 `allGroupsExpanded: Boolean` 字段
2. `ExpandAllGroups` → `allGroupsExpanded = true`
3. `CollapseAllGroups` → `allGroupsExpanded = false`
4. `GroupToggled` → 在 `groupExpandStates` 中写入个别覆盖值（优先级高于 `allGroupsExpanded`）
5. `LogGrouper.group()` 默认展开值改为 `groupExpandStates[id] ?: allGroupsExpanded`

```kotlin
fun reduce(state: LogsState, command: LogsCommand): ReduceResult<LogsState, LogsSideEffect> {
    return when (command) {
        is LogsCommand.LogsAdded -> {
            val newLogs = state.logs + command.logs
            state.copy(logs = newLogs, logsChanged = true).noSideEffects()
        }
        is LogsCommand.GroupToggled -> {
            val current = state.groupExpandStates[command.groupId]
                ?: state.allGroupsExpanded
            state.copy(
                groupExpandStates = state.groupExpandStates + (command.groupId to !current),
                logsChanged = true,
            ).noSideEffects()
        }
        is LogsCommand.ExpandAllGroups -> {
            state.copy(allGroupsExpanded = true, logsChanged = true).noSideEffects()
        }
        is LogsCommand.CollapseAllGroups -> {
            state.copy(allGroupsExpanded = false, logsChanged = true).noSideEffects()
        }
        is LogsCommand.ScrollStateChanged -> {
            state.copy(isAtBottom = command.isAtBottom).noSideEffects()
        }
        // ...
    }
}
```

**SelectAll 遗漏子日志（Bug）**：折叠状态下 `Group.children` 不在 adapter 的 `currentList` 中，需遍历所有 `LogListItem.Group.children` 收集 logLineId：

```kotlin
// 修复后的 SelectAll
val visibleIds = adapter.currentList.flatMap {
    when (it) {
        is LogListItem.Item -> listOf(it.data.logLineId)
        is LogListItem.Group -> it.children.map { child -> child.logLineId }
    }
}.toMutableSet()
```

---

## 3. Adapter 变更

### 3.1 多 ViewType 支持

**修改文件**：`feature/logging/presentation/.../list/adapter/LogsAdapter.kt`

```kotlin
class LogsAdapter(
    private val onClick: (LogLineItem) -> Unit,
    private val onGroupClick: (Long) -> Unit,
    private val onSelectClick: (LogLineItem) -> Unit,
    private val onCopyClick: (LogLineItem) -> Unit,
    private val onCreateFilterClick: (LogLineItem) -> Unit,
) : BaseListAdapter<LogListItem, ViewBinding>(
    diffCallback = object : DiffUtil.ItemCallback<LogListItem>() {
        override fun areItemsTheSame(old: LogListItem, new: LogListItem) = when {
            old is LogListItem.Item && new is LogListItem.Item -> old.data.logLineId == new.data.logLineId
            old is LogListItem.Group && new is LogListItem.Group -> old.header.logLineId == new.header.logLineId
            else -> false
        }
        override fun areContentsTheSame(old: LogListItem, new: LogListItem): Boolean = old == new
    },
) {
    init { setHasStableIds(true) }

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_GROUP = 1
    }

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is LogListItem.Item -> VIEW_TYPE_ITEM
        is LogListItem.Group -> VIEW_TYPE_GROUP
    }

    override fun getItemId(position: Int): Long = when (val item = getItem(position)) {
        is LogListItem.Item -> item.data.logLineId
        is LogListItem.Group -> item.header.logLineId
    }

    override fun createHolder(layoutInflater: LayoutInflater, parent: ViewGroup): BaseViewHolder<LogListItem, ViewBinding> =
        error("Use createHolderForType")
}
```

**关键问题**：当前 `BaseListAdapter` 的 `onCreateViewHolder` 调用 `createHolder`，不支持多 ViewType。需要修改基类或拆分逻辑。

### 3.2 BaseListAdapter 扩展

**修改文件**：`core/recycler/.../adapter/BaseListAdapter.kt`

将 `onCreateViewHolder` 改为支持多 ViewType：

```kotlin
abstract class BaseListAdapter<T, D : ViewBinding>(diffUtil: DiffUtil.ItemCallback<T>) :
    ListAdapter<T, BaseViewHolder<T, D>>(diffUtil) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder<T, D> =
        createHolder(LayoutInflater.from(parent.context), parent, viewType)

    open fun createHolder(layoutInflater: LayoutInflater, parent: ViewGroup, viewType: Int): BaseViewHolder<T, D> =
        createHolder(layoutInflater, parent)

    /** 默认实现保留向后兼容 */
    open fun createHolder(layoutInflater: LayoutInflater, parent: ViewGroup): BaseViewHolder<T, D> =
        error("Override createHolder or createHolder(layoutInflater, parent, viewType)")
}
```

### 3.3 LogsAdapter 多 ViewType 实现

```kotlin
override fun createHolder(
    layoutInflater: LayoutInflater,
    parent: ViewGroup,
    viewType: Int,
): BaseViewHolder<LogListItem, ViewBinding> = when (viewType) {
    VIEW_TYPE_ITEM -> LogViewHolder(
        binding = ItemLogBinding.inflate(layoutInflater, parent, false),
        onClick = onClick,
        onSelectClick = onSelectClick,
        onCopyClick = onCopyClick,
        onCreateFilterClick = onCreateFilterClick,
    )
    VIEW_TYPE_GROUP -> GroupViewHolder(
        binding = ItemGroupLogBinding.inflate(layoutInflater, parent, false),
        onGroupClick = onGroupClick,
    )
    else -> error("Unknown viewType: $viewType")
}
```

### 3.4 GroupViewHolder

**新建文件**：`feature/logging/presentation/.../list/viewholder/GroupViewHolder.kt`

```kotlin
class GroupViewHolder(
    binding: ItemGroupLogBinding,
    private val onGroupClick: (Long) -> Unit,
) : BaseViewHolder<LogListItem, ItemGroupLogBinding>(binding) {

    init {
        binding.root.setOnClickListener {
            val item = currentItem as? LogListItem.Group ?: return@setOnClickListener
            onGroupClick(item.header.logLineId)
        }
    }

    override fun ItemGroupLogBinding.bindTo(data: LogListItem) {
        val group = data as LogListItem.Group
        logText.text = group.header.displayText
        levelView.logLevel = group.maxLevel
        logText.maxLines = 1
        countView.text = "x${group.count}"
        expandIcon.rotation = if (group.expanded) 180f else 0f
        container.isSelected = false
    }
}
```

### 3.5 ItemGroupLogBinding 布局

**新建文件**：`feature/logging/presentation/src/main/res/layout/item_group_log.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/container"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@color/group_log_background"
    android:gravity="center_vertical"
    android:minHeight="48dp"
    android:orientation="horizontal"
    android:paddingStart="8dp"
    android:paddingEnd="8dp">

    <com.f0x1d.logfox.feature.logging.presentation.list.view.LevelView
        android:id="@+id/levelView"
        android:layout_width="6dp"
        android:layout_height="0dp"
        app:levelIndicator="true"
        android:layout_marginEnd="8dp" />

    <TextView
        android:id="@+id/logText"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:ellipsize="end"
        android:maxLines="1" />

    <TextView
        android:id="@+id/countView"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="8dp"
        android:layout_marginEnd="4dp"
        android:textAppearance="@style/TextAppearance.Material3.BodyMedium"
        android:textColor="?attr/colorPrimary" />

    <ImageView
        android:id="@+id/expandIcon"
        android:layout_width="24dp"
        android:layout_height="24dp"
        android:src="@drawable/ic_arrow_down"
        android:contentDescription="@string/expand_group" />

</LinearLayout>
```

---

## 4. RecyclerView 性能优化

### 4.1 变更清单

| 项目 | 当前状态 | 目标状态 |
|------|---------|---------|
| `setHasStableIds` | 未设置 | `true` |
| `getItemId` | 未覆写 | 覆写，返回 `logLineId` |
| DiffUtil | `diffCallback<T>()` 基于 `id` | 自定义 DiffCallback 区分 Item/Group |
| ItemViewCacheSize | 未设置 | `20` |
| ItemAnimator | `null` | 保持 `null` |

### 4.2 LogsFragment 修改

```kotlin
logsRecycler.setHasStableIds(true)
logsRecycler.setItemViewCacheSize(20)
logsRecycler.recycledViewPool.setMaxRecycledViews(VIEW_TYPE_ITEM, 50)
logsRecycler.recycledViewPool.setMaxRecycledViews(VIEW_TYPE_GROUP, 10)
```

---

## 5. 快速回到顶部/底部 FAB

### 5.1 方案

复用现有 `scroll_fab`，不新建 FAB 组件。在 `LogsFragment` 中增加滚动监听器，根据 `isAtBottom` 切换图标和点击行为。

### 5.2 图标资源

- 向下箭头：`@drawable/ic_arrow_downward`（表示「回到底部」）
- 向上箭头：`@drawable/ic_arrow_upward`（表示「回到顶部」）

如果项目中没有这些图标，使用系统默认方向图标替代。

### 5.3 实现

```kotlin
// onViewCreated 中
logsRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        val canScrollDown = recyclerView.canScrollVertically(1)
        send(LogsCommand.ScrollStateChanged(isAtBottom = !canScrollDown))
    }
})

scrollFab.setOnClickListener {
    val lm = logsRecycler.layoutManager as LinearLayoutManager
    if (viewModel.state.value.resumeLoggingWithBottomTouch) {
        send(LogsCommand.Resume)
    } else {
        val canScrollDown = logsRecycler.canScrollVertically(1)
        if (canScrollDown) {
            logsRecycler.smoothScrollToPosition(adapter.itemCount - 1)
        } else {
            logsRecycler.smoothScrollToPosition(0)
        }
    }
}

// render 中
private fun FragmentLogsBinding.processFAB(state: LogsViewState) {
    if (!state.paused && state.items.size < 10) {
        scrollFab.hide()
        return
    }
    when (state.scrollFabIcon) {
        ScrollFabIcon.HIDDEN -> scrollFab.hide()
        ScrollFabIcon.SCROLL_DOWN -> {
            scrollFab.show()
            scrollFab.setImageResource(R.drawable.ic_arrow_downward)
        }
        ScrollFabIcon.SCROLL_UP -> {
            scrollFab.show()
            scrollFab.setImageResource(R.drawable.ic_arrow_upward)
        }
    }
}
```

### 5.4 FAB 图标推导

在 `LogsViewStateMapper` 中：

```kotlin
val scrollFabIcon = when {
    !paused && items.size < 10 -> ScrollFabIcon.HIDDEN
    isAtBottom -> ScrollFabIcon.SCROLL_UP
    else -> ScrollFabIcon.SCROLL_DOWN
}
```

---

## 6. 日志行点击高亮并复制

### 6.1 ViewHolder 变更

**修改文件**：`LogViewHolder.kt`

```kotlin
init {
    binding.apply {
        root.setOnClickListener {
            val item = currentItem ?: return@setOnClickListener
            if (item.selected) {
                onSelectClick(item) // 选择模式
            } else {
                onClick(item)
                highlightAndCopy() // 高亮动画 + 复制
            }
        }
        root.setOnLongClickListener {
            popupMenu.show()
            return@setOnLongClickListener true
        }
    }
}

private fun highlightAndCopy() {
    val context = binding.root.context
    currentItem?.let { item ->
        context.copyText(item.content)
        // 高亮动画
        binding.container.animate()
            .setDuration(150)
            .alpha(0.5f)
            .withEndAction {
                binding.container.animate()
                    .setDuration(150)
                    .alpha(1f)
                    .start()
            }
            .start()
    }
}
```

### 6.2 高亮效果说明

选择透明度动画方案（不引入额外背景色资源）：
1. 点击瞬间 `alpha` 降到 0.5（150ms）
2. 恢复到 1.0（150ms）
3. 总持续时长 300ms

此动画时间固定，无外部依赖。

### 6.3 与选择模式交互

选择模式下保持现有行为，不触发复制：

```kotlin
if (currentItem?.selected == true) {
    onSelectClick(item) // 保留选中/取消选中逻辑
} else {
    // 正常模式：复制 + 高亮
}
```

---

## 7. 最大分组条数设置

### 7.1 全局跳过

**设计决策**：Setting 链路过于复杂（22 个文件 + TEA 双向绑定），且 LogLineItem 生成时（Reducer 内部）需要访问此值。将在 Reducer 中使用硬编码默认值 100，若后续评估收益足够，再补全 Setting 链路。

**代码变更**：

在 `LogGrouper` 中以常量形式：

```kotlin
internal object LogGrouper {
    private const val DEFAULT_MAX_GROUP_SIZE = 100

    fun group(
        items: List<LogLineItem>,
        groupExpandStates: Map<Long, Boolean>,
    ): List<LogListItem> = group(items, groupExpandStates, DEFAULT_MAX_GROUP_SIZE)
}
```

> 后续迭代方案：通过 SettingsRepository 注入 DefaultMaxGroupSize UseCase 到 ViewModel → State → Reducer → LogGrouper，并添加 Settings UI 下拉菜单。实现时不引入新的 UseCase（与现有 Settings 模式相同）。

---

## 8. 文件变更汇总

### 新建文件

| 文件 | 说明 |
|------|------|
| `LogListItem.kt` | sealed class 定义 Item / Group |
| `ScrollFabIcon.kt` | FAB 图标枚举 |
| `LogGrouper.kt` | 分组算法 |
| `GroupViewHolder.kt` | 分组条目 ViewHolder |
| `item_group_log.xml` | 分组条目布局 |

### 修改文件

| 文件 | 变更 |
|------|------|
| `LogsState.kt` | 新增 groupExpandStates（初始 `emptyMap()`）、isAtBottom、allGroupsExpanded |
| `LogsViewState.kt` | logs → items（`List<LogListItem>`），新增 scrollFabIcon |
| `LogsCommand.kt` | 新增 GroupToggled、ExpandAllGroups、CollapseAllGroups、ScrollStateChanged |
| `LogsReducer.kt` / ViewModel | 集成 LogGrouper，处理新增 Command |
| `LogsViewStateMapper.kt` | 调用 LogGrouper.group()，推导 scrollFabIcon |
| `BaseListAdapter.kt` | 支持多 ViewType（新 createHolder 重载） |
| `LogsAdapter.kt` | 多 ViewType 支持，自定义 DiffCallback |
| `LogViewHolder.kt` | 单击复制 + 高亮动画 |
| `LogsFragment.kt` | FAB 双向导航 + 滚动监听 + RecyclerView setHasStableIds 等 |

## 9. 全部展开/折叠菜单（待实现）

### 9.1 菜单项新增

在 `logs_menu.xml` 中添加两个菜单项：

```xml
<item
    android:id="@+id/expand_all_item"
    android:title="@string/expand_all_groups"
    app:showAsAction="never" />
<item
    android:id="@+id/collapse_all_item"
    android:title="@string/collapse_all_groups"
    app:showAsAction="never" />
```

### 9.2 Fragment 连线

```kotlin
// LogsFragment.kt — onOptionsItemSelected
R.id.expand_all_item -> { send(LogsCommand.ExpandAllGroups); true }
R.id.collapse_all_item -> { send(LogsCommand.CollapseAllGroups); true }
```

### 9.3 字符串资源

```xml
<string name="expand_all_groups">全部展开</string>
<string name="collapse_all_groups">全部折叠</string>
```

## 10. 分组长按菜单（待实现）

### 10.1 GroupViewHolder 长按

设计文档第 11 节（差异化逻辑）明确规定 Group Header 长按应弹出 PopupMenu（Select/Copy/CreateFilter）。当前实现仅有 `setOnClickListener`，缺少 `setOnLongClickListener`。

```kotlin
class GroupViewHolder(
    binding: ItemGroupLogBinding,
    private val onGroupClick: (Long) -> Unit,
    private val onGroupLongClick: (LogListItem.Group) -> Unit,
) : BaseViewHolder<LogListItem, ItemGroupLogBinding>(binding) {

    init {
        binding.root.setOnClickListener {
            val item = currentItem as? LogListItem.Group ?: return@setOnClickListener
            onGroupClick(item.header.logLineId)
        }
        binding.root.setOnLongClickListener {
            val item = currentItem as? LogListItem.Group ?: return@setOnLongClickListener false
            onGroupLongClick(item)
            true
        }
    }
    // ...
}
```

### 10.2 Fragment 长按处理

参考现有 `LogsFragment.kt` 中 `onCreateFilter` / `onSelectClick` 的连线方式，在构造 `LogsAdapter` 时传入长按回调，弹出 PopupMenu 后 dispatch 对应的 Command。

## 11. 搜索结果自动展开（待实现）

需求 R1-8：搜索结果中包含的日志行若处于折叠分组内，自动展开该分组并滚动到可见区域。

### 11.1 实现方案

当 SearchLogs 命令返回匹配结果后，遍历结果中每条日志行所属的分组 ID：
1. 若分组当前折叠，dispatch `GroupToggled(groupId)` 展开
2. 通过 `smoothScrollToPosition` 滚动到第一个匹配项位置

在 `LogsReducer` 处理 `SearchQueryChanged` 或新增专用 `ExpandGroupsContaining` Command 实现。

## 12. FAB 过渡动画（待实现）

需求 R3-7：FAB 显示/隐藏使用 Material Design scale+fade 过渡（200ms）。

当前实现使用 `scrollFab.show()/hide()`，未显式设置动画。应改为：

```kotlin
scrollFab.apply {
    scaleX = 0f; scaleY = 0f; alpha = 0f
    animate()
        .scaleX(1f).scaleY(1f).alpha(1f)
        .setDuration(200)
        .setInterpolator(DecelerateInterpolator())
        .start()
}
```

## 13. 分组背景色对齐

设计文档 3.5 节指定 `android:background="@color/group_log_background"`，当前实现 `android:background="?attr/colorSurfaceVariant"`。需确认 `@color/group_log_background` 是否已在主题中定义，若无则添加。

## 14. 已发现 Bug 汇总

| Bug | 严重度 | 描述 | 修复位置 |
|-----|--------|------|---------|
| B1 | P0 | `ExpandAllGroups` 逻辑反转：`emptyMap()` + 默认 `?: false` = 全部折叠 | `LogsReducer.kt:245-250`，`LogsState.kt`，`LogGrouper.kt` |
| B2 | P0 | `SelectAll` 遗漏分组子日志：折叠状态下 children 不在 currentList | `LogsFragment.kt:111-116` |
| B3 | P1 | ExpandAll/CollapseAll 菜单项缺失：Command 已就位但无 UI 入口 | `logs_menu.xml`，`LogsFragment.kt` |
| B4 | P2 | 分组头部缺少长按菜单 | `GroupViewHolder.kt`，`LogsAdapter.kt` |
| B5 | P3 | 分组背景色与设计不一致 | `item_group_log.xml:10` |

---

## 9. 数据流图解

```
[Reducer]
  └─ LogLine -> LogLineItem (原有映射)
        └─ LogGrouper.group(items, groupExpandStates, maxGroupSize)
              └─ List<LogListItem> (Item | Group)
                    └─ LogsViewStateMapper
                          └─ LogsViewState(items = ..., scrollFabIcon = ...)
                                └─ LogsFragment.render()
                                      ├─ adapter.submitList(state.items)
                                      ├─ processFAB(state)
                                      └─ updateLogsList(...)
                                            └─ RecyclerView displays Item or Group ViewHolder
```

## 10. 命令流

```
[Group Header Click] → LogsCommand.GroupToggled(groupId)
    → Reducer: groupExpandStates 写入/覆盖
    → LogGrouper 重新计算
    → DiffUtil 局部更新

[Group Header Long Press] → 弹出 PopupMenu（Select/Copy/CreateFilter）
    → 沿用现有 LogsCommand 链

[Log Line Click] → LogsCommand.ItemClicked(logLineId)
    → 非选择模式：触发复制 SideEffect → handleSideEffect → copyText + snackbar
    → 选择模式：触发选择逻辑（现有）
```

---

## 11. 差异化逻辑（Item vs Group）

| 操作 | Item | Group（折叠） | Group（展开子项） |
|------|------|-------------|----------------|
| 单击 | 复制 + 高亮 | 展开分组 | 折叠分组 |
| 长按 | PopupMenu | PopupMenu | 子项各自长按 |
| 选择 | 选中/取消 | 选中/取消（全组？仅头部？**仅头部**） | 子项各自选中 |
| 导出 | 直接导出 | 展开后导出所有子项 | 展开后导出 |

---

## 15. 实现偏差记录

### 15.1 搜索自动展开（实现 vs 设计）

**设计要求**（R1-8）：搜索结果中包含匹配行时，仅自动展开包含匹配行的分组。

**实际实现**（LogsViewStateMapper.kt:37）：
```kotlin
state.allGroupsExpanded || hasActiveQuery
```
当任何搜索激活时，**所有**分组全部展开，而非仅展开匹配分组。

**影响**：用户清除搜索后，所有分组仍保持展开状态（allGroupsExpanded=true），直到手动折叠。与「仅匹配组展开」的需求不完全一致。

### 15.2 单击复制通路双重执行

**设计预期**：单击 → `ItemClicked` Command → CopyLog SideEffect → `FormatAndCopyLog` → 格式化复制 + Toast。

**实际实现**（LogViewHolder.kt:59-60、70-85）：
```kotlin
onClick(item.data)           // 走 TEA 链路复制（格式化版）
highlightAndCopy(item.data)  // 直接 copyText（原始版）
```
两条通路同时执行：TEA 链路（通过 EffectHandler 产出格式化文本 + Toast）和 ViewHolder 直连（直接 `copyText(content)`）。两次复制覆盖剪贴板，格式不一致。

### 15.3 分组选择状态未同步

`GroupViewHolder.bindTo` 未设置 `container.isSelected`，而 `LogViewHolder.bindTo` 设置了。选择模式下分组头部选中状态不可见。

### 15.4 updateLogsList 双重 submitList

`LogsFragment.kt:440-441`：
```kotlin
adapter.submitList(null)
adapter.submitList(items) { ... }
```
先提交 null 再提交真实列表，完全绕过了 DiffUtil 增量更新。目的可能是解决 `onRestoreInstanceState` 的滚动位置恢复问题，但代价是每次日志变更都全量重绑。

### 15.5 processFAB 与 LogsViewStateMapper 冗余检查

`LogsViewStateMapper.kt:43` 和 `LogsFragment.kt:345` 都检查了 `!state.paused` 条件。FAB 的 hidden 判定逻辑分散在两处。

## 16. 优化建议

### O1 — 移除 updateLogsList 双重 submit

移除 `adapter.submitList(null)`，直接 `adapter.submitList(items)`。`setHasStableIds(true)` + `getItemId()` 已保证稳定 ID，DiffUtil 可正确处理。滚动位置恢复通过 `layoutManager.onRestoreInstanceState` 独立完成。

**影响**：减少全量 rebind，DiffUtil 增量更新生效。

### O2 — 仅展开搜索匹配的分组

在 `LogsViewStateMapper` 中，找到搜索匹配行所属的分组 ID，仅对匹配组 dispatch `GroupToggled`，而非全量 `allGroupsExpanded || hasActiveQuery`。需要 Mapper 能感知搜索结果或由 Reducer 协助。

### O3 — 单击复制收敛为单通路

移除 `LogViewHolder.highlightAndCopy` 中的 `copyText` 调用，仅保留 TEA 链路（ItemClicked → CopyLog → EffectHandler → FormattedCopy → Toast）。高亮动画保留在 ViewHolder。

### O4 — GroupViewHolder 选择状态同步

`GroupViewHolder.bindTo` 中设置 `container.isSelected = group.children.any { it.selected }` 或等价逻辑。

### O5 — 提取 FAB 资源引用为常量

将 `R.drawable.ic_arrow_drop_down` / `R.drawable.ic_arrow_drop_up` 提取为文件级 `private val` 常量，避免硬编码在多处。

### O6 — BaseViewHolder 泛型擦除警告

`LogsAdapter.createHolder` 中有 3 处 `@Suppress("UNCHECKED_CAST")`。可通过在 `BaseListAdapter` 中拆出 `fun createMultiTypeHolder(...)` 返回值用具体类型避免强转。低优先级，不影响正确性。
