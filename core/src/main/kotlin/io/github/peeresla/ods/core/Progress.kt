package io.github.peeresla.ods.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Привязка прогресса к содержанию (migrateToBank в app.js). Вопрос переехал на другой id — прогресс переезжает,
 * изменили текст или правильный ответ — прогресс по нему начинается заново.
 */
fun migrateToBank(state: AppState, bank: Bank): AppState {
    val qFpById = bank.questions.associate { it.id to questionFp(it) }
    val qIdByFp = HashMap<String, String>()
    for (q in bank.questions) qIdByFp[questionFp(q)] = q.id // как new Map(...): при совпадении побеждает последний
    fun resolveQ(id: String, fp: String?): String? =
        if (fp.isNullOrEmpty()) null else if (qFpById[id] == fp) id else qIdByFp[fp]

    val progress = LinkedHashMap<String, QProgress>()
    for ((id, p) in jsKeyOrder(state.progress)) {
        val target = resolveQ(id, p.fp ?: LEGACY_QUESTION_FP[id]) ?: continue
        val existing = progress[target]
        if (existing != null && existing.last >= p.last) continue
        progress[target] = p.copy(fp = qFpById[target])
    }

    val lastExam = state.lastExam?.let { le ->
        le.copy(items = le.items.map { item ->
            val target = resolveQ(item.id, item.fp ?: LEGACY_QUESTION_FP[item.id])
            if (target != null) item.copy(id = target, fp = qFpById[target]) else item.copy(gone = true) // вопроса больше нет — в разборе не показываем
        })
    }

    // Начатый экзамен по изменившимся вопросам продолжать нельзя — ответы относились к другим вариантам
    val exam = state.exam?.let { ex ->
        val fps = ex.fps ?: emptyMap()
        val ids = ex.ids.map { resolveQ(it, fps[it] ?: LEGACY_QUESTION_FP[it]) }
        if (ids.withIndex().any { (i, id) -> id != ex.ids[i] }) null
        else ex.copy(fps = ids.filterNotNull().associateWith { qFpById.getValue(it) })
    }

    val sFpById = bank.scenarios.associate { it.id to scenarioFp(it) }
    val sIdByFp = HashMap<String, String>()
    for (sc in bank.scenarios) sIdByFp[scenarioFp(sc)] = sc.id
    val scenarios = LinkedHashMap<String, ScenarioData>()
    val archive = state.scenarioArchive.toMutableList()
    for ((id, d) in jsKeyOrder(state.scenarios)) {
        val fp = d.fp ?: LEGACY_SCENARIO_FP[id]
        val target = if (fp.isNullOrEmpty()) null else if (sFpById[id] == fp) id else sIdByFp[fp]
        if (target != null && target !in scenarios) {
            scenarios[target] = d.copy(fp = sFpById[target])
        } else if (d.draft.isNotEmpty() || d.attempts.isNotEmpty()) {
            archive.add(archiveEntry(d, id)) // свои ответы не теряем
        }
    }

    return state.copy(progress = progress, lastExam = lastExam, exam = exam, scenarios = scenarios, scenarioArchive = archive)
}

private fun archiveEntry(d: ScenarioData, oldId: String): JsonObject {
    val base = d.toJson()
    return JsonObject(base + ("oldId" to JsonPrimitive(oldId)))
}

fun recordAnswer(state: AppState, bank: Bank, id: String, isCorrect: Boolean, now: Long): AppState {
    var p = state.progressOf(id)
    p = p.copy(shown = p.shown + 1)
    p = if (isCorrect) {
        val streak = p.streak + 1
        p.copy(correct = p.correct + 1, streak = streak, mistake = if (streak >= LEARNED_STREAK) false else p.mistake)
    } else {
        p.copy(streak = 0, mistake = true)
    }
    p = p.copy(last = now, fp = bank.questionsById[id]?.let { questionFp(it) })
    return state.copy(progress = state.progress + (id to p))
}

fun isLearned(state: AppState, id: String): Boolean = state.progressOf(id).streak >= LEARNED_STREAK

data class Stats(val total: Int, val shown: Long, val learned: Int, val percent: Int?)

fun statsFor(state: AppState, ids: List<String>): Stats {
    var shown = 0L
    var correct = 0L
    var learned = 0
    for (id in ids) {
        val p = state.progressOf(id)
        shown += p.shown
        correct += p.correct
        if (p.streak >= LEARNED_STREAK) learned += 1
    }
    return Stats(ids.size, shown, learned, if (shown > 0) jsRound(correct.toDouble() / shown * 100) else null)
}

/** Math.round из JS: половина округляется вверх. */
fun jsRound(v: Double): Int = Math.floor(v + 0.5).toInt()

fun mistakeIds(state: AppState, bank: Bank): List<String> =
    bank.questions.filter { state.progressOf(it.id).mistake }
        .sortedBy { state.progressOf(it.id).last }
        .map { it.id }

/** Ситуацию начали решать — запоминаем её отпечаток (ensureScenarioData). */
fun withScenario(state: AppState, bank: Bank?, id: String, change: (ScenarioData) -> ScenarioData): AppState {
    var d = state.scenarios[id] ?: ScenarioData()
    bank?.scenariosById?.get(id)?.let { d = d.copy(fp = scenarioFp(it)) }
    d = change(d)
    return state.copy(scenarios = state.scenarios + (id to d))
}

/** Попытка решения ситуации. Разобранная ситуация засчитывается как полная дневная норма. */
fun gradeScenario(state: AppState, bank: Bank?, id: String, grade: String, text: String, now: Long, today: String): Pair<AppState, Boolean> {
    val s = withScenario(state, bank, id) { it.copy(attempts = it.attempts + Attempt(isoString(now), grade, text), draft = "") }
    val (activity, reached) = countPractice(s.activity, DAILY_GOAL, today)
    return s.copy(activity = activity) to reached
}

/** Сброс прогресса: настройки сохраняются. */
fun resetProgress(state: AppState): AppState = AppState(settings = state.settings)
