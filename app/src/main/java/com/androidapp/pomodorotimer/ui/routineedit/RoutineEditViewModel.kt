package com.androidapp.pomodorotimer.ui.routineedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.androidapp.pomodorotimer.data.model.RoutineItem
import com.androidapp.pomodorotimer.data.repository.PresetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RoutineEditViewModel(private val repository: PresetRepository) : ViewModel() {

    private val _items = MutableStateFlow<List<RoutineItem>>(emptyList())
    val items: StateFlow<List<RoutineItem>> = _items

    val displayList: StateFlow<List<RoutineListEntry>> = _items
        .map { buildDisplayList(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun loadItems(presetId: Int) {
        viewModelScope.launch {
            _items.value = repository.getRoutineItemsOnce(presetId)
        }
    }

    suspend fun saveItems(presetId: Int) {
        repository.saveRoutineItems(presetId, _items.value)
    }

    fun removeItem(item: RoutineItem) {
        _items.value = _items.value
            .filter { it.id != item.id || it.order != item.order }
            .mapIndexed { i, it -> reorder(it, i) }
    }

    fun moveItem(from: Int, to: Int) {
        val list = _items.value.toMutableList()
        if (from < 0 || to < 0 || from >= list.size || to >= list.size || from == to) return

        when (list[from]) {
            is RoutineItem.LoopEnd -> return

            is RoutineItem.LoopStart -> {
                val endIndex = findMatchingLoopEnd(list, from)
                if (endIndex == -1) return
                if (to in from..endIndex) return

                val block = list.subList(from, endIndex + 1).toList()
                repeat(block.size) { list.removeAt(from) }
                val insertAt = if (to > endIndex) to - block.size + 1 else to
                block.forEachIndexed { i, item -> list.add(insertAt + i, item) }
            }

            else -> {
                val moved = list.removeAt(from)
                val insertAt = if (to > from) to - 1 else to
                list.add(insertAt.coerceIn(0, list.size), moved)
            }
        }

        _items.value = list.mapIndexed { i, item -> reorder(item, i) }
    }

    // ---- CRUD ----

    fun addLoop(count: Int, insertAfterIndex: Int?) {
        val list = _items.value.toMutableList()
        val pos = if (insertAfterIndex == null) list.size else insertAfterIndex + 1
        list.add(pos, RoutineItem.LoopEnd(order = 0))
        list.add(pos, RoutineItem.LoopStart(order = 0, count = count))
        _items.value = list.mapIndexed { i, item -> reorder(item, i) }
    }

    fun addTimer(seconds: Int, tickSound: String?, tickVolume: Int, insertAfterIndex: Int?) =
        insertItem(
            RoutineItem.Timer(
                order = 0,
                durationSeconds = seconds,
                tickSound = tickSound,
                tickVolume = tickVolume
            ),
            insertAfterIndex
        )

    fun addAlarm(volume: Int, duration: Int, soundUri: String, vibrate: Boolean, insertAfterIndex: Int?) =
        insertItem(
            RoutineItem.Alarm(
                order = 0, volume = volume, durationSeconds = duration,
                soundUri = soundUri, vibrate = vibrate
            ),
            insertAfterIndex
        )

    fun updateLoopStart(id: Int, count: Int) = updateItem { item ->
        if (item is RoutineItem.LoopStart && item.id == id) item.copy(count = count) else item
    }

    fun updateTimer(id: Int, seconds: Int, tickSound: String?, tickVolume: Int) = updateItem { item ->
        if (item is RoutineItem.Timer && item.id == id)
            item.copy(durationSeconds = seconds, tickSound = tickSound, tickVolume = tickVolume)
        else item
    }

    fun updateAlarm(id: Int, volume: Int, duration: Int, soundUri: String, vibrate: Boolean) =
        updateItem { item ->
            if (item is RoutineItem.Alarm && item.id == id)
                item.copy(volume = volume, durationSeconds = duration,
                    soundUri = soundUri, vibrate = vibrate)
            else item
        }

    private fun insertItem(item: RoutineItem, insertAfterIndex: Int?) {
        val list = _items.value.toMutableList()
        val pos = if (insertAfterIndex == null) list.size else insertAfterIndex + 1
        list.add(pos, item)
        _items.value = list.mapIndexed { i, it -> reorder(it, i) }
    }

    private fun updateItem(transform: (RoutineItem) -> RoutineItem) {
        _items.value = _items.value.map(transform)
    }

    private fun reorder(item: RoutineItem, newOrder: Int): RoutineItem = when (item) {
        is RoutineItem.LoopStart -> item.copy(order = newOrder)
        is RoutineItem.LoopEnd   -> item.copy(order = newOrder)
        is RoutineItem.Timer     -> item.copy(order = newOrder)
        is RoutineItem.Alarm     -> item.copy(order = newOrder)
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

    private fun buildDisplayList(items: List<RoutineItem>): List<RoutineListEntry> {
        val result = mutableListOf<RoutineListEntry>()
        val depthStack = ArrayDeque<Int>()

        for (i in items.indices) {
            val item = items[i]
            val currentDepth = depthStack.size
            when (item) {
                is RoutineItem.LoopStart -> {
                    result.add(RoutineListEntry.Item(item, depth = currentDepth))
                    depthStack.addLast(i)
                }
                is RoutineItem.LoopEnd -> {
                    result.add(RoutineListEntry.AddButton(insertAfterIndex = i - 1, depth = currentDepth))
                    depthStack.removeLastOrNull()
                    result.add(RoutineListEntry.Item(item, depth = depthStack.size))
                }
                else -> result.add(RoutineListEntry.Item(item, depth = currentDepth))
            }
        }
        return result
    }

    class Factory(private val repository: PresetRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return RoutineEditViewModel(repository) as T
        }
    }
}