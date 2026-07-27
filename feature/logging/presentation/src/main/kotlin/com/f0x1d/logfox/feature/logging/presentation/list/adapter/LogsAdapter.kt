package com.f0x1d.logfox.feature.logging.presentation.list.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.f0x1d.logfox.core.recycler.adapter.BaseListAdapter
import com.f0x1d.logfox.feature.logging.presentation.databinding.ItemGroupLogBinding
import com.f0x1d.logfox.feature.logging.presentation.databinding.ItemLogBinding
import com.f0x1d.logfox.feature.logging.presentation.list.model.LogLineItem
import com.f0x1d.logfox.feature.logging.presentation.list.model.LogListItem
import com.f0x1d.logfox.feature.logging.presentation.list.viewholder.GroupViewHolder
import com.f0x1d.logfox.feature.logging.presentation.list.viewholder.LogViewHolder

class LogsAdapter(
    private val onClick: (LogLineItem) -> Unit,
    private val onGroupClick: (Long) -> Unit,
    private val onSelectClick: (LogLineItem) -> Unit,
    private val onCopyClick: (LogLineItem) -> Unit,
    private val onCreateFilterClick: (LogLineItem) -> Unit,
) : BaseListAdapter<LogListItem, androidx.viewbinding.ViewBinding>(
    object : DiffUtil.ItemCallback<LogListItem>() {
        override fun areItemsTheSame(oldItem: LogListItem, newItem: LogListItem): Boolean = when {
            oldItem is LogListItem.Item && newItem is LogListItem.Item ->
                oldItem.data.logLineId == newItem.data.logLineId
            oldItem is LogListItem.Group && newItem is LogListItem.Group ->
                oldItem.header.logLineId == newItem.header.logLineId
            else -> false
        }

        override fun areContentsTheSame(oldItem: LogListItem, newItem: LogListItem): Boolean =
            oldItem == newItem
    },
) {
    companion object {
        const val VIEW_TYPE_ITEM = 0
        const val VIEW_TYPE_GROUP = 1
    }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = when (val item = getItem(position)) {
        is LogListItem.Item -> item.data.logLineId
        is LogListItem.Group -> item.header.logLineId
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is LogListItem.Item -> VIEW_TYPE_ITEM
        is LogListItem.Group -> VIEW_TYPE_GROUP
    }

    override fun createHolder(
        layoutInflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int,
    ): com.f0x1d.logfox.core.recycler.viewholder.BaseViewHolder<LogListItem, androidx.viewbinding.ViewBinding> {
        @Suppress("UNCHECKED_CAST")
        return when (viewType) {
            VIEW_TYPE_ITEM -> LogViewHolder(
                binding = ItemLogBinding.inflate(layoutInflater, parent, false),
                onClick = onClick,
                onSelectClick = onSelectClick,
                onCopyClick = onCopyClick,
                onCreateFilterClick = onCreateFilterClick,
            ) as com.f0x1d.logfox.core.recycler.viewholder.BaseViewHolder<LogListItem, androidx.viewbinding.ViewBinding>

            VIEW_TYPE_GROUP -> {
                val groupBinding = ItemGroupLogBinding.inflate(layoutInflater, parent, false)
                groupBinding.root.apply {
                    layoutParams = RecyclerView.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT,
                    )
                }
                GroupViewHolder(
                    binding = groupBinding,
                    onGroupClick = onGroupClick,
                ) as com.f0x1d.logfox.core.recycler.viewholder.BaseViewHolder<LogListItem, androidx.viewbinding.ViewBinding>
            }

            else -> error("Unknown viewType: $viewType")
        }
    }
}
