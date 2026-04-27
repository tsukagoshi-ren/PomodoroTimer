package com.androidapp.pomodorotimer.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.androidapp.pomodorotimer.data.model.Preset
import com.androidapp.pomodorotimer.data.model.TriggerType
import com.androidapp.pomodorotimer.receiver.AlarmReceiver
import java.util.Calendar

object AlarmScheduler {

    /**
     * Preset の triggerType に応じてスケジュールまたはキャンセルする。
     * AlarmReceiver からの自己再スケジュールにも使う。
     */
    fun scheduleOrCancel(context: Context, preset: Preset) {
        when (preset.triggerType) {
            TriggerType.BUTTON -> cancel(context, preset.id)
            TriggerType.DATETIME -> {
                val dt = preset.triggerDatetime
                if (dt != null && dt > System.currentTimeMillis()) {
                    schedule(context, preset.id, dt)
                } else {
                    cancel(context, preset.id)
                }
            }
            TriggerType.WEEKLY -> {
                val next = calcNextWeeklyTrigger(preset.weekdays, preset.triggerTimeOfDay)
                if (next != null) {
                    schedule(context, preset.id, next)
                } else {
                    cancel(context, preset.id)
                }
            }
        }
    }

    /**
     * 指定ミリ秒にアラームをセット（内部用）。
     */
    fun schedule(context: Context, presetId: Int, triggerTimeMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(context, presetId)
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent
                    )
                }
            }
            else -> alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent
            )
        }
    }

    fun cancel(context: Context, presetId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildPendingIntent(context, presetId))
    }

    /**
     * weekdays（ビットフラグ）と timeOfDay（分）から
     * 次回のトリガー時刻（ミリ秒）を計算する。
     *
     * Calendar.DAY_OF_WEEK: 日=1, 月=2, ... 土=7
     * ビットフラグ:          日=1, 月=2, 火=4, 水=8, 木=16, 金=32, 土=64
     */
    fun calcNextWeeklyTrigger(weekdays: Int, timeOfDay: Int): Long? {
        if (weekdays == 0) return null
        val hour = timeOfDay / 60
        val minute = timeOfDay % 60

        val now = Calendar.getInstance()
        val candidate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // 今日を含む7日間をチェック
        for (offset in 0..6) {
            candidate.apply {
                timeInMillis = now.timeInMillis
                add(Calendar.DAY_OF_MONTH, offset)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            // 過去ならスキップ（offsetが0 = 今日かつ時刻が過去の場合）
            if (candidate.timeInMillis <= System.currentTimeMillis()) continue

            // Calendar.DAY_OF_WEEK (1=日 〜 7=土) → ビット位置に変換
            val calDow = candidate.get(Calendar.DAY_OF_WEEK) // 1〜7
            val bit = 1 shl (calDow - 1)  // 日=1,月=2,火=4,...
            if (weekdays and bit != 0) {
                return candidate.timeInMillis
            }
        }
        return null
    }

    /**
     * 曜日ビットフラグの表示文字列を生成。
     * 例: 月・水・金
     */
    fun weekdaysToString(weekdays: Int): String {
        val names = listOf("日", "月", "火", "水", "木", "金", "土")
        return names.indices
            .filter { weekdays and (1 shl it) != 0 }
            .joinToString("・") { names[it] }
    }

    private fun buildPendingIntent(context: Context, presetId: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_PRESET_ID, presetId)
        }
        return PendingIntent.getBroadcast(
            context, presetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}