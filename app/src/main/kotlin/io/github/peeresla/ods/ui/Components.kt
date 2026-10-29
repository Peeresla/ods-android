package io.github.peeresla.ods.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text as M3Text

val Radius = 14.dp
val CardShape = RoundedCornerShape(Radius)
val BtnShape = RoundedCornerShape(12.dp)
val PillShape = RoundedCornerShape(999.dp)

@Composable
fun T(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 17.sp,
    color: Color = LocalOdsColors.current.text,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign? = null,
    lineHeight: TextUnit = 1.45.em,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = BaseText,
) {
    M3Text(
        text = text,
        modifier = modifier,
        style = style.copy(fontSize = size, color = color, fontWeight = weight, lineHeight = lineHeight, textAlign = align ?: TextAlign.Unspecified),
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}

/** Тень как --shadow в светлой теме; в тёмной её нет. */
fun Modifier.cardShadow(colors: OdsColors, shape: Shape = CardShape): Modifier =
    if (colors.shadow) this.shadow(3.dp, shape, ambientColor = Color(0x1A0F1B33), spotColor = Color(0x260F1B33)) else this

@Composable
fun OdsCard(
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    topAccent: Color? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    gap: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalOdsColors.current
    Column(
        modifier
            .fillMaxWidth()
            .cardShadow(c)
            .clip(CardShape)
            .background(c.surface)
            .border(1.dp, borderColor ?: c.border, CardShape)
            .topStripe(topAccent, 4.dp)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/** Полоса сверху карточки (border-top: 4px solid). */
fun Modifier.topStripe(color: Color?, width: Dp): Modifier =
    if (color == null) this else this.drawBehind { drawRect(color, size = size.copy(height = width.toPx())) }.padding(top = width)

/** Полоса слева (border-left). */
fun Modifier.leftStripe(color: Color?, width: Dp): Modifier =
    if (color == null) this else this.drawBehind { drawRect(color, size = size.copy(width = width.toPx())) }.padding(start = width)

/** Карточка-ссылка: спокойный отклик как у ячеек iOS — без волны, лёгкое затемнение. */
@Composable
fun PressCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gap: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalOdsColors.current
    Column(
        modifier
            .fillMaxWidth()
            .cardShadow(c)
            .pressFeedback(ripple = false, delayed = true, shape = CardShape, base = c.surface, held = c.surface2, rippleColor = c.text, onClick = onClick)
            .border(1.dp, c.border, CardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

enum class BtnKind { Default, Primary, Danger, GradeOk, GradePartial, GradeFail }

@Composable
fun OdsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.Default,
    enabled: Boolean = true,
    block: Boolean = false,
    delayed: Boolean = false,
    fontSize: TextUnit = 17.sp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
) {
    val c = LocalOdsColors.current
    val (bg, fg, border) = when (kind) {
        BtnKind.Default -> Triple(c.surface, c.text, c.border)
        BtnKind.Primary -> Triple(c.accent, c.onAccent, c.accent)
        BtnKind.Danger -> Triple(c.surface, c.bad, c.bad)
        BtnKind.GradeOk -> Triple(c.surface, c.ok, c.ok)
        BtnKind.GradePartial -> Triple(c.surface, c.accent, c.accent)
        BtnKind.GradeFail -> Triple(c.surface, c.bad, c.bad)
    }
    val held = if (kind == BtnKind.Primary) lerp(bg, Color.Black, 0.08f) else c.surface2
    Box(
        modifier
            .then(if (block) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .pressFeedback(
                enabled = enabled,
                ripple = true,
                delayed = delayed,
                shape = BtnShape,
                base = bg,
                held = held,
                rippleColor = if (kind == BtnKind.Primary) Color.White else fg,
                onClick = onClick,
            )
            .border(1.5.dp, border, BtnShape)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        T(text, size = fontSize, color = fg, weight = FontWeight.SemiBold, align = TextAlign.Center, lineHeight = 1.3.em)
    }
}

enum class OptionLook { Normal, Selected, Correct, Wrong }

/**
 * Вариант ответа. [animateKey] меняется, когда вариант только что выбран: правильный «подпрыгивает»,
 * неверный покачивается.
 */
@Composable
fun OptionButton(
    text: String,
    look: OptionLook,
    enabled: Boolean,
    animateKey: Any?,
    onClick: () -> Unit,
) {
    val c = LocalOdsColors.current
    val reduce = LocalReduceMotion.current
    val pop = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(animateKey) {
        if (animateKey == null || reduce) return@LaunchedEffect
        when (look) {
            OptionLook.Correct, OptionLook.Selected -> pop.animateTo(1f, keyframes {
                durationMillis = 350
                0.97f at 0 using Easings.Out
                1.02f at 175 using Easings.Out
                1f at 350
            })
            OptionLook.Wrong -> shake.animateTo(0f, keyframes {
                durationMillis = 400
                0f at 0 using Easings.EaseInOut
                -6f at 80 using Easings.EaseInOut
                5f at 160 using Easings.EaseInOut
                -3f at 240 using Easings.EaseInOut
                2f at 320 using Easings.EaseInOut
                0f at 400
            })
            OptionLook.Normal -> Unit
        }
    }
    val (bg, border) = when (look) {
        OptionLook.Normal -> c.surface to c.border
        OptionLook.Selected -> c.accentBg to c.accent
        OptionLook.Correct -> c.okBg to c.ok
        OptionLook.Wrong -> c.badBg to c.bad
    }
    // Полупрозрачный фон выбранного варианта кладём поверх поверхности
    val base = if (look == OptionLook.Normal) c.surface else lerpOver(c.surface, bg)
    val held = if (look == OptionLook.Selected) base else c.surface2
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                translationX = shake.value * density
            }
            .heightIn(min = 52.dp)
            .pressFeedback(enabled = enabled, ripple = true, delayed = false, shape = BtnShape, base = base, held = held, rippleColor = c.text, onClick = onClick)
            .semantics {
                when (look) {
                    OptionLook.Correct -> stateDescription = "правильный ответ"
                    OptionLook.Wrong -> stateDescription = "неверный ответ"
                    OptionLook.Selected -> stateDescription = "выбрано"
                    OptionLook.Normal -> Unit
                }
            }
            .border(1.5.dp, border, BtnShape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val mark = when (look) {
            OptionLook.Correct -> "✓ " to c.ok
            OptionLook.Wrong -> "✗ " to c.bad
            else -> null
        }
        M3Text(
            text = buildAnnotatedString {
                if (mark != null) withStyle(SpanStyle(color = mark.second, fontWeight = FontWeight.Bold)) { append(mark.first) }
                append(text)
            },
            style = BaseText.copy(fontSize = 16.sp, lineHeight = 1.4.em, color = c.text),
        )
    }
}

/** Цвет с прозрачностью, наложенный на непрозрачный фон. */
fun lerpOver(base: Color, top: Color): Color = lerp(base, top.copy(alpha = 1f), top.alpha)

@Composable
fun ProgressBar(value: Int, max: Int, modifier: Modifier = Modifier) {
    val c = LocalOdsColors.current
    val f = if (max <= 0) 0f else (value.toFloat() / max).coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(c.surface2)
            .drawBehind {
                if (f > 0f) drawRoundRect(c.accent, size = size.copy(width = size.width * f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            },
    )
}

@Composable
fun Tag(text: String, internal: Boolean = false, modifier: Modifier = Modifier) {
    val c = LocalOdsColors.current
    Box(
        modifier
            .clip(PillShape)
            .background(if (internal) c.accentBg else c.surface2)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        T(text, size = 13.sp, color = if (internal) c.accent else c.text, weight = if (internal) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, lineHeight = 1.45.em)
    }
}

@Composable
fun InternalBadge(modifier: Modifier = Modifier) = Tag("Внутренний документ ГУ", internal = true, modifier = modifier)

enum class BadgeKind { Accent, Muted, GradeOk, GradePartial, GradeFail }

fun badgeKindForGrade(grade: String): BadgeKind = when (grade) {
    "ok" -> BadgeKind.GradeOk
    "partial" -> BadgeKind.GradePartial
    else -> BadgeKind.GradeFail
}

@Composable
fun Badge(text: String, kind: BadgeKind, modifier: Modifier = Modifier) {
    val c = LocalOdsColors.current
    val (bg, fg) = when (kind) {
        BadgeKind.Accent -> c.accent to c.onAccent
        BadgeKind.Muted -> c.surface2 to c.muted
        BadgeKind.GradeOk -> c.ok to Color.White
        BadgeKind.GradePartial -> c.accent to c.onAccent
        BadgeKind.GradeFail -> c.bad to Color.White
    }
    Box(
        modifier
            .widthIn(min = 28.dp)
            .clip(PillShape)
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        T(text, size = 14.sp, color = fg, weight = FontWeight.SemiBold, align = TextAlign.Center, lineHeight = 1.3.em)
    }
}

@Composable
fun EmptyState(text: String) {
    T(
        text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp),
        color = LocalOdsColors.current.muted,
        align = TextAlign.Center,
    )
}

@Composable
fun Notice(text: String, modifier: Modifier = Modifier) {
    val c = LocalOdsColors.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(lerpOver(c.bg, c.accentBg))
            .border(1.dp, c.accent, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) { T(text, size = 15.sp) }
}

@Composable
fun SectionTitle(text: String) {
    T(
        text.uppercase(),
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp),
        size = 15.sp,
        weight = FontWeight.SemiBold,
        color = LocalOdsColors.current.muted,
        style = BaseText.copy(letterSpacing = 0.04.em),
    )
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, align: TextAlign? = null) =
    T(text, modifier = modifier, size = 15.sp, color = LocalOdsColors.current.muted, align = align)

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) =
    T(text, modifier = modifier.padding(top = 20.dp, bottom = 8.dp), weight = FontWeight.SemiBold)

/** Строка из двух кнопок на всю ширину. */
@Composable
fun ButtonRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

/** Число «подпрыгивает» при изменении (keyframes bump). */
@Composable
fun rememberBump(key: Any?, enabled: Boolean): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val a = remember { Animatable(1f) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(key) {
        if (!enabled || reduce) return@LaunchedEffect
        a.animateTo(1f, keyframes {
            durationMillis = 400
            1f at 0 using Easings.Out
            1.3f at 160 using Easings.Out
            1f at 400
        })
    }
    return a
}

/** Появление снизу с растворением (rise-in). */
@Composable
fun Modifier.riseIn(key: Any?): Modifier {
    val reduce = LocalReduceMotion.current
    val p = remember(key) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(key) { p.animateTo(1f, tween(280, easing = Easings.EaseOut)) }
    return this.graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * 8.dp.toPx()
    }
}
