package com.androidapp.pomodorotimer.data.db.preset

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.androidapp.pomodorotimer.data.model.Preset
import com.androidapp.pomodorotimer.data.model.TriggerType

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val triggerType: String,       // TriggerType.name() で保存
    val triggerDatetime: Long?
) {
    fun toPreset() = Preset(
        id = id,
        name = name,
        triggerType = TriggerType.valueOf(triggerType),
        triggerDatetime = triggerDatetime
    )

    companion object {
        fun fromPreset(p: Preset) = PresetEntity(
            id = p.id,
            name = p.name,
            triggerType = p.triggerType.name,
            triggerDatetime = p.triggerDatetime
        )
    }
}