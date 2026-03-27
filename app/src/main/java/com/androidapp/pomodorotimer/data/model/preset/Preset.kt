package com.androidapp.pomodorotimer.data.model.preset

data class Preset(
    val id: Int = 0,
    val name: String,
    val workMinutes: Int,
    val shortBreakMinutes: Int,
    val longBreakMinutes: Int,
    val cyclesBeforeLong: Int
)