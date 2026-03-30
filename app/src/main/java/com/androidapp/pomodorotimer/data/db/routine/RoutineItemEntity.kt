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
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["presetId"])]
)
data class RoutineItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val presetId: Int,
    val order: Int,
    val type: String,

    val repeatCount: Int? = null,
    val durationSeconds: Int? = null,
    val volume: Int? = null,
    val alarmDurationSeconds: Int? = null,
    val soundUri: String? = null,
    val vibrate: Boolean? = null,
    val tickSound: String? = null       // v4追加: Timerのティック音リソース名
) {
    fun toRoutineItem(): RoutineItem = when (type) {
        "LOOP_START" -> RoutineItem.LoopStart(id, order, repeatCount!!)
        "LOOP_END"   -> RoutineItem.LoopEnd(id, order)
        "TIMER"      -> RoutineItem.Timer(id, order, durationSeconds!!, tickSound)
        "ALARM"      -> RoutineItem.Alarm(id, order, volume!!, alarmDurationSeconds!!, soundUri!!, vibrate!!)
        else -> throw IllegalStateException("Unknown type: $type")
    }

    companion object {
        fun fromRoutineItem(presetId: Int, item: RoutineItem): RoutineItemEntity = when (item) {
            is RoutineItem.LoopStart -> RoutineItemEntity(
                id = item.id, presetId = presetId, order = item.order,
                type = "LOOP_START", repeatCount = item.count
            )
            is RoutineItem.LoopEnd -> RoutineItemEntity(
                id = item.id, presetId = presetId, order = item.order,
                type = "LOOP_END"
            )
            is RoutineItem.Timer -> RoutineItemEntity(
                id = item.id, presetId = presetId, order = item.order,
                type = "TIMER", durationSeconds = item.durationSeconds,
                tickSound = item.tickSound
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