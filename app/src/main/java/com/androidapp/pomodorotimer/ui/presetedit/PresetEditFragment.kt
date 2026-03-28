package com.androidapp.pomodorotimer.ui.presetedit

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.androidapp.pomodorotimer.App
import com.androidapp.pomodorotimer.R
import com.androidapp.pomodorotimer.data.model.TriggerType
import com.androidapp.pomodorotimer.databinding.FragmentPresetEditBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PresetEditFragment : Fragment() {

    private var _binding: FragmentPresetEditBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PresetEditViewModel by viewModels {
        PresetEditViewModel.Factory(
            (requireActivity().application as App).presetRepository,
            requireActivity().applicationContext
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPresetEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val presetId = arguments?.getInt("presetId", -1) ?: -1
        if (presetId != -1) viewModel.loadPreset(presetId)

        // トリガー行タップでダイアログ
        binding.rowTrigger.setOnClickListener { showTriggerDialog() }

        // ルーティン行タップで遷移（新規の場合は先に保存してからIDを渡す）
        binding.rowRoutine.setOnClickListener {
            // クリック時点でEditTextの内容をViewModelに反映してから保存
            viewModel.setName(binding.editName.text.toString().trim())
            viewLifecycleOwner.lifecycleScope.launch {
                val savedId = viewModel.saveAndGetId()
                if (savedId == null) {
                    Toast.makeText(requireContext(), "プリセット名を入力してください", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                findNavController().navigate(
                    R.id.action_presetEdit_to_routineEdit,
                    Bundle().apply { putInt("presetId", savedId) }
                )
            }
        }

        // UI状態の反映
        var nameInitialized = false

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                // プリセット名は初回（既存プリセットの読み込み時）のみセット
                if (!nameInitialized && state.name.isNotEmpty()) {
                    binding.editName.setText(state.name)
                    nameInitialized = true
                }

                binding.textTriggerValue.text = when (state.triggerType) {
                    TriggerType.BUTTON -> "ボタンで開始"
                    TriggerType.DATETIME -> {
                        val dt = state.triggerDatetime
                        if (dt != null) {
                            SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN).format(dt)
                        } else "日時を選択"
                    }
                }
            }
        }

        binding.buttonSave.setOnClickListener {
            val name = binding.editName.text.toString().trim()
            viewModel.setName(name)
            viewLifecycleOwner.lifecycleScope.launch {
                val ok = viewModel.save()
                if (ok) findNavController().popBackStack()
                else Toast.makeText(requireContext(), "プリセット名を入力してください", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showTriggerDialog() {
        val options = arrayOf("ボタンで開始", "日時指定")
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("トリガーを選択")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewModel.setTrigger(TriggerType.BUTTON, null)
                    1 -> showDateTimePicker()
                }
            }
            .show()
    }

    private fun showDateTimePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, y, m, d ->
            cal.set(y, m, d)
            TimePickerDialog(requireContext(), { _, h, min ->
                cal.set(Calendar.HOUR_OF_DAY, h)
                cal.set(Calendar.MINUTE, min)
                viewModel.setTrigger(TriggerType.DATETIME, cal.timeInMillis)
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}