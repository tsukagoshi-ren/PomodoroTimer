package com.androidapp.pomodorotimer.data.repository

import com.androidapp.pomodorotimer.data.db.preset.PresetDao
import com.androidapp.pomodorotimer.data.db.preset.PresetEntity
import com.androidapp.pomodorotimer.data.db.routine.RoutineItemDao
import com.androidapp.pomodorotimer.data.db.routine.RoutineItemEntity
import com.androidapp.pomodorotimer.data.model.Preset
import com.androidapp.pomodorotimer.data.model.RoutineItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PresetRepository(
    private val presetDao: PresetDao,
    private val routineItemDao: RoutineItemDao
) {
    // --- Preset ---

    fun getAllPresets(): Flow<List<Preset>> =
        presetDao.getAllPresets().map { list -> list.map { it.toPreset() } }

    suspend fun getPresetById(id: Int): Preset? =
        presetDao.getPresetById(id)?.toPreset()

    suspend fun savePreset(preset: Preset): Int {
        val id = presetDao.insertPreset(PresetEntity.fromPreset(preset))
        return id.toInt()
    }

    suspend fun updatePreset(preset: Preset) =
        presetDao.updatePreset(PresetEntity.fromPreset(preset))

    suspend fun deletePreset(preset: Preset) =
        presetDao.deletePreset(PresetEntity.fromPreset(preset))

    // --- RoutineItem ---

    fun getRoutineItems(presetId: Int): Flow<List<RoutineItem>> =
        routineItemDao.getItemsByPresetId(presetId)
            .map { list -> list.map { it.toRoutineItem() } }

    suspend fun getRoutineItemsOnce(presetId: Int): List<RoutineItem> =
        routineItemDao.getItemsByPresetIdOnce(presetId)
            .map { it.toRoutineItem() }

    // ルーティン全体を保存（差し替え方式）
    suspend fun saveRoutineItems(presetId: Int, items: List<RoutineItem>) {
        routineItemDao.deleteAllByPresetId(presetId)
        val entities = items.mapIndexed { index, item ->
            RoutineItemEntity.fromRoutineItem(presetId, item).copy(order = index)
        }
        routineItemDao.insertAll(entities)
    }
}