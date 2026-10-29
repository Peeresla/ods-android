package io.github.peeresla.ods.core

import org.junit.Test
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class BackupTest {
    private val bank = testBank()

    @Test
    fun importsBackupFromWebVersion() {
        val parsed = assertIs<Backup.Parsed.Ok>(Backup.parse(fixture("web-backup.json")))
        val st = migrateToBank(parsed.state, bank)
        assertEquals(setOf("1", "2", "3"), st.progress.keys)
        assertEquals(3, st.progress.getValue("1").shown)
        assertEquals(1793241856611, st.progress.getValue("1").last)
        assertEquals("partial", st.scenarioOf("s1").attempts.single().grade)
        assertEquals("Мой ответ\nвторая строка", st.scenarioOf("s1").attempts.single().text)
        assertEquals("черновик", st.scenarioOf("s2").draft)
        assertEquals(3, st.lastExam!!.total)
        assertNull(st.lastExam!!.items[2].chosen)
        assertEquals(Activity("2026-10-29", 1, 1, "2026-10-29", 16), st.activity)
        assertEquals("29.10.2026, 05:44", formatDateTime(parsed.exportedAt!!, ZoneId.of("Europe/Moscow")))
        assertEquals(
            "Восстановить прогресс из копии от 29.10.2026, 05:44? Текущий прогресс будет заменён.",
            Backup.confirmText(parsed.exportedAt, ZoneId.of("Europe/Moscow")),
        )
    }

    @Test
    fun exportThenImportRestores() {
        val st0 = migrateToBank((Backup.parse(fixture("web-backup.json")) as Backup.Parsed.Ok).state, bank)
        val withExam = startExam(st0, bank, 5, kotlin.random.Random(1))
        val text = Backup.export(withExam, bank.version, 1_800_000_000_000)
        val back = (Backup.parse(text) as Backup.Parsed.Ok).state
        assertEquals(st0, back) // экзамен в копию не попадает
        // Формат, как в веб-версии
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(text).obj()!!
        assertEquals("ods-trainer", obj["app"].str())
        assertEquals("test-1", obj["bankVersion"].str())
        assertEquals("2027-01-15T08:00:00.000Z", obj["exportedAt"].str())
        assertEquals(setOf("schema", "progress", "scenarios", "settings", "exam", "lastExam", "activity", "records", "scenarioArchive"), obj["state"].obj()!!.keys)
    }

    @Test
    fun resetKeepsSettings() {
        val st = AppState(settings = Settings(15), correctRunRecord = 7, progress = mapOf("1" to QProgress(shown = 1)))
        assertEquals(AppState(settings = Settings(15)), resetProgress(st))
    }

    @Test
    fun rejectsOtherFiles() {
        assertEquals(Backup.Parsed.Error("Не удалось прочитать файл"), Backup.parse("{oops"))
        assertEquals(Backup.Parsed.Error("Это не резервная копия тренажёра"), Backup.parse("{\"app\":\"x\",\"state\":{}}"))
    }

    @Test
    fun tolerantState() {
        val st = AppState.normalize(
            kotlinx.serialization.json.Json.parseToJsonElement(
                """{"progress":{"1":{"shown":"3","correct":-1,"mistake":1,"extra":true},"2":5},
                   "settings":{"passThreshold":99},"exam":{"startedAt":"x"},"activity":{"lastDone":"bad","streak":2.7},
                   "scenarios":{"s1":{"attempts":[{"date":"d","grade":"ok"},{"date":"d","grade":"meh"}]}},"unknown":1}""",
            ),
        )
        assertEquals(QProgress(shown = 3, correct = 0, mistake = true), st.progress["1"])
        assertEquals(1, st.progress.size)
        assertEquals(20, st.settings.passThreshold)
        assertNull(st.exam)
        assertEquals(Activity(null, 2, 0, null, 0), st.activity)
        assertEquals(listOf(Attempt("d", "ok", "")), st.scenarioOf("s1").attempts)
    }

    @Test
    fun fileName() {
        assertEquals("ods-trainer-backup-2026-10-29.json", Backup.fileName(1_793_241_856_611, ZoneId.of("Europe/Moscow")))
    }
}
