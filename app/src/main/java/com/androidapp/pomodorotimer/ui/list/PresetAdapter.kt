package com.androidapp.pomodorotimer.ui.list

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
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
import com.androidapp.pomodorotimer.util.AlarmScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class PresetAdapter(
    private val onTap: (Preset) -> Unit,
    private val onLongPress: (Preset) -> Unit,
    private val onEditSwipe: (Preset) -> Unit,
    private val onDeleteSwipe: (Preset) -> Unit,
    private val onCheckToggle: (Preset) -> Unit
) : ListAdapter<Preset, PresetAdapter.ViewHolder>(DiffCallback) {

    var itemTouchHelper: ItemTouchHelper? = null
    var isSelectionMode: Boolean = false
    var selectedIds: Set<Int> = emptySet()

    private var swipedHolder: ViewHolder? = null

    companion object DiffCallback : DiffUtil.ItemCallback<Preset>() {
        override fun areItemsTheSame(a: Preset, b: Preset) = a.id == b.id
        override fun areContentsTheSame(a: Preset, b: Preset) = a == b

        private const val SWIPE_MAX_DP = 80f
        private const val DIRECTION_THRESHOLD = 12f
        private const val SNAP_THRESHOLD_RATIO = 0.3f
    }

    private fun closeSwipedHolder(except: ViewHolder? = null) {
        val current = swipedHolder ?: return
        if (current === except) return
        current.resetSwipeAnimated()
        swipedHolder = null
    }

    inner class ViewHolder(private val binding: ItemPresetBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var swipeMaxPx = 0f
        var isSwiped = false
        private var swipedDirection = 0

        @SuppressLint("ClickableViewAccessibility")
        fun bind(preset: Preset) {
            val density = binding.root.context.resources.displayMetrics.density
            swipeMaxPx = SWIPE_MAX_DP * density

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
                TriggerType.WEEKLY -> {
                    val days = AlarmScheduler.weekdaysToString(preset.weekdays)
                    val h = preset.triggerTimeOfDay / 60
                    val m = preset.triggerTimeOfDay % 60
                    "$days %02d:%02d".format(h, m)
                }
            }

            if (isSelectionMode) {
                resetSwipe()
                // 選択モードでは背景タッチリスナーをクリア
                binding.swipeBgEdit.setOnTouchListener(null)
                binding.swipeBgDelete.setOnTouchListener(null)
                binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = preset.id in selectedIds
                binding.dragHandle.visibility = View.VISIBLE
                binding.cardView.setOnTouchListener(null)
                binding.cardView.setOnLongClickListener(null)
                binding.root.setOnClickListener { onCheckToggle(preset) }
                binding.root.setOnLongClickListener(null)
                binding.checkbox.setOnClickListener { onCheckToggle(preset) }
                binding.dragHandle.setOnLongClickListener {
                    itemTouchHelper?.startDrag(this)
                    true
                }
            } else {
                binding.checkbox.visibility = View.GONE
                binding.dragHandle.visibility = View.GONE
                binding.root.setOnClickListener(null)
                binding.root.setOnLongClickListener(null)
                // 背景エリアへのタッチをcardViewに転送する
                binding.swipeBgEdit.setOnTouchListener { _, event ->
                    binding.cardView.dispatchTouchEvent(event)
                }
                binding.swipeBgDelete.setOnTouchListener { _, event ->
                    binding.cardView.dispatchTouchEvent(event)
                }
                setupSwipeTouch(preset)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        fun setupSwipeTouch(preset: Preset) {
            var startX = 0f
            var startY = 0f
            var startTransX = 0f
            var swipeDecided = false
            var verticalDecided = false
            var longPressHandled = false

            val longPressRunnable = Runnable {
                if (!swipeDecided) {
                    longPressHandled = true
                    onLongPress(preset)
                }
            }

            binding.cardView.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        startY = event.rawY
                        startTransX = binding.cardView.translationX
                        swipeDecided = false
                        verticalDecided = false
                        longPressHandled = false

                        if (!isSwiped) {
                            v.postDelayed(longPressRunnable, 500)
                        }
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (isSwiped) {
                            val dx = event.rawX - startX
                            val dy = event.rawY - startY
                            if (abs(dx) > DIRECTION_THRESHOLD || abs(dy) > DIRECTION_THRESHOLD) {
                                resetSwipeAnimated()
                                swipedHolder = null
                            }
                            return@setOnTouchListener true
                        }

                        val dx = event.rawX - startX
                        val dy = event.rawY - startY

                        if (!swipeDecided && !verticalDecided) {
                            if (abs(dx) > DIRECTION_THRESHOLD || abs(dy) > DIRECTION_THRESHOLD) {
                                if (abs(dx) >= abs(dy)) {
                                    swipeDecided = true
                                    v.removeCallbacks(longPressRunnable)
                                    v.parent?.requestDisallowInterceptTouchEvent(true)
                                    closeSwipedHolder(except = this)
                                } else {
                                    verticalDecided = true
                                    v.removeCallbacks(longPressRunnable)
                                    v.parent?.requestDisallowInterceptTouchEvent(false)
                                    return@setOnTouchListener false
                                }
                            }
                        }

                        if (verticalDecided) return@setOnTouchListener false
                        if (!swipeDecided) return@setOnTouchListener true

                        val newTx = (startTransX + dx).coerceIn(-swipeMaxPx, swipeMaxPx)
                        translateCard(newTx)
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        v.removeCallbacks(longPressRunnable)
                        v.parent?.requestDisallowInterceptTouchEvent(false)

                        if (verticalDecided) return@setOnTouchListener false

                        val dx = event.rawX - startX
                        val dy = event.rawY - startY

                        when {
                            isSwiped && abs(dx) < DIRECTION_THRESHOLD && abs(dy) < DIRECTION_THRESHOLD -> {
                                val dir = swipedDirection
                                resetSwipeAnimated()
                                swipedHolder = null
                                if (dir > 0) onEditSwipe(preset)
                                else onDeleteSwipe(preset)
                                true
                            }

                            !swipeDecided && abs(dx) < DIRECTION_THRESHOLD && abs(dy) < DIRECTION_THRESHOLD -> {
                                if (!longPressHandled) onTap(preset)
                                true
                            }

                            swipeDecided -> {
                                val totalTx = startTransX + dx
                                val threshold = swipeMaxPx * SNAP_THRESHOLD_RATIO
                                when {
                                    totalTx > threshold -> {
                                        snapCard(swipeMaxPx)
                                        isSwiped = true
                                        swipedDirection = 1
                                        swipedHolder = this
                                    }
                                    totalTx < -threshold -> {
                                        snapCard(-swipeMaxPx)
                                        isSwiped = true
                                        swipedDirection = -1
                                        swipedHolder = this
                                    }
                                    else -> {
                                        resetSwipeAnimated()
                                        if (swipedHolder === this) swipedHolder = null
                                    }
                                }
                                true
                            }

                            else -> false
                        }
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        v.removeCallbacks(longPressRunnable)
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        if (swipeDecided && !isSwiped) {
                            resetSwipeAnimated()
                            if (swipedHolder === this) swipedHolder = null
                        }
                        false
                    }

                    else -> false
                }
            }
        }

        private fun translateCard(tx: Float) {
            binding.cardView.translationX = tx
            when {
                tx > 0 -> {
                    binding.swipeBgEdit.visibility = View.VISIBLE
                    binding.swipeBgDelete.visibility = View.INVISIBLE
                    binding.swipeBgEdit.alpha = (tx / swipeMaxPx).coerceIn(0f, 1f)
                }
                tx < 0 -> {
                    binding.swipeBgEdit.visibility = View.INVISIBLE
                    binding.swipeBgDelete.visibility = View.VISIBLE
                    binding.swipeBgDelete.alpha = (abs(tx) / swipeMaxPx).coerceIn(0f, 1f)
                }
                else -> {
                    binding.swipeBgEdit.visibility = View.INVISIBLE
                    binding.swipeBgDelete.visibility = View.INVISIBLE
                }
            }
        }

        private fun snapCard(targetX: Float) {
            binding.cardView.animate()
                .translationX(targetX)
                .setDuration(150)
                .start()
            if (targetX > 0) {
                binding.swipeBgEdit.visibility = View.VISIBLE
                binding.swipeBgEdit.alpha = 1f
                binding.swipeBgDelete.visibility = View.INVISIBLE
            } else {
                binding.swipeBgDelete.visibility = View.VISIBLE
                binding.swipeBgDelete.alpha = 1f
                binding.swipeBgEdit.visibility = View.INVISIBLE
            }
        }

        fun resetSwipe() {
            isSwiped = false
            swipedDirection = 0
            binding.cardView.translationX = 0f
            binding.swipeBgEdit.visibility = View.INVISIBLE
            binding.swipeBgDelete.visibility = View.INVISIBLE
        }

        fun resetSwipeAnimated() {
            isSwiped = false
            swipedDirection = 0
            binding.cardView.animate()
                .translationX(0f)
                .setDuration(150)
                .withEndAction {
                    binding.swipeBgEdit.visibility = View.INVISIBLE
                    binding.swipeBgDelete.visibility = View.INVISIBLE
                }
                .start()
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
}