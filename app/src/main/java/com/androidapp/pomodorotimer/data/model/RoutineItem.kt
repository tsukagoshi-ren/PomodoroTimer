package com.androidapp.pomodorotimer.data.model

sealed class RoutineItem {
    abstract val id: Int
    abstract val order: Int

    data class RepeatStart(
        override val id: Int = 0,
        override val order: Int,
        val count: Int  // 繰り返し回数
    ) : RoutineItem()

    data class RepeatEnd(
        override val id: Int = 0,
        override val order: Int
    ) : RoutineItem()

    data class ConditionStart(
        override val id: Int = 0,
        override val order: Int
        // 条件の詳細は後で追加
    ) : RoutineItem()

    data class ConditionEnd(
        override val id: Int = 0,
        override val order: Int
    ) : RoutineItem()

    data class Timer(
        override val id: Int = 0,
        override val order: Int,
        val durationSeconds: Int
    ) : RoutineItem()

    data class Alarm(
        override val id: Int = 0,
        override val order: Int,
        val volume: Int,          // 0-100
        val durationSeconds: Int,
        val soundUri: String,
        val vibrate: Boolean
    ) : RoutineItem()
}