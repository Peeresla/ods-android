package io.github.peeresla.ods.core

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Вечернее напоминание: текст — слова начальника ЦУКС из bossPhrase (новых фраз не придумываем).
 * null — напоминать не нужно: норма на сегодня уже выполнена.
 */
fun reminderText(activity: Activity, today: String): String? {
    val st = streakStatus(activity, today)
    if (st.doneToday) return null
    return bossPhrase(st)
}

/** Ближайший момент hh:mm по местному времени строго после [nowMs]. */
fun nextReminderAt(nowMs: Long, zone: ZoneId, hour: Int, minute: Int): Long {
    val now = Instant.ofEpochMilli(nowMs).atZone(zone)
    var at = now.with(LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)))
    if (!at.isAfter(now)) at = at.plusDays(1).with(LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)))
    return at.toInstant().toEpochMilli()
}

/** Начало следующих местных суток — тогда виджет должен показать, что норма на новый день ещё не выполнена. */
fun nextMidnight(nowMs: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

/** Строка для виджета и уведомления: «Норма выполнена» или «Норма на сегодня: X / 10» — как на главной. */
fun goalLine(st: StreakStatus): String =
    if (st.doneToday) "Норма выполнена" else "Норма на сегодня: ${st.todayCount} / $DAILY_GOAL"
