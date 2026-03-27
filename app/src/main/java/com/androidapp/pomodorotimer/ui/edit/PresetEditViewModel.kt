package com.androidapp.pomodorotimer.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.androidapp.pomodorotimer.data.model.preset.Preset
import com.androidapp.pomodorotimer.data.repository.preset.PresetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PresetEditViewModel(
    private val repository: PresetRepository
) : ViewModel() {

    private val _preset = MutableStateFlow<Preset?>(null)
    val preset: StateFlow<Preset?> = _preset

    fun loadPreset(id: Int) {
        viewModelScope.launch {
            _preset.value = repository.getPresetById(id)
        }
    }

    fun updatePreset(preset: Preset) {
        viewModelScope.launch {
            repository.updatePreset(preset)
        }
    }

    class Factory(private val repository: PresetRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PresetEditViewModel(repository) as T
        }
    }
}