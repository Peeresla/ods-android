package io.github.peeresla.ods.extras

import android.content.Context

/**
 * Настройки этого устройства: напоминание и виброотклик. Не входят в резервную копию —
 * в веб-версии таких настроек нет, а формат копии должен оставаться общим.
 */
class ExtrasPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("extras", Context.MODE_PRIVATE)

    var reminderOn: Boolean
        get() = prefs.getBoolean("reminder_on", false)
        set(v) { prefs.edit().putBoolean("reminder_on", v).apply() }

    var reminderHour: Int
        get() = prefs.getInt("reminder_hour", 20)
        set(v) { prefs.edit().putInt("reminder_hour", v).apply() }

    var reminderMinute: Int
        get() = prefs.getInt("reminder_minute", 0)
        set(v) { prefs.edit().putInt("reminder_minute", v).apply() }

    var haptics: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(v) { prefs.edit().putBoolean("haptics", v).apply() }
}
