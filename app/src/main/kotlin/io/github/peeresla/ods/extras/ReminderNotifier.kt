package io.github.peeresla.ods.extras

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.peeresla.ods.MainActivity
import io.github.peeresla.ods.R
import io.github.peeresla.ods.core.SystemClock
import io.github.peeresla.ods.core.reminderText
import io.github.peeresla.ods.core.today

/** Уведомление «от начальника ЦУКС», если норма на сегодня не выполнена. */
object ReminderNotifier {
    private const val CHANNEL = "reminder"
    private const val ID = 1

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun notifyIfNeeded(context: Context) {
        val state = StateFile.read(context)
        val text = reminderText(state.activity, SystemClock.today()) ?: return
        show(context, text)
    }

    fun show(context: Context, text: String) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(0xFFF07A1A.toInt())
            .setContentTitle("Начальник ЦУКС")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID, n)
        } catch (e: SecurityException) {
            // разрешение отозвали — молча пропускаем
        }
    }

    /** Норма выполнена — напоминание больше не актуально. */
    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID)
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Напоминание о норме", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Вечером, если норма на сегодня не выполнена"
            },
        )
    }
}
