package com.f0x1d.logfox.feature.logging.presentation.list.viewholder

import android.view.Gravity
import androidx.appcompat.widget.PopupMenu
import com.f0x1d.logfox.core.recycler.viewholder.BaseViewHolder
import com.f0x1d.logfox.feature.logging.presentation.R
import com.f0x1d.logfox.feature.logging.presentation.databinding.ItemGroupLogBinding
import com.f0x1d.logfox.feature.logging.presentation.list.model.LogListItem

class GroupViewHolder(
    binding: ItemGroupLogBinding,
    private val onGroupClick: (Long) -> Unit,
    private val onSelectClick: (LogListItem.Group) -> Unit,
    private val onCopyClick: (LogListItem.Group) -> Unit,
    private val onCreateFilterClick: (LogListItem.Group) -> Unit,
) : BaseViewHolder<LogListItem, ItemGroupLogBinding>(binding) {

    private val popupMenu: PopupMenu = PopupMenu(
        binding.root.context,
        binding.root,
        Gravity.END,
    ).apply {
        inflate(R.menu.log_menu)
        setForceShowIcon(true)

        setOnMenuItemClickListener {
            val item = currentItem as? LogListItem.Group ?: return@setOnMenuItemClickListener false

            when (it.itemId) {
                R.id.select_item -> {
                    onSelectClick(item)
                    true
                }

                R.id.copy_item -> {
                    onCopyClick(item)
                    true
                }

                R.id.create_filter_item -> {
                    onCreateFilterClick(item)
                    true
                }

                else -> false
            }
        }
    }

    init {
        binding.root.setOnClickListener {
            val item = currentItem as? LogListItem.Group ?: return@setOnClickListener
            onGroupClick(item.header.logLineId)
        }
        binding.root.setOnLongClickListener {
            popupMenu.show()
            true
        }
    }

    override fun ItemGroupLogBinding.bindTo(data: LogListItem) {
        val group = data as LogListItem.Group
        logText.text = group.header.displayText
        levelView.logLevel = group.maxLevel
        countView.text = "x${group.count}"
        expandIcon.rotation = if (group.expanded) 180f else 0f
    }

    override fun ItemGroupLogBinding.detach() {
        popupMenu.dismiss()
    }
}
