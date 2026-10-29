package io.github.peeresla.ods.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MigrationTest {
    private val bank = testBank()

    @Test
    fun progressFollowsContent() {
        val q2fp = questionFp(bank.questionsById.getValue("2"))
        val st = AppState(
            progress = linkedMapOf(
                // Вопрос 2 раньше был под номером 10 — прогресс переезжает
                "10" to QProgress(shown = 5, correct = 4, streak = 2, last = 100, fp = q2fp),
                // У вопроса 1 сменился текст — прогресс сбрасывается
                "1" to QProgress(shown = 3, correct = 3, streak = 3, last = 50, fp = "other"),
                // Вопрос 3 на месте
                "3" to QProgress(shown = 1, correct = 0, streak = 0, last = 70, mistake = true, fp = questionFp(bank.questionsById.getValue("3"))),
            ),
        )
        val m = migrateToBank(st, bank)
        assertEquals(setOf("2", "3"), m.progress.keys)
        assertEquals(5, m.progress.getValue("2").shown)
        assertEquals(q2fp, m.progress.getValue("2").fp)
        assertTrue(m.progress.getValue("3").mistake)
    }

    @Test
    fun newerProgressWins() {
        val fp = questionFp(bank.questionsById.getValue("1"))
        val st = AppState(progress = linkedMapOf("7" to QProgress(shown = 1, last = 10, fp = fp), "1" to QProgress(shown = 9, last = 20, fp = fp)))
        assertEquals(9, migrateToBank(st, bank).progress.getValue("1").shown)
    }

    @Test
    fun legacyFingerprintWithoutFp() {
        // Старый прогресс без отпечатка сверяется с таблицей банка 1.x: у тестового банка тексты другие — прогресс не переносится
        val st = AppState(progress = mapOf("1" to QProgress(shown = 2)))
        assertTrue(migrateToBank(st, bank).progress.isEmpty())
    }

    @Test
    fun scenariosAndArchive() {
        val st = AppState(
            scenarios = linkedMapOf(
                "old" to ScenarioData(fp = scenarioFp(bank.scenariosById.getValue("s1")), attempts = listOf(Attempt("2026-01-01T00:00:00.000Z", "ok", "т"))),
                "s2" to ScenarioData(fp = "changed", draft = "мой черновик"),
                "gone" to ScenarioData(fp = "zzz"),
            ),
        )
        val m = migrateToBank(st, bank)
        assertEquals(setOf("s1"), m.scenarios.keys)
        assertEquals(1, m.scenarios.getValue("s1").attempts.size)
        assertEquals(1, m.scenarioArchive.size)
        assertEquals("s2", m.scenarioArchive[0]["oldId"].str())
        assertEquals("мой черновик", m.scenarioArchive[0]["draft"].str())
    }

    @Test
    fun examStopsIfQuestionsChanged() {
        val ex = Exam(0, listOf("1", "2"), emptyMap(), emptyMap(), 0, mapOf("1" to "bad", "2" to questionFp(bank.questionsById.getValue("2"))))
        assertNull(migrateToBank(AppState(exam = ex), bank).exam)
        val ok = ex.copy(fps = mapOf("1" to questionFp(bank.questionsById.getValue("1")), "2" to questionFp(bank.questionsById.getValue("2"))))
        assertEquals(ok, migrateToBank(AppState(exam = ok), bank).exam)
    }

    @Test
    fun lastExamItemsGone() {
        val le = LastExam("", 0, false, 2, 0, 2, false, listOf(ExamItem("1", 0, true, questionFp(bank.questionsById.getValue("1"))), ExamItem("5", null, false, "nope")))
        val items = migrateToBank(AppState(lastExam = le), bank).lastExam!!.items
        assertEquals(false, items[0].gone)
        assertEquals(true, items[1].gone)
    }
}
