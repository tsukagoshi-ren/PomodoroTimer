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

    /**
     * 選択モード突入時のプリセットリストのスナップショット。
     * キャンセル時にこの状態へ DB を復元する。
     */
    private var snapshot: List<Preset> = emptyList()

    // ---- 選択モード ----

    fun enterSelectionMode(presetId: Int) {
        // 現在の表示リストをスナップショットとして保存
        snapshot = _presetsFlow.value.toList()
        _isSelectionMode.value = true
        _selectedIds.value = setOf(presetId)
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedIds.value = emptySet()
        snapshot = emptyList()
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

    // ---- 保存（確定） ----

    fun commitChanges() {
        // 現状をそのまま確定するだけ（DB はすでに各操作で更新済み）
        exitSelectionMode()
    }

    // ---- キャンセル（スナップショットへ復元） ----

    fun cancelChanges() {
        val snap = snapshot
        if (snap.isEmpty()) {
            exitSelectionMode()
            return
        }
        viewModelScope.launch {
            // 1. 現在 DB にある全プリセットを取得
            val currentPresets = _presetsFlow.value

            // 2. スナップショットに存在しない ID（選択モード中に追加されたコピー）を削除
            val snapshotIds = snap.map { it.id }.toSet()
            val toDelete = currentPresets.filter { it.id !in snapshotIds }
            if (toDelete.isNotEmpty()) repository.deletePresets(toDelete)

            // 3. スナップショットに存在するが現在 DB にない ID（選択モード中に削除された）を復元
            val currentIds = currentPresets.map { it.id }.toSet()
            val toRestore = snap.filter { it.id !in currentIds }
            // Room の insert(onConflict=REPLACE) を利用して復元
            // RoutineItem は削除時に CASCADE で消えているため復元不可だが、
            // Preset 自体は復元する（RoutineItem は空になる）
            for (preset in toRestore) {
                repository.savePresetWithId(preset)
            }

            // 4. 並べ替え・order を元に戻す
            repository.reorderPresets(snap.map { it.id })

            exitSelectionMode()
        }
    }

    // ---- その他アクション ----

    fun deletePreset(preset: Preset) {
        viewModelScope.launch { repository.deletePreset(preset) }
    }

    fun deleteSelected() {
        val ids = _selectedIds.value
        val toDelete = _presetsFlow.value.filter { it.id in ids }
        viewModelScope.launch {
            repository.deletePresets(toDelete)
            _selectedIds.value = emptySet()
        }
    }

    fun copySelected() {
        val ids = _selectedIds.value
        val toCopy = _presetsFlow.value.filter { it.id in ids }
        viewModelScope.launch {
            repository.copyPresets(toCopy)
            _selectedIds.value = emptySet()
        }
    }

    fun movePreset(fromIndex: Int, toIndex: Int) {
        val list = _presetsFlow.value.toMutableList()
        if (fromIndex < 0 || toIndex < 0 || fromIndex >= list.size || toIndex >= list.size) return
        val moved = list.removeAt(fromIndex)
        list.add(toIndex, moved)
        viewModelScope.launch { repository.reorderPresets(list.map { it.id }) }
    }

    class Factory(private val repository: PresetRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PresetListViewModel(repository) as T
        }
    }
}