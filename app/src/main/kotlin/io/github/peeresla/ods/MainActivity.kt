package io.github.peeresla.ods

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.peeresla.ods.ui.AppRoot
import io.github.peeresla.ods.ui.OdsTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private var reduceMotion by mutableStateOf(false)
    private val startedAt = SystemClock.uptimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Шапка всегда тёмно-синяя — значки строки состояния светлые
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        reduceMotion = animationsOff()
        setContent {
            OdsTheme(reduceMotion = reduceMotion) {
                AppRoot(vm, startedAt)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        reduceMotion = animationsOff()
        vm.onForeground()
    }

    override fun onStop() {
        vm.onBackground()
        super.onStop()
    }

    /** «Удалить анимации» / масштаб анимации 0 в настройках Android. */
    private fun animationsOff(): Boolean =
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
