package io.github.peeresla.ods.core

import org.junit.Test
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReminderTest {
    private val zone = ZoneId.of("Europe/Moscow")

    @Test
    fun textOnlyWhenGoalNotDone() {
        val a = Activity(lastDone = "2026-10-28", streak = 5, best = 5, today = "2026-10-29", todayCount = 3)
        assertEquals(
            "Норма на сегодня не выполнена. Серия 5 дней сгорит в полночь. Осталось 7 вопросов.",
            reminderText(a, "2026-10-29"),
        )
        val done = a.copy(lastDone = "2026-10-29", streak = 6, todayCount = 10)
        assertNull(reminderText(done, "2026-10-29"))
        assertEquals("Норма на сегодня: 3 / 10", goalLine(streakStatus(a, "2026-10-29")))
        assertEquals("Норма выполнена", goalLine(streakStatus(done, "2026-10-29")))
    }

    @Test
    fun nextTime() {
        val clock = FakeClock(0, zone).apply { set("2026-10-29T19:30:00") }
        val at = nextReminderAt(clock.nowMs(), zone, 20, 0)
        assertEquals("2026-10-29T17:00:00Z", java.time.Instant.ofEpochMilli(at).toString())
        clock.set("2026-10-29T20:00:00")
        assertEquals("2026-10-30T17:00:00Z", java.time.Instant.ofEpochMilli(nextReminderAt(clock.nowMs(), zone, 20, 0)).toString())
        clock.set("2026-10-29T23:10:00")
        assertEquals("2026-10-29T21:00:00Z", java.time.Instant.ofEpochMilli(nextMidnight(clock.nowMs(), zone)).toString())
    }
}
