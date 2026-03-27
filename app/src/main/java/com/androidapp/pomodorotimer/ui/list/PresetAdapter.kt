package com.androidapp.pomodorotimer.ui.list

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.androidapp.pomodorotimer.data.model.preset.Preset
import com.androidapp.pomodorotimer.databinding.ItemPresetBinding

class PresetAdapter(
    private val onTap: (Preset) -> Unit,
    private val onLongPress: (Preset) -> Unit
) : ListAdapter<Preset, PresetAdapter.PresetViewHolder>(DiffCallback) {

    inner class PresetViewHolder(
        private val binding: ItemPresetBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(preset: Preset) {
            binding.textPresetName.text = preset.name
            binding.root.setOnClickListener { onTap(preset) }
            binding.root.setOnLongClickListener {
                onLongPress(preset)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PresetViewHolder {
        val binding = ItemPresetBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PresetViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PresetViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Preset>() {
        override fun areItemsTheSame(oldItem: Preset, newItem: Preset) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Preset, newItem: Preset) =
            oldItem == newItem
    }
}