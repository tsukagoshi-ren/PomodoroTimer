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

class RoutineItemAdapter(
    private val onDelete: (RoutineItem) -> Unit,
    private val onEdit: (RoutineItem) -> Unit,
    private val onMove: (from: Int, to: Int) -> Unit
) : ListAdapter<RoutineItem, RoutineItemAdapter.ViewHolder>(DiffCallback) {

    // ItemTouchHelperをAdapterから参照できるよう保持
    var itemTouchHelper: ItemTouchHelper? = null

    inner class ViewHolder(private val binding: ItemRoutineBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RoutineItem) {
            binding.textItemType.text = item.label()
            binding.textItemSummary.text = item.summary()

            // インデント（LoopStart/End以外のアイテムをループ内として視覚的に示す）
            val startPx = (item.indentDp() *
                    binding.root.context.resources.displayMetrics.density).toInt()
            binding.dragHandle.setPadding(
                startPx + binding.dragHandle.paddingTop,
                binding.dragHandle.paddingTop,
                binding.dragHandle.paddingRight,
                binding.dragHandle.paddingBottom
            )

            binding.buttonEdit.visibility =
                if (item.isEditable()) android.view.View.VISIBLE else android.view.View.GONE
            binding.buttonEdit.setOnClickListener { onEdit(item) }
            binding.buttonDelete.setOnClickListener { onDelete(item) }

            // ドラッグハンドルの長押しでドラッグ開始
            binding.dragHandle.setOnLongClickListener {
                itemTouchHelper?.startDrag(this)
                true
            }
            // タッチ開始でもドラッグを即座に受け付ける（長押し後のムーブに対応）
            binding.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper?.startDrag(this)
                }
                false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemRoutineBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    // ドラッグ中のリスト内での一時的な並び替え表示
    fun onItemMoved(from: Int, to: Int) {
        val list = currentList.toMutableList()
        val moved = list.removeAt(from)
        list.add(to, moved)
        submitList(list)
    }

    // ドラッグ完了時にViewModelへ通知
    fun onItemDropped(from: Int, to: Int) {
        onMove(from, to)
    }

    companion object DiffCallback : DiffUtil.ItemCallback<RoutineItem>() {
        override fun areItemsTheSame(a: RoutineItem, b: RoutineItem) =
            a.id == b.id && a.order == b.order
        override fun areContentsTheSame(a: RoutineItem, b: RoutineItem) = a == b
    }
}

private fun RoutineItem.label() = when (this) {
    is RoutineItem.LoopStart      -> "🔁 ループ開始"
    is RoutineItem.LoopEnd        -> "🔁 ループ終了"
    is RoutineItem.ConditionStart -> "❓ 条件分岐始まり"
    is RoutineItem.ConditionEnd   -> "❓ 条件分岐終わり"
    is RoutineItem.Timer          -> "⏱ タイマー"
    is RoutineItem.Alarm          -> "🔔 アラーム"
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

private fun RoutineItem.indentDp() = when (this) {
    is RoutineItem.LoopStart, is RoutineItem.LoopEnd,
    is RoutineItem.ConditionStart, is RoutineItem.ConditionEnd -> 0
    else -> 16
}

private fun RoutineItem.isEditable() = when (this) {
    is RoutineItem.LoopStart, is RoutineItem.Timer, is RoutineItem.Alarm -> true
    else -> false
}