package com.androidapp.pomodorotimer.data.repository.preset

import com.androidapp.pomodorotimer.data.db.preset.PresetDao
import com.androidapp.pomodorotimer.data.db.preset.PresetEntity
import com.androidapp.pomodorotimer.data.model.preset.Preset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PresetRepository(private val presetDao: PresetDao) {

    fun getAllPresets(): Flow<List<Preset>> {
        return presetDao.getAllPresets().map { entities ->
            entities.map { it.toPreset() }
        }
    }

    suspend fun getPresetById(id: Int): Preset? {
        return presetDao.getPresetById(id)?.toPreset()
    }

    suspend fun savePreset(preset: Preset) {
        presetDao.insertPreset(PresetEntity.fromPreset(preset))
    }

    suspend fun updatePreset(preset: Preset) {
        presetDao.updatePreset(PresetEntity.fromPreset(preset))
    }

    suspend fun deletePreset(preset: Preset) {
        presetDao.deletePreset(PresetEntity.fromPreset(preset))
    }
}