package com.androidapp.pomodorotimer.ui.routineedit

import com.androidapp.pomodorotimer.data.model.RoutineItem

/**
 * ルーティン編集画面のRecyclerViewに表示するエントリ。
 *
 * - [Item]       : 実際のルーティンアイテム行
 *                   depth = 0 → ループ外、1以上 → ループ内（インデントレベル）
 * - [AddButton]  : アイテム追加ボタン行
 *                   insertAfterIndex = null  → リスト末尾への追加
 *                   insertAfterIndex = n     → LoopEnd[n] の直前（ループ内末尾）への追加
 *                   depth = ボタンのインデントレベル
 */
sealed class RoutineListEntry {
    data class Item(val routineItem: RoutineItem, val depth: Int = 0) : RoutineListEntry()
    data class AddButton(val insertAfterIndex: Int?, val depth: Int = 0) : RoutineListEntry()
}