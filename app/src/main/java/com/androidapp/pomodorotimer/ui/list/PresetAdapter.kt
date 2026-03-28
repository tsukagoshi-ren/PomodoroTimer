package com.androidapp.pomodorotimer.ui.list

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.androidapp.pomodorotimer.data.model.Preset
import com.androidapp.pomodorotimer.data.model.TriggerType
import com.androidapp.pomodorotimer.databinding.ItemPresetBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PresetAdapter(
    private val onTap: (Preset) -> Unit,
    private val onLongPress: (Preset) -> Unit
) : ListAdapter<Preset, PresetAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemPresetBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(preset: Preset) {
            binding.textPresetName.text = preset.name
            binding.textTrigger.text = when (preset.triggerType) {
                TriggerType.BUTTON -> "ボタンで開始"
                TriggerType.DATETIME -> {
                    val dt = preset.triggerDatetime
                    if (dt != null) {
                        val fmt = SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN)
                        fmt.format(Date(dt))
                    } else "日時未設定"
                }
            }
            binding.root.setOnClickListener { onTap(preset) }
            binding.root.setOnLongClickListener { onLongPress(preset); true }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemPresetBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object DiffCallback : DiffUtil.ItemCallback<Preset>() {
        override fun areItemsTheSame(a: Preset, b: Preset) = a.id == b.id
        override fun areContentsTheSame(a: Preset, b: Preset) = a == b
    }
}