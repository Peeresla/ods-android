package io.github.peeresla.ods.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max

/** Даже на быстром тапе элемент успевает плавно «просесть» (MIN_HOLD_MS в app.js). */
const val MIN_HOLD_MS = 130L

/** Волна из точки касания. */
class Ripple(val center: Offset, val radius: Float) {
    val progress = Animatable(0f)
    val fade = Animatable(1f)
}

/**
 * Состояние нажатия. Одно непрерывное движение, как в iOS: пока палец на элементе, он плавно «проседает»
 * до 0,97 и чуть темнеет; отпустили — мягко пружинит обратно с лёгким перелётом. Анимация не перезапускается,
 * а продолжается от текущего значения — иначе был бы рывок.
 */
@Stable
class PressState {
    val scale: Animatable<Float, AnimationVector1D> = Animatable(1f)
    val held: Animatable<Float, AnimationVector1D> = Animatable(0f)
    val ripples = mutableStateListOf<Ripple>()
    var rippleColor: Color = Color.Black
}

@Composable
fun rememberPressState(): PressState = remember { PressState() }

/**
 * Нажатие с откликом. [delayed] — действие выполняется, когда нажатие успело проиграться (переходы между экранами);
 * иначе — сразу при отпускании. [ripple] — волна (кнопки и варианты); у плиток и карточек её нет.
 */
fun Modifier.pressable(
    state: PressState,
    scope: CoroutineScope,
    enabled: Boolean,
    reduceMotion: Boolean,
    ripple: Boolean,
    delayed: Boolean,
    shape: Shape,
    action: () -> Unit,
    scaleTo: Float = 0.97f,
): Modifier = this
    .graphicsLayer {
        val s = state.scale.value
        scaleX = s
        scaleY = s
    }
    .drawWithContent {
        drawContent()
        if (state.ripples.isEmpty()) return@drawWithContent
        val outline = shape.createOutline(size, layoutDirection, this)
        val clip = Path().apply { addOutline(outline) }
        clipPath(clip) {
            for (r in state.ripples) {
                val p = r.progress.value
                // 0 % → 0,22; 60 % → 0,14; 100 % → 0
                val alpha = (if (p < 0.6f) 0.22f - 0.08f * (p / 0.6f) else 0.14f * (1f - (p - 0.6f) / 0.4f)) * r.fade.value
                drawCircle(state.rippleColor, radius = r.radius * p, center = r.center, alpha = alpha.coerceIn(0f, 1f))
            }
        }
    }
    .semantics(mergeDescendants = true) {
        role = Role.Button
        if (!enabled) disabled()
        onClick { if (enabled) action(); enabled }
    }
    .pointerInput(enabled, reduceMotion, ripple, delayed) {
        if (!enabled) return@pointerInput
        detectTapGestures(onPress = { offset ->
            val pressAt = SystemClock.uptimeMillis()
            var rip: Ripple? = null
            if (reduceMotion) {
                state.scale.snapTo(scaleTo)
                state.held.snapTo(1f)
            } else {
                scope.launch { state.scale.animateTo(scaleTo, tween(200, easing = Easings.Out)) }
                scope.launch { state.held.animateTo(1f, tween(120)) }
                if (ripple) {
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val r = Ripple(offset, hypot(max(offset.x, w - offset.x), max(offset.y, h - offset.y)))
                    rip = r
                    state.ripples.add(r)
                    scope.launch {
                        r.progress.animateTo(1f, tween(500, easing = Easings.Out))
                        state.ripples.remove(r)
                    }
                }
            }
            val released = tryAwaitRelease()
            if (!released) {
                // Палец пошёл прокручивать страницу — это не нажатие: отпускаем сразу и гасим волну
                rip?.let { r -> scope.launch { r.fade.animateTo(0f, tween(150)); state.ripples.remove(r) } }
                releasePress(state, scope, reduceMotion, 0)
                return@detectTapGestures
            }
            val wait = if (reduceMotion) 0L else max(0L, pressAt + MIN_HOLD_MS - SystemClock.uptimeMillis())
            if (delayed && wait > 0) {
                scope.launch {
                    delay(wait)
                    action()
                }
            } else {
                action()
            }
            releasePress(state, scope, reduceMotion, wait)
        })
    }

private fun releasePress(state: PressState, scope: CoroutineScope, reduceMotion: Boolean, wait: Long) {
    scope.launch {
        if (reduceMotion) {
            state.scale.snapTo(1f)
            state.held.snapTo(0f)
            return@launch
        }
        if (wait > 0) delay(wait)
        launch { state.held.animateTo(0f, tween(300)) }
        state.scale.animateTo(1f, tween(400, easing = Easings.Spring))
    }
}

/** Фон, который темнеет при удержании (без перерисовки содержимого). */
fun Modifier.pressBackground(state: PressState, base: Color, held: Color, shape: Shape, rippleColor: Color): Modifier {
    state.rippleColor = rippleColor
    return this.drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(outline, lerp(base, held, state.held.value))
    }
}

/** Удобная обёртка: состояние, область корутин и отклик в одном вызове. */
@Composable
fun Modifier.pressFeedback(
    enabled: Boolean = true,
    ripple: Boolean,
    delayed: Boolean,
    shape: Shape,
    base: Color,
    held: Color,
    rippleColor: Color,
    onClick: () -> Unit,
    state: PressState = rememberPressState(),
): Modifier {
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current
    val click by rememberUpdatedState(onClick)
    return this
        .pressable(state, scope, enabled, reduce, ripple && !reduce, delayed, shape, { click() })
        .pressBackground(state, base, held, shape, rippleColor)
}
