package com.androidapp.pomodorotimer.data.db.preset

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {

    @Query("SELECT * FROM presets ORDER BY `order` ASC, id ASC")
    fun getAllPresets(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets WHERE id = :id")
    suspend fun getPresetById(id: Int): PresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: PresetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(presets: List<PresetEntity>)

    @Update
    suspend fun updatePreset(preset: PresetEntity)

    @Update
    suspend fun updateAll(presets: List<PresetEntity>)

    @Delete
    suspend fun deletePreset(preset: PresetEntity)

    @Delete
    suspend fun deleteAll(presets: List<PresetEntity>)

    @Query("SELECT MAX(`order`) FROM presets")
    suspend fun getMaxOrder(): Int?
}