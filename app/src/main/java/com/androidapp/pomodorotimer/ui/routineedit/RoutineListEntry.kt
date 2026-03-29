package com.androidapp.pomodorotimer.ui.routineedit

import com.androidapp.pomodorotimer.data.model.RoutineItem

/**
 * ルーティン編集画面のRecyclerViewに表示するエントリ。
 *
 * - [Item]       : 実際のルーティンアイテム行
 * - [AddButton]  : アイテム追加ボタン行
 *                   insertAfterIndex = null  → リスト末尾への追加
 *                   insertAfterIndex = n     → LoopStart[n] の直後（ループ内）への追加
 */
sealed class RoutineListEntry {
    data class Item(val routineItem: RoutineItem) : RoutineListEntry()
    data class AddButton(val insertAfterIndex: Int?) : RoutineListEntry()
}