package io.github.peeresla.ods.extras

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.github.peeresla.ods.MainActivity
import io.github.peeresla.ods.R
import io.github.peeresla.ods.core.AppState
import io.github.peeresla.ods.core.DAYS
import io.github.peeresla.ods.core.SystemClock
import io.github.peeresla.ods.core.goalLine
import io.github.peeresla.ods.core.plural
import io.github.peeresla.ods.core.streakStatus
import io.github.peeresla.ods.core.today

/** Виджет «Серия дней» на рабочем столе: огонёк, число дней и норма на сегодня — как карточка на главной. */
class StreakWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = build(context, StateFile.read(context))
        for (id in ids) manager.updateAppWidget(id, views)
        Scheduler.scheduleMidnight(context)
    }

    override fun onEnabled(context: Context) {
        Scheduler.scheduleMidnight(context)
    }

    override fun onDisabled(context: Context) {
        Scheduler.scheduleMidnight(context) // виджетов не осталось — будильник снимется
    }

    companion object {
        fun hasWidgets(context: Context): Boolean =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, StreakWidget::class.java)).isNotEmpty()

        /** Обновить все виджеты. state = null — прочитать прогресс из файла. */
        fun updateAll(context: Context, state: AppState?) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StreakWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(context, state ?: StateFile.read(context))
            for (id in ids) manager.updateAppWidget(id, views)
        }

        fun build(context: Context, state: AppState): RemoteViews {
            val st = streakStatus(state.activity, SystemClock.today())
            val views = RemoteViews(context.packageName, R.layout.widget_streak)
            // Пока норма не выполнена, огонёк и число серые — как на главной
            val color = if (st.doneToday) 0xFF52E081.toInt() else 0xFF9AA8C0.toInt()
            views.setInt(R.id.widget_icon, "setColorFilter", color)
            views.setTextViewText(R.id.widget_value, st.streak.toString())
            views.setTextColor(R.id.widget_value, color)
            views.setTextViewText(R.id.widget_label, "${plural(st.streak, DAYS)} подряд")
            views.setTextViewText(R.id.widget_goal, goalLine(st))
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            return views
        }
    }
}
