package com.androidapp.pomodorotimer.data.repository

import com.androidapp.pomodorotimer.data.db.preset.PresetDao
import com.androidapp.pomodorotimer.data.db.preset.PresetEntity
import com.androidapp.pomodorotimer.data.db.routine.RoutineItemDao
import com.androidapp.pomodorotimer.data.db.routine.RoutineItemEntity
import com.androidapp.pomodorotimer.data.model.Preset
import com.androidapp.pomodorotimer.data.model.RoutineItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
        val maxOrder = presetDao.getMaxOrder() ?: -1
        val entity = PresetEntity.fromPreset(preset).copy(order = maxOrder + 1)
        val id = presetDao.insertPreset(entity)
        return id.toInt()
    }

    suspend fun updatePreset(preset: Preset) =
        presetDao.updatePreset(PresetEntity.fromPreset(preset))

    suspend fun deletePreset(preset: Preset) =
        presetDao.deletePreset(PresetEntity.fromPreset(preset))

    suspend fun deletePresets(presets: List<Preset>) =
        presetDao.deleteAll(presets.map { PresetEntity.fromPreset(it) })

    /**
     * 複数プリセットを、それぞれ元の直下にコピーする。
     *
     * 手順:
     * 1. 現在の全プリセットを order 順で取得
     * 2. order が後ろのものから処理することで、前に挿入してもインデックスがずれない
     * 3. 全挿入後に order を連番で振り直す
     */
    suspend fun copyPresets(presets: List<Preset>) {
        val current = getAllPresets().first().toMutableList()

        // 表示順が後ろのものから処理（前への挿入でインデックスずれを防ぐ）
        val sortedByIndexDesc = presets.sortedByDescending { p ->
            current.indexOfFirst { it.id == p.id }
        }

        for (original in sortedByIndexDesc) {
            val insertIndex = current.indexOfFirst { it.id == original.id }
                .let { if (it == -1) current.size else it + 1 }

            // DB に複製を挿入（order は後で一括更新するので仮値 0）
            val newId = presetDao.insertPreset(
                PresetEntity(
                    id = 0,
                    name = "${original.name} のコピー",
                    triggerType = original.triggerType.name,
                    triggerDatetime = original.triggerDatetime,
                    order = 0
                )
            ).toInt()

            // ルーティンアイテムもコピー
            val items = routineItemDao.getItemsByPresetIdOnce(original.id)
            if (items.isNotEmpty()) {
                routineItemDao.insertAll(items.map { it.copy(id = 0, presetId = newId) })
            }

            // ローカルリストにも挿入して後続の insertIndex 計算に反映
            current.add(insertIndex, original.copy(id = newId, name = "${original.name} のコピー"))
        }

        // 全プリセットの order を連番で振り直す
        reorderPresets(current.map { it.id })
    }

    /**
     * プリセットの表示順を一括更新。
     * [orderedIds] は新しい表示順の id リスト（先頭が order=0）。
     */
    suspend fun reorderPresets(orderedIds: List<Int>) {
        val entities = orderedIds.mapIndexedNotNull { index, id ->
            presetDao.getPresetById(id)?.copy(order = index)
        }
        presetDao.updateAll(entities)
    }

    // --- RoutineItem ---

    fun getRoutineItems(presetId: Int): Flow<List<RoutineItem>> =
        routineItemDao.getItemsByPresetId(presetId)
            .map { list -> list.map { it.toRoutineItem() } }

    suspend fun getRoutineItemsOnce(presetId: Int): List<RoutineItem> =
        routineItemDao.getItemsByPresetIdOnce(presetId)
            .map { it.toRoutineItem() }

    suspend fun saveRoutineItems(presetId: Int, items: List<RoutineItem>) {
        routineItemDao.deleteAllByPresetId(presetId)
        val entities = items.mapIndexed { index, item ->
            RoutineItemEntity.fromRoutineItem(presetId, item).copy(order = index)
        }
        routineItemDao.insertAll(entities)
    }
}