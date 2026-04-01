package com.androidapp.pomodorotimer.data.model

sealed class RoutineItem {
    abstract val id: Int
    abstract val order: Int

    data class LoopStart(
        override val id: Int = 0,
        override val order: Int,
        val count: Int
    ) : RoutineItem()

    data class LoopEnd(
        override val id: Int = 0,
        override val order: Int
    ) : RoutineItem()

    data class Timer(
        override val id: Int = 0,
        override val order: Int,
        val durationSeconds: Int,
        val tickSound: String? = null,   // res/raw のリソース名、null = 無音
        val tickVolume: Int = 80         // Tick音量 0〜100
    ) : RoutineItem()

    data class Alarm(
        override val id: Int = 0,
        override val order: Int,
        val volume: Int,
        val durationSeconds: Int,
        val soundUri: String,
        val vibrate: Boolean
    ) : RoutineItem()
}