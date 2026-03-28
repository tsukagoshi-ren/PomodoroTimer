package com.androidapp.pomodorotimer.ui.routineedit

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.androidapp.pomodorotimer.App
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

    // 音選択インテントの結果を受け取るコールバック
    // アラーム編集ダイアログを開くときにここに処理をセットする
    private var onSoundPicked: ((Uri) -> Unit)? = null

    private val soundPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    result.data?.getParcelableExtra(
                        RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java
                    )
                } else {
                    @Suppress("DEPRECATION")
                    result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                } ?: return@registerForActivityResult
                onSoundPicked?.invoke(uri)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoutineEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val presetId = arguments?.getInt("presetId") ?: return

        adapter = RoutineItemAdapter(
            onDelete = { item -> viewModel.removeItem(item) },
            onEdit   = { item -> showEditDialog(item) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        viewModel.loadItems(presetId)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.items.collect { adapter.submitList(it) }
        }

        binding.buttonAddItem.setOnClickListener { showAddItemDialog() }

        binding.buttonSave.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.saveItems(presetId)
                parentFragmentManager.popBackStack()
            }
        }
    }

    private fun showAddItemDialog() {
        val options = arrayOf(
            "繰り返し始まり", "繰り返し終わり",
            "条件分岐始まり", "条件分岐終わり",
            "タイマー", "アラーム"
        )
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("アイテムを追加")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRepeatStartDialog()
                    1 -> viewModel.addRepeatEnd()
                    2 -> viewModel.addConditionStart()
                    3 -> viewModel.addConditionEnd()
                    4 -> showTimerDialog()
                    5 -> showAlarmDialog()
                }
            }
            .show()
    }

    private fun showRepeatStartDialog(existingId: Int = 0, existingCount: Int = 3) {
        val input = android.widget.EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(existingCount.toString())
            hint = "繰り返し回数"
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("繰り返し回数")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val count = input.text.toString().toIntOrNull() ?: return@setPositiveButton
                if (existingId == 0) viewModel.addRepeatStart(count)
                else viewModel.updateRepeatStart(existingId, count)
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showTimerDialog(existingId: Int = 0, existingSeconds: Int = 60) {
        val layout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(48, 16, 48, 0)
        }
        val editMin = android.widget.EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText((existingSeconds / 60).toString())
            hint = "分"
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val editSec = android.widget.EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText((existingSeconds % 60).toString())
            hint = "秒"
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
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
                if (existingId == 0) viewModel.addTimer(total)
                else viewModel.updateTimer(existingId, total)
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showAlarmDialog(
        existingId: Int = 0,
        existingVolume: Int = 80,
        existingDuration: Int = 3,
        existingVibrate: Boolean = true,
        existingSoundUri: String = RingtoneManager
            .getDefaultUri(RingtoneManager.TYPE_ALARM).toString()
    ) {
        // ダイアログ表示中に選択中のUriを保持する変数
        var currentSoundUri = existingSoundUri

        val layout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }

        fun label(text: String) = android.widget.TextView(requireContext()).apply {
            this.text = text; setPadding(0, 16, 0, 0)
        }

        val editVolume = android.widget.EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(existingVolume.toString()); hint = "音量（0-100）"
        }
        val editDuration = android.widget.EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(existingDuration.toString()); hint = "鳴る秒数"
        }
        val checkVibrate = android.widget.CheckBox(requireContext()).apply {
            text = "バイブレーション"; isChecked = existingVibrate
        }

        // 音選択ボタン
        val currentName = getRingtoneName(existingSoundUri)
        val buttonSound = android.widget.Button(requireContext()).apply {
            text = "🎵 $currentName"
            setOnClickListener {
                // コールバックをセットしてから音選択画面を起動
                onSoundPicked = { uri ->
                    currentSoundUri = uri.toString()
                    text = "🎵 ${getRingtoneName(uri.toString())}"
                }
                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "アラーム音を選択")
                    val current = Uri.parse(currentSoundUri)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
                }
                soundPickerLauncher.launch(intent)
            }
        }

        layout.addView(label("音量（0〜100）"))
        layout.addView(editVolume)
        layout.addView(label("鳴る秒数"))
        layout.addView(editDuration)
        layout.addView(label("アラーム音"))
        layout.addView(buttonSound)
        layout.addView(checkVibrate)

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("アラーム設定")
            .setView(layout)
            .setPositiveButton("OK") { _, _ ->
                val vol = editVolume.text.toString().toIntOrNull()
                    ?.coerceIn(0, 100) ?: return@setPositiveButton
                val dur = editDuration.text.toString().toIntOrNull()
                    ?: return@setPositiveButton
                val vib = checkVibrate.isChecked
                if (existingId == 0) viewModel.addAlarm(vol, dur, currentSoundUri, vib)
                else viewModel.updateAlarm(existingId, vol, dur, currentSoundUri, vib)
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showEditDialog(item: RoutineItem) {
        when (item) {
            is RoutineItem.RepeatStart ->
                showRepeatStartDialog(item.id, item.count)
            is RoutineItem.Timer ->
                showTimerDialog(item.id, item.durationSeconds)
            is RoutineItem.Alarm ->
                showAlarmDialog(
                    item.id, item.volume, item.durationSeconds,
                    item.vibrate, item.soundUri
                )
            else -> {}
        }
    }

    // URIから音の名前を取得するヘルパー
    private fun getRingtoneName(uriString: String): String {
        return try {
            val uri = Uri.parse(uriString)
            RingtoneManager.getRingtone(requireContext(), uri)
                ?.getTitle(requireContext()) ?: "デフォルト"
        } catch (e: Exception) {
            "デフォルト"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}