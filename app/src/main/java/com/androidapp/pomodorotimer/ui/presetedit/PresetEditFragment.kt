package com.androidapp.pomodorotimer.ui.presetedit

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
                getString(R.string.trigger_datetime)
            )) { _, which ->
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