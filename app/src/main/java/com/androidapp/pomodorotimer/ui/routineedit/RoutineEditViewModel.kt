package com.androidapp.pomodorotimer.ui.routineedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.androidapp.pomodorotimer.data.model.RoutineItem
import com.androidapp.pomodorotimer.data.repository.PresetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RoutineEditViewModel(private val repository: PresetRepository) : ViewModel() {

    private val _items = MutableStateFlow<List<RoutineItem>>(emptyList())
    val items: StateFlow<List<RoutineItem>> = _items

    fun loadItems(presetId: Int) {
        viewModelScope.launch {
            _items.value = repository.getRoutineItemsOnce(presetId)
        }
    }

    suspend fun saveItems(presetId: Int) {
        repository.saveRoutineItems(presetId, _items.value)
    }

    fun removeItem(item: RoutineItem) {
        _items.value = _items.value.filter { it.id != item.id || it.order != item.order }
            .mapIndexed { i, it -> reorder(it, i) }
    }

    fun addRepeatStart(count: Int) = addItem(
        RoutineItem.RepeatStart(order = 0, count = count)
    )
    fun addRepeatEnd() = addItem(RoutineItem.RepeatEnd(order = 0))
    fun addConditionStart() = addItem(RoutineItem.ConditionStart(order = 0))
    fun addConditionEnd() = addItem(RoutineItem.ConditionEnd(order = 0))
    fun addTimer(seconds: Int) = addItem(RoutineItem.Timer(order = 0, durationSeconds = seconds))
    fun addAlarm(volume: Int, duration: Int, soundUri: String, vibrate: Boolean) = addItem(
        RoutineItem.Alarm(
            order = 0,
            volume = volume,
            durationSeconds = duration,
            soundUri = soundUri,
            vibrate = vibrate
        )
    )

    fun updateRepeatStart(id: Int, count: Int) = updateItem { item ->
        if (item is RoutineItem.RepeatStart && item.id == id) item.copy(count = count) else item
    }
    fun updateTimer(id: Int, seconds: Int) = updateItem { item ->
        if (item is RoutineItem.Timer && item.id == id) item.copy(durationSeconds = seconds) else item
    }
    fun updateAlarm(id: Int, volume: Int, duration: Int, soundUri: String, vibrate: Boolean) =
        updateItem { item ->
            if (item is RoutineItem.Alarm && item.id == id)
                item.copy(
                    volume = volume,
                    durationSeconds = duration,
                    soundUri = soundUri,
                    vibrate = vibrate
                )
            else item
        }

    private fun addItem(item: RoutineItem) {
        val list = _items.value.toMutableList()
        list.add(reorder(item, list.size))
        _items.value = list
    }

    private fun updateItem(transform: (RoutineItem) -> RoutineItem) {
        _items.value = _items.value.map(transform)
    }

    private fun reorder(item: RoutineItem, newOrder: Int): RoutineItem = when (item) {
        is RoutineItem.RepeatStart   -> item.copy(order = newOrder)
        is RoutineItem.RepeatEnd     -> item.copy(order = newOrder)
        is RoutineItem.ConditionStart -> item.copy(order = newOrder)
        is RoutineItem.ConditionEnd  -> item.copy(order = newOrder)
        is RoutineItem.Timer         -> item.copy(order = newOrder)
        is RoutineItem.Alarm         -> item.copy(order = newOrder)
    }

    class Factory(private val repository: PresetRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return RoutineEditViewModel(repository) as T
        }
    }
}