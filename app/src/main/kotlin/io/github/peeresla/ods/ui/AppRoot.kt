package io.github.peeresla.ods.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peeresla.ods.AppViewModel
import io.github.peeresla.ods.Nav
import io.github.peeresla.ods.Phase
import io.github.peeresla.ods.Route
import io.github.peeresla.ods.core.Bank
import io.github.peeresla.ods.core.trainTitle
import kotlinx.coroutines.delay

private const val APP_TITLE = "Тренажёр МЧС"

/** Что показывать: экран с учётом того, что банк заблокирован или не загрузился (paint в app.js). */
private data class Screen(val route: Route, val locked: Boolean, val loading: Boolean)

@Composable
fun AppRoot(vm: AppViewModel, startedAt: Long) {
    val c = LocalOdsColors.current
    val reduce = LocalReduceMotion.current
    val phase by vm.phase.collectAsState()
    val route by vm.route.collectAsState()
    val bank by vm.bank.collectAsState()
    val modal by vm.modal.collectAsState()
    val toast by vm.toast.collectAsState()
    val ready by vm.ready.collectAsState()

    val screen = when (phase) {
        is Phase.Locked -> Screen(Route.Home, locked = true, loading = false)
        Phase.Loading -> Screen(Route.Home, locked = false, loading = true)
        Phase.NoBank -> Screen(if (route.screen == "settings") route else Route.Home, locked = false, loading = false)
        Phase.Ready -> Screen(route, locked = false, loading = false)
    }

    // Системная кнопка и жест «Назад» — как «Назад» в шапке. На главной — обычное поведение Android.
    val canGoBack = !screen.locked && !screen.loading && vm.backTarget(screen.route) != null
    BackHandler(enabled = modal == null && canGoBack) { vm.back() }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            val title = when {
                screen.locked || screen.loading -> APP_TITLE
                else -> screenTitle(screen.route, bank)
            }
            val hasBack = canGoBack
            Header(
                title = title,
                showBack = hasBack,
                showSettings = !hasBack && !screen.locked && !screen.loading,
                onBack = { vm.back() },
                onSettings = { vm.navigate(Route.of("settings")) },
            )
            AnimatedContent(
                targetState = screen,
                modifier = Modifier.fillMaxWidth().weight(1f),
                transitionSpec = { screenTransition(vm.nav.value, reduce) },
                contentKey = { "${it.locked}|${it.loading}|${it.route}" },
                label = "screen",
            ) { s ->
                Box(Modifier.fillMaxSize().background(c.bg)) {
                    when {
                        s.loading -> Unit
                        s.locked -> UnlockScreen(vm)
                        else -> RouteContent(s.route, vm)
                    }
                }
            }
        }

        ToastHost(toast)
        ConfirmModal(modal) { vm.closeModal(it) }

        // Заставка: не меньше 4 с и пока не загрузился банк. При отключённых анимациях не задерживает запуск.
        var splashVisible by remember { mutableStateOf(true) }
        LaunchedEffect(ready) {
            if (!ready) return@LaunchedEffect
            val wait = if (reduce) 0L else (startedAt + SPLASH_MIN_MS - SystemClock.uptimeMillis()).coerceAtLeast(0)
            delay(wait)
            splashVisible = false
        }
        AnimatedVisibility(
            visible = splashVisible,
            enter = fadeIn(tween(0)),
            exit = fadeOut(tween(if (reduce) 0 else 450, easing = Easings.Ease)),
        ) {
            Splash()
        }
    }
}

private fun screenTitle(r: Route, bank: Bank?): String {
    return when (r.screen) {
        "" -> APP_TITLE
        "train" -> trainTitle(r.arg ?: "all", bank)
        "exam" -> if (r.arg == "result") "Результат" else "Экзамен"
        "mistakes" -> "Ошибки"
        "scenarios" -> if (r.arg == null) "Ситуации" else bank?.scenariosById?.get(r.arg)?.title?.takeIf { it.isNotEmpty() } ?: "Ситуация"
        "docs" -> "По документам"
        "settings" -> "Настройки"
        else -> APP_TITLE
    }
}

