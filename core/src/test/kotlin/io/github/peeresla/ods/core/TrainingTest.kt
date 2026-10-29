package io.github.peeresla.ods.core

import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TrainingTest {
    private val bank = testBank()

    @Test
    fun everyQuestionOpensAndCorrectAnswerCountsAfterShuffle() {
        val random = Random(42)
        var st = AppState()
        var s = createSession(KEY_ALL, st, bank, random)
        val seen = HashSet<String>()
        repeat(bank.questions.size * 5) {
            val (s1, view) = prepareTrain(s, st, bank, random)
            val ask = assertIs<TrainView.Ask>(view)
            seen.add(ask.question.id)
            val cur = s1.current!!
            assertEquals(ask.question.options.indices.toList(), cur.order.sorted())
            // Нажимаем вариант, стоящий на экране там, куда его поставило перемешивание
            val shownPos = cur.order.indexOf(ask.question.correct)
            val r = answerTraining(st, s1, bank, cur.order[shownPos], 1000L + it, "2026-10-01")!!
            assertTrue(r.state.progressOf(ask.question.id).correct > st.progressOf(ask.question.id).correct)
            st = r.state
            s = nextQuestion(r.session)
        }
        assertEquals(bank.questions.map { it.id }.toSet(), seen)
        assertEquals(15L, st.correctRunRecord)
    }

    @Test
    fun mistakesFirstAndNoRepeatAcrossRounds() {
        val random = Random(7)
        val st = AppState(progress = mapOf("3" to QProgress(shown = 1, last = 5, mistake = true), "1" to QProgress(shown = 1, last = 1)))
        repeat(20) {
            val order = orderByPriority(bank.questions.map { it.id }, st, random)
            assertEquals(listOf("3", "2", "1"), order) // ошибки, затем новые (самые давние), затем давно отвеченные
        }
        var s = createSession(KEY_ALL, st, bank, random)
        repeat(30) {
            val last = s.queue.last()
            s = startNextRound(s.copy(pos = s.queue.size), st, bank, random)
            assertNotEquals(last, s.queue.first())
        }
    }

    @Test
    fun mistakesSessionEnds() {
        val random = Random(1)
        var st = AppState(progress = mapOf("2" to QProgress(shown = 1, mistake = true)))
        var s = createSession(KEY_MISTAKES, st, bank, random)
        assertEquals(listOf("2"), s.queue)
        repeat(2) {
            val (s1, _) = prepareTrain(s, st, bank, random)
            val r = answerTraining(st, s1, bank, 1, 10, "2026-10-01")!!
            st = r.state
            s = nextQuestion(r.session)
        }
        val (_, view) = prepareTrain(s, st, bank, random)
        assertEquals(TrainView.Empty("Ошибок больше нет. Отвечено подряд: 2."), view)
    }

    @Test
    fun newRecordToast() {
        val random = Random(1)
        var st = AppState(correctRunRecord = 1)
        var s = createSession("a", st, bank, random)
        val toasts = ArrayList<String?>()
        repeat(3) {
            val (s1, view) = prepareTrain(s, st, bank, random)
            val q = (view as TrainView.Ask).question
            val r = answerTraining(st, s1, bank, q.correct, 10, "2026-10-01")!!
            toasts.add(r.toast)
            st = r.state
            s = nextQuestion(r.session)
        }
        assertEquals(listOf(null, "Новый рекорд: 2 верных подряд", null), toasts)
        assertEquals(3L, st.correctRunRecord)
    }

    @Test
    fun unknownCategory() {
        assertEquals(TrainView.Unknown, prepareTrain(createSession("zzz", AppState(), bank, Random(1)), AppState(), bank, Random(1)).second)
    }

    @Test
    fun texts() {
        assertEquals(
            "Серия из 1 отвеченного вопроса подряд прервётся и не засчитается — в следующий раз счёт начнётся с нуля. Сами ответы уже учтены в статистике.",
            leaveTrainingText(1),
        )
        assertEquals("Работа над ошибками", trainTitle(KEY_MISTAKES, bank))
        assertEquals("Раздел А", trainTitle("a", bank))
    }
}
