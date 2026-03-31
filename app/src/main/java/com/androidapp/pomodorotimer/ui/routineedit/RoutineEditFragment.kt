package com.androidapp.pomodorotimer.ui.routineedit

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
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

    private val tickSoundOptions: List<Pair<String, String?>> = listOf(
        "なし"                to null,
        "🕐 時計（カチッ）"   to "tick_clock",
        "🪵 木製（コン）"     to "tick_wood",
        "🔔 ベル（リン）"     to "tick_bell",
        "📳 ソフト（ポッ）"   to "tick_soft",
        "📳 ビープ（ピッ）"   to "tick_beep",
        "🎮 デジタル（ブッ）" to "tick_digital",
    )

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

        // ---- ItemTouchHelper ----
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            private var dragFromDisplay = -1
            private var dragToDisplay   = -1

            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val pos = viewHolder.adapterPosition
                if (pos < 0 || pos >= adapter.itemCount) return makeMovementFlags(0, 0)
                val entry = adapter.getEntry(pos)
                val draggable = when {
                    entry is RoutineListEntry.AddButton -> false
                    entry is RoutineListEntry.Item &&
                            entry.routineItem is RoutineItem.LoopEnd -> false
                    else -> true
                }
                return if (draggable)
                    makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
                else
                    makeMovementFlags(0, 0)
            }

            override fun onSelectedChanged(
                viewHolder: RecyclerView.ViewHolder?,
                actionState: Int
            ) {
                super.onSelectedChanged(viewHolder, actionState)

                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    val pos = viewHolder.adapterPosition
                    if (pos < 0) return
                    val entry = adapter.getEntry(pos)

                    if (entry is RoutineListEntry.Item &&
                        entry.routineItem is RoutineItem.LoopStart
                    ) {
                        // ブロック内の全 ViewHolder を収集
                        val (blockVhs, blockEntries) = collectBlockViewHoldersAndEntries(pos)
                        if (blockVhs.isNotEmpty()) {
                            val touchY = viewHolder.itemView.top.toFloat() +
                                    viewHolder.itemView.height / 2f
                            dragHelper.startOverlay(viewHolder, blockVhs, blockEntries, touchY)
                        }
                    }
                } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                    dragHelper.stopOverlay()
                    adapter.clearGreyOut()
                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (dragHelper.isActive) {
                    // オーバーレイをドラッグ中ViewHolderのtop位置に追従させる
                    val draggedTop = viewHolder.itemView.top.toFloat() + dY
                    dragHelper.updateOverlayPosition(draggedTop)
                    // 本体ViewHolderは透明なので通常描画はスキップ
                    return
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos   = target.adapterPosition
                if (fromPos < 0 || toPos < 0 || fromPos == toPos) return false

                val dragEntry   = adapter.getEntry(fromPos)
                val targetEntry = adapter.getEntry(toPos)

                if (targetEntry is RoutineListEntry.AddButton) return false

                if (dragEntry is RoutineListEntry.Item &&
                    dragEntry.routineItem is RoutineItem.LoopStart
                ) {
                    val blockStart = if (dragFromDisplay != -1) dragFromDisplay else fromPos
                    val blockEnd   = findBlockEndDisplay(blockStart)
                    if (blockEnd != -1 && toPos in blockStart..blockEnd) return false
                }

                if (dragFromDisplay == -1) dragFromDisplay = fromPos
                dragToDisplay = toPos
                adapter.onItemMoved(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)

                val from = dragFromDisplay
                val to   = dragToDisplay
                dragFromDisplay = -1
                dragToDisplay   = -1
                dragHelper.stopOverlay()
                adapter.clearGreyOut()

                if (from != -1 && to != -1 && from != to) {
                    val stableDisplayList = viewModel.displayList.value
                    val fromItem = displayToItemIndex(stableDisplayList, from)
                    val toItem   = displayToItemIndex(stableDisplayList, to)
                    if (fromItem != -1 && toItem != -1) {
                        viewModel.moveItem(fromItem, toItem)
                    }
                }
            }

            override fun isLongPressDragEnabled(): Boolean = false

            // ---- ヘルパー ----

            /**
             * displayList 上で loopStartDisplayPos の LoopStart に対応する
             * LoopEnd の display インデックスを返す。見つからなければ -1。
             */
            private fun findBlockEndDisplay(loopStartDisplayPos: Int): Int {
                var depth = 0
                for (i in loopStartDisplayPos until adapter.itemCount) {
                    val e = adapter.getEntry(i)
                    if (e is RoutineListEntry.Item) {
                        when (e.routineItem) {
                            is RoutineItem.LoopStart -> depth++
                            is RoutineItem.LoopEnd -> {
                                depth--
                                if (depth == 0) return i
                            }
                            else -> {}
                        }
                    }
                }
                return -1
            }

            /**
             * displayList 上の [loopStartDisplayPos] から始まるブロック
             * (LoopStart〜LoopEnd) の全 ViewHolder と対応 Entry を収集する。
             */
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

            private fun displayToItemIndex(
                displayList: List<RoutineListEntry>,
                displayPos: Int
            ): Int {
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

        binding.fabAddItem.setOnClickListener {
            showAddItemDialog(insertAfterIndex = null)
        }
    }

    // ---- ダイアログ ----

    private fun showAddItemDialog(insertAfterIndex: Int?) {
        val options = arrayOf("ループ", "タイマー", "アラーム")
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("アイテムを追加")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showLoopDialog(insertAfterIndex = insertAfterIndex)
                    1 -> showTimerDialog(insertAfterIndex = insertAfterIndex)
                    2 -> showAlarmDialog(insertAfterIndex = insertAfterIndex)
                }
            }
            .show()
    }

    private fun showLoopDialog(
        existingId: Int = 0,
        existingCount: Int = 3,
        insertAfterIndex: Int? = null
    ) {
        val input = android.widget.EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(existingCount.toString())
            hint = "繰り返し回数"
        }
        val isNew = existingId == 0
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("ループ回数")
            .setView(input)
            .setPositiveButton(if (isNew) "追加" else "OK") { _, _ ->
                val count = input.text.toString().toIntOrNull() ?: return@setPositiveButton
                if (isNew) viewModel.addLoop(count, insertAfterIndex)
                else       viewModel.updateLoopStart(existingId, count)
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showTimerDialog(
        existingId: Int = 0,
        existingSeconds: Int = 60,
        existingTickSound: String? = null,
        insertAfterIndex: Int? = null
    ) {
        var selectedTickSound: String? = existingTickSound
        val initH = existingSeconds / 3600
        val initM = (existingSeconds % 3600) / 60
        val initS = existingSeconds % 60
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density

        val pickerRow = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(0, (16 * dp).toInt(), 0, 0)
        }

        fun makePicker(max: Int, init: Int, labelText: String): android.widget.LinearLayout {
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
                text = labelText; gravity = android.view.Gravity.CENTER; textSize = 12f
                col.addView(this)
            }
            return col
        }

        val colH = makePicker(23, initH, "時間")
        val colM = makePicker(59, initM, "分")
        val colS = makePicker(59, initS, "秒")
        pickerRow.addView(colH); pickerRow.addView(colM); pickerRow.addView(colS)

        fun tickDisplayName(resName: String?) =
            tickSoundOptions.firstOrNull { it.second == resName }?.first ?: "なし"

        val tickRow = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
        }
        android.widget.TextView(ctx).apply {
            text = "ティック音"; textSize = 14f
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            tickRow.addView(this)
        }
        val tickButton = android.widget.Button(ctx).apply {
            text = tickDisplayName(selectedTickSound)
            tickRow.addView(this)
        }
        tickButton.setOnClickListener {
            val names = tickSoundOptions.map { it.first }.toTypedArray()
            val curIdx = tickSoundOptions.indexOfFirst { it.second == selectedTickSound }
                .coerceAtLeast(0)
            androidx.appcompat.app.AlertDialog.Builder(ctx)
                .setTitle("ティック音を選択")
                .setSingleChoiceItems(names, curIdx) { dialog, which ->
                    selectedTickSound = tickSoundOptions[which].second
                    tickButton.text = tickSoundOptions[which].first
                    dialog.dismiss()
                }
                .setNegativeButton("キャンセル", null)
                .show()
        }

        val layout = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(pickerRow); addView(tickRow)
        }
        val pickerH = colH.getChildAt(0) as NumberPicker
        val pickerM = colM.getChildAt(0) as NumberPicker
        val pickerS = colS.getChildAt(0) as NumberPicker

        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("タイマー時間")
            .setView(layout)
            .setPositiveButton("OK") { _, _ ->
                val total = pickerH.value * 3600 + pickerM.value * 60 + pickerS.value
                if (total <= 0) return@setPositiveButton
                if (existingId == 0) viewModel.addTimer(total, selectedTickSound, insertAfterIndex)
                else                 viewModel.updateTimer(existingId, total, selectedTickSound)
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showAlarmDialog(
        existingId: Int = 0,
        existingVolume: Int = 80,
        existingDuration: Int = 3,
        existingVibrate: Boolean = true,
        existingSoundUri: String = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString(),
        insertAfterIndex: Int? = null
    ) {
        var currentSoundUri = existingSoundUri
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val initM = existingDuration / 60
        val initS = existingDuration % 60

        val layout = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), 0)
        }

        fun label(text: String) = android.widget.TextView(ctx).apply {
            this.text = text
            setPadding(0, (12 * dp).toInt(), 0, (4 * dp).toInt())
            textSize = 13f
        }

        val editVolume = android.widget.EditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(existingVolume.toString())
            hint = "音量（0-100）"
        }

        val pickerRow = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        fun makePicker(max: Int, init: Int, lbl: String): android.widget.LinearLayout {
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
                text = lbl; gravity = android.view.Gravity.CENTER; textSize = 12f
                col.addView(this)
            }
            return col
        }

        val colM = makePicker(59, initM, "分")
        val colS = makePicker(59, initS, "秒")
        pickerRow.addView(colM); pickerRow.addView(colS)
        val pickerM = colM.getChildAt(0) as NumberPicker
        val pickerS = colS.getChildAt(0) as NumberPicker

        val checkVibrate = android.widget.CheckBox(ctx).apply {
            text = "バイブレーション"; isChecked = existingVibrate
            setPadding(0, (8 * dp).toInt(), 0, 0)
        }
        val buttonSound = android.widget.Button(ctx).apply {
            text = "🎵 ${getRingtoneName(existingSoundUri)}"
        }
        buttonSound.setOnClickListener {
            onSoundPicked = { uri ->
                currentSoundUri = uri.toString()
                buttonSound.text = "🎵 ${getRingtoneName(uri.toString())}"
            }
            soundPickerLauncher.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "アラーム音を選択")
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(currentSoundUri))
            })
        }

        layout.addView(label("音量（0〜100）")); layout.addView(editVolume)
        layout.addView(label("鳴る時間"));      layout.addView(pickerRow)
        layout.addView(label("アラーム音"));    layout.addView(buttonSound)
        layout.addView(checkVibrate)

        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("アラーム設定")
            .setView(layout)
            .setPositiveButton("OK") { _, _ ->
                val vol = editVolume.text.toString().toIntOrNull()?.coerceIn(0, 100)
                    ?: return@setPositiveButton
                val dur = pickerM.value * 60 + pickerS.value
                if (dur <= 0) return@setPositiveButton
                val vib = checkVibrate.isChecked
                if (existingId == 0) viewModel.addAlarm(vol, dur, currentSoundUri, vib, insertAfterIndex)
                else                 viewModel.updateAlarm(existingId, vol, dur, currentSoundUri, vib)
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showEditDialog(item: RoutineItem) {
        when (item) {
            is RoutineItem.LoopStart -> showLoopDialog(item.id, item.count)
            is RoutineItem.Timer     -> showTimerDialog(item.id, item.durationSeconds, item.tickSound)
            is RoutineItem.Alarm     -> showAlarmDialog(
                item.id, item.volume, item.durationSeconds, item.vibrate, item.soundUri)
            else -> {}
        }
    }

    private fun getRingtoneName(uriString: String): String = try {
        RingtoneManager.getRingtone(requireContext(), Uri.parse(uriString))
            ?.getTitle(requireContext()) ?: "デフォルト"
    } catch (_: Exception) { "デフォルト" }

    override fun onDestroyView() {
        dragHelper.stopOverlay()
        super.onDestroyView()
        _binding = null
    }
}