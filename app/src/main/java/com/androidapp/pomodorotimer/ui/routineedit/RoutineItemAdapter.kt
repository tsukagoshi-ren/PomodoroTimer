package com.androidapp.pomodorotimer.ui.routineedit

import android.view.LayoutInflater
import android.view.MotionEvent
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
    /** AddButtonがタップされたとき呼ばれる。insertAfterIndex はViewModelと同じ意味 */
    private val onAddButtonClick: (insertAfterIndex: Int?) -> Unit,
    /** ドラッグ完了時に呼ばれる。from/to はRoutineItemリスト上のインデックス */
    private val onMove: (from: Int, to: Int) -> Unit
) : ListAdapter<RoutineListEntry, RecyclerView.ViewHolder>(RoutineDiffCallback) {

    var itemTouchHelper: ItemTouchHelper? = null

    companion object {
        private const val TYPE_ITEM       = 0
        private const val TYPE_ADD_BUTTON = 1
    }

    // ---- ViewHolders ----

    inner class ItemViewHolder(private val binding: ItemRoutineBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: RoutineListEntry.Item) {
            val item = entry.routineItem
            binding.textItemType.text = item.label()
            binding.textItemSummary.text = item.summary()

            binding.buttonEdit.visibility =
                if (item.isEditable()) android.view.View.VISIBLE else android.view.View.GONE
            binding.buttonEdit.setOnClickListener { onEdit(item) }
            binding.buttonDelete.setOnClickListener { onDelete(item) }

            // ドラッグハンドルの長押し or タッチでドラッグ開始
            binding.dragHandle.setOnLongClickListener {
                itemTouchHelper?.startDrag(this)
                true
            }
            binding.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper?.startDrag(this)
                }
                false
            }
        }
    }

    inner class AddButtonViewHolder(private val binding: ItemRoutineAddButtonBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: RoutineListEntry.AddButton) {
            binding.buttonAddItem.setOnClickListener {
                onAddButtonClick(entry.insertAfterIndex)
            }
        }
    }

    // ---- ListAdapter overrides ----

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
        when (val entry = getItem(position)) {
            is RoutineListEntry.Item      -> (holder as ItemViewHolder).bind(entry)
            is RoutineListEntry.AddButton -> (holder as AddButtonViewHolder).bind(entry)
        }
    }

    // ---- ドラッグ中の一時的な並び替え表示 ----

    /** displayListエントリのインデックスをRoutineItemリスト上のインデックスに変換 */
    private fun toItemIndex(displayIndex: Int): Int {
        var itemCount = 0
        for (i in 0 until displayIndex) {
            if (getItem(i) is RoutineListEntry.Item) itemCount++
        }
        return itemCount
    }

    fun onItemMoved(fromDisplay: Int, toDisplay: Int) {
        val list = currentList.toMutableList()
        val moved = list.removeAt(fromDisplay)
        list.add(toDisplay, moved)
        submitList(list)
    }

    fun onItemDropped(fromDisplay: Int, toDisplay: Int) {
        // displayListのインデックスをRoutineItemリスト上のインデックスに変換して通知
        onMove(toItemIndex(fromDisplay), toItemIndex(toDisplay))
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

// ---- 拡張関数 ----

private fun RoutineItem.label() = when (this) {
    is RoutineItem.LoopStart -> "🔁 ループ開始"
    is RoutineItem.LoopEnd   -> "🔁 ループ終了"
    is RoutineItem.Timer     -> "⏱ タイマー"
    is RoutineItem.Alarm     -> "🔔 アラーム"
}

private fun RoutineItem.summary() = when (this) {
    is RoutineItem.LoopStart -> "${count}回繰り返す"
    is RoutineItem.Timer -> {
        val m = durationSeconds / 60; val s = durationSeconds % 60
        if (m > 0) "${m}分${s}秒" else "${s}秒"
    }
    is RoutineItem.Alarm -> "音量${volume}% / ${durationSeconds}秒" +
            (if (vibrate) " / バイブあり" else "")
    else -> ""
}

private fun RoutineItem.isEditable() = when (this) {
    is RoutineItem.LoopStart, is RoutineItem.Timer, is RoutineItem.Alarm -> true
    else -> false
}