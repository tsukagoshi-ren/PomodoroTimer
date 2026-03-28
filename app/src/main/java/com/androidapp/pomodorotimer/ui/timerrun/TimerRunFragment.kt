package com.androidapp.pomodorotimer.ui.timerrun

import android.media.AudioManager
import android.media.RingtoneManager
import android.os.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.androidapp.pomodorotimer.App
import com.androidapp.pomodorotimer.databinding.FragmentTimerRunBinding
import kotlinx.coroutines.launch

class TimerRunFragment : Fragment() {

    private var _binding: FragmentTimerRunBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TimerRunViewModel by viewModels {
        TimerRunViewModel.Factory((requireActivity().application as App).presetRepository)
    }

    private lateinit var adapter: TimerRunItemAdapter
    private var ringtone: android.media.Ringtone? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTimerRunBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val presetId = arguments?.getInt("presetId") ?: return
        viewModel.load(presetId)

        adapter = TimerRunItemAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        // UI状態の反映
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.textPresetName.text = state.presetName

                // アイテムリストをアダプターに渡す（ハイライト位置付き）
                adapter.submitList(state.executionList, state.currentIndex)

                // 現在位置へスクロール
                if (state.executionList.isNotEmpty()) {
                    binding.recyclerView.scrollToPosition(
                        state.currentIndex.coerceIn(0, state.executionList.size - 1)
                    )
                }

                // タイマー表示
                if (state.showTimer) {
                    val m = state.remainingSeconds / 60
                    val s = state.remainingSeconds % 60
                    binding.textTimer.text = "%02d:%02d".format(m, s)
                    binding.textTimer.visibility = View.VISIBLE
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressBar.progress = state.progress
                } else {
                    binding.textTimer.visibility = View.GONE
                    binding.progressBar.visibility = View.GONE
                }

                // ボタン状態
                when (state.runState) {
                    RunState.IDLE -> {
                        binding.buttonStart.text = "開始"
                        binding.buttonStart.isEnabled = true
                        binding.buttonPause.isEnabled = false
                        binding.buttonStop.isEnabled = false
                        binding.textFinished.visibility = View.GONE
                    }
                    RunState.RUNNING -> {
                        binding.buttonStart.isEnabled = false
                        binding.buttonPause.isEnabled = true
                        binding.buttonStop.isEnabled = true
                        binding.textFinished.visibility = View.GONE
                    }
                    RunState.PAUSED -> {
                        binding.buttonStart.text = "再開"
                        binding.buttonStart.isEnabled = true
                        binding.buttonPause.isEnabled = false
                        binding.buttonStop.isEnabled = true
                        binding.textFinished.visibility = View.GONE
                    }
                    RunState.FINISHED -> {
                        binding.buttonStart.isEnabled = false
                        binding.buttonPause.isEnabled = false
                        binding.buttonStop.isEnabled = false
                        binding.textFinished.visibility = View.VISIBLE
                    }
                }
            }
        }

        // アラームイベントの監視
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.alarmEvent.collect { event ->
                if (event != null) playAlarm(event)
                else stopAlarm()
            }
        }

        binding.buttonStart.setOnClickListener {
            when (viewModel.uiState.value.runState) {
                RunState.IDLE -> viewModel.start()
                RunState.PAUSED -> viewModel.resume()
                else -> {}
            }
        }
        binding.buttonPause.setOnClickListener { viewModel.pause() }
        binding.buttonStop.setOnClickListener { viewModel.stop(); stopAlarm() }
    }

    private fun playAlarm(event: TimerRunViewModel.AlarmEvent) {
        stopAlarm()
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = RingtoneManager.getRingtone(requireContext(), uri)?.also { r ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) r.isLooping = true
                val audioManager = requireContext()
                    .getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    (max * event.volume / 100).coerceIn(0, max),
                    AudioManager.FLAG_SHOW_UI
                )
                r.play()
            }
        } catch (e: Exception) { /* 無視 */ }

        if (event.vibrate) {
            val vibrator = getVibrator()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 300), 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 500, 300), 0)
            }
        }
    }

    private fun stopAlarm() {
        ringtone?.stop()
        ringtone = null
        getVibrator().cancel()
    }

    private fun getVibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (requireContext().getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE)
                    as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            requireContext().getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
        }

    override fun onDestroyView() {
        stopAlarm()
        super.onDestroyView()
        _binding = null
    }
}