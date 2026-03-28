package com.androidapp.pomodorotimer.ui.routineedit

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.androidapp.pomodorotimer.data.model.RoutineItem
import com.androidapp.pomodorotimer.databinding.ItemRoutineBinding

class RoutineItemAdapter(
    private val onDelete: (RoutineItem) -> Unit,
    private val onEdit: (RoutineItem) -> Unit
) : ListAdapter<RoutineItem, RoutineItemAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemRoutineBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RoutineItem) {
            binding.textItemType.text = item.label()
            binding.textItemSummary.text = item.summary()
            // 繰り返し・条件の始まり終わりはインデント表現
            binding.root.setPadding(item.indentDp() * 4, 0, 0, 0)

            binding.buttonEdit.visibility =
                if (item.isEditable()) android.view.View.VISIBLE else android.view.View.GONE
            binding.buttonEdit.setOnClickListener { onEdit(item) }
            binding.buttonDelete.setOnClickListener { onDelete(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemRoutineBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object DiffCallback : DiffUtil.ItemCallback<RoutineItem>() {
        override fun areItemsTheSame(a: RoutineItem, b: RoutineItem) =
            a.id == b.id && a.order == b.order
        override fun areContentsTheSame(a: RoutineItem, b: RoutineItem) = a == b
    }
}

private fun RoutineItem.label() = when (this) {
    is RoutineItem.RepeatStart    -> "🔁 繰り返し始まり"
    is RoutineItem.RepeatEnd      -> "🔁 繰り返し終わり"
    is RoutineItem.ConditionStart -> "❓ 条件分岐始まり"
    is RoutineItem.ConditionEnd   -> "❓ 条件分岐終わり"
    is RoutineItem.Timer          -> "⏱ タイマー"
    is RoutineItem.Alarm          -> "🔔 アラーム"
}

private fun RoutineItem.summary() = when (this) {
    is RoutineItem.RepeatStart -> "${count}回繰り返す"
    is RoutineItem.Timer       -> {
        val m = durationSeconds / 60; val s = durationSeconds % 60
        if (m > 0) "${m}分${s}秒" else "${s}秒"
    }
    is RoutineItem.Alarm -> "音量${volume}% / ${durationSeconds}秒" +
            (if (vibrate) " / バイブあり" else "")
    else -> ""
}

private fun RoutineItem.indentDp() = when (this) {
    is RoutineItem.RepeatEnd, is RoutineItem.ConditionEnd -> 0
    is RoutineItem.RepeatStart, is RoutineItem.ConditionStart -> 0
    else -> 16
}

private fun RoutineItem.isEditable() = when (this) {
    is RoutineItem.RepeatStart, is RoutineItem.Timer, is RoutineItem.Alarm -> true
    else -> false
}