/**
 * Переходы как в iOS: вперёд — новый экран въезжает справа, старый уходит влево и притухает;
 * назад — наоборот, уходящий экран остаётся сверху.
 */
private fun screenTransition(nav: Nav, reduce: Boolean): ContentTransform {
    if (reduce) return ContentTransform(fadeIn(tween(0)), fadeOut(tween(0)), sizeTransform = null)
    val d = 340
    val spec = tween<IntOffset>(d, easing = Easings.Out)
    return when (nav) {
        Nav.Forward -> ContentTransform(
            slideInHorizontally(spec) { it },
            slideOutHorizontally(spec) { -it / 4 } + fadeOut(tween(d, easing = Easings.Out), targetAlpha = 0.6f),
            targetContentZIndex = 1f,
            sizeTransform = null,
        )
        Nav.Back -> ContentTransform(
            slideInHorizontally(spec) { -it / 4 } + fadeIn(tween(d, easing = Easings.Out), initialAlpha = 0.6f),
            slideOutHorizontally(spec) { it },
            targetContentZIndex = -1f,
            sizeTransform = null,
        )
        Nav.Fade -> ContentTransform(fadeIn(tween(250, easing = Easings.EaseOut)), fadeOut(tween(180, easing = Easings.EaseIn)), sizeTransform = null)
    }
}

@Composable
private fun Header(title: String, showBack: Boolean, showSettings: Boolean, onBack: () -> Unit, onSettings: () -> Unit) {
    val c = LocalOdsColors.current
    val reduce = LocalReduceMotion.current
    Box(
        Modifier
            .fillMaxWidth()
            .background(c.headerBg)
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .height(52.dp)
            .padding(horizontal = 8.dp),
    ) {
        AnimatedContent(
            targetState = title,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 84.dp),
            transitionSpec = { fadeIn(tween(if (reduce) 0 else 250)) togetherWith fadeOut(tween(if (reduce) 0 else 180)) },
            label = "title",
        ) { t ->
            T(t, size = 17.sp, weight = FontWeight.SemiBold, color = c.headerText, align = TextAlign.Center, maxLines = 1)
        }
        AnimatedVisibility(
            visible = showBack,
            modifier = Modifier.align(Alignment.CenterStart),
            enter = fadeIn(tween(if (reduce) 0 else 250)),
            exit = fadeOut(tween(if (reduce) 0 else 150)),
        ) {
            HeaderButton(onBack, Modifier.widthIn(min = 48.dp)) {
                T("‹ Назад", modifier = Modifier.padding(horizontal = 8.dp), size = 17.sp, color = c.headerText)
            }
        }
        AnimatedVisibility(
            visible = showSettings,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = fadeIn(tween(if (reduce) 0 else 250)),
            exit = fadeOut(tween(if (reduce) 0 else 150)),
        ) {
            HeaderButton(onSettings, Modifier.size(48.dp), label = "Настройки") {
                IconView(Icons.Gear, 24.dp, c.headerText)
            }
        }
    }
}

/** Кнопки шапки при нажатии полупрозрачные, как в iOS. */
@Composable
private fun HeaderButton(onClick: () -> Unit, modifier: Modifier, label: String? = null, content: @Composable () -> Unit) {
    val state = rememberPressState()
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current
    Box(
        modifier
            .heightIn(min = 48.dp)
            .graphicsLayer { alpha = 1f - 0.45f * state.held.value }
            .pressable(state, scope, true, reduce, ripple = false, delayed = true, shape = RoundedCornerShape(10.dp), action = onClick, scaleTo = 1f)
            .then(if (label != null) Modifier.semanticsLabel(label) else Modifier),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun Modifier.semanticsLabel(label: String): Modifier =
    this.then(Modifier.semantics { contentDescription = label })
