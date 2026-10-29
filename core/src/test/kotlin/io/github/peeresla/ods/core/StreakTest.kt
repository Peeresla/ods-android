package io.github.peeresla.ods.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreakTest {
    @Test
    fun duolingoRules() {
        var a = Activity()
        var st = streakStatus(a, "2026-10-01")
        assertTrue(st.never)
        assertEquals("Заступаешь на первое дежурство. Норма — 10 вопросов в день. Приступай.", bossPhrase(st))

        // 9 ответов — норма не выполнена
        var r = countPractice(a, 9, "2026-10-01"); a = r.first
        assertFalse(r.second)
        st = streakStatus(a, "2026-10-01")
        assertEquals(9, st.todayCount)
        assertEquals(1, st.left)
        assertEquals("Первое дежурство идёт: 9 из 10. Добей норму — и серия пошла. Осталось 1 вопрос.", bossPhrase(st))

        // 10-й — день засчитан
        r = countPractice(a, 1, "2026-10-01"); a = r.first
        assertTrue(r.second)
        st = streakStatus(a, "2026-10-01")
        assertTrue(st.doneToday)
        assertEquals(1, st.streak)
        assertFalse(countPractice(a, 1, "2026-10-01").second) // повторно не празднуем

        // Следующий день: серия под угрозой
        st = streakStatus(a, "2026-10-02")
        assertTrue(st.atRisk)
        assertEquals(1, st.streak)
        assertEquals(0, st.todayCount)
        assertEquals("Норма на сегодня не выполнена. Серия 1 день сгорит в полночь. Осталось 10 вопросов.", bossPhrase(st))
        r = countPractice(a, 10, "2026-10-02"); a = r.first
        assertEquals(2, a.streak)
        assertEquals(2, a.best)

        // Пропуск одного дня — серия сгорела
        st = streakStatus(a, "2026-10-04")
        assertEquals(0, st.streak)
        assertEquals(1, st.missed)
        assertEquals(2, st.best)
        r = countPractice(a, 10, "2026-10-04"); a = r.first
        assertEquals(1, a.streak)
        assertEquals(2, a.best)

        st = streakStatus(a, "2026-10-20")
        assertEquals(15, st.missed)
        assertEquals("15 дней без доклада. Я уже решил, что ты сменил место службы. Срочно за тренажёр.", bossPhrase(st))
    }

    @Test
    fun scenarioCountsAsFullGoal() {
        val clock = FakeClock(0).apply { set("2026-10-01T23:59:00") }
        val (st, reached) = gradeScenario(AppState(), testBank(), "s1", "ok", "ответ", clock.nowMs(), clock.today())
        assertTrue(reached)
        assertEquals("2026-10-01", st.activity.lastDone)
        assertEquals("ok", st.scenarioOf("s1").attempts.single().grade)
        assertEquals("9cgeuh", st.scenarioOf("s1").fp)
    }

    @Test
    fun localDateAtMidnight() {
        val clock = FakeClock(0).apply { set("2026-10-01T23:59:59") }
        assertEquals("2026-10-01", clock.today())
        clock.advance(1000)
        assertEquals("2026-10-02", clock.today())
    }

    @Test
    fun phrases() {
        fun done(n: Int) = bossPhrase(StreakStatus(false, true, false, n, n, 0, 10, 0))
        assertEquals("Неделя без пропусков. Вот это оперативная готовность. Завтра будет 8 дней подряд — не сорви.", done(7))
        assertEquals("Норма выполнена. Но один день — ещё не система. Завтра будет 2 дня подряд — не сорви.", done(1))
        assertEquals(
            "Сегодня ты ещё не заступал. 21 день подряд сгорят в полночь — не подводи смену. Осталось 3 вопроса.",
            bossPhrase(StreakStatus(false, false, true, 21, 21, 0, 7, 3)),
        )
        assertEquals("2 дня тишины. В ЦУКС так не работают — догоняй.", bossPhrase(StreakStatus(false, false, false, 0, 3, 2, 0, 10)))
    }
}
