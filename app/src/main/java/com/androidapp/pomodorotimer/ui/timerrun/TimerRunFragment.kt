package com.androidapp.pomodorotimer.ui.timerrun

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.*
import android.os.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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

    // SoundPool（ティック音用）
    private var soundPool: SoundPool? = null
    private val soundIdCache = mutableMapOf<String, Int>()

    // Android 13以上の通知権限リクエスト
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // 許可・拒否どちらでもタイマーは動作する（通知が出ないだけ）
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTimerRunBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Android 13以上で通知権限がなければリクエスト
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        initSoundPool()

        val presetId = arguments?.getInt("presetId") ?: return
        viewModel.load(presetId)

        adapter = TimerRunItemAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        // UI状態の反映
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.textPresetName.text = state.presetName
                adapter.submitList(state.executionList, state.currentIndex)

                if (state.executionList.isNotEmpty()) {
                    binding.recyclerView.scrollToPosition(
                        state.currentIndex.coerceIn(0, state.executionList.size - 1)
                    )
                }

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

                when (state.runState) {
                    RunState.IDLE -> {
                        binding.buttonStart.setText(com.androidapp.pomodorotimer.R.string.action_start)
                        binding.buttonStart.isEnabled = true
                        binding.buttonPause.isEnabled = false
                        binding.buttonStop.isEnabled  = false
                        binding.textFinished.visibility = View.GONE
                    }
                    RunState.RUNNING -> {
                        binding.buttonStart.isEnabled = false
                        binding.buttonPause.isEnabled = true
                        binding.buttonStop.isEnabled  = true
                        binding.textFinished.visibility = View.GONE
                    }
                    RunState.PAUSED -> {
                        binding.buttonStart.setText(com.androidapp.pomodorotimer.R.string.action_resume)
                        binding.buttonStart.isEnabled = true
                        binding.buttonPause.isEnabled = false
                        binding.buttonStop.isEnabled  = true
                        binding.textFinished.visibility = View.GONE
                    }
                    RunState.FINISHED -> {
                        binding.buttonStart.isEnabled = false
                        binding.buttonPause.isEnabled = false
                        binding.buttonStop.isEnabled  = false
                        binding.textFinished.visibility = View.VISIBLE
                    }
                }
            }
        }

        // アラームイベント
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.alarmEvent.collect { event ->
                if (event != null) playAlarm(event) else stopAlarm()
            }
        }

        // ティック音イベント
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.tickEvent.collect { resourceName ->
                if (resourceName != null) playTick(resourceName)
            }
        }

        binding.buttonStart.setOnClickListener {
            when (viewModel.uiState.value.runState) {
                RunState.IDLE   -> viewModel.start()
                RunState.PAUSED -> viewModel.resume()
                else -> {}
            }
        }
        binding.buttonPause.setOnClickListener { viewModel.pause() }
        binding.buttonStop.setOnClickListener  { viewModel.stop(); stopAlarm() }
    }

    // ---- SoundPool ----

    private fun initSoundPool() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attrs)
            .build()
    }

    private fun playTick(resourceName: String) {
        val pool = soundPool ?: return
        val ctx  = context ?: return
        val soundId = soundIdCache.getOrPut(resourceName) {
            val resId = ctx.resources.getIdentifier(resourceName, "raw", ctx.packageName)
            if (resId == 0) return
            pool.load(ctx, resId, 1)
        }
        pool.play(soundId, 0.8f, 0.8f, 1, 0, 1.0f)
    }

    // ---- アラーム ----

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
        } catch (_: Exception) {}

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
        soundPool?.release()
        soundPool = null
        soundIdCache.clear()
        super.onDestroyView()
        _binding = null
    }
}