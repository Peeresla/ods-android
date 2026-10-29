package io.github.peeresla.ods.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import io.github.peeresla.ods.AppViewModel

enum class Haptic { Correct, Wrong, Tap }

/**
 * Виброотклик при ответе: верно — короткое «подтверждение», неверно — «отказ», выбор в экзамене — лёгкий щелчок.
 * Системная настройка «Виброотклик при касании» соблюдается: если она выключена, вибрации нет.
 */
@Composable
fun rememberHaptics(vm: AppViewModel): (Haptic) -> Unit {
    val view = LocalView.current
    val extras by vm.extras.collectAsState()
    val on = extras.haptics
    return { kind ->
        if (on) {
            val constant = when (kind) {
                Haptic.Correct -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
                Haptic.Wrong -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
                Haptic.Tap -> HapticFeedbackConstants.KEYBOARD_TAP
            }
            view.performHapticFeedback(constant)
        }
    }
}
