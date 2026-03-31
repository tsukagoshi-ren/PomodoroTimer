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
            val ctx = binding.root.context
            binding.textItemLabel.text   = item.label(ctx)
            binding.textItemSummary.text = item.summary(ctx)

            if (isHighlighted) {
                binding.root.setBackgroundColor(ContextCompat.getColor(ctx, R.color.highlight_current))
                binding.textItemLabel.setTypeface(null, Typeface.BOLD)
                binding.indicator.visibility = android.view.View.VISIBLE
            } else {
                binding.root.setBackgroundColor(ContextCompat.getColor(ctx, android.R.color.transparent))
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

private fun RoutineItem.label(ctx: android.content.Context) = when (this) {
    is RoutineItem.Timer     -> ctx.getString(R.string.item_label_timer)
    is RoutineItem.Alarm     -> ctx.getString(R.string.item_label_alarm)
    is RoutineItem.LoopStart -> ctx.getString(R.string.item_label_loop_start)
    is RoutineItem.LoopEnd   -> ctx.getString(R.string.item_label_loop_end)
}

private fun RoutineItem.summary(ctx: android.content.Context) = when (this) {
    is RoutineItem.Timer -> {
        val h = durationSeconds / 3600
        val m = (durationSeconds % 3600) / 60
        val s = durationSeconds % 60
        when {
            h > 0 -> ctx.getString(R.string.summary_time_hms, h, m, s)
            m > 0 -> ctx.getString(R.string.summary_time_ms, m, s)
            else  -> ctx.getString(R.string.summary_time_s, s)
        }
    }
    is RoutineItem.Alarm ->
        ctx.getString(R.string.summary_alarm, volume, durationSeconds) +
                if (vibrate) ctx.getString(R.string.summary_vibrate) else ""
    is RoutineItem.LoopStart -> ctx.getString(R.string.summary_loop_count, count)
    else -> ""
}