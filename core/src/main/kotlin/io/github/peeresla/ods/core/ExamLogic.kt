package io.github.peeresla.ods.core

import kotlin.random.Random

fun examSize(bank: Bank): Int = Math.min(EXAM_SIZE, bank.questions.size)

fun effectiveThreshold(state: AppState, total: Int): Int = Math.min(state.settings.passThreshold, total)

/** Сколько осталось. Считается от времени старта, а не тиками, поэтому переживает закрытие приложения. */
fun examRemaining(exam: Exam?, now: Long): Long = if (exam == null) 0 else EXAM_DURATION_MS - (now - exam.startedAt)

fun answeredCount(exam: Exam): Int = exam.ids.count { exam.answers.containsKey(it) }

// Банк могли обновить посреди экзамена — убираем исчезнувшие вопросы и чиним порядок вариантов.
fun sanitizeExam(state: AppState, bank: Bank, random: Random): AppState {
    val ex = state.exam ?: return state
    val ids = ex.ids.filter { it in bank.questionsById }
    if (ids.isEmpty()) return state.copy(exam = null)
    val orders = ex.orders.toMutableMap()
    val answers = ex.answers.toMutableMap()
    for (id in ids) {
        val n = bank.questionsById.getValue(id).options.size
        val order = orders[id]
        val valid = order != null && order.size == n && (0 until n).all { it in order }
        if (!valid) orders[id] = (0 until n).shuffled(random)
        val a = answers[id]
        if (a != null && a >= n) answers.remove(id)
    }
    val pos = if (ex.pos >= ids.size) ids.size - 1 else ex.pos
    return state.copy(exam = ex.copy(ids = ids, orders = orders, answers = answers, pos = pos))
}

fun startExam(state: AppState, bank: Bank, now: Long, random: Random): AppState {
    val ids = bank.questions.map { it.id }.shuffled(random).take(examSize(bank))
    val orders = ids.associateWith { (0 until bank.questionsById.getValue(it).options.size).shuffled(random) }
    val fps = ids.associateWith { questionFp(bank.questionsById.getValue(it)) }
    return state.copy(exam = Exam(startedAt = now, ids = ids, orders = orders, answers = emptyMap(), pos = 0, fps = fps))
}

fun chooseExamAnswer(state: AppState, orig: Int): AppState {
    val ex = state.exam ?: return state
    return state.copy(exam = ex.copy(answers = ex.answers + (ex.ids[ex.pos] to orig)))
}

fun moveExam(state: AppState, delta: Int): AppState {
    val ex = state.exam ?: return state
    return state.copy(exam = ex.copy(pos = (ex.pos + delta).coerceIn(0, ex.ids.size - 1)))
}

/** Завершение экзамена. Неотвеченные засчитываются ошибками. second = выполнена ли норма дня только что. */
fun finishExam(state: AppState, bank: Bank, timeout: Boolean, now: Long, today: String): Pair<AppState, Boolean> {
    val ex = state.exam ?: return state to false
    var st = state
    val items = ex.ids.filter { it in bank.questionsById }.map { id ->
        val q = bank.questionsById.getValue(id)
        val chosen = ex.answers[id]
        val ok = chosen == q.correct
        st = recordAnswer(st, bank, id, ok, now)
        ExamItem(id, chosen, ok, questionFp(q))
    }
    val score = items.count { it.ok }
    val threshold = effectiveThreshold(st, items.size)
    val (activity, goal) = countPractice(st.activity, items.count { it.chosen != null }, today)
    val last = LastExam(
        date = isoString(now),
        durationMs = Math.min(now - ex.startedAt, EXAM_DURATION_MS),
        timeout = timeout,
        total = items.size,
        score = score,
        threshold = threshold,
        passed = score >= threshold,
        items = items,
    )
    return st.copy(activity = activity, lastExam = last, exam = null) to goal
}

fun examFinishConfirmText(left: Int): String = "Без ответа: $left. Они будут засчитаны как ошибки. Завершить экзамен?"
