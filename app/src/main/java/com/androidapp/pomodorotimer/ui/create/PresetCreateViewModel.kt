package com.androidapp.pomodorotimer.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.androidapp.pomodorotimer.data.model.preset.Preset
import com.androidapp.pomodorotimer.data.repository.preset.PresetRepository
import kotlinx.coroutines.launch

class PresetCreateViewModel(
    private val repository: PresetRepository
) : ViewModel() {

    fun savePreset(preset: Preset) {
        viewModelScope.launch {
            repository.savePreset(preset)
        }
    }

    class Factory(private val repository: PresetRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PresetCreateViewModel(repository) as T
        }
    }
}