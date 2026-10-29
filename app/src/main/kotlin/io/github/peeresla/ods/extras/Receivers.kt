package io.github.peeresla.ods.extras

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Сработал будильник: напоминание или полночь. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                when (intent.action) {
                    Scheduler.ACTION_REMIND -> {
                        ReminderNotifier.notifyIfNeeded(context)
                        Scheduler.scheduleReminder(context) // на завтра
                    }
                    Scheduler.ACTION_MIDNIGHT -> {
                        StreakWidget.updateAll(context, null)
                        Scheduler.scheduleMidnight(context)
                    }
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/** Будильники не переживают перезагрузку, обновление приложения и смену часового пояса — ставим заново. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.rescheduleAll(context)
        StreakWidget.updateAll(context, null)
    }
}
