package com.androidapp.pomodorotimer.ui.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.androidapp.pomodorotimer.R
import com.androidapp.pomodorotimer.data.model.Preset
import com.androidapp.pomodorotimer.data.model.TriggerType
import com.androidapp.pomodorotimer.databinding.ItemPresetBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PresetAdapter(
    private val onTap: (Preset) -> Unit,
    private val onLongPress: (Preset) -> Unit,
    private val onEditClick: (Preset) -> Unit,
    private val onCheckToggle: (Preset) -> Unit
) : ListAdapter<Preset, PresetAdapter.ViewHolder>(DiffCallback) {

    var itemTouchHelper: ItemTouchHelper? = null
    var isSelectionMode: Boolean = false
    var selectedIds: Set<Int> = emptySet()

    inner class ViewHolder(private val binding: ItemPresetBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(preset: Preset) {
            binding.textPresetName.text = preset.name
            binding.textTrigger.text = when (preset.triggerType) {
                TriggerType.BUTTON -> binding.root.context.getString(R.string.trigger_button)
                TriggerType.DATETIME -> {
                    val dt = preset.triggerDatetime
                    if (dt != null) {
                        SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(dt))
                    } else {
                        binding.root.context.getString(R.string.trigger_datetime_unset)
                    }
                }
            }

            if (isSelectionMode) {
                // 選択モード: チェックボックス + ドラッグハンドル + 編集ボタン
                binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = preset.id in selectedIds
                binding.dragHandle.visibility = View.VISIBLE
                binding.buttonEdit.visibility = View.VISIBLE

                binding.root.setOnClickListener { onCheckToggle(preset) }
                binding.root.setOnLongClickListener(null)
                binding.checkbox.setOnClickListener { onCheckToggle(preset) }
                binding.buttonEdit.setOnClickListener { onEditClick(preset) }
                binding.dragHandle.setOnLongClickListener {
                    itemTouchHelper?.startDrag(this)
                    true
                }
            } else {
                // 通常モード
                binding.checkbox.visibility = View.GONE
                binding.dragHandle.visibility = View.GONE
                binding.buttonEdit.visibility = View.GONE

                binding.root.setOnClickListener { onTap(preset) }
                binding.root.setOnLongClickListener { onLongPress(preset); true }
                binding.dragHandle.setOnLongClickListener(null)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemPresetBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    fun onItemMoved(from: Int, to: Int) {
        val list = currentList.toMutableList()
        val moved = list.removeAt(from)
        list.add(to, moved)
        submitList(list)
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Preset>() {
        override fun areItemsTheSame(a: Preset, b: Preset) = a.id == b.id
        override fun areContentsTheSame(a: Preset, b: Preset) = a == b
    }
}