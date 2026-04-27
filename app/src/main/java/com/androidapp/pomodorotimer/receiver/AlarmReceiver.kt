package com.androidapp.pomodorotimer.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.androidapp.pomodorotimer.App
import com.androidapp.pomodorotimer.MainActivity
import com.androidapp.pomodorotimer.R
import com.androidapp.pomodorotimer.data.model.TriggerType
import com.androidapp.pomodorotimer.util.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val presetId = intent.getIntExtra(EXTRA_PRESET_ID, -1)
        if (presetId == -1) return

        // 通知を出す
        showNotification(context, presetId)

        // WEEKLYなら次回をスケジュール
        CoroutineScope(Dispatchers.IO).launch {
            val repo = (context.applicationContext as App).presetRepository
            val preset = repo.getPresetById(presetId) ?: return@launch
            if (preset.triggerType == TriggerType.WEEKLY) {
                // 今発火した分は除外するため1分後以降で次を探す
                val next = AlarmScheduler.calcNextWeeklyTrigger(
                    preset.weekdays,
                    preset.triggerTimeOfDay
                )
                if (next != null) {
                    AlarmScheduler.schedule(context, presetId, next)
                }
            }
        }
    }

    private fun showNotification(context: Context, presetId: Int) {
        val activityIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_PRESET_ID, presetId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, presetId, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        createNotificationChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_alarm_title))
            .setContentText(context.getString(R.string.notif_alarm_text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(presetId, notification)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_alarm_name),
                NotificationManager.IMPORTANCE_HIGH
            )
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        const val EXTRA_PRESET_ID = "preset_id"
        const val CHANNEL_ID = "alarm_trigger_channel"
    }
}