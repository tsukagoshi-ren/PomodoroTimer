package com.androidapp.pomodorotimer.ui.timerrun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.androidapp.pomodorotimer.data.model.RoutineItem
import com.androidapp.pomodorotimer.data.repository.PresetRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class RunState { IDLE, RUNNING, PAUSED, FINISHED }

data class TimerRunUiState(
    val presetName: String = "",
    val executionList: List<RoutineItem> = emptyList(),
    val currentItemLabel: String = "",
    val currentItemSummary: String = "",
    val remainingSeconds: Int = 0,
    val showTimer: Boolean = false,
    val runState: RunState = RunState.IDLE,
    val progress: Int = 0,
    val totalItems: Int = 0,
    val currentIndex: Int = 0
)

/** Tick音イベント: リソース名と音量（0.0〜1.0）のペア */
data class TickEvent(val resourceName: String, val volume: Float)

class TimerRunViewModel(private val repository: PresetRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(TimerRunUiState())
    val uiState: StateFlow<TimerRunUiState> = _uiState

    private val _alarmEvent = MutableStateFlow<AlarmEvent?>(null)
    val alarmEvent: StateFlow<AlarmEvent?> = _alarmEvent

    private val _tickEvent = MutableSharedFlow<TickEvent?>(extraBufferCapacity = 1)
    val tickEvent: SharedFlow<TickEvent?> = _tickEvent

    data class AlarmEvent(val volume: Int, val durationSeconds: Int, val vibrate: Boolean)

    private var executionList: List<RoutineItem> = emptyList()
    private var currentIndex = 0
    private var timerJob: Job? = null

    fun load(presetId: Int) {
        viewModelScope.launch {
            val preset = repository.getPresetById(presetId) ?: return@launch
            val items = repository.getRoutineItemsOnce(presetId)
            executionList = expandRoutine(items)
            _uiState.value = TimerRunUiState(
                presetName = preset.name,
                executionList = executionList,
                totalItems = executionList.size,
                runState = RunState.IDLE
            )
            if (executionList.isNotEmpty()) updateUiForCurrentItem()
        }
    }

    fun start() {
        if (_uiState.value.runState == RunState.RUNNING) return
        _uiState.value = _uiState.value.copy(runState = RunState.RUNNING)
        runCurrentItem()
    }

    fun pause() {
        timerJob?.cancel()
        _uiState.value = _uiState.value.copy(runState = RunState.PAUSED)
    }

    fun resume() {
        if (_uiState.value.runState != RunState.PAUSED) return
        _uiState.value = _uiState.value.copy(runState = RunState.RUNNING)
        continueTimer(_uiState.value.remainingSeconds)
    }

    fun stop() {
        timerJob?.cancel()
        currentIndex = 0
        _uiState.value = _uiState.value.copy(runState = RunState.IDLE)
        if (executionList.isNotEmpty()) updateUiForCurrentItem()
    }

    private fun runCurrentItem() {
        if (currentIndex >= executionList.size) {
            _uiState.value = _uiState.value.copy(runState = RunState.FINISHED)
            return
        }
        when (val item = executionList[currentIndex]) {
            is RoutineItem.Timer -> continueTimer(item.durationSeconds)
            is RoutineItem.Alarm -> runAlarm(item)
            else -> advanceToNext()
        }
    }

    private fun continueTimer(seconds: Int) {
        timerJob?.cancel()
        val timerItem = executionList.getOrNull(currentIndex) as? RoutineItem.Timer
        val tickSound  = timerItem?.tickSound
        val tickVolume = (timerItem?.tickVolume ?: 80) / 100f

        timerJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                _uiState.value = _uiState.value.copy(
                    remainingSeconds = remaining,
                    progress = calcProgress(remaining)
                )
                if (tickSound != null) {
                    _tickEvent.tryEmit(TickEvent(tickSound, tickVolume))
                }
                delay(1000)
                remaining--
            }
            _uiState.value = _uiState.value.copy(remainingSeconds = 0, progress = 0)
            advanceToNext()
        }
    }

    private fun runAlarm(alarm: RoutineItem.Alarm) {
        _alarmEvent.value = AlarmEvent(alarm.volume, alarm.durationSeconds, alarm.vibrate)
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            delay(alarm.durationSeconds * 1000L)
            _alarmEvent.value = null
            advanceToNext()
        }
    }

    private fun advanceToNext() {
        currentIndex++
        if (currentIndex >= executionList.size) {
            _uiState.value = _uiState.value.copy(runState = RunState.FINISHED)
            return
        }
        updateUiForCurrentItem()
        runCurrentItem()
    }

    private fun updateUiForCurrentItem() {
        if (currentIndex >= executionList.size) return
        val item = executionList[currentIndex]
        _uiState.value = _uiState.value.copy(
            executionList = executionList,
            currentItemLabel = item.label(),
            currentItemSummary = item.summary(),
            showTimer = item is RoutineItem.Timer,
            remainingSeconds = if (item is RoutineItem.Timer) item.durationSeconds else 0,
            currentIndex = currentIndex,
            totalItems = executionList.size,
            progress = if (item is RoutineItem.Timer) 100 else 0
        )
    }

    private fun calcProgress(remaining: Int): Int {
        val item = executionList.getOrNull(currentIndex) as? RoutineItem.Timer ?: return 0
        return (remaining * 100 / item.durationSeconds).coerceIn(0, 100)
    }

    private fun expandRoutine(items: List<RoutineItem>): List<RoutineItem> {
        val result = mutableListOf<RoutineItem>()
        var i = 0
        while (i < items.size) {
            val item = items[i]
            if (item is RoutineItem.LoopStart) {
                val endIndex = findMatchingLoopEnd(items, i)
                if (endIndex == -1) { i++; continue }
                val block = items.subList(i + 1, endIndex)
                repeat(item.count) { result.addAll(expandRoutine(block)) }
                i = endIndex + 1
            } else if (item is RoutineItem.LoopEnd) {
                i++
            } else {
                result.add(item)
                i++
            }
        }
        return result
    }

    private fun findMatchingLoopEnd(items: List<RoutineItem>, startIndex: Int): Int {
        var depth = 0
        for (i in startIndex until items.size) {
            when (items[i]) {
                is RoutineItem.LoopStart -> depth++
                is RoutineItem.LoopEnd   -> { depth--; if (depth == 0) return i }
                else -> {}
            }
        }
        return -1
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }

    class Factory(private val repository: PresetRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return TimerRunViewModel(repository) as T
        }
    }
}

private fun RoutineItem.label() = when (this) {
    is RoutineItem.Timer     -> "⏱ タイマー"
    is RoutineItem.Alarm     -> "🔔 アラーム"
    is RoutineItem.LoopStart -> "🔁 ループ開始"
    is RoutineItem.LoopEnd   -> "🔁 ループ終了"
}

private fun RoutineItem.summary() = when (this) {
    is RoutineItem.Timer -> {
        val m = durationSeconds / 60; val s = durationSeconds % 60
        if (m > 0) "${m}分${s}秒" else "${s}秒"
    }
    is RoutineItem.Alarm     -> "音量${volume}% / ${durationSeconds}秒"
    is RoutineItem.LoopStart -> "${count}回繰り返す"
    else -> ""
}