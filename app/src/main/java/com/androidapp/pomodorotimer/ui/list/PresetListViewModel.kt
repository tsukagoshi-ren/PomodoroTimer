package com.androidapp.pomodorotimer.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.androidapp.pomodorotimer.data.model.Preset
import com.androidapp.pomodorotimer.data.repository.PresetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PresetListUiState(
    val presets: List<Preset> = emptyList(),
    val selectedIds: Set<Int> = emptySet(),
    val isSelectionMode: Boolean = false
) {
    val selectedCount: Int get() = selectedIds.size
    val allSelected: Boolean get() = presets.isNotEmpty() && selectedIds.size == presets.size
}

class PresetListViewModel(
    private val repository: PresetRepository
) : ViewModel() {

    private val _presetsFlow = repository.getAllPresets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedIds = MutableStateFlow<Set<Int>>(emptySet())
    private val _isSelectionMode = MutableStateFlow(false)

    val uiState: StateFlow<PresetListUiState> = combine(
        _presetsFlow, _selectedIds, _isSelectionMode
    ) { presets, selectedIds, isSelectionMode ->
        PresetListUiState(presets, selectedIds, isSelectionMode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PresetListUiState())

    // ---- 選択モード ----

    fun enterSelectionMode(presetId: Int) {
        _isSelectionMode.value = true
        _selectedIds.value = setOf(presetId)
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedIds.value = emptySet()
    }

    fun toggleSelection(presetId: Int) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(presetId)) current.remove(presetId) else current.add(presetId)
        _selectedIds.value = current
    }

    fun selectAll() {
        _selectedIds.value = _presetsFlow.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    // ---- アクション ----

    fun deletePreset(preset: Preset) {
        viewModelScope.launch { repository.deletePreset(preset) }
    }

    fun deleteSelected() {
        val ids = _selectedIds.value
        val toDelete = _presetsFlow.value.filter { it.id in ids }
        viewModelScope.launch {
            repository.deletePresets(toDelete)
            exitSelectionMode()
        }
    }

    fun copySelected() {
        val ids = _selectedIds.value
        // order順（現在の表示順）でコピー
        val toCopy = _presetsFlow.value.filter { it.id in ids }
        viewModelScope.launch {
            repository.copyPresets(toCopy)
            exitSelectionMode()
        }
    }

    fun movePreset(fromIndex: Int, toIndex: Int) {
        val list = _presetsFlow.value.toMutableList()
        if (fromIndex < 0 || toIndex < 0 || fromIndex >= list.size || toIndex >= list.size) return
        val moved = list.removeAt(fromIndex)
        list.add(toIndex, moved)
        val orderedIds = list.map { it.id }
        viewModelScope.launch { repository.reorderPresets(orderedIds) }
    }

    class Factory(private val repository: PresetRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PresetListViewModel(repository) as T
        }
    }
}