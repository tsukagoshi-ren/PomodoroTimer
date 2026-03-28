package com.androidapp.pomodorotimer.data.model

data class Preset(
    val id: Int = 0,
    val name: String,
    val triggerType: TriggerType,
    val triggerDatetime: Long? = null  // DATETIME の場合のみ使用
)