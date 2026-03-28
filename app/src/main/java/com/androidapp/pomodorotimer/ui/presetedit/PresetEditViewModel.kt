package com.androidapp.pomodorotimer.ui.presetedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.androidapp.pomodorotimer.data.model.Preset
import com.androidapp.pomodorotimer.data.model.TriggerType
import com.androidapp.pomodorotimer.data.repository.PresetRepository
import com.androidapp.pomodorotimer.util.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PresetEditUiState(
    val id: Int = -1,
    val name: String = "",
    val triggerType: TriggerType = TriggerType.BUTTON,
    val triggerDatetime: Long? = null
)

class PresetEditViewModel(
    private val repository: PresetRepository,
    private val appContext: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(PresetEditUiState())
    val uiState: StateFlow<PresetEditUiState> = _uiState

    fun loadPreset(id: Int) {
        viewModelScope.launch {
            val preset = repository.getPresetById(id) ?: return@launch
            _uiState.value = PresetEditUiState(
                id = preset.id,
                name = preset.name,
                triggerType = preset.triggerType,
                triggerDatetime = preset.triggerDatetime
            )
        }
    }

    fun setName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun setTrigger(type: TriggerType, datetime: Long?) {
        _uiState.value = _uiState.value.copy(triggerType = type, triggerDatetime = datetime)
    }

    // 保存してIDを返す（新規の場合はinsertして採番されたIDを返す）
    suspend fun saveAndGetId(): Int? {
        val name = _uiState.value.name
        if (name.isBlank()) return null
        val state = _uiState.value
        val preset = Preset(
            id = if (state.id == -1) 0 else state.id,
            name = name,
            triggerType = state.triggerType,
            triggerDatetime = state.triggerDatetime
        )
        return if (state.id == -1) {
            val newId = repository.savePreset(preset)
            _uiState.value = state.copy(id = newId)
            scheduleOrCancel(newId, state)
            newId
        } else {
            repository.updatePreset(preset)
            scheduleOrCancel(state.id, state)
            state.id
        }
    }

    // Fragmentから名前をセットした後に呼ぶ
    suspend fun save(): Boolean {
        val state = _uiState.value
        if (state.name.isBlank()) return false
        val preset = Preset(
            id = if (state.id == -1) 0 else state.id,
            name = state.name,
            triggerType = state.triggerType,
            triggerDatetime = state.triggerDatetime
        )
        val savedId = if (state.id == -1) {
            repository.savePreset(preset)
        } else {
            repository.updatePreset(preset)
            state.id
        }
        // AlarmManager への登録・解除
        scheduleOrCancel(savedId, state)
        return true
    }

    private fun scheduleOrCancel(presetId: Int, state: PresetEditUiState) {
        if (state.triggerType == TriggerType.DATETIME &&
            state.triggerDatetime != null &&
            state.triggerDatetime > System.currentTimeMillis()
        ) {
            AlarmScheduler.schedule(appContext, presetId, state.triggerDatetime)
        } else {
            AlarmScheduler.cancel(appContext, presetId)
        }
    }

    private fun binding_editName_workaround() = _uiState.value.name

    class Factory(
        private val repository: PresetRepository,
        private val appContext: android.content.Context
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PresetEditViewModel(repository, appContext) as T
        }
    }
}