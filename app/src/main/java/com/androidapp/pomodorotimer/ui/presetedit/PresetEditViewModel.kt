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

    /**
     * rowRoutine タップ時に採番したIDを記録する。
     * キャンセル時にこのIDが新規作成分であれば削除する。
     */
    private var provisionallyCreatedId: Int? = null

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

    /**
     * ルーティン編集画面に遷移する前に呼ぶ。
     * 新規の場合はDBにinsertしてIDを確保し、provisionallyCreatedId に記録する。
     * 既存の場合は通常通りupdateしてIDを返す。
     * 名前が空の場合は null を返す。
     */
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
            provisionallyCreatedId = newId   // 仮作成IDを記録
            scheduleOrCancel(newId, state)
            newId
        } else {
            repository.updatePreset(preset)
            scheduleOrCancel(state.id, state)
            state.id
        }
    }

    /**
     * 保存ボタン押下時に呼ぶ。
     * - 名前が空 → false
     * - ルーティンアイテムが0件 → false（エラーコードで区別）
     * - それ以外 → 保存して true
     */
    enum class SaveResult { OK, NAME_EMPTY, NO_ROUTINE }

    suspend fun save(): SaveResult {
        val state = _uiState.value
        if (state.name.isBlank()) return SaveResult.NAME_EMPTY

        // ルーティンアイテムの件数チェック
        val currentId = if (state.id == -1) {
            // まだDBに存在しない（rowRoutineを1度もタップしていない）→ アイテムは必ず0件
            return SaveResult.NO_ROUTINE
        } else {
            state.id
        }
        val items = repository.getRoutineItemsOnce(currentId)
        if (items.isEmpty()) return SaveResult.NO_ROUTINE

        // 保存
        val preset = Preset(
            id = currentId,
            name = state.name,
            triggerType = state.triggerType,
            triggerDatetime = state.triggerDatetime
        )
        repository.updatePreset(preset)
        scheduleOrCancel(currentId, state)

        // 正常保存できたので仮作成フラグを解除
        provisionallyCreatedId = null
        return SaveResult.OK
    }

    /**
     * キャンセル時に呼ぶ。
     * rowRoutine タップで仮作成したプリセットがある場合は削除する。
     * 既存プリセットの編集キャンセルの場合は何もしない。
     */
    suspend fun cancelAndCleanup() {
        val id = provisionallyCreatedId ?: return
        val preset = repository.getPresetById(id) ?: return
        repository.deletePreset(preset)
        AlarmScheduler.cancel(appContext, id)
        provisionallyCreatedId = null
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