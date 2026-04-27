package com.androidapp.pomodorotimer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.androidapp.pomodorotimer.App
import com.androidapp.pomodorotimer.data.model.TriggerType
import com.androidapp.pomodorotimer.util.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        CoroutineScope(Dispatchers.IO).launch {
            val presets = (context.applicationContext as App)
                .presetRepository.getAllPresets().first()

            presets.forEach { preset ->
                when (preset.triggerType) {
                    TriggerType.DATETIME -> {
                        val dt = preset.triggerDatetime
                        if (dt != null && dt > System.currentTimeMillis()) {
                            AlarmScheduler.schedule(context, preset.id, dt)
                        }
                    }
                    TriggerType.WEEKLY -> {
                        // 次回の該当曜日・時刻を再スケジュール
                        AlarmScheduler.scheduleOrCancel(context, preset)
                    }
                    TriggerType.BUTTON -> { /* 何もしない */ }
                }
            }
        }
    }
}