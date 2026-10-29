package io.github.peeresla.ods

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import io.github.peeresla.ods.data.BankRepository
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Сквозная проверка как у пользователя: вход по паролю, тренировка, экзамен, ситуации, настройки.
 * Банк — тестовый (зашифрован tools/build.mjs паролем test-pass-123), сеть недоступна — проверяется работа офлайн из кэша.
 * Скриншоты складываются в files/screens, CI забирает их через run-as.
 */
@RunWith(AndroidJUnit4::class)
class WalkthroughTest {
    private val instr = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context = instr.targetContext
    private val dev: UiDevice = UiDevice.getInstance(instr)
    private val tag: String = InstrumentationRegistry.getArguments().getString("tag") ?: "run"
    private val screens = File(ctx.filesDir, "screens").apply { mkdirs() }
    private var shotNo = 0

    @Before
    fun prepare() {
        File(ctx.filesDir, "state.json").delete()
        ctx.getSharedPreferences("unlock", Context.MODE_PRIVATE).edit().clear().commit()
        val bank = instr.context.assets.open("fixtures/test-bank.enc.json").use { it.readBytes() }
        File(ctx.filesDir, "bank.enc.json").writeBytes(bank)
        BankRepository.urlOverride = "http://127.0.0.1:9/questions.enc.json" // сети нет — работаем из кэша
    }

    @Test
    fun walkthrough() {
        launch()
        if (tag == "light") {
            Thread.sleep(1200); shot("splash-1")
            Thread.sleep(1500); shot("splash-2")
        }
        need(By.text("Вход"), 20_000)
        dev.wait(Until.gone(By.desc("Заставка")), 15_000)
        Thread.sleep(300)
        shot("unlock")

        // Неверный пароль
        val field = need(By.clazz("android.widget.EditText"))
        field.text = "wrong-password"
        tap(Sel.text("Открыть")) // расшифровка долгая — без повторного нажатия
        need(By.text("Неверный пароль"), 90_000)
        shot("unlock-wrong")

        need(By.text("Открыть"), 90_000)
        Thread.sleep(500)
        need(By.clazz("android.widget.EditText")).text = "test-pass-123"
        tap(Sel.text("Открыть"))
        need(By.textStartsWith("Тренировка"), 90_000)
        Thread.sleep(600)
        shot("home-first")

        // Тренировка: каждый вопрос открывается, правильный ответ засчитывается после перемешивания
        tap(Sel.starts("Тренировка"), expect = By.text("отвечено подряд"))
        Thread.sleep(500)
        shot("train")
        repeat(6) { i ->
            answer(Sel.text("Верно"))
            Thread.sleep(500)
            if (i == 0) shot("train-correct")
            tap(Sel.text("Дальше"))
            Thread.sleep(600)
        }
        answer(Sel.starts("Неверно"))
        Thread.sleep(500)
        shot("train-wrong")

        // Выход из тренировки — окно в стиле приложения
        dev.pressBack()
        need(By.text("Выйти из тренировки?"))
        Thread.sleep(400)
        shot("leave-modal")
        tap(Sel.text("Остаться"))
        Thread.sleep(400)
        dev.pressBack()
        tap(Sel.text("Выйти"), expect = By.text("Ответов дано: 7"))
        Thread.sleep(500)
        shot("home-after-train")

        // Экзамен
        tap(Sel.starts("Экзамен"), expect = By.text("Начать экзамен"))
        Thread.sleep(500)
        shot("exam-intro")
        tap(Sel.text("Начать экзамен"), expect = By.textStartsWith("Вопрос 1 из"))
        Thread.sleep(600)
        shot("exam-question")
        repeat(3) { i ->
            tap(Sel.text("Верно"), scroll = true)
            Thread.sleep(400)
            if (i < 2) {
                tap(Sel.text("Дальше"))
                Thread.sleep(600)
            }
        }
        shot("exam-last")
        tap(Sel.text("Завершить"), expect = By.text("Сдано"))
        Thread.sleep(600)
        shot("exam-result")
        tap(Sel.text("На главную"), scroll = true, expect = By.text("Тренажёр МЧС"))
        Thread.sleep(800)
        Thread.sleep(600)

        // Ошибки
        tap(Sel.starts("Ошибки"), scroll = true, expect = By.textStartsWith("Повторить ошибки"))
        Thread.sleep(500)
        shot("mistakes")
        dev.pressBack()
        Thread.sleep(600)

        // Ситуации
        tap(Sel.starts("Ситуации"), scroll = true, expect = By.textStartsWith("Тестовая ситуация"))
        Thread.sleep(500)
        shot("scenarios")
        tap(Sel.starts("Тестовая ситуация"))
        Thread.sleep(800)
        shot("scenario-open")
        need(By.text("Показать эталон"))
        need(By.clazz("android.widget.EditText")).text = "Доложить, уточнить обстановку"
        Thread.sleep(500)
        tap(Sel.text("Показать эталон"), scroll = true, expect = By.text("Эталон"))
        Thread.sleep(700)
        shot("scenario-reference")
        tap(Sel.text("Частично"), scroll = true)
        Thread.sleep(800)
        shot("scenario-graded")
        dev.pressBack()
        Thread.sleep(500)
        dev.pressBack()
        Thread.sleep(600)

        // По документам
        tap(Sel.starts("По документам"), scroll = true, expect = By.textStartsWith("Раздел А"))
        Thread.sleep(500)
        shot("docs")
        dev.pressBack()
        Thread.sleep(600)

        // Настройки
        tap(Sel.desc("Настройки"), expect = By.text("Порог «сдано»"))
        Thread.sleep(500)
        shot("settings")
        scrollDown()
        shot("settings-2")
        dev.pressBack()
        need(By.text("Норма выполнена"))
        Thread.sleep(600)
        shot("home-final")
        saveFrameStats()
    }

    /** Статистика кадров за весь проход (Janky frames, перцентили времени кадра). */
    private fun saveFrameStats() {
        val pfd = instr.uiAutomation.executeShellCommand("dumpsys gfxinfo ${ctx.packageName}")
        val text = android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { String(it.readBytes()) }
        File(screens, "$tag-gfxinfo.txt").writeText(text)
    }

    private fun launch() {
        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ctx.startActivity(intent)
        dev.wait(Until.hasObject(By.pkg(ctx.packageName).depth(0)), 10_000)
    }

    private fun need(sel: BySelector, timeout: Long = 10_000): UiObject2 {
        val o = dev.wait(Until.findObject(sel), timeout)
        if (o == null) {
            shot("FAILED")
            File(screens, "$tag-FAILED-hierarchy.xml").outputStream().use { dev.dumpWindowHierarchy(it) }
        }
        assertNotNull("Не найдено на экране: $sel", o)
        return o!!
    }

    /** Найти, при необходимости прокручивая вниз. */
    private fun find(sel: BySelector): UiObject2 {
        repeat(6) {
            dev.wait(Until.findObject(sel), 1500)?.let { return it }
            scrollDown()
        }
        return need(sel, 2000)
    }

    /** Нажать; элемент мог перерисоваться между поиском и нажатием — тогда ищем снова. */
    /** Селектор: для ожидания (BySelector) и для поиска в снимке дерева элементов. */
    private class Sel(val by: BySelector, val attr: String, val value: String, val prefix: Boolean) {
        override fun toString() = by.toString()

        fun matches(node: Map<String, String>): Boolean {
            val v = node[attr] ?: return false
            return if (prefix) v.startsWith(value) else v == value
        }

        companion object {
            fun text(t: String) = Sel(By.text(t), "text", t, false)
            fun starts(t: String) = Sel(By.textStartsWith(t), "text", t, true)
            fun desc(t: String) = Sel(By.desc(t), "content-desc", t, false)
        }
    }

    /**
     * Нажатие по координатам из снимка дерева элементов (dumpWindowHierarchy). Снимок не «устаревает»,
     * в отличие от UiObject2, поэтому перерисовка экрана между поиском и нажатием не мешает.
     */
    private fun tap(sel: Sel, timeout: Long = 10_000, scroll: Boolean = false, expect: BySelector? = null) {
        // Нажатие могло прийтись на смену экрана — если ожидаемое не появилось, нажимаем ещё раз
        repeat(3) {
            tapOnce(sel, timeout, scroll)
            if (expect == null || dev.wait(Until.hasObject(expect), 6000)) return
        }
        if (expect != null) need(expect, 20_000)
    }

    private fun tapOnce(sel: Sel, timeout: Long, scroll: Boolean) {
        if (scroll) find(sel.by) else need(sel.by, timeout)
        repeat(6) {
            val bounds = snapshotBounds(sel)
            if (bounds != null) {
                dev.click(bounds.centerX(), bounds.centerY())
                return
            }
            Thread.sleep(400)
        }
        throw AssertionError("Не удалось нажать: $sel")
    }

    /** Ответить в тренировке: нажатие могло прийтись на уходящий экран — тогда нажимаем ещё раз. */
    private fun answer(sel: Sel) {
        repeat(3) {
            tap(sel, scroll = true)
            if (dev.wait(Until.hasObject(By.text("Дальше")), 3000)) return
        }
        need(By.text("Дальше"))
    }

    private fun snapshotBounds(sel: Sel): android.graphics.Rect? {
        val out = java.io.ByteArrayOutputStream()
        dev.dumpWindowHierarchy(out)
        val xml = out.toString("UTF-8")
        for (m in Regex("<node ([^>]*)>").findAll(xml)) {
            val attrs = Regex("([a-z-]+)=\"([^\"]*)\"").findAll(m.groupValues[1]).associate { it.groupValues[1] to unescape(it.groupValues[2]) }
            if (attrs["package"] != ctx.packageName || !sel.matches(attrs)) continue
            val b = Regex("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]").find(attrs["bounds"] ?: continue) ?: continue
            val (l, t, r, btm) = b.destructured
            val rect = android.graphics.Rect(l.toInt(), t.toInt(), r.toInt(), btm.toInt())
            if (rect.width() > 0 && rect.height() > 0) return rect
        }
        return null
    }

    private fun unescape(v: String) =
        v.replace("&#10;", "\n").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&apos;", "'").replace("&amp;", "&")

    private var scrolls = 0

    private fun scrollDown() {
        val list = dev.findObject(By.scrollable(true))
        android.util.Log.i("WalkthroughTest", "scroll: display ${dev.displayWidth}x${dev.displayHeight}, list ${list?.visibleBounds}")
        if (list != null) {
            try {
                val moved = list.scroll(androidx.test.uiautomator.Direction.DOWN, 0.6f)
                Thread.sleep(400)
                if (++scrolls <= 3) shot("scroll-$moved")
                return
            } catch (e: androidx.test.uiautomator.StaleObjectException) {
                // ниже — запасной вариант
            }
        }
        val w = dev.displayWidth
        val h = dev.displayHeight
        dev.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 25)
        Thread.sleep(500)
    }

    private fun shot(name: String) {
        val bmp: Bitmap = instr.uiAutomation.takeScreenshot() ?: return
        val file = File(screens, "%s-%02d-%s.png".format(tag, ++shotNo, name))
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
