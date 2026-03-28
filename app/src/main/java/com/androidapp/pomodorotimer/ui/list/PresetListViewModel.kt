package com.androidapp.pomodorotimer.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.androidapp.pomodorotimer.data.model.Preset
import com.androidapp.pomodorotimer.data.repository.PresetRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PresetListViewModel(
    private val repository: PresetRepository
) : ViewModel() {

    val presets: StateFlow<List<Preset>> = repository.getAllPresets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deletePreset(preset: Preset) {
        viewModelScope.launch { repository.deletePreset(preset) }
    }

    class Factory(private val repository: PresetRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PresetListViewModel(repository) as T
        }
    }
}