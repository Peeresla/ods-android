package io.github.peeresla.ods.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.peeresla.ods.core.MAX_MISSED_SHADE

/** Цвета — токены из :root в styles.css веб-версии. */
@Immutable
data class OdsColors(
    val dark: Boolean,
    val navy: Color = Color(0xFF12305E),
    val accent: Color,
    val onAccent: Color = Color(0xFF10203F),
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    val ok: Color,
    val okBg: Color,
    val bad: Color,
    val badBg: Color,
    val accentBg: Color,
    val headerBg: Color,
    val headerText: Color = Color.White,
    val icon: Color,
    val iconBg: Color,
    val shadow: Boolean,
) {
    /** Цвет карточки серии по числу пропущенных дней: 0 — зелёный (hue 140) … 10 и больше — красный (hue 0). */
    fun statusHue(missed: Int): Float = 140f - 14f * Math.min(missed, MAX_MISSED_SHADE)

    fun status(missed: Int): Color =
        if (dark) Color.hsl(statusHue(missed), 0.70f, 0.60f) else Color.hsl(statusHue(missed), 0.72f, 0.36f)

    fun statusBg(missed: Int): Color =
        if (dark) Color.hsl(statusHue(missed), 0.70f, 0.55f, alpha = 0.14f) else Color.hsl(statusHue(missed), 0.75f, 0.45f, alpha = 0.12f)
}

val LightColors = OdsColors(
    dark = false,
    accent = Color(0xFFF07A1A),
    bg = Color(0xFFEEF1F6),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFE4E9F2),
    text = Color(0xFF0F1B33),
    muted = Color(0xFF5A6680),
    border = Color(0xFFD3DAE6),
    ok = Color(0xFF1D7F46),
    okBg = Color(0xFFE2F3E9),
    bad = Color(0xFFC0292B),
    badBg = Color(0xFFFBE4E4),
    accentBg = Color(0xFFFFF0E2),
    headerBg = Color(0xFF12305E),
    icon = Color(0xFF12305E),
    iconBg = Color(0xFFE8EEF8),
    shadow = true,
)

val DarkColors = OdsColors(
    dark = true,
    accent = Color(0xFFFF8A2A),
    bg = Color(0xFF0A1325),
    surface = Color(0xFF13213B),
    surface2 = Color(0xFF1D2E4D),
    text = Color(0xFFE9EEF7),
    muted = Color(0xFF9AA8C0),
    border = Color(0xFF273A5C),
    ok = Color(0xFF46D184),
    okBg = Color(0x2446D184),
    bad = Color(0xFFFF6B6B),
    badBg = Color(0x24FF6B6B),
    accentBg = Color(0x24FF8A2A),
    headerBg = Color(0xFF0C1A33),
    icon = Color(0xFF9CBCF2),
    iconBg = Color(0xFF1A2C4B),
    shadow = false,
)

val LocalOdsColors = staticCompositionLocalOf { LightColors }

/** Системное «Удалить анимации» (масштаб анимации 0) — анимации отключаются, заставка не задерживает запуск. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Плавные кривые из styles.css */
object Easings {
    val Out = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    val Spring = CubicBezierEasing(0.34f, 1.4f, 0.64f, 1f)
    val Turn = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)
    val Fill = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)
    val Progress = CubicBezierEasing(0.4f, 0f, 0.3f, 1f)
    val Modal = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.15f)
    val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
    val EaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
    val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    val Ease = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
}

val BaseText = TextStyle(fontFamily = FontFamily.Default, fontSize = 17.sp, lineHeight = 1.45.em)

@Composable
fun OdsTheme(reduceMotion: Boolean, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(primary = colors.accent, background = colors.bg, surface = colors.surface, onSurface = colors.text, onBackground = colors.text)
    } else {
        lightColorScheme(primary = colors.accent, background = colors.bg, surface = colors.surface, onSurface = colors.text, onBackground = colors.text)
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalOdsColors provides colors, LocalReduceMotion provides reduceMotion, content = content)
    }
}
