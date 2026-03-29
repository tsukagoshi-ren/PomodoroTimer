package com.androidapp.pomodorotimer.ui.timerrun

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.androidapp.pomodorotimer.R
import com.androidapp.pomodorotimer.data.model.RoutineItem
import com.androidapp.pomodorotimer.databinding.ItemTimerRunBinding

class TimerRunItemAdapter : RecyclerView.Adapter<TimerRunItemAdapter.ViewHolder>() {

    private var items: List<RoutineItem> = emptyList()
    private var highlightIndex: Int = -1

    fun submitList(newItems: List<RoutineItem>, currentIndex: Int) {
        items = newItems
        highlightIndex = currentIndex
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemTimerRunBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RoutineItem, isHighlighted: Boolean) {
            binding.textItemLabel.text = item.label()
            binding.textItemSummary.text = item.summary()

            if (isHighlighted) {
                binding.root.setBackgroundColor(
                    ContextCompat.getColor(binding.root.context, R.color.highlight_current)
                )
                binding.textItemLabel.setTypeface(null, Typeface.BOLD)
                binding.indicator.visibility = android.view.View.VISIBLE
            } else {
                binding.root.setBackgroundColor(
                    ContextCompat.getColor(binding.root.context, android.R.color.transparent)
                )
                binding.textItemLabel.setTypeface(null, Typeface.NORMAL)
                binding.indicator.visibility = android.view.View.INVISIBLE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemTimerRunBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(items[position], position == highlightIndex)

    override fun getItemCount() = items.size
}

private fun RoutineItem.label() = when (this) {
    is RoutineItem.Timer     -> "⏱ タイマー"
    is RoutineItem.Alarm     -> "🔔 アラーム"
    is RoutineItem.LoopStart -> "🔁 ループ開始"
    is RoutineItem.LoopEnd   -> "🔁 ループ終了"
}

private fun RoutineItem.summary() = when (this) {
    is RoutineItem.Timer     -> { val m = durationSeconds / 60; val s = durationSeconds % 60; if (m > 0) "${m}分${s}秒" else "${s}秒" }
    is RoutineItem.Alarm     -> "音量${volume}% / ${durationSeconds}秒" + if (vibrate) " / バイブあり" else ""
    is RoutineItem.LoopStart -> "${count}回繰り返す"
    else -> ""
}