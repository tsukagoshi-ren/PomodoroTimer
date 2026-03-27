package com.androidapp.pomodorotimer.ui.timer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.RingtoneManager
import android.os.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.androidapp.pomodorotimer.data.model.preset.Preset
import com.androidapp.pomodorotimer.data.repository.preset.PresetRepository
import com.androidapp.pomodorotimer.service.timer.TimerService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class TimerPhase { WORK, SHORT_BREAK, LONG_BREAK }
enum class TimerState { IDLE, RUNNING, PAUSED }

data class TimerUiState(
    val phase: TimerPhase = TimerPhase.WORK,
    val timerState: TimerState = TimerState.IDLE,
    val remainingSeconds: Int = 0,
    val currentCycle: Int = 1,
    val totalCycles: Int = 4,
    val presetName: String = ""
)

class TimerViewModel(
    private val repository: PresetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState

    private var timerJob: Job? = null
    private var timerService: TimerService? = null
    private var serviceBound = false

    private var workSeconds = 0
    private var shortBreakSeconds = 0
    private var longBreakSeconds = 0

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val b = binder as TimerService.TimerBinder
            timerService = b.getService()
            serviceBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            timerService = null
            serviceBound = false
        }
    }

    fun bindService(context: Context) {
        val intent = Intent(context, TimerService::class.java)
        context.startService(intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService(context: Context) {
        if (serviceBound) {
            context.unbindService(serviceConnection)
            serviceBound = false
        }
    }

    fun stopService(context: Context) {
        if (serviceBound) {
            context.unbindService(serviceConnection)
            serviceBound = false
        }
        context.stopService(Intent(context, TimerService::class.java))
    }

    fun loadPreset(preset: Preset) {
        workSeconds = preset.workMinutes * 60
        shortBreakSeconds = preset.shortBreakMinutes * 60
        longBreakSeconds = preset.longBreakMinutes * 60
        _uiState.value = TimerUiState(
            phase = TimerPhase.WORK,
            timerState = TimerState.IDLE,
            remainingSeconds = workSeconds,
            currentCycle = 1,
            totalCycles = preset.cyclesBeforeLong,
            presetName = preset.name
        )
        updateNotification()
    }

    suspend fun getPreset(id: Int): Preset? = repository.getPresetById(id)

    fun start() {
        if (_uiState.value.timerState == TimerState.RUNNING) return
        _uiState.value = _uiState.value.copy(timerState = TimerState.RUNNING)
        timerJob = viewModelScope.launch {
            while (_uiState.value.remainingSeconds > 0) {
                delay(1000)
                _uiState.value = _uiState.value.copy(
                    remainingSeconds = _uiState.value.remainingSeconds - 1
                )
                updateNotification()
            }
            onPhaseComplete()
        }
    }

    fun pause() {
        timerJob?.cancel()
        _uiState.value = _uiState.value.copy(timerState = TimerState.PAUSED)
        updateNotification()
    }

    fun reset() {
        timerJob?.cancel()
        _uiState.value = _uiState.value.copy(
            timerState = TimerState.IDLE,
            remainingSeconds = workSeconds,
            phase = TimerPhase.WORK,
            currentCycle = 1
        )
        updateNotification()
    }

    private fun onPhaseComplete() {
        notifyPhaseComplete()
        val current = _uiState.value
        _uiState.value = when (current.phase) {
            TimerPhase.WORK -> {
                if (current.currentCycle >= current.totalCycles) {
                    current.copy(
                        phase = TimerPhase.LONG_BREAK,
                        timerState = TimerState.IDLE,
                        remainingSeconds = longBreakSeconds
                    )
                } else {
                    current.copy(
                        phase = TimerPhase.SHORT_BREAK,
                        timerState = TimerState.IDLE,
                        remainingSeconds = shortBreakSeconds
                    )
                }
            }
            TimerPhase.SHORT_BREAK -> current.copy(
                phase = TimerPhase.WORK,
                timerState = TimerState.IDLE,
                remainingSeconds = workSeconds,
                currentCycle = current.currentCycle + 1
            )
            TimerPhase.LONG_BREAK -> current.copy(
                phase = TimerPhase.WORK,
                timerState = TimerState.IDLE,
                remainingSeconds = workSeconds,
                currentCycle = 1
            )
        }
        updateNotification()
    }

    private fun notifyPhaseComplete() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(timerService?.applicationContext ?: return, uri).play()
        } catch (e: Exception) { /* ignore */ }

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (timerService?.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            timerService?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 500, 200, 500), -1)
        }
    }

    private fun updateNotification() {
        val state = _uiState.value
        val phaseText = when (state.phase) {
            TimerPhase.WORK -> "作業中"
            TimerPhase.SHORT_BREAK -> "短い休憩"
            TimerPhase.LONG_BREAK -> "長い休憩"
        }
        val minutes = state.remainingSeconds / 60
        val seconds = state.remainingSeconds % 60
        timerService?.updateNotification(
            state.presetName,
            "$phaseText - %02d:%02d".format(minutes, seconds)
        )
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }

    class Factory(private val repository: PresetRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return TimerViewModel(repository) as T
        }
    }
}