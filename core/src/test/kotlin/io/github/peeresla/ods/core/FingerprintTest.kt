package io.github.peeresla.ods.core

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

class FingerprintTest {
    @Test
    fun controlValues() {
        assertEquals("ztntfp", fingerprint(""))
        assertEquals("7aigaz", fingerprint("abc"))
    }

    @Test
    fun sameAsWebVersion() {
        // Отпечатки, которые веб-версия записала в резервную копию для тестового банка
        val bank = testBank()
        assertEquals("mbbf1y", questionFp(bank.questionsById.getValue("1")))
        assertEquals("1y86cwf", questionFp(bank.questionsById.getValue("2")))
        assertEquals("gbehaw", questionFp(bank.questionsById.getValue("3")))
        assertEquals("9cgeuh", scenarioFp(bank.scenariosById.getValue("s1")))
        assertEquals("1o8mkke", scenarioFp(bank.scenariosById.getValue("s2")))
    }

    @Test
    fun utf16CodeUnits() {
        // Символ вне BMP — две UTF-16 единицы, как charCodeAt в JS
        assertEquals(fingerprint("🚒"), fingerprint("🚒"))
    }

    @Test
    fun legacyTables() {
        assertEquals(103, LEGACY_QUESTION_FP.size)
        assertEquals(14, LEGACY_SCENARIO_FP.size)
        assertEquals("1yw355g", LEGACY_QUESTION_FP["1"])
        assertEquals("18qkcde", LEGACY_SCENARIO_FP["s1"])
    }

    /** Настоящий банк 2.0 приватный и в репозиторий не кладётся. Проверка идёт, если указать путь: ODS_BANK_JSON=… */
    @Test
    fun realBankControlValues() {
        val path = System.getenv("ODS_BANK_JSON")
        assumeTrue(path != null && File(path).exists())
        val bank = Bank.parse(File(path!!).readText())
        assertEquals("86cigw", questionFp(bank.questionsById.getValue("1")))
        assertEquals("18qkcde", scenarioFp(bank.scenariosById.getValue("s1")))
    }
}
