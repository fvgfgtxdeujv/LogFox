package com.f0x1d.logfox.feature.logging.presentation.list.viewholder

import com.f0x1d.logfox.core.recycler.viewholder.BaseViewHolder
import com.f0x1d.logfox.feature.logging.presentation.databinding.ItemGroupLogBinding
import com.f0x1d.logfox.feature.logging.presentation.list.model.LogListItem

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
        countView.text = "x${group.count}"
        expandIcon.rotation = if (group.expanded) 180f else 0f
    }
}
