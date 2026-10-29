package io.github.peeresla.ods.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val MaxContent = 640.dp

/** Отступы снизу от панели жестов и клавиатуры; по бокам — от выреза камеры в горизонтальном положении. */
@Composable
private fun Modifier.screenInsets(): Modifier = this
    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom))
    .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.navigationBars).only(WindowInsetsSides.Horizontal))

/** Прокручиваемый экран шириной не больше 640 dp. [bottomBar] — закреплённая внизу панель кнопок. */
@Composable
fun ScreenScroll(
    scroll: ScrollState = rememberScrollState(),
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalOdsColors.current
    Column(Modifier.fillMaxSize().screenInsets()) {
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = MaxContent)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (bottomBar == null) 24.dp else 8.dp),
                content = content,
            )
        }
        if (bottomBar != null) {
            Box(Modifier.fillMaxWidth().background(c.bg), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = MaxContent).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) { bottomBar() }
            }
        }
    }
}

/** Длинный список. */
@Composable
fun ScreenList(
    state: LazyListState = rememberLazyListState(),
    gap: androidx.compose.ui.unit.Dp = 12.dp,
    content: LazyListScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().screenInsets(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = MaxContent).fillMaxWidth(),
            state = state,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
            content = content,
        )
    }
}
