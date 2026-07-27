package com.f0x1d.logfox.feature.logging.presentation.list.viewholder

import android.view.Gravity
import androidx.appcompat.widget.PopupMenu
import com.f0x1d.logfox.core.copy.copyText
import com.f0x1d.logfox.core.recycler.viewholder.BaseViewHolder
import com.f0x1d.logfox.feature.logging.presentation.R
import com.f0x1d.logfox.feature.logging.presentation.databinding.ItemLogBinding
import com.f0x1d.logfox.feature.logging.presentation.list.model.LogLineItem
import com.f0x1d.logfox.feature.logging.presentation.list.model.LogListItem

class LogViewHolder(
    binding: ItemLogBinding,
    private val onClick: (LogLineItem) -> Unit,
    private val onSelectClick: (LogLineItem) -> Unit,
    private val onCopyClick: (LogLineItem) -> Unit,
    private val onCreateFilterClick: (LogLineItem) -> Unit,
) : BaseViewHolder<LogListItem, ItemLogBinding>(binding) {

    private val popupMenu: PopupMenu = PopupMenu(
        binding.root.context,
        binding.root,
        Gravity.END,
    ).apply {
        inflate(R.menu.log_menu)
        setForceShowIcon(true)

        setOnMenuItemClickListener {
            val item = currentItem as? LogListItem.Item ?: return@setOnMenuItemClickListener false

            when (it.itemId) {
                R.id.select_item -> {
                    onSelectClick(item.data)
                    true
                }

                R.id.copy_item -> {
                    onCopyClick(item.data)
                    true
                }

                R.id.create_filter_item -> {
                    onCreateFilterClick(item.data)
                    true
                }

                else -> false
            }
        }
    }

    init {
        binding.apply {
            root.setOnClickListener {
                val item = currentItem as? LogListItem.Item ?: return@setOnClickListener
                if (item.data.selected) {
                    onSelectClick(item.data)
                } else {
                    onClick(item.data)
                    highlightAndCopy(item.data)
                }
            }
            root.setOnLongClickListener {
                popupMenu.show()
                return@setOnLongClickListener true
            }
        }
    }

    private fun highlightAndCopy(item: LogLineItem) {
        if (item.content.isEmpty()) return

        val context = binding.root.context
        context.copyText(item.content)

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

    override fun ItemLogBinding.bindTo(data: LogListItem) {
        val lineItem = (data as LogListItem.Item).data
        logText.textSize = lineItem.textSize
        levelView.textSize = lineItem.textSize

        logText.text = lineItem.displayText

        levelView.logLevel = lineItem.level

        logText.maxLines = if (lineItem.expanded) Int.MAX_VALUE else 1
        container.isSelected = lineItem.selected
    }

    override fun ItemLogBinding.detach() {
        popupMenu.dismiss()
    }
}
