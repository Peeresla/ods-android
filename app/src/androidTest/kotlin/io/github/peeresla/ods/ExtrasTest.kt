package io.github.peeresla.ods

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.peeresla.ods.core.Activity
import io.github.peeresla.ods.core.AppState
import io.github.peeresla.ods.core.SystemClock
import io.github.peeresla.ods.core.today
import io.github.peeresla.ods.extras.AlarmReceiver
import io.github.peeresla.ods.extras.Scheduler
import io.github.peeresla.ods.extras.StreakWidget
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Этап 2: вечернее напоминание и виджет. Разрешение на уведомления CI выдаёт заранее (pm grant). */
@RunWith(AndroidJUnit4::class)
class ExtrasTest {
    private val instr = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context = instr.targetContext
    private val dev = UiDevice.getInstance(instr)
    private val screens = File(ctx.filesDir, "screens").apply { mkdirs() }
    private val today = SystemClock.today()
    private val yesterday = LocalDate.parse(today).minusDays(1).toString()

    private fun writeState(a: Activity) = File(ctx.filesDir, "state.json").writeText(AppState(activity = a).serialize())

    @Test
    fun reminderWhenGoalNotDone() {
        writeState(Activity(lastDone = yesterday, streak = 5, best = 5, today = today, todayCount = 3))
        ctx.sendBroadcast(Intent(ctx, AlarmReceiver::class.java).setAction(Scheduler.ACTION_REMIND))
        dev.openNotification()
        val n = dev.wait(Until.findObject(By.textContains("Серия 5 дней сгорит в полночь")), 10_000)
        shot("notify-shade")
        assertNotNull("Нет напоминания в шторке", n)
        dev.pressBack()
    }

    @Test
    fun noReminderWhenGoalDone() {
        dev.executeShellCommand("cmd notification cancel_all")
        writeState(Activity(lastDone = today, streak = 6, best = 6, today = today, todayCount = 12))
        ctx.sendBroadcast(Intent(ctx, AlarmReceiver::class.java).setAction(Scheduler.ACTION_REMIND))
        Thread.sleep(2000)
        dev.openNotification()
        val n = dev.wait(Until.findObject(By.text("Начальник ЦУКС")), 3000)
        shot("notify-none")
        dev.pressBack()
        assertNull("Напоминание пришло, хотя норма выполнена", n)
    }

    @Test
    fun widgetLooks() {
        renderWidget("widget-pending", Activity(lastDone = yesterday, streak = 5, best = 5, today = today, todayCount = 3))
        renderWidget("widget-done", Activity(lastDone = today, streak = 6, best = 6, today = today, todayCount = 10))
        renderWidget("widget-zero", Activity())
    }

    private fun renderWidget(name: String, a: Activity) {
        val d = ctx.resources.displayMetrics.density
        instr.runOnMainSync {
            val parent = FrameLayout(ctx)
            val view = StreakWidget.build(ctx, AppState(activity = a)).apply(ctx, parent)
            val w = (300 * d).toInt()
            val h = (80 * d).toInt()
            view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, w, h)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bmp))
            File(screens, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun shot(name: String) {
        val bmp = instr.uiAutomation.takeScreenshot() ?: return
        File(screens, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
