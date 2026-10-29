package io.github.peeresla.ods.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

data class Category(val id: String, val title: String)

data class Question(
    val id: String,
    val category: String,
    val question: String,
    val options: List<String>,
    val correct: Int,
    val explanation: String,
    val source: String,
    val internal: Boolean,
)

data class Scenario(
    val id: String,
    val title: String,
    val situation: String,
    val answer: List<String>,
    val internal: Boolean,
)

/** Расшифрованный и нормализованный банк вопросов (normalizeBank в app.js). */
class Bank(
    val version: String,
    val updated: String,
    val note: String,
    val categories: List<Category>,
    val questions: List<Question>,
    val scenarios: List<Scenario>,
    /** Сколько вопросов пропущено из-за ошибок в файле. */
    val skipped: Int,
) {
    val questionsById: Map<String, Question> = questions.associateBy { it.id }
    val categoriesById: Map<String, Category> = categories.associateBy { it.id }
    val scenariosById: Map<String, Scenario> = scenarios.associateBy { it.id }

    fun categoryTitle(id: String): String = categoriesById[id]?.title ?: ""

    fun questionsInCategory(catId: String): List<String> = questions.filter { it.category == catId }.map { it.id }

    companion object {
        fun parse(text: String): Bank = normalize(Json.parseToJsonElement(text))

        fun normalize(rawEl: JsonElement): Bank {
            val raw = rawEl.obj() ?: throw IllegalArgumentException("Неверный формат questions.json")

            val categories = (raw["categories"].arr() ?: emptyList())
                .mapNotNull { it.obj() }
                .filter { !it["id"].isNullish() }
                .map { c ->
                    val id = c["id"]!!.jsString()
                    Category(id, if (!c["title"].isNullish()) c["title"]!!.jsString() else id)
                }

            val seen = HashSet<String>()
            var skipped = 0
            val questions = ArrayList<Question>()
            for (el in raw["questions"].arr() ?: emptyList()) {
                val q = el.obj()
                val options = q?.get("options").arr()
                val correct = q?.get("correct").int()
                val id = q?.get("id")?.takeIf { !it.isNullish() }?.jsString()
                val text = q?.get("question").str()
                val valid = q != null && id != null && text != null &&
                    options != null && options.size >= 2 &&
                    correct != null && correct >= 0 && correct < options.size &&
                    id !in seen
                if (!valid) {
                    skipped += 1
                    continue
                }
                seen.add(id!!)
                questions.add(
                    Question(
                        id = id,
                        category = q!!["category"]?.takeIf { !it.isNullish() }?.jsString() ?: "",
                        question = text!!,
                        options = options!!.map { it.jsString() },
                        correct = correct!!.toInt(),
                        explanation = q["explanation"].str() ?: "",
                        source = q["source"].str() ?: "",
                        internal = q["internal"].isTrue(),
                    ),
                )
            }

            val scenarios = (raw["scenarios"].arr() ?: emptyList())
                .mapNotNull { it.obj() }
                .filter { !it["id"].isNullish() && it["situation"].str() != null }
                .map { s ->
                    Scenario(
                        id = s["id"]!!.jsString(),
                        title = s["title"].str() ?: "",
                        situation = s["situation"].str()!!,
                        answer = s["answer"].arr()?.map { it.jsString() } ?: emptyList(),
                        internal = s["internal"].isTrue(),
                    )
                }

            return Bank(
                version = raw["version"]?.takeIf { !it.isNullish() }?.jsString() ?: "",
                updated = raw["updated"]?.takeIf { !it.isNullish() }?.jsString() ?: "",
                note = raw["note"].str() ?: "",
                categories = categories,
                questions = questions,
                scenarios = scenarios,
                skipped = skipped,
            )
        }
    }
}
