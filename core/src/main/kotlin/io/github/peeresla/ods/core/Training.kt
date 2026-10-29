package io.github.peeresla.ods.core

import kotlin.random.Random

const val KEY_ALL = "all"
const val KEY_MISTAKES = "mistakes"

/** Вопрос на экране: порядок вариантов (исходные индексы) и выбранный вариант. */
data class Current(val id: String, val order: List<Int>, val chosen: Int?)

/**
 * Тренировка идёт без конца: вопросы закончились — начинается новый круг.
 * answered — отвечено подряд в этой тренировке, correctRun — верных подряд (для рекорда).
 */
data class Session(
    val key: String,
    val queue: List<String>,
    val pos: Int = 0,
    val current: Current? = null,
    val answered: Int = 0,
    val correctRun: Long = 0,
    val recordBeaten: Boolean = false,
    val unknown: Boolean = false,
)

// Сначала вопросы с ошибками, затем — давно не повторённые (новые считаются самыми давними).
fun orderByPriority(ids: List<String>, state: AppState, random: Random): List<String> =
    ids.shuffled(random).sortedWith(
        compareBy<String> { if (state.progressOf(it).mistake) 0 else 1 }.thenBy { state.progressOf(it).last },
    )

fun sessionIds(key: String, state: AppState, bank: Bank): List<String> = when (key) {
    KEY_ALL -> bank.questions.map { it.id }
    KEY_MISTAKES -> mistakeIds(state, bank)
    else -> bank.questionsInCategory(key)
}

fun createSession(key: String, state: AppState, bank: Bank, random: Random): Session {
    if (key != KEY_ALL && key != KEY_MISTAKES && key !in bank.categoriesById) return Session(key, emptyList(), unknown = true)
    return Session(key, orderByPriority(sessionIds(key, state, bank), state, random))
}

fun startNextRound(session: Session, state: AppState, bank: Bank, random: Random): Session {
    val lastId = session.queue.lastOrNull()
    val queue = orderByPriority(sessionIds(session.key, state, bank), state, random).toMutableList()
    // Не задавать тот же вопрос дважды подряд на стыке кругов
    if (queue.size > 1 && queue[0] == lastId) queue.add(queue.removeAt(0))
    return session.copy(queue = queue, pos = 0)
}

sealed interface TrainView {
    data object Unknown : TrainView
    data class Empty(val text: String) : TrainView
    data class Ask(val session: Session, val question: Question) : TrainView
}

/**
 * Готовит вопрос к показу (renderTrain): при необходимости начинает новый круг и перемешивает варианты.
 * Возвращает обновлённую тренировку и то, что показывать.
 */
fun prepareTrain(session: Session, state: AppState, bank: Bank, random: Random): Pair<Session, TrainView> {
    if (session.unknown) return session to TrainView.Unknown
    if (session.queue.isEmpty()) {
        return session to TrainView.Empty(if (session.key == KEY_MISTAKES) "Ошибок нет." else "В этом разделе пока нет вопросов.")
    }
    var s = session
    if (s.pos >= s.queue.size) {
        s = startNextRound(s, state, bank, random)
        if (s.queue.isEmpty()) return s to TrainView.Empty("Ошибок больше нет. Отвечено подряд: ${s.answered}.")
    }
    val id = s.queue[s.pos]
    val q = bank.questionsById[id] ?: return s.copy(pos = s.pos + 1, current = null).let { prepareTrain(it, state, bank, random) }
    if (s.current?.id != id) {
        // Варианты перемешиваются при каждом показе вопроса
        s = s.copy(current = Current(id, q.options.indices.shuffled(random), null))
    }
    return s to TrainView.Ask(s, q)
}

data class AnswerResult(
    val state: AppState,
    val session: Session,
    val goalReached: Boolean,
    /** Текст уведомления: норма выполнена или новый рекорд. */
    val toast: String?,
    val recordBumped: Boolean,
)

/** Ответ в тренировке (действие train-answer). Ответ сохраняется сразу. */
fun answerTraining(state: AppState, session: Session, bank: Bank, orig: Int, now: Long, today: String): AnswerResult? {
    val cur = session.current ?: return null
    if (cur.chosen != null) return null
    val q = bank.questionsById[cur.id] ?: return null
    val ok = orig == q.correct
    var s = session.copy(current = cur.copy(chosen = orig), answered = session.answered + 1)
    s = s.copy(correctRun = if (ok) s.correctRun + 1 else 0)
    if (!ok) s = s.copy(recordBeaten = false) // новая серия может снова побить рекорд
    var st = state
    var newRecord = false
    if (s.correctRun > st.correctRunRecord) {
        newRecord = !s.recordBeaten && st.correctRunRecord > 0
        st = st.copy(correctRunRecord = s.correctRun)
        s = s.copy(recordBeaten = true)
    }
    st = recordAnswer(st, bank, cur.id, ok, now)
    val (activity, goal) = countPractice(st.activity, 1, today)
    st = st.copy(activity = activity)
    val toast = when {
        goal -> goalToast(activity.streak)
        newRecord -> "Новый рекорд: ${s.correctRun} ${plural(s.correctRun, listOf("верный", "верных", "верных"))} подряд"
        else -> null
    }
    return AnswerResult(st, s, goal, toast, s.recordBeaten && ok)
}

fun nextQuestion(session: Session): Session = session.copy(pos = session.pos + 1, current = null)

fun trainTitle(key: String, bank: Bank?): String = when (key) {
    KEY_ALL -> "Тренировка"
    KEY_MISTAKES -> "Работа над ошибками"
    else -> bank?.categoryTitle(key)?.takeIf { it.isNotEmpty() } ?: "Тренировка"
}

fun leaveTrainingText(n: Int): String =
    "Серия из $n ${plural(n, listOf("отвеченного вопроса", "отвеченных вопросов", "отвеченных вопросов"))} подряд прервётся и не засчитается — в следующий раз счёт начнётся с нуля. Сами ответы уже учтены в статистике."
