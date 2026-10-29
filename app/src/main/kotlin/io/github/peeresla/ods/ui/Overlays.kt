package io.github.peeresla.ods.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peeresla.ods.ModalRequest
import io.github.peeresla.ods.ToastMsg
import kotlinx.coroutines.delay

/** Окно подтверждения в стиле приложения (не системное). Тап по затемнению — «Остаться»/«Отмена». */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ConfirmModal(request: ModalRequest?, onResult: (Boolean) -> Unit) {
    val c = LocalOdsColors.current
    val reduce = LocalReduceMotion.current
    var shown by remember { mutableStateOf<ModalRequest?>(null) }
    if (request != null) shown = request
    BackHandler(enabled = request != null) { onResult(false) }

    AnimatedVisibility(
        visible = request != null,
        enter = if (reduce) fadeIn(tween(0)) else fadeIn(tween(200, easing = Easings.EaseOut)),
        exit = if (reduce) fadeOut(tween(0)) else fadeOut(tween(180, easing = Easings.EaseIn)),
    ) {
        val m = shown ?: return@AnimatedVisibility
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x80080F1E))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onResult(false) }
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .animateEnterExit(
                        enter = if (reduce) fadeIn(tween(0)) else scaleIn(tween(280, easing = Easings.Modal), initialScale = 0.9f) + fadeIn(tween(280)),
                        exit = if (reduce) fadeOut(tween(0)) else scaleOut(tween(180, easing = Easings.EaseIn), targetScale = 0.95f) + fadeOut(tween(180)),
                    )
                    .widthIn(max = 340.dp)
                    .fillMaxWidth()
                    .shadow(24.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.surface)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (m.title.isNotEmpty()) {
                    T(m.title, size = 19.sp, weight = FontWeight.Bold, align = TextAlign.Center)
                    T(m.text, modifier = Modifier.padding(top = 10.dp), size = 15.sp, color = c.muted, align = TextAlign.Center)
                } else {
                    T(m.text, size = 16.sp, align = TextAlign.Center)
                }
                ButtonRow(Modifier.padding(top = 20.dp)) {
                    OdsButton(m.ok, { onResult(true) }, Modifier.weight(1f), kind = BtnKind.Danger, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp))
                    OdsButton(m.cancel, { onResult(false) }, Modifier.weight(1f), kind = BtnKind.Primary, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp))
                }
            }
        }
    }
}

/** Уведомление внизу экрана на 2,6 с. */
@Composable
fun ToastHost(msg: ToastMsg?) {
    val c = LocalOdsColors.current
    val reduce = LocalReduceMotion.current
    var visible by remember { mutableStateOf<ToastMsg?>(null) }
    LaunchedEffect(msg?.id) {
        if (msg == null) return@LaunchedEffect
        visible = msg
        delay(2600)
        visible = null
    }
    var last by remember { mutableStateOf<ToastMsg?>(null) }
    if (visible != null) last = visible
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible != null,
            enter = if (reduce) fadeIn(tween(0)) else fadeIn(tween(250, easing = Easings.EaseOut)) + slideInVertically(tween(250, easing = Easings.EaseOut)) { 24 },
            exit = fadeOut(tween(if (reduce) 0 else 200)),
        ) {
            val t = last ?: return@AnimatedVisibility
            Box(
                Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
                    .shadow(8.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.navy)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                T(t.text, size = 15.sp, color = Color.White)
            }
        }
    }
}
