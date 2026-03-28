package com.androidapp.pomodorotimer.data.db.routine

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.androidapp.pomodorotimer.data.db.preset.PresetEntity
import com.androidapp.pomodorotimer.data.model.RoutineItem

@Entity(
    tableName = "routine_items",
    foreignKeys = [
        ForeignKey(
            entity = PresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.CASCADE  // プリセット削除時に関連アイテムも削除
        )
    ],
    indices = [Index(value = ["presetId"])]
)
data class RoutineItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val presetId: Int,
    val order: Int,
    val type: String,              // "REPEAT_START", "REPEAT_END", etc.

    // REPEAT_START
    val repeatCount: Int? = null,

    // TIMER
    val durationSeconds: Int? = null,

    // ALARM
    val volume: Int? = null,
    val alarmDurationSeconds: Int? = null,
    val soundUri: String? = null,
    val vibrate: Boolean? = null
) {
    fun toRoutineItem(): RoutineItem = when (type) {
        "REPEAT_START" -> RoutineItem.RepeatStart(id, order, repeatCount!!)
        "REPEAT_END"   -> RoutineItem.RepeatEnd(id, order)
        "CONDITION_START" -> RoutineItem.ConditionStart(id, order)
        "CONDITION_END"   -> RoutineItem.ConditionEnd(id, order)
        "TIMER" -> RoutineItem.Timer(id, order, durationSeconds!!)
        "ALARM" -> RoutineItem.Alarm(
            id, order, volume!!, alarmDurationSeconds!!, soundUri!!, vibrate!!
        )
        else -> throw IllegalStateException("Unknown type: $type")
    }

    companion object {
        fun fromRoutineItem(presetId: Int, item: RoutineItem): RoutineItemEntity = when (item) {
            is RoutineItem.RepeatStart -> RoutineItemEntity(
                id = item.id, presetId = presetId, order = item.order,
                type = "REPEAT_START", repeatCount = item.count
            )
            is RoutineItem.RepeatEnd -> RoutineItemEntity(
                id = item.id, presetId = presetId, order = item.order,
                type = "REPEAT_END"
            )
            is RoutineItem.ConditionStart -> RoutineItemEntity(
                id = item.id, presetId = presetId, order = item.order,
                type = "CONDITION_START"
            )
            is RoutineItem.ConditionEnd -> RoutineItemEntity(
                id = item.id, presetId = presetId, order = item.order,
                type = "CONDITION_END"
            )
            is RoutineItem.Timer -> RoutineItemEntity(
                id = item.id, presetId = presetId, order = item.order,
                type = "TIMER", durationSeconds = item.durationSeconds
            )
            is RoutineItem.Alarm -> RoutineItemEntity(
                id = item.id, presetId = presetId, order = item.order,
                type = "ALARM", volume = item.volume,
                alarmDurationSeconds = item.durationSeconds,
                soundUri = item.soundUri, vibrate = item.vibrate
            )
        }
    }
}