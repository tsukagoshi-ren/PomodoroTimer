package com.androidapp.pomodorotimer.ui.routineedit

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.androidapp.pomodorotimer.data.model.RoutineItem
import com.androidapp.pomodorotimer.databinding.ItemRoutineBinding
import com.androidapp.pomodorotimer.databinding.ItemRoutineAddButtonBinding

class RoutineItemAdapter(
    private val onDelete: (RoutineItem) -> Unit,
    private val onEdit: (RoutineItem) -> Unit,
    private val onAddButtonClick: (insertAfterIndex: Int?) -> Unit,
    private val onMove: (from: Int, to: Int) -> Unit
) : ListAdapter<RoutineListEntry, RecyclerView.ViewHolder>(RoutineDiffCallback) {

    var itemTouchHelper: ItemTouchHelper? = null

    /**
     * グレーアウトする display インデックスの範囲（両端含む）。
     * null = グレーアウトなし。
     *
     * notifyDataSetChanged は submitList と競合してクラッシュするため使わない。
     * 代わりに影響する行だけ notifyItemRangeChanged で更新する。
     * これは RecyclerView のレイアウト中でも安全に呼べる。
     */
    private var greyOutRange: IntRange? = null

    fun setGreyOutRange(range: IntRange) {
        val old = greyOutRange
        greyOutRange = range
        // 変化した範囲を再描画
        val start = minOf(old?.first ?: range.first, range.first)
        val end   = maxOf(old?.last  ?: range.last,  range.last)
        notifyItemRangeChanged(start, end - start + 1)
    }

    fun clearGreyOut() {
        val old = greyOutRange ?: return
        greyOutRange = null
        notifyItemRangeChanged(old.first, old.last - old.first + 1)
    }

    companion object {
        const val TYPE_ITEM       = 0
        const val TYPE_ADD_BUTTON = 1
    }

    fun getEntry(position: Int): RoutineListEntry = getItem(position)

    inner class ItemViewHolder(private val binding: ItemRoutineBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: RoutineListEntry.Item, isGreyedOut: Boolean) {
            val item = entry.routineItem
            binding.textItemType.text = item.label()
            binding.textItemSummary.text = item.summary()

            val density = binding.root.context.resources.displayMetrics.density
            val indentPx = (entry.depth * 20 * density).toInt()
            val basePx = (12 * density).toInt()
            (binding.root.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                lp.marginStart = basePx + indentPx
                binding.root.layoutParams = lp
            }

            binding.buttonEdit.visibility =
                if (item.isEditable()) android.view.View.VISIBLE else android.view.View.GONE
            binding.buttonEdit.setOnClickListener { onEdit(item) }
            binding.buttonDelete.setOnClickListener { onDelete(item) }

            val isLoopEnd = item is RoutineItem.LoopEnd
            if (isLoopEnd) {
                binding.dragHandle.alpha = 0.15f
                binding.dragHandle.setOnLongClickListener(null)
                binding.dragHandle.isClickable = false
            } else {
                binding.dragHandle.alpha = if (isGreyedOut) 0.1f else 0.4f
                binding.dragHandle.isClickable = !isGreyedOut
                if (isGreyedOut) {
                    binding.dragHandle.setOnLongClickListener(null)
                } else {
                    binding.dragHandle.setOnLongClickListener {
                        itemTouchHelper?.startDrag(this)
                        true
                    }
                }
            }

            binding.root.alpha = if (isGreyedOut) 0.35f else 1.0f
        }
    }

    inner class AddButtonViewHolder(private val binding: ItemRoutineAddButtonBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: RoutineListEntry.AddButton, isGreyedOut: Boolean) {
            val density = binding.root.context.resources.displayMetrics.density
            val indentPx = (entry.depth * 20 * density).toInt()
            binding.root.setPadding(
                (28 * density).toInt() + indentPx,
                binding.root.paddingTop,
                binding.root.paddingRight,
                binding.root.paddingBottom
            )
            binding.buttonAddItem.setOnClickListener {
                onAddButtonClick(entry.insertAfterIndex)
            }
            binding.root.alpha = if (isGreyedOut) 0.35f else 1.0f
        }
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is RoutineListEntry.Item      -> TYPE_ITEM
        is RoutineListEntry.AddButton -> TYPE_ADD_BUTTON
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            TYPE_ITEM -> ItemViewHolder(
                ItemRoutineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            else -> AddButtonViewHolder(
                ItemRoutineAddButtonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val isGreyedOut = greyOutRange?.contains(position) == true
        when (val entry = getItem(position)) {
            is RoutineListEntry.Item      -> (holder as ItemViewHolder).bind(entry, isGreyedOut)
            is RoutineListEntry.AddButton -> (holder as AddButtonViewHolder).bind(entry, isGreyedOut)
        }
    }

    // ---- displayList → _items インデックス変換 ----

    fun toItemIndex(displayPos: Int): Int {
        if (displayPos < 0 || displayPos >= currentList.size) return -1
        if (currentList[displayPos] is RoutineListEntry.AddButton) return -1
        var count = 0
        for (i in 0 until displayPos) {
            if (currentList[i] is RoutineListEntry.Item) count++
        }
        return count
    }

    // ---- ドラッグ中の表示更新 ----

    fun onItemMoved(fromDisplay: Int, toDisplay: Int) {
        val list = currentList.toMutableList()
        val moved = list.removeAt(fromDisplay)
        list.add(toDisplay, moved)
        submitList(list)
    }

    fun onItemDropped(fromDisplay: Int, toDisplay: Int) {
        val fromItem = toItemIndex(fromDisplay)
        val toItem   = toItemIndex(toDisplay)
        if (fromItem != -1 && toItem != -1) {
            onMove(fromItem, toItem)
        }
    }
}

private object RoutineDiffCallback : DiffUtil.ItemCallback<RoutineListEntry>() {
    override fun areItemsTheSame(a: RoutineListEntry, b: RoutineListEntry): Boolean {
        if (a is RoutineListEntry.Item && b is RoutineListEntry.Item)
            return a.routineItem.id == b.routineItem.id &&
                    a.routineItem.order == b.routineItem.order
        if (a is RoutineListEntry.AddButton && b is RoutineListEntry.AddButton)
            return a.insertAfterIndex == b.insertAfterIndex
        return false
    }
    override fun areContentsTheSame(a: RoutineListEntry, b: RoutineListEntry) = a == b
}

private fun RoutineItem.label() = when (this) {
    is RoutineItem.LoopStart -> "🔁 ループ開始"
    is RoutineItem.LoopEnd   -> "🔁 ループ終了"
    is RoutineItem.Timer     -> "⏱ タイマー"
    is RoutineItem.Alarm     -> "🔔 アラーム"
}

private fun RoutineItem.summary() = when (this) {
    is RoutineItem.LoopStart -> "${count}回繰り返す"
    is RoutineItem.Timer -> {
        val h = durationSeconds / 3600
        val m = (durationSeconds % 3600) / 60
        val s = durationSeconds % 60
        val timeStr = when {
            h > 0 -> "${h}時間${m}分${s}秒"
            m > 0 -> "${m}分${s}秒"
            else  -> "${s}秒"
        }
        if (tickSound != null) "$timeStr · $tickSound" else timeStr
    }
    is RoutineItem.Alarm -> "音量${volume}% · ${durationSeconds}秒" +
            if (vibrate) " · バイブあり" else ""
    else -> ""
}

private fun RoutineItem.isEditable() = this is RoutineItem.LoopStart ||
        this is RoutineItem.Timer || this is RoutineItem.Alarm