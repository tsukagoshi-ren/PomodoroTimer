package com.androidapp.pomodorotimer.data.db.preset

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.androidapp.pomodorotimer.data.model.preset.Preset

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val workMinutes: Int,
    val shortBreakMinutes: Int,
    val longBreakMinutes: Int,
    val cyclesBeforeLong: Int
) {
    // DBのEntityをドメインモデルに変換する
    fun toPreset() = Preset(
        id = id,
        name = name,
        workMinutes = workMinutes,
        shortBreakMinutes = shortBreakMinutes,
        longBreakMinutes = longBreakMinutes,
        cyclesBeforeLong = cyclesBeforeLong
    )

    companion object {
        // ドメインモデルからEntityに変換する
        fun fromPreset(preset: Preset) = PresetEntity(
            id = preset.id,
            name = preset.name,
            workMinutes = preset.workMinutes,
            shortBreakMinutes = preset.shortBreakMinutes,
            longBreakMinutes = preset.longBreakMinutes,
            cyclesBeforeLong = preset.cyclesBeforeLong
        )
    }
}