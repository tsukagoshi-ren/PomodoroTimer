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

            presets.filter {
                it.triggerType == TriggerType.DATETIME &&
                        it.triggerDatetime != null &&
                        it.triggerDatetime > System.currentTimeMillis()
            }.forEach {
                AlarmScheduler.schedule(context, it.id, it.triggerDatetime!!)
            }
        }
    }
}