package io.github.peeresla.ods.extras

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.peeresla.ods.core.nextMidnight
import io.github.peeresla.ods.core.nextReminderAt
import java.time.ZoneId

/**
 * Будильники AlarmManager: вечернее напоминание и обновление виджета в полночь.
 * Неточные (setAndAllowWhileIdle) — им не нужно особое разрешение на точные будильники;
 * система может сдвинуть их на несколько минут, для напоминания это не важно.
 */
object Scheduler {
    const val ACTION_REMIND = "io.github.peeresla.ods.REMIND"
    const val ACTION_MIDNIGHT = "io.github.peeresla.ods.MIDNIGHT"

    fun rescheduleAll(context: Context) {
        scheduleReminder(context)
        scheduleMidnight(context)
    }

    fun scheduleReminder(context: Context) {
        val prefs = ExtrasPrefs(context)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context, ACTION_REMIND, 1)
        am.cancel(pi)
        if (!prefs.reminderOn) return
        val at = nextReminderAt(System.currentTimeMillis(), ZoneId.systemDefault(), prefs.reminderHour, prefs.reminderMinute)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun scheduleMidnight(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context, ACTION_MIDNIGHT, 2)
        am.cancel(pi)
        if (!StreakWidget.hasWidgets(context)) return
        // Пара секунд после полуночи — чтобы новые сутки точно наступили
        am.set(AlarmManager.RTC, nextMidnight(System.currentTimeMillis(), ZoneId.systemDefault()) + 2000, pi)
    }

    private fun pending(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, AlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
