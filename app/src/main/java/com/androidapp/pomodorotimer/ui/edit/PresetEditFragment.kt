package com.androidapp.pomodorotimer.ui.edit

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
import com.androidapp.pomodorotimer.data.model.preset.Preset
import com.androidapp.pomodorotimer.databinding.FragmentPresetEditBinding
import kotlinx.coroutines.launch

class PresetEditFragment : Fragment() {

    private var _binding: FragmentPresetEditBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PresetEditViewModel by viewModels {
        PresetEditViewModel.Factory(
            (requireActivity().application as App).presetRepository
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPresetEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val presetId = arguments?.getInt("presetId") ?: return
        viewModel.loadPreset(presetId)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.preset.collect { preset ->
                preset ?: return@collect
                binding.editName.setText(preset.name)
                binding.editWorkMinutes.setText(preset.workMinutes.toString())
                binding.editShortBreakMinutes.setText(preset.shortBreakMinutes.toString())
                binding.editLongBreakMinutes.setText(preset.longBreakMinutes.toString())
                binding.editCyclesBeforeLong.setText(preset.cyclesBeforeLong.toString())
            }
        }

        binding.buttonSave.setOnClickListener {
            val name = binding.editName.text.toString().trim()
            val work = binding.editWorkMinutes.text.toString().toIntOrNull()
            val shortBreak = binding.editShortBreakMinutes.text.toString().toIntOrNull()
            val longBreak = binding.editLongBreakMinutes.text.toString().toIntOrNull()
            val cycles = binding.editCyclesBeforeLong.text.toString().toIntOrNull()

            if (name.isEmpty() || work == null || shortBreak == null
                || longBreak == null || cycles == null) {
                Toast.makeText(requireContext(),
                    "すべての項目を入力してください", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.updatePreset(
                Preset(
                    id = presetId,
                    name = name,
                    workMinutes = work,
                    shortBreakMinutes = shortBreak,
                    longBreakMinutes = longBreak,
                    cyclesBeforeLong = cycles
                )
            )
            findNavController().popBackStack()
        }

        binding.buttonCancel.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}