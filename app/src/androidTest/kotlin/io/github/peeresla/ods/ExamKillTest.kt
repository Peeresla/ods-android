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
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Экзамен переживает убийство процесса. Запускается двумя отдельными вызовами am instrument:
 * после start() процесс приложения завершается, resume() открывает приложение заново.
 * Заодно проверяется, что пароль второй раз не спрашивается (ключ сохранён).
 */
@RunWith(AndroidJUnit4::class)
class ExamKillTest {
    private val instr = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context = instr.targetContext
    private val dev: UiDevice = UiDevice.getInstance(instr)
    private val screens = File(ctx.filesDir, "screens").apply { mkdirs() }

    @Test
    fun start() {
        File(ctx.filesDir, "state.json").delete()
        ctx.getSharedPreferences("unlock", Context.MODE_PRIVATE).edit().clear().commit()
        val bank = instr.context.assets.open("fixtures/test-bank.enc.json").use { it.readBytes() }
        File(ctx.filesDir, "bank.enc.json").writeBytes(bank)
        BankRepository.urlOverride = "http://127.0.0.1:9/questions.enc.json"
        launch()
        need(By.clazz("android.widget.EditText")).text = "test-pass-123"
        need(By.text("Открыть")).click()
        need(By.textStartsWith("Экзамен"), 90_000).click()
        need(By.text("Начать экзамен")).click()
        need(By.textStartsWith("Вопрос 1 из"))
        Thread.sleep(500)
        need(By.text("Верно")).click()
        Thread.sleep(300)
        need(By.text("Дальше")).click()
        need(By.textStartsWith("Вопрос 2 из"))
        Thread.sleep(500)
        shot("kill-1-before")
    }

    @Test
    fun resume() {
        BankRepository.urlOverride = "http://127.0.0.1:9/questions.enc.json"
        launch()
        assertNull("Пароль спросили повторно", dev.wait(Until.findObject(By.text("Вход")), 2000))
        need(By.textStartsWith("Идёт экзамен"), 30_000)
        Thread.sleep(300)
        shot("kill-2-home")
        need(By.text("Продолжить экзамен")).click()
        need(By.textStartsWith("Вопрос 2 из"))
        need(By.textStartsWith("Отвечено 1 из"))
        Thread.sleep(500)
        shot("kill-3-resumed")
    }

    private fun launch() {
        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ctx.startActivity(intent)
        dev.wait(Until.hasObject(By.pkg(ctx.packageName).depth(0)), 10_000)
        dev.wait(Until.findObject(By.desc("Заставка")), 3000)
        dev.wait(Until.gone(By.desc("Заставка")), 15_000)
    }

    private fun need(sel: BySelector, timeout: Long = 10_000): UiObject2 {
        val o = dev.wait(Until.findObject(sel), timeout)
        if (o == null) shot("kill-FAILED")
        assertNotNull("Не найдено на экране: $sel", o)
        return o!!
    }

    private fun shot(name: String) {
        val bmp: Bitmap = instr.uiAutomation.takeScreenshot() ?: return
        File(screens, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
