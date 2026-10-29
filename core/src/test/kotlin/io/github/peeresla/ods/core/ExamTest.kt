package io.github.peeresla.ods.core

import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExamTest {
    private val bank = testBank()

    @Test
    fun timerCountsFromStart() {
        val clock = FakeClock(0).apply { set("2026-10-01T12:00:00") }
        var st = startExam(AppState(), bank, clock.nowMs(), Random(1))
        val ex = assertNotNull(st.exam)
        assertEquals(3, ex.ids.size)
        assertEquals("20:00", formatDuration(examRemaining(ex, clock.nowMs())))
        clock.advance(65_500)
        assertEquals("18:55", formatDuration(examRemaining(ex, clock.nowMs())))

        // Состояние пережило «убийство процесса»: сохранили и прочитали заново
        st = AppState.parse(st.serialize())
        assertEquals(ex, st.exam)

        // Ответили на один вопрос, затем приложение было закрыто дольше 20 минут
        st = chooseExamAnswer(st, bank.questionsById.getValue(ex.ids[0]).correct)
        clock.advance(EXAM_DURATION_MS)
        assertTrue(examRemaining(st.exam, clock.nowMs()) <= 0)
        val (done, _) = finishExam(st, bank, true, clock.nowMs(), clock.today())
        assertNull(done.exam)
        val r = assertNotNull(done.lastExam)
        assertTrue(r.timeout)
        assertEquals(EXAM_DURATION_MS, r.durationMs)
        assertEquals(1, r.score)
        assertEquals(3, r.threshold) // порог не больше числа вопросов
        assertEquals(false, r.passed)
        // Неотвеченные — ошибки
        assertEquals(2, done.progress.values.count { it.mistake })
        assertEquals(1, done.activity.todayCount)
    }

    @Test
    fun passedWithThreshold() {
        var st = AppState(settings = Settings(2))
        st = startExam(st, bank, 0, Random(2))
        for (i in 0 until 3) {
            val id = st.exam!!.ids[st.exam!!.pos]
            st = chooseExamAnswer(st, bank.questionsById.getValue(id).correct)
            st = moveExam(st, 1)
        }
        assertEquals(2, st.exam!!.pos)
        assertEquals(3, answeredCount(st.exam!!))
        val r = finishExam(st, bank, false, 1000, "2026-10-01").first.lastExam!!
        assertEquals(3, r.score)
        assertEquals(2, r.threshold)
        assertTrue(r.passed)
    }

    @Test
    fun sanitizeAfterBankChange() {
        val st = AppState(exam = Exam(0, listOf("1", "99", "3"), mapOf("1" to listOf(0, 0, 1)), mapOf("1" to 7, "3" to 1), 5))
        val ex = sanitizeExam(st, bank, Random(3)).exam!!
        assertEquals(listOf("1", "3"), ex.ids)
        assertEquals(listOf(0, 1, 2), ex.orders.getValue("1").sorted())
        assertEquals(mapOf("3" to 1), ex.answers)
        assertEquals(1, ex.pos)
    }
}
