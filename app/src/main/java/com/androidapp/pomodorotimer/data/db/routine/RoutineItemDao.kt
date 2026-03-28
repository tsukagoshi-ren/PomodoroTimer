package com.androidapp.pomodorotimer.data.db.routine

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineItemDao {

    @Query("SELECT * FROM routine_items WHERE presetId = :presetId ORDER BY `order` ASC")
    fun getItemsByPresetId(presetId: Int): Flow<List<RoutineItemEntity>>

    @Query("SELECT * FROM routine_items WHERE presetId = :presetId ORDER BY `order` ASC")
    suspend fun getItemsByPresetIdOnce(presetId: Int): List<RoutineItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: RoutineItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RoutineItemEntity>)

    @Delete
    suspend fun deleteItem(item: RoutineItemEntity)

    @Query("DELETE FROM routine_items WHERE presetId = :presetId")
    suspend fun deleteAllByPresetId(presetId: Int)
}