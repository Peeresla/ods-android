package io.github.peeresla.ods.core

import org.junit.Test
import kotlin.test.assertEquals

class BankTest {
    @Test
    fun normalizeSkipsInvalid() {
        val bank = testBank()
        assertEquals(3, bank.questions.size)
        assertEquals(3, bank.skipped)
        assertEquals("Тестовый вопрос 3", bank.questionsById.getValue("3").question)
        assertEquals("", bank.scenariosById.getValue("s2").title)
        assertEquals(listOf("1", "2"), bank.questionsInCategory("a"))
        assertEquals("Раздел Б", bank.categoryTitle("b"))
    }

    @Test
    fun tolerantTypes() {
        val bank = Bank.parse(
            """{"questions":[{"id":"x","question":"q","options":[1,null,true],"correct":1.0,"internal":"yes"}],
               "categories":[{"id":7},{"title":"no id"}],"scenarios":[{"id":1,"situation":5},{"id":2,"situation":"s","answer":"x"}]}""",
        )
        assertEquals(listOf("1", "null", "true"), bank.questions[0].options)
        assertEquals(1, bank.questions[0].correct)
        assertEquals(false, bank.questions[0].internal)
        assertEquals(listOf(Category("7", "7")), bank.categories)
        assertEquals(listOf("2"), bank.scenarios.map { it.id })
        assertEquals(emptyList(), bank.scenarios[0].answer)
    }
}
