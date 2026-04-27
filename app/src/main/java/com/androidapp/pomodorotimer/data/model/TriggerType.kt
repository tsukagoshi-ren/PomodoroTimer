package com.androidapp.pomodorotimer.data.model

enum class TriggerType {
    BUTTON,    // 手動（ボタン押下）
    DATETIME,  // 一回限り日時指定
    WEEKLY     // 曜日＋時刻の繰り返し
}