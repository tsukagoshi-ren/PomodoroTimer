package com.androidapp.pomodorotimer.ui.routineedit

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.androidapp.pomodorotimer.App
import com.androidapp.pomodorotimer.R
import com.androidapp.pomodorotimer.data.model.RoutineItem
import com.androidapp.pomodorotimer.databinding.FragmentRoutineEditBinding
import kotlinx.coroutines.launch

class RoutineEditFragment : Fragment() {

    private var _binding: FragmentRoutineEditBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RoutineEditViewModel by viewModels {
        RoutineEditViewModel.Factory((requireActivity().application as App).presetRepository)
    }

    private lateinit var adapter: RoutineItemAdapter
    private lateinit var dragHelper: LoopBlockDragHelper
    private var presetId: Int = -1

    // アラーム音選択
    private var onSoundPicked: ((Uri) -> Unit)? = null
    private val soundPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                } ?: return@registerForActivityResult
                onSoundPicked?.invoke(uri)
            }
        }

    // Tick音の選択肢
    private val tickSoundOptions: List<Pair<String, String?>> by lazy {
        listOf(
            getString(R.string.tick_sound_none)    to null,
            getString(R.string.tick_sound_clock)   to "tick_clock",
            getString(R.string.tick_sound_wood)    to "tick_wood",
            getString(R.string.tick_sound_bell)    to "tick_bell",
            getString(R.string.tick_sound_soft)    to "tick_soft",
            getString(R.string.tick_sound_beep)    to "tick_beep",
            getString(R.string.tick_sound_digital) to "tick_digital",
        )
    }

    // SoundPool（プレビュー用）
    private var soundPool: SoundPool? = null
    private val soundIdCache = mutableMapOf<String, Int>()
    private var currentTickStreamId: Int = 0

    // アラームプレビュー用
    private var previewRingtone: Ringtone? = null
    private var previewRingtoneUri: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoutineEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        presetId = arguments?.getInt("presetId") ?: return
        dragHelper = LoopBlockDragHelper(binding.recyclerView)
        initSoundPool()

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_routine_edit, menu)
            }
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == R.id.action_save) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        viewModel.saveItems(presetId)
                        parentFragmentManager.popBackStack()
                    }
                    return true
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        adapter = RoutineItemAdapter(
            onDelete         = { item -> viewModel.removeItem(item) },
            onEdit           = { item -> showEditDialog(item) },
            onAddButtonClick = { insertAfterIndex -> showAddItemDialog(insertAfterIndex) },
            onMove           = { from, to -> viewModel.moveItem(from, to) }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            private var dragFromDisplay = -1
            private var dragToDisplay   = -1

            override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                val pos = vh.adapterPosition
                if (pos < 0 || pos >= adapter.itemCount) return makeMovementFlags(0, 0)
                val entry = adapter.getEntry(pos)
                val draggable = when {
                    entry is RoutineListEntry.AddButton -> false
                    entry is RoutineListEntry.Item && entry.routineItem is RoutineItem.LoopEnd -> false
                    else -> true
                }
                return if (draggable) makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
                else makeMovementFlags(0, 0)
            }

            override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(vh, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && vh != null) {
                    val pos = vh.adapterPosition
                    if (pos < 0) return
                    val entry = adapter.getEntry(pos)
                    if (entry is RoutineListEntry.Item && entry.routineItem is RoutineItem.LoopStart) {
                        val (blockVhs, blockEntries) = collectBlockViewHoldersAndEntries(pos)
                        if (blockVhs.isNotEmpty()) {
                            val touchY = vh.itemView.top.toFloat() + vh.itemView.height / 2f
                            dragHelper.startOverlay(vh, blockVhs, blockEntries, touchY)
                        }
                    }
                } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                    dragHelper.stopOverlay()
                    adapter.clearGreyOut()
                }
            }

            override fun onChildDraw(
                c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                if (dragHelper.isActive) {
                    dragHelper.updateOverlayPosition(vh.itemView.top.toFloat() + dY)
                    return
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            }

            override fun onMove(
                rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = vh.adapterPosition
                val toPos   = target.adapterPosition
                if (fromPos < 0 || toPos < 0 || fromPos == toPos) return false

                val dragEntry   = adapter.getEntry(fromPos)
                val targetEntry = adapter.getEntry(toPos)
                if (targetEntry is RoutineListEntry.AddButton) return false

                if (dragEntry is RoutineListEntry.Item && dragEntry.routineItem is RoutineItem.LoopStart) {
                    val blockStart = if (dragFromDisplay != -1) dragFromDisplay else fromPos
                    val blockEnd   = findBlockEndDisplay(blockStart)
                    if (blockEnd != -1 && toPos in blockStart..blockEnd) return false
                }

                if (dragFromDisplay == -1) dragFromDisplay = fromPos
                dragToDisplay = toPos
                adapter.onItemMoved(fromPos, toPos)
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                val from = dragFromDisplay
                val to   = dragToDisplay
                dragFromDisplay = -1
                dragToDisplay   = -1
                dragHelper.stopOverlay()
                adapter.clearGreyOut()

                if (from != -1 && to != -1 && from != to) {
                    val stable  = viewModel.displayList.value
                    val fromItem = displayToItemIndex(stable, from)
                    val toItem   = displayToItemIndex(stable, to)
                    if (fromItem != -1 && toItem != -1) viewModel.moveItem(fromItem, toItem)
                }
            }

            override fun isLongPressDragEnabled() = false

            private fun findBlockEndDisplay(loopStartDisplayPos: Int): Int {
                var depth = 0
                for (i in loopStartDisplayPos until adapter.itemCount) {
                    val e = adapter.getEntry(i)
                    if (e is RoutineListEntry.Item) {
                        when (e.routineItem) {
                            is RoutineItem.LoopStart -> depth++
                            is RoutineItem.LoopEnd   -> { depth--; if (depth == 0) return i }
                            else -> {}
                        }
                    }
                }
                return -1
            }

            private fun collectBlockViewHoldersAndEntries(
                loopStartDisplayPos: Int
            ): Pair<List<RecyclerView.ViewHolder>, List<RoutineListEntry>> {
                val blockEnd = findBlockEndDisplay(loopStartDisplayPos)
                if (blockEnd == -1) return Pair(emptyList(), emptyList())
                val vhs     = mutableListOf<RecyclerView.ViewHolder>()
                val entries = mutableListOf<RoutineListEntry>()
                for (displayPos in loopStartDisplayPos..blockEnd) {
                    val entry = adapter.getEntry(displayPos)
                    if (entry is RoutineListEntry.AddButton) continue
                    binding.recyclerView.findViewHolderForAdapterPosition(displayPos)
                        ?.let { vhs.add(it); entries.add(entry) }
                }
                return Pair(vhs, entries)
            }

            private fun displayToItemIndex(displayList: List<RoutineListEntry>, displayPos: Int): Int {
                if (displayPos < 0 || displayPos >= displayList.size) return -1
                if (displayList[displayPos] is RoutineListEntry.AddButton) return -1
                var count = 0
                for (i in 0 until displayPos) {
                    if (displayList[i] is RoutineListEntry.Item) count++
                }
                return count
            }
        })
        touchHelper.attachToRecyclerView(binding.recyclerView)
        adapter.itemTouchHelper = touchHelper

        viewModel.loadItems(presetId)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.displayList.collect { adapter.submitList(it) }
        }

        binding.fabAddItem.setOnClickListener { showAddItemDialog(null) }
    }

    // ---- SoundPool ----

    private fun initSoundPool() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attrs).build()
    }

    /** Tick音を1回再生してプレビュー（再生中なら停止） */
    private fun previewTickSound(resourceName: String?, volume: Float, isPlaying: Boolean): Boolean {
        val pool = soundPool ?: return false
        val ctx  = context ?: return false
        if (isPlaying) {
            pool.stop(currentTickStreamId)
            return false
        }
        if (resourceName == null) return false
        val soundId = soundIdCache.getOrPut(resourceName) {
            val resId = ctx.resources.getIdentifier(resourceName, "raw", ctx.packageName)
            if (resId == 0) return false
            pool.load(ctx, resId, 1)
        }
        // SoundPool はロード完了前に play すると無音なので少し待つ
        pool.setOnLoadCompleteListener { _, _, _ -> }
        currentTickStreamId = pool.play(soundId, volume, volume, 1, 0, 1.0f)
        return true
    }

    /** アラーム音プレビュー（再生中なら停止、停止中なら再生） */
    private fun previewAlarmSound(uriString: String, volume: Int, isPlaying: Boolean): Boolean {
        if (isPlaying) {
            previewRingtone?.stop()
            previewRingtone = null
            previewRingtoneUri = null
            return false
        }
        try {
            val ctx = requireContext()
            val uri = Uri.parse(uriString)
            previewRingtone = RingtoneManager.getRingtone(ctx, uri)?.also { r ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) r.isLooping = false
                val audioManager = ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    (max * volume / 100).coerceIn(0, max),
                    0
                )
                r.play()
            }
            previewRingtoneUri = uriString
        } catch (_: Exception) {}
        return true
    }

    private fun stopAllPreviews() {
        soundPool?.stop(currentTickStreamId)
        currentTickStreamId = 0
        previewRingtone?.stop()
        previewRingtone = null
        previewRingtoneUri = null
    }

    // ---- ダイアログ ----

    private fun showAddItemDialog(insertAfterIndex: Int?) {
        stopAllPreviews()
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_add_item_title)
            .setItems(arrayOf(
                getString(R.string.item_type_loop),
                getString(R.string.item_type_timer),
                getString(R.string.item_type_alarm)
            )) { _, which ->
                when (which) {
                    0 -> showLoopDialog(insertAfterIndex = insertAfterIndex)
                    1 -> showTimerDialog(insertAfterIndex = insertAfterIndex)
                    2 -> showAlarmDialog(insertAfterIndex = insertAfterIndex)
                }
            }
            .show()
    }

    // ---- ループダイアログ（ドラムロール 1〜99） ----

    private fun showLoopDialog(
        existingId: Int = 0,
        existingCount: Int = 3,
        insertAfterIndex: Int? = null
    ) {
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density
        val isNew = existingId == 0

        val picker = NumberPicker(ctx).apply {
            minValue = 1
            maxValue = 99
            value = existingCount.coerceIn(1, 99)
            wrapSelectorWheel = false
        }

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(0, (16 * dp).toInt(), 0, (8 * dp).toInt())
            addView(picker)
            addView(TextView(ctx).apply {
                text = ctx.getString(R.string.unit_times)
                gravity = android.view.Gravity.CENTER
                textSize = 13f
            })
        }

        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle(R.string.dialog_loop_count_title)
            .setView(container)
            .setPositiveButton(if (isNew) R.string.action_add else R.string.action_ok) { _, _ ->
                if (isNew) viewModel.addLoop(picker.value, insertAfterIndex)
                else       viewModel.updateLoopStart(existingId, picker.value)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    // ---- タイマーダイアログ ----

    private fun showTimerDialog(
        existingId: Int = 0,
        existingSeconds: Int = 60,
        existingTickSound: String? = null,
        existingTickVolume: Int = 80,
        insertAfterIndex: Int? = null
    ) {
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density
        val isNew = existingId == 0

        var selectedTickSound  = existingTickSound
        var selectedTickVolume = existingTickVolume
        var tickPreviewPlaying = false

        val initH = existingSeconds / 3600
        val initM = (existingSeconds % 3600) / 60
        val initS = existingSeconds % 60

        // ── 時間ピッカー行 ──
        val pickerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(0, (16 * dp).toInt(), 0, 0)
        }

        fun makePicker(max: Int, init: Int, labelRes: Int): android.widget.LinearLayout {
            val col = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            NumberPicker(ctx).apply {
                minValue = 0; maxValue = max; value = init; wrapSelectorWheel = true
                col.addView(this)
            }
            android.widget.TextView(ctx).apply {
                text = ctx.getString(labelRes)
                gravity = android.view.Gravity.CENTER
                textSize = 12f
                col.addView(this)
            }
            return col
        }

        val colH = makePicker(23, initH, R.string.unit_hours)
        val colM = makePicker(59, initM, R.string.unit_minutes)
        val colS = makePicker(59, initS, R.string.unit_seconds)
        pickerRow.addView(colH); pickerRow.addView(colM); pickerRow.addView(colS)

        val pickerH = colH.getChildAt(0) as NumberPicker
        val pickerM = colM.getChildAt(0) as NumberPicker
        val pickerS = colS.getChildAt(0) as NumberPicker

        // ── Tick音選択行（プレビューボタンを選択ボタンの左隣に配置） ──
        fun tickDisplayName(resName: String?) =
            tickSoundOptions.firstOrNull { it.second == resName }?.first
                ?: getString(R.string.tick_sound_none)

        val tickSoundRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), 0)
        }
        android.widget.TextView(ctx).apply {
            text = ctx.getString(R.string.label_tick_sound)
            textSize = 14f
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            tickSoundRow.addView(this)
        }
        // プレビューボタン（背景なし・テキストのみ）
        val tickPreviewButton = android.widget.TextView(ctx).apply {
            text = ctx.getString(R.string.action_preview_play)
            textSize = 13f
            setTextColor(android.graphics.Color.parseColor("#FF2196F3"))
            setPadding((8 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt())
            isClickable = true
            isFocusable = true
            val tv = android.util.TypedValue()
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
            background = androidx.core.content.ContextCompat.getDrawable(ctx, tv.resourceId)
        }
        val tickSoundButton = android.widget.Button(ctx).apply {
            text = tickDisplayName(selectedTickSound)
        }
        tickSoundRow.addView(tickPreviewButton)
        tickSoundRow.addView(tickSoundButton)

        // ── Tick音量スライダー行 ──
        val tickVolumeRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), 0)
        }
        android.widget.TextView(ctx).apply {
            text = ctx.getString(R.string.label_tick_volume)
            textSize = 14f
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(0, 0, (8 * dp).toInt(), 0)
            tickVolumeRow.addView(this)
        }
        val tickVolumeLabel = android.widget.TextView(ctx).apply {
            text = "$selectedTickVolume"
            textSize = 13f
            minWidth = (32 * dp).toInt()
            gravity = android.view.Gravity.END
        }
        val tickVolumeSeek = SeekBar(ctx).apply {
            max = 100
            progress = selectedTickVolume
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    selectedTickVolume = progress
                    tickVolumeLabel.text = "$progress"
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        tickVolumeRow.addView(tickVolumeSeek)
        tickVolumeRow.addView(tickVolumeLabel)

        // Tick音選択 → ボタンラベル更新、プレビュー停止
        tickSoundButton.setOnClickListener {
            if (tickPreviewPlaying) {
                soundPool?.stop(currentTickStreamId)
                tickPreviewPlaying = false
                tickPreviewButton.text = ctx.getString(R.string.action_preview_play)
            }
            val names  = tickSoundOptions.map { it.first }.toTypedArray()
            val curIdx = tickSoundOptions.indexOfFirst { it.second == selectedTickSound }.coerceAtLeast(0)
            androidx.appcompat.app.AlertDialog.Builder(ctx)
                .setTitle(R.string.dialog_tick_sound_title)
                .setSingleChoiceItems(names, curIdx) { dialog, which ->
                    selectedTickSound = tickSoundOptions[which].second
                    tickSoundButton.text = tickSoundOptions[which].first
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }

        // Tick音プレビュー（再生終了後に1.5秒で自動リセット）
        tickPreviewButton.setOnClickListener {
            if (tickPreviewPlaying) {
                soundPool?.stop(currentTickStreamId)
                tickPreviewPlaying = false
                tickPreviewButton.text = ctx.getString(R.string.action_preview_play)
            } else {
                val played = previewTickSound(selectedTickSound, selectedTickVolume / 100f, false)
                if (played) {
                    tickPreviewPlaying = true
                    tickPreviewButton.text = ctx.getString(R.string.action_preview_stop)
                    tickPreviewButton.postDelayed({
                        if (tickPreviewPlaying) {
                            tickPreviewPlaying = false
                            tickPreviewButton.text = ctx.getString(R.string.action_preview_play)
                        }
                    }, 1500L)
                }
            }
        }

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(pickerRow)
            addView(tickSoundRow)
            addView(tickVolumeRow)
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle(R.string.dialog_timer_title)
            .setView(layout)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                stopAllPreviews()
                val total = pickerH.value * 3600 + pickerM.value * 60 + pickerS.value
                if (total <= 0) return@setPositiveButton
                if (isNew) viewModel.addTimer(total, selectedTickSound, selectedTickVolume, insertAfterIndex)
                else       viewModel.updateTimer(existingId, total, selectedTickSound, selectedTickVolume)
            }
            .setNegativeButton(R.string.action_cancel) { _, _ -> stopAllPreviews() }
            .setOnDismissListener { stopAllPreviews() }
            .create()
        dialog.show()
    }

    // ---- アラームダイアログ ----

    private fun showAlarmDialog(
        existingId: Int = 0,
        existingVolume: Int = 80,
        existingDuration: Int = 3,
        existingVibrate: Boolean = true,
        existingSoundUri: String = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString(),
        insertAfterIndex: Int? = null
    ) {
        val ctx  = requireContext()
        val dp   = ctx.resources.displayMetrics.density
        val isNew = existingId == 0
        val initM = existingDuration / 60
        val initS = existingDuration % 60

        var currentSoundUri   = existingSoundUri
        var selectedVolume    = existingVolume
        var alarmPreviewPlaying = false

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), 0)
        }

        fun label(text: String) = android.widget.TextView(ctx).apply {
            this.text = text
            setPadding(0, (12 * dp).toInt(), 0, (4 * dp).toInt())
            textSize = 13f
        }

        // ── 音量スライダー ──
        layout.addView(label(ctx.getString(R.string.label_alarm_volume)))

        val volumeRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val volumeLabel = android.widget.TextView(ctx).apply {
            text = "$selectedVolume"
            textSize = 13f
            minWidth = (36 * dp).toInt()
            gravity = android.view.Gravity.END
            setPadding(0, 0, (8 * dp).toInt(), 0)
        }
        val volumeSeek = SeekBar(ctx).apply {
            max = 100
            progress = selectedVolume
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    selectedVolume = progress
                    volumeLabel.text = "$progress"
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        volumeRow.addView(volumeSeek)
        volumeRow.addView(volumeLabel)
        layout.addView(volumeRow)

        // ── 鳴る時間ピッカー ──
        layout.addView(label(ctx.getString(R.string.label_alarm_duration)))

        val pickerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }
        fun makePicker(max: Int, init: Int, labelRes: Int): android.widget.LinearLayout {
            val col = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            NumberPicker(ctx).apply {
                minValue = 0; maxValue = max; value = init; wrapSelectorWheel = true
                col.addView(this)
            }
            android.widget.TextView(ctx).apply {
                text = ctx.getString(labelRes)
                gravity = android.view.Gravity.CENTER
                textSize = 12f
                col.addView(this)
            }
            return col
        }
        val colM = makePicker(59, initM, R.string.unit_minutes)
        val colS = makePicker(59, initS, R.string.unit_seconds)
        pickerRow.addView(colM); pickerRow.addView(colS)
        val pickerM = colM.getChildAt(0) as NumberPicker
        val pickerS = colS.getChildAt(0) as NumberPicker
        layout.addView(pickerRow)

        // ── バイブレーション ──
        val checkVibrate = android.widget.CheckBox(ctx).apply {
            text = ctx.getString(R.string.label_vibration)
            isChecked = existingVibrate
            setPadding(0, (8 * dp).toInt(), 0, 0)
        }
        layout.addView(checkVibrate)

        // ── アラーム音選択 + プレビュー ──
        layout.addView(label(ctx.getString(R.string.label_alarm_sound)))

        val soundRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, (4 * dp).toInt(), 0, 0)
        }
        val buttonSound = android.widget.Button(ctx).apply {
            text = ctx.getString(R.string.summary_alarm_sound, getRingtoneName(existingSoundUri))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val previewButton = android.widget.Button(ctx).apply {
            text = ctx.getString(R.string.action_preview_play)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT).also {
                (it as android.widget.LinearLayout.LayoutParams).marginStart = (8 * dp).toInt()
            }
        }
        soundRow.addView(buttonSound)
        soundRow.addView(previewButton)
        layout.addView(soundRow)

        // 音選択
        buttonSound.setOnClickListener {
            if (alarmPreviewPlaying) {
                previewRingtone?.stop()
                previewRingtone = null
                alarmPreviewPlaying = false
                previewButton.text = ctx.getString(R.string.action_preview_play)
            }
            onSoundPicked = { uri ->
                currentSoundUri = uri.toString()
                buttonSound.text = ctx.getString(R.string.summary_alarm_sound, getRingtoneName(uri.toString()))
            }
            soundPickerLauncher.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, ctx.getString(R.string.dialog_alarm_sound_title))
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(currentSoundUri))
            })
        }

        // プレビュー
        previewButton.setOnClickListener {
            alarmPreviewPlaying = previewAlarmSound(currentSoundUri, selectedVolume, alarmPreviewPlaying)
            previewButton.text = ctx.getString(
                if (alarmPreviewPlaying) R.string.action_preview_stop
                else R.string.action_preview_play
            )
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle(R.string.dialog_alarm_title)
            .setView(layout)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                stopAllPreviews()
                val dur = pickerM.value * 60 + pickerS.value
                if (dur <= 0) return@setPositiveButton
                val vib = checkVibrate.isChecked
                if (isNew) viewModel.addAlarm(selectedVolume, dur, currentSoundUri, vib, insertAfterIndex)
                else       viewModel.updateAlarm(existingId, selectedVolume, dur, currentSoundUri, vib)
            }
            .setNegativeButton(R.string.action_cancel) { _, _ -> stopAllPreviews() }
            .setOnDismissListener { stopAllPreviews() }
            .create()
        dialog.show()
    }

    private fun showEditDialog(item: RoutineItem) {
        when (item) {
            is RoutineItem.LoopStart -> showLoopDialog(item.id, item.count)
            is RoutineItem.Timer     -> showTimerDialog(item.id, item.durationSeconds, item.tickSound, item.tickVolume)
            is RoutineItem.Alarm     -> showAlarmDialog(item.id, item.volume, item.durationSeconds, item.vibrate, item.soundUri)
            else -> {}
        }
    }

    private fun getRingtoneName(uriString: String): String = try {
        RingtoneManager.getRingtone(requireContext(), Uri.parse(uriString))
            ?.getTitle(requireContext()) ?: ""
    } catch (_: Exception) { "" }

    override fun onDestroyView() {
        stopAllPreviews()
        soundPool?.release()
        soundPool = null
        soundIdCache.clear()
        dragHelper.stopOverlay()
        super.onDestroyView()
        _binding = null
    }
}

// Button に OutlinedButton スタイル相当を適用するヘルパー（拡張関数）
private fun android.widget.Button.style(ctx: android.content.Context) {
    // デフォルトのまま（テーマに従う）
}