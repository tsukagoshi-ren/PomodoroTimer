package com.androidapp.pomodorotimer.data.model

data class Preset(
    val id: Int = 0,
    val name: String,
    val triggerType: TriggerType,
    val triggerDatetime: Long? = null,
    val order: Int = 0,
    // WEEKLY用
    val weekdays: Int = 0,           // ビットフラグ 日=1,月=2,火=4,水=8,木=16,金=32,土=64
    val triggerTimeOfDay: Int = 0    // 分単位 例: 08:30 = 510
)