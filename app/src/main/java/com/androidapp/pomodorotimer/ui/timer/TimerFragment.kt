package com.androidapp.pomodorotimer.ui.timer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.androidapp.pomodorotimer.App
import com.androidapp.pomodorotimer.databinding.FragmentTimerBinding
import kotlinx.coroutines.launch

class TimerFragment : Fragment() {

    private var _binding: FragmentTimerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TimerViewModel by viewModels {
        TimerViewModel.Factory(
            (requireActivity().application as App).presetRepository
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTimerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val presetId = arguments?.getInt("presetId") ?: return

        viewModel.bindService(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getPreset(presetId)?.let { preset ->
                viewModel.loadPreset(preset)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                val minutes = state.remainingSeconds / 60
                val seconds = state.remainingSeconds % 60
                binding.textTimer.text = "%02d:%02d".format(minutes, seconds)

                binding.textPresetName.text = state.presetName

                binding.textPhase.text = when (state.phase) {
                    TimerPhase.WORK -> "作業中"
                    TimerPhase.SHORT_BREAK -> "短い休憩"
                    TimerPhase.LONG_BREAK -> "長い休憩"
                }

                binding.textCycle.text =
                    "サイクル ${state.currentCycle} / ${state.totalCycles}"

                binding.buttonStart.isEnabled =
                    state.timerState != TimerState.RUNNING
                binding.buttonPause.isEnabled =
                    state.timerState == TimerState.RUNNING
                binding.buttonReset.isEnabled =
                    state.timerState != TimerState.IDLE
            }
        }

        binding.buttonStart.setOnClickListener { viewModel.start() }
        binding.buttonPause.setOnClickListener { viewModel.pause() }
        binding.buttonReset.setOnClickListener { viewModel.reset() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (viewModel.uiState.value.timerState == TimerState.IDLE) {
            viewModel.stopService(requireContext())
        } else {
            viewModel.unbindService(requireContext())
        }
    }
}