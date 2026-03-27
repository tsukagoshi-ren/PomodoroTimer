package com.androidapp.pomodorotimer.ui.create

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.androidapp.pomodorotimer.App
import com.androidapp.pomodorotimer.data.model.preset.Preset
import com.androidapp.pomodorotimer.databinding.FragmentPresetCreateBinding

class PresetCreateFragment : Fragment() {

    private var _binding: FragmentPresetCreateBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PresetCreateViewModel by viewModels {
        PresetCreateViewModel.Factory(
            (requireActivity().application as App).presetRepository
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPresetCreateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

            viewModel.savePreset(
                Preset(
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