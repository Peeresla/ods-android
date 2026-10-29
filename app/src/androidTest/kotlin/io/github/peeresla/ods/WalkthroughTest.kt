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
        tap(By.text("Открыть"))
        need(By.text("Неверный пароль"), 90_000)
        shot("unlock-wrong")

        need(By.text("Открыть"), 90_000)
        Thread.sleep(500)
        need(By.clazz("android.widget.EditText")).text = "test-pass-123"
        tap(By.text("Открыть"))
        need(By.textStartsWith("Тренировка"), 90_000)
        Thread.sleep(600)
        shot("home-first")

        // Тренировка: каждый вопрос открывается, правильный ответ засчитывается после перемешивания
        tap(By.textStartsWith("Тренировка"))
        need(By.text("отвечено подряд"))
        Thread.sleep(500)
        shot("train")
        repeat(6) { i ->
            tap(By.text("Верно"), scroll = true)
            need(By.text("Дальше"))
            Thread.sleep(500)
            if (i == 0) shot("train-correct")
            tap(By.text("Дальше"))
            Thread.sleep(600)
        }
        tap(By.textStartsWith("Неверно"), scroll = true)
        need(By.text("Дальше"))
        Thread.sleep(500)
        shot("train-wrong")

        // Выход из тренировки — окно в стиле приложения
        dev.pressBack()
        need(By.text("Выйти из тренировки?"))
        Thread.sleep(400)
        shot("leave-modal")
        tap(By.text("Остаться"))
        Thread.sleep(400)
        dev.pressBack()
        tap(By.text("Выйти"))
        need(By.text("Ответов дано: 7"))
        Thread.sleep(500)
        shot("home-after-train")

        // Экзамен
        tap(By.textStartsWith("Экзамен"))
        need(By.text("Начать экзамен"))
        Thread.sleep(500)
        shot("exam-intro")
        tap(By.text("Начать экзамен"))
        need(By.textStartsWith("Вопрос 1 из"))
        Thread.sleep(600)
        shot("exam-question")
        repeat(3) { i ->
            tap(By.text("Верно"), scroll = true)
            Thread.sleep(400)
            if (i < 2) {
                tap(By.text("Дальше"))
                Thread.sleep(600)
            }
        }
        shot("exam-last")
        tap(By.text("Завершить"))
        need(By.text("Сдано"))
        Thread.sleep(600)
        shot("exam-result")
        tap(By.text("На главную"), scroll = true)
        need(By.text("Тренажёр МЧС"))
        Thread.sleep(800)
        Thread.sleep(600)

        // Ошибки
        tap(By.textStartsWith("Ошибки"), scroll = true)
        need(By.textStartsWith("Повторить ошибки"))
        Thread.sleep(500)
        shot("mistakes")
        dev.pressBack()
        Thread.sleep(600)

        // Ситуации
        tap(By.textStartsWith("Ситуации"), scroll = true)
        need(By.textStartsWith("Тестовая ситуация"))
        Thread.sleep(500)
        shot("scenarios")
        tap(By.textStartsWith("Тестовая ситуация"))
        Thread.sleep(800)
        shot("scenario-open")
        need(By.text("Показать эталон"))
        need(By.clazz("android.widget.EditText")).text = "Доложить, уточнить обстановку"
        Thread.sleep(500)
        tap(By.text("Показать эталон"), scroll = true)
        need(By.text("Эталон"))
        Thread.sleep(700)
        shot("scenario-reference")
        tap(By.text("Частично"), scroll = true)
        Thread.sleep(800)
        shot("scenario-graded")
        dev.pressBack()
        Thread.sleep(500)
        dev.pressBack()
        Thread.sleep(600)

        // По документам
        tap(By.textStartsWith("По документам"), scroll = true)
        need(By.textStartsWith("Раздел А"))
        Thread.sleep(500)
        shot("docs")
        dev.pressBack()
        Thread.sleep(600)

        // Настройки
        tap(By.desc("Настройки"))
        need(By.text("Порог «сдано»"))
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
    private fun tap(sel: BySelector, timeout: Long = 10_000, scroll: Boolean = false) {
        repeat(8) {
            try {
                // Нажатие по координатам: узел мог перерисоваться между поиском и нажатием
                val c = (if (scroll) find(sel) else need(sel, timeout)).visibleCenter
                dev.click(c.x, c.y)
                return
            } catch (e: androidx.test.uiautomator.StaleObjectException) {
                Thread.sleep(500)
            }
        }
        val c = (if (scroll) find(sel) else need(sel, timeout)).visibleCenter
        dev.click(c.x, c.y)
    }

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
