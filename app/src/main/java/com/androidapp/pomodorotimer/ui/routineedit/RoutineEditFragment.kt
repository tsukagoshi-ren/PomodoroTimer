package com.androidapp.pomodorotimer.ui.routineedit

import android.app.Activity
import android.content.Intent
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRoutineEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        presetId = arguments?.getInt("presetId") ?: return

        // ---- ActionBar に保存ボタンを追加 ----
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

        // ---- Adapter ----
        adapter = RoutineItemAdapter(
            onDelete = { item -> viewModel.removeItem(item) },
            onEdit   = { item -> showEditDialog(item) },
            onAddButtonClick = { insertAfterIndex -> showAddItemDialog(insertAfterIndex) },
            onMove   = { from, to -> viewModel.moveItem(from, to) }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        // ---- ItemTouchHelper ----
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            private var dragFrom = -1
            private var dragTo   = -1

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                // AddButtonへのドロップは禁止
                if (target is RoutineItemAdapter.AddButtonViewHolder) return false
                val from = viewHolder.adapterPosition
                val to   = target.adapterPosition
                if (dragFrom == -1) dragFrom = from
                dragTo = to
                adapter.onItemMoved(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                if (dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
                    adapter.onItemDropped(dragFrom, dragTo)
                }
                dragFrom = -1
                dragTo   = -1
            }

            // AddButtonはドラッグ不可
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                if (viewHolder is RoutineItemAdapter.AddButtonViewHolder) return 0
                return super.getMovementFlags(recyclerView, viewHolder)
            }

            override fun isLongPressDragEnabled(): Boolean = false
        })
        touchHelper.attachToRecyclerView(binding.recyclerView)
        adapter.itemTouchHelper = touchHelper

        viewModel.loadItems(presetId)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.displayList.collect { adapter.submitList(it) }
        }
    }

    // ---- ダイアログ ----

    /**
     * アイテム追加ダイアログ。
     * [insertAfterIndex] が null のとき末尾追加、指定のとき該当インデックスの直後に挿入。
     */
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

    private fun showLoopDialog(existingId: Int = 0, existingCount: Int = 3, insertAfterIndex: Int? = null) {
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

    private fun showTimerDialog(existingId: Int = 0, existingSeconds: Int = 60, insertAfterIndex: Int? = null) {
        val layout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(48, 16, 48, 0)
        }
        val editMin = android.widget.EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText((existingSeconds / 60).toString()); hint = "分"
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val editSec = android.widget.EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText((existingSeconds % 60).toString()); hint = "秒"
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        layout.addView(editMin)
        layout.addView(android.widget.TextView(requireContext()).apply { text = " : " })
        layout.addView(editSec)

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("タイマー（分：秒）")
            .setView(layout)
            .setPositiveButton("OK") { _, _ ->
                val total = (editMin.text.toString().toIntOrNull() ?: 0) * 60 +
                        (editSec.text.toString().toIntOrNull() ?: 0)
                if (total <= 0) return@setPositiveButton
                if (existingId == 0) viewModel.addTimer(total, insertAfterIndex)
                else                 viewModel.updateTimer(existingId, total)
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
        val layout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }
        fun label(text: String) = android.widget.TextView(requireContext()).apply { this.text = text; setPadding(0, 16, 0, 0) }
        val editVolume = android.widget.EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(existingVolume.toString()); hint = "音量（0-100）"
        }
        val editDuration = android.widget.EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(existingDuration.toString()); hint = "鳴る秒数"
        }
        val checkVibrate = android.widget.CheckBox(requireContext()).apply { text = "バイブレーション"; isChecked = existingVibrate }
        val buttonSound = android.widget.Button(requireContext()).apply {
            text = "🎵 ${getRingtoneName(existingSoundUri)}"
            setOnClickListener {
                onSoundPicked = { uri ->
                    currentSoundUri = uri.toString()
                    text = "🎵 ${getRingtoneName(uri.toString())}"
                }
                soundPickerLauncher.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "アラーム音を選択")
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(currentSoundUri))
                })
            }
        }
        layout.addView(label("音量（0〜100）")); layout.addView(editVolume)
        layout.addView(label("鳴る秒数"));     layout.addView(editDuration)
        layout.addView(label("アラーム音"));   layout.addView(buttonSound)
        layout.addView(checkVibrate)

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("アラーム設定")
            .setView(layout)
            .setPositiveButton("OK") { _, _ ->
                val vol = editVolume.text.toString().toIntOrNull()?.coerceIn(0, 100) ?: return@setPositiveButton
                val dur = editDuration.text.toString().toIntOrNull() ?: return@setPositiveButton
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
            is RoutineItem.Timer     -> showTimerDialog(item.id, item.durationSeconds)
            is RoutineItem.Alarm     -> showAlarmDialog(item.id, item.volume, item.durationSeconds, item.vibrate, item.soundUri)
            else -> {}
        }
    }

    private fun getRingtoneName(uriString: String): String {
        return try {
            RingtoneManager.getRingtone(requireContext(), Uri.parse(uriString))
                ?.getTitle(requireContext()) ?: "デフォルト"
        } catch (e: Exception) { "デフォルト" }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}