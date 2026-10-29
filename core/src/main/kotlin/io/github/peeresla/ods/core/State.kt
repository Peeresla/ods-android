package io.github.peeresla.ods.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val STATE_SCHEMA = 1
const val EXAM_SIZE = 20
const val EXAM_DURATION_MS = 20L * 60 * 1000
const val DEFAULT_PASS_THRESHOLD = 18
const val LEARNED_STREAK = 2 // столько правильных подряд — вопрос выучен и уходит из «Ошибок»
const val MAX_MISSED_SHADE = 10 // столько пропущенных дней и больше — красный цвет
const val DAILY_GOAL = 10 // ответов в день, чтобы день засчитался в серию (как урок в Duolingo)

val GRADES: Map<String, String> = linkedMapOf("ok" to "Справился", "partial" to "Частично", "fail" to "Не справился")

data class QProgress(
    val shown: Long = 0,
    val correct: Long = 0,
    val streak: Long = 0,
    val last: Long = 0,
    val mistake: Boolean = false,
    val fp: String? = null,
)

data class Attempt(val date: String, val grade: String, val text: String)

data class ScenarioData(
    val fp: String? = null,
    val draft: String = "",
    val attempts: List<Attempt> = emptyList(),
)

data class Settings(val passThreshold: Int = DEFAULT_PASS_THRESHOLD)

data class Exam(
    val startedAt: Long,
    val ids: List<String>,
    /** id вопроса → порядок показа исходных индексов вариантов. */
    val orders: Map<String, List<Int>>,
    /** id вопроса → выбранный исходный индекс. */
    val answers: Map<String, Int>,
    val pos: Int,
    val fps: Map<String, String>? = null,
)

data class ExamItem(val id: String, val chosen: Int?, val ok: Boolean, val fp: String?, val gone: Boolean = false)

data class LastExam(
    val date: String,
    val durationMs: Long,
    val timeout: Boolean,
    val total: Int,
    val score: Int,
    val threshold: Int,
    val passed: Boolean,
    val items: List<ExamItem>,
)

/**
 * lastDone — последний день (ГГГГ-ММ-ДД) с выполненной нормой, streak — серия, закончившаяся этим днём;
 * today и todayCount — сколько ответов засчитано в норму сегодня.
 */
data class Activity(
    val lastDone: String? = null,
    val streak: Int = 0,
    val best: Int = 0,
    val today: String? = null,
    val todayCount: Int = 0,
)

/** Прогресс пользователя. Структура совпадает с defaultState() в app.js — ради резервных копий. */
data class AppState(
    val progress: Map<String, QProgress> = emptyMap(),
    val scenarios: Map<String, ScenarioData> = emptyMap(),
    val settings: Settings = Settings(),
    val exam: Exam? = null,
    val lastExam: LastExam? = null,
    val activity: Activity = Activity(),
    /** Лучшая серия верных ответов подряд в тренировке (records.correctRun). */
    val correctRunRecord: Long = 0,
    /** Ответы на ситуации, которых больше нет в банке. Хранятся как есть. */
    val scenarioArchive: List<JsonObject> = emptyList(),
) {
    fun progressOf(id: String): QProgress = progress[id] ?: QProgress()
    fun scenarioOf(id: String): ScenarioData = scenarios[id] ?: ScenarioData()

    fun toJson(): JsonObject = buildJsonObject {
        put("schema", STATE_SCHEMA)
        put("progress", JsonObject(progress.mapValues { (_, p) -> p.toJson() }))
        put("scenarios", JsonObject(scenarios.mapValues { (_, s) -> s.toJson() }))
        put("settings", buildJsonObject { put("passThreshold", settings.passThreshold) })
        put("exam", exam?.toJson() ?: JsonNull)
        put("lastExam", lastExam?.toJson() ?: JsonNull)
        put("activity", buildJsonObject {
            put("lastDone", activity.lastDone)
            put("streak", activity.streak)
            put("best", activity.best)
            put("today", activity.today)
            put("todayCount", activity.todayCount)
        })
        put("records", buildJsonObject { put("correctRun", correctRunRecord) })
        put("scenarioArchive", JsonArray(scenarioArchive))
    }

    fun serialize(): String = Json.encodeToString(JsonElement.serializer(), toJson())

    companion object {
        fun parse(text: String?): AppState =
            if (text.isNullOrBlank()) AppState() else normalize(runCatching { Json.parseToJsonElement(text) }.getOrNull())

        /** normalizeState из app.js: читает терпимо, неизвестные и неверные поля пропускает. */
        fun normalize(rawEl: JsonElement?): AppState {
            val raw = rawEl.obj() ?: return AppState()

            val progress = LinkedHashMap<String, QProgress>()
            raw["progress"].obj()?.let { src ->
                for ((id, pEl) in jsKeyOrder(src)) {
                    val p = pEl.obj() ?: continue
                    progress[id] = QProgress(
                        shown = toCount(p["shown"]),
                        correct = toCount(p["correct"]),
                        streak = toCount(p["streak"]),
                        last = toCount(p["last"]),
                        mistake = p["mistake"].truthy(),
                        fp = p["fp"].str(),
                    )
                }
            }

            val scenarios = LinkedHashMap<String, ScenarioData>()
            raw["scenarios"].obj()?.let { src ->
                for ((id, sEl) in jsKeyOrder(src)) {
                    val s = sEl.obj() ?: continue
                    scenarios[id] = ScenarioData(
                        fp = s["fp"].str(),
                        draft = s["draft"].str() ?: "",
                        attempts = s["attempts"].arr()?.mapNotNull { aEl ->
                            val a = aEl.obj() ?: return@mapNotNull null
                            val date = a["date"].str() ?: return@mapNotNull null
                            val grade = a["grade"].str()?.takeIf { it in GRADES } ?: return@mapNotNull null
                            Attempt(date, grade, a["text"].str() ?: "")
                        } ?: emptyList(),
                    )
                }
            }

            val settingsObj = raw["settings"].obj()
            val settings = if (settingsObj != null && settingsObj.containsKey("passThreshold")) {
                Settings(clampThreshold(settingsObj["passThreshold"]))
            } else {
                Settings()
            }

            val exam = raw["exam"].obj()?.let { ex ->
                val startedAt = ex["startedAt"].num()?.takeIf { it.isFinite() } ?: return@let null
                val ids = ex["ids"].arr() ?: return@let null
                val orders = ex["orders"].obj() ?: return@let null
                val answers = ex["answers"].obj() ?: return@let null
                Exam(
                    startedAt = startedAt.toLong(),
                    ids = ids.map { it.jsString() },
                    orders = orders.mapNotNull { (k, v) ->
                        val list = v.arr()?.map { it.int()?.toInt() ?: -1 } ?: return@mapNotNull null
                        k to list
                    }.toMap(),
                    answers = answers.mapNotNull { (k, v) -> v.int()?.let { k to it.toInt() } }.toMap(),
                    pos = toCount(ex["pos"]).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    fps = ex["fps"].obj()?.mapNotNull { (k, v) -> v.str()?.let { k to it } }?.toMap(),
                )
            }

            val lastExam = raw["lastExam"].obj()?.let { le ->
                val items = le["items"].arr() ?: return@let null
                LastExam(
                    date = le["date"].str() ?: "",
                    durationMs = toCount(le["durationMs"]),
                    timeout = le["timeout"].truthy(),
                    total = toCount(le["total"]).toInt(),
                    score = toCount(le["score"]).toInt(),
                    threshold = toCount(le["threshold"]).toInt(),
                    passed = le["passed"].truthy(),
                    items = items.mapNotNull { it.obj() }.map { i ->
                        ExamItem(
                            id = i["id"]?.takeIf { !it.isNullish() }?.jsString() ?: "",
                            chosen = i["chosen"].int()?.toInt(),
                            ok = i["ok"].truthy(),
                            fp = i["fp"].str(),
                            gone = i["gone"].truthy(),
                        )
                    },
                )
            }

            val correctRun = raw["records"].obj()?.let { toCount(it["correctRun"]) } ?: 0L
            val archive = raw["scenarioArchive"].arr()?.mapNotNull { it.obj() } ?: emptyList()

            val activity = raw["activity"].obj()?.let { a ->
                fun day(v: JsonElement?): String? = v.str()?.takeIf { DAY_RE.matches(it) }
                Activity(
                    lastDone = day(a["lastDone"]),
                    streak = toCount(a["streak"]).toInt(),
                    best = toCount(a["best"]).toInt(),
                    today = day(a["today"]),
                    todayCount = toCount(a["todayCount"]).toInt(),
                )
            } ?: Activity()

            return AppState(progress, scenarios, settings, exam, lastExam, activity, correctRun, archive)
        }
    }
}

private val DAY_RE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

fun clampThreshold(v: Int): Int = v.coerceIn(1, EXAM_SIZE)

internal fun clampThreshold(v: JsonElement?): Int {
    val p = v as? JsonPrimitive ?: return DEFAULT_PASS_THRESHOLD
    val d = if (p.isString) p.content.trim().toDoubleOrNull() ?: (if (p.content.isBlank()) 0.0 else null) else p.num()
    if (d == null || !d.isFinite()) return DEFAULT_PASS_THRESHOLD
    return clampThreshold(Math.round(d).toInt())
}

internal fun QProgress.toJson(): JsonObject = buildJsonObject {
    put("shown", shown)
    put("correct", correct)
    put("streak", streak)
    put("last", last)
    put("mistake", mistake)
    if (fp != null) put("fp", fp)
}

internal fun ScenarioData.toJson(): JsonObject = buildJsonObject {
    if (fp != null) put("fp", fp)
    put("draft", draft)
    put("attempts", buildJsonArray {
        for (a in attempts) add(buildJsonObject { put("date", a.date); put("grade", a.grade); put("text", a.text) })
    })
}

internal fun Exam.toJson(): JsonObject = buildJsonObject {
    put("startedAt", startedAt)
    put("ids", JsonArray(ids.map { JsonPrimitive(it) }))
    put("orders", JsonObject(orders.mapValues { (_, o) -> JsonArray(o.map { JsonPrimitive(it) }) }))
    put("answers", JsonObject(answers.mapValues { (_, a) -> JsonPrimitive(a) }))
    put("pos", pos)
    if (fps != null) put("fps", JsonObject(fps.mapValues { (_, f) -> JsonPrimitive(f) }))
}

internal fun LastExam.toJson(): JsonObject = buildJsonObject {
    put("date", date)
    put("durationMs", durationMs)
    put("timeout", timeout)
    put("total", total)
    put("score", score)
    put("threshold", threshold)
    put("passed", passed)
    put("items", buildJsonArray {
        for (i in items) add(buildJsonObject {
            put("id", i.id)
            put("chosen", i.chosen?.let { JsonPrimitive(it) } ?: JsonNull)
            put("ok", i.ok)
            if (i.fp != null) put("fp", i.fp)
            if (i.gone) put("gone", true)
        })
    })
}
