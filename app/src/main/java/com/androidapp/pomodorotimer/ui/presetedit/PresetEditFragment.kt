package com.androidapp.pomodorotimer.ui.presetedit

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.androidapp.pomodorotimer.App
import com.androidapp.pomodorotimer.R
import com.androidapp.pomodorotimer.data.model.TriggerType
import com.androidapp.pomodorotimer.databinding.FragmentPresetEditBinding
import com.androidapp.pomodorotimer.util.AlarmScheduler
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

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { performCancel() }
            }
        )

        binding.rowTrigger.setOnClickListener { showTriggerDialog() }

        binding.rowRoutine.setOnClickListener {
            viewModel.setName(binding.editName.text.toString().trim())
            viewLifecycleOwner.lifecycleScope.launch {
                val savedId = viewModel.saveAndGetId()
                if (savedId == null) {
                    Toast.makeText(requireContext(), R.string.error_preset_name_empty, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                findNavController().navigate(
                    R.id.action_presetEdit_to_routineEdit,
                    Bundle().apply { putInt("presetId", savedId) }
                )
            }
        }

        binding.buttonCancel.setOnClickListener { performCancel() }

        binding.buttonSave.setOnClickListener {
            viewModel.setName(binding.editName.text.toString().trim())
            viewLifecycleOwner.lifecycleScope.launch {
                when (viewModel.save()) {
                    PresetEditViewModel.SaveResult.OK ->
                        findNavController().popBackStack()
                    PresetEditViewModel.SaveResult.NAME_EMPTY ->
                        Toast.makeText(requireContext(), R.string.error_preset_name_empty, Toast.LENGTH_SHORT).show()
                    PresetEditViewModel.SaveResult.NO_ROUTINE ->
                        Toast.makeText(requireContext(), R.string.error_no_routine, Toast.LENGTH_SHORT).show()
                }
            }
        }

        var nameInitialized = false
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                if (!nameInitialized && state.name.isNotEmpty()) {
                    binding.editName.setText(state.name)
                    nameInitialized = true
                }
                binding.textTriggerValue.text = when (state.triggerType) {
                    TriggerType.BUTTON -> getString(R.string.trigger_button)
                    TriggerType.DATETIME -> {
                        val dt = state.triggerDatetime
                        if (dt != null) SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(dt)
                        else getString(R.string.trigger_datetime_select)
                    }
                    TriggerType.WEEKLY -> {
                        val days = AlarmScheduler.weekdaysToString(state.weekdays)
                        val h = state.triggerTimeOfDay / 60
                        val m = state.triggerTimeOfDay % 60
                        if (days.isEmpty()) getString(R.string.trigger_weekly)
                        else "$days %02d:%02d".format(h, m)
                    }
                }
            }
        }
    }

    private fun performCancel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.cancelAndCleanup()
            findNavController().popBackStack()
        }
    }

    private fun showTriggerDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_trigger_title)
            .setItems(arrayOf(
                getString(R.string.trigger_button),
                getString(R.string.trigger_datetime),
                getString(R.string.trigger_weekly)
            )) { _, which ->
                when (which) {
                    0 -> viewModel.setTriggerButton()
                    1 -> showDateTimePicker()
                    2 -> showWeeklyDialog()
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
                // 旧 setTrigger ではなく新メソッドを使う
                viewModel.setTriggerDatetime(cal.timeInMillis)
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showWeeklyDialog() {
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density
        val state = viewModel.uiState.value

        var selectedWeekdays = state.weekdays
        val initH = state.triggerTimeOfDay / 60
        val initM = state.triggerTimeOfDay % 60

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), 0)
        }

        // ── 曜日選択トグルボタン行 ──
        val dayNames = listOf("日", "月", "火", "水", "木", "金", "土")

        val chipRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        dayNames.forEachIndexed { index, name ->
            val bit = 1 shl index
            val toggle = android.widget.ToggleButton(ctx).apply {
                textOn = name
                textOff = name
                text = name
                isChecked = selectedWeekdays and bit != 0
                layoutParams = LinearLayout.LayoutParams(
                    0, (40 * dp).toInt(), 1f
                ).apply { setMargins((2 * dp).toInt(), 0, (2 * dp).toInt(), 0) }
                setOnCheckedChangeListener { _, isChecked ->
                    selectedWeekdays = if (isChecked) {
                        selectedWeekdays or bit
                    } else {
                        selectedWeekdays and bit.inv()
                    }
                }
            }
            chipRow.addView(toggle)
        }
        layout.addView(chipRow)

        // ── 時刻ラベル ──
        layout.addView(android.widget.TextView(ctx).apply {
            text = ctx.getString(R.string.label_trigger_time)
            textSize = 13f
            setPadding(0, (16 * dp).toInt(), 0, (8 * dp).toInt())
        })

        // ── 時刻ピッカー ──
        val pickerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        fun makePickerCol(max: Int, init: Int, label: String): Pair<LinearLayout, NumberPicker> {
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val picker = NumberPicker(ctx).apply {
                minValue = 0; maxValue = max; value = init; wrapSelectorWheel = true
            }
            col.addView(picker)
            col.addView(android.widget.TextView(ctx).apply {
                text = label
                gravity = android.view.Gravity.CENTER
                textSize = 12f
            })
            return Pair(col, picker)
        }

        val (colH, pickerH) = makePickerCol(23, initH, ctx.getString(R.string.unit_hours))
        val (colM, pickerM) = makePickerCol(59, initM, ctx.getString(R.string.unit_minutes))
        pickerRow.addView(colH)
        pickerRow.addView(colM)
        layout.addView(pickerRow)

        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle(R.string.dialog_weekly_title)
            .setView(layout)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                if (selectedWeekdays == 0) {
                    Toast.makeText(ctx, R.string.error_no_weekday_selected, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val timeOfDay = pickerH.value * 60 + pickerM.value
                viewModel.setTriggerWeekly(selectedWeekdays, timeOfDay)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}