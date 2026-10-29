package io.github.peeresla.ods.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.peeresla.ods.AppViewModel
import io.github.peeresla.ods.Route
import io.github.peeresla.ods.core.AppState
import io.github.peeresla.ods.core.Bank
import io.github.peeresla.ods.core.EXAM_DURATION_MS
import io.github.peeresla.ods.core.answeredCount
import io.github.peeresla.ods.core.effectiveThreshold
import io.github.peeresla.ods.core.examRemaining
import io.github.peeresla.ods.core.examSize
import io.github.peeresla.ods.core.formatDateTime
import io.github.peeresla.ods.core.formatDuration
import io.github.peeresla.ods.core.plural

@Composable
fun ExamScreen(vm: AppViewModel, result: Boolean) {
    val state by vm.state.collectAsState()
    val bank by vm.bank.collectAsState()
    val now by vm.now.collectAsState()
    val b = bank ?: return
    if (result) {
        ExamResult(vm, state, b)
        return
    }
    val density = LocalDensity.current
    val reduce = LocalReduceMotion.current
    // Начало экзамена и переход между вопросами — короткий сдвиг: вперёд или назад
    val pos = state.exam?.pos ?: -1
    AnimatedContent(
        targetState = pos,
        transitionSpec = { nudge(forward = targetState >= initialState, density, reduce) },
        label = "exam",
    ) { p ->
        val ex = state.exam
        if (p < 0 || ex == null) {
            ExamIntro(vm, state, b)
        } else {
            ExamQuestion(vm, state, b, p.coerceAtMost(ex.ids.size - 1), now)
        }
    }
}

@Composable
private fun ExamIntro(vm: AppViewModel, state: AppState, b: Bank) {
    val n = examSize(b)
    val last = state.lastExam
    ScreenScroll {
        OdsCard {
            T("Условия", size = 22.sp, weight = FontWeight.Bold, lineHeight = 1.3.em)
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Bullet("$n ${plural(n, listOf("случайный вопрос", "случайных вопроса", "случайных вопросов"))}")
                Bullet("${EXAM_DURATION_MS / 60000} минут")
                Bullet("Подсказок нет — правильные ответы и пояснения после завершения")
                Bullet("Сдано: не менее ${effectiveThreshold(state, n)} из $n")
            }
        }
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OdsButton("Начать экзамен", { vm.examStart() }, kind = BtnKind.Primary, block = true, enabled = n > 0, delayed = true)
            if (last != null) {
                OdsButton(
                    "Последний результат: ${last.score} из ${last.total} — ${if (last.passed) "сдано" else "не сдано"}",
                    { vm.navigate(Route.of("exam", "result")) },
                    block = true,
                    delayed = true,
                )
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row {
        T("•", Modifier.padding(start = 6.dp, end = 10.dp))
        T(text, Modifier.weight(1f))
    }
}

@Composable
private fun ExamQuestion(vm: AppViewModel, state: AppState, b: Bank, pos: Int, now: Long) {
    val c = LocalOdsColors.current
    val ex = state.exam ?: return
    val id = ex.ids[pos]
    val q = b.questionsById[id] ?: return
    val chosen = ex.answers[id]
    val total = ex.ids.size
    val isLast = pos == total - 1
    val remaining = examRemaining(ex, now)
    val done = answeredCount(ex)
    val haptic = rememberHaptics(vm)
    ScreenScroll(
        bottomBar = {
            ButtonRow {
                OdsButton("Назад", { vm.examMove(-1) }, Modifier.weight(1f), enabled = pos > 0, delayed = true)
                if (isLast) {
                    OdsButton("Завершить", { vm.examFinish() }, Modifier.weight(1f), kind = BtnKind.Primary)
                } else {
                    OdsButton("Дальше", { vm.examMove(1) }, Modifier.weight(1f), kind = BtnKind.Primary, delayed = true)
                }
            }
        },
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            T("Вопрос ${pos + 1} из $total", Modifier.weight(1f), size = 15.sp, color = c.muted)
            T(formatDuration(remaining), size = 15.sp, weight = FontWeight.Bold, color = if (remaining < 60000) c.bad else c.text)
        }
        ProgressBar(done, total)
        T(q.question, Modifier.padding(top = 18.dp, bottom = 16.dp), size = 20.sp, weight = FontWeight.SemiBold, lineHeight = 1.35.em)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (orig in ex.orders[id] ?: q.options.indices.toList()) {
                val selected = chosen == orig
                OptionButton(
                    text = q.options[orig],
                    look = if (selected) OptionLook.Selected else OptionLook.Normal,
                    enabled = true,
                    animateKey = if (selected) "$id:$orig" else null,
                    onClick = {
                        haptic(Haptic.Tap)
                        vm.examChoose(orig)
                    },
                )
            }
        }
        Muted("Отвечено $done из $total. Ответ можно изменить до завершения.", Modifier.padding(top = 16.dp))
        if (!isLast) {
            OdsButton("Завершить экзамен", { vm.examFinish() }, Modifier.padding(top = 16.dp), block = true)
        }
    }
}

@Composable
private fun ExamResult(vm: AppViewModel, state: AppState, b: Bank) {
    val c = LocalOdsColors.current
    val r = state.lastExam
    if (r == null) {
        ScreenScroll { EmptyState("Экзамен ещё не сдавался.") }
        return
    }
    val errors = r.items.filter { !it.ok && !it.gone && it.id in b.questionsById }
    ScreenList {
        item {
            OdsCard(topAccent = if (r.passed) c.ok else c.bad, gap = 6.dp) {
                T(if (r.passed) "Сдано" else "Не сдано", Modifier.fillMaxWidth(), size = 26.sp, weight = FontWeight.Bold, color = if (r.passed) c.ok else c.bad, align = TextAlign.Center)
                T("${r.score} из ${r.total}", Modifier.fillMaxWidth(), size = 20.sp, weight = FontWeight.SemiBold, align = TextAlign.Center)
                Muted("Порог: ${r.threshold} · время: ${formatDuration(r.durationMs)}${if (r.timeout) " (время вышло)" else ""}", Modifier.fillMaxWidth(), align = TextAlign.Center)
                Muted(formatDateTime(r.date), Modifier.fillMaxWidth(), align = TextAlign.Center)
            }
        }
        item { Column(Modifier.padding(top = 12.dp)) { SectionTitleTight("Ошибки: ${errors.size}") } }
        if (errors.isEmpty()) item { EmptyState("Ошибок нет.") }
        items(errors.size, key = { "e" + errors[it].id }) { i ->
            val item = errors[i]
            val q = b.questionsById.getValue(item.id)
            val yours = item.chosen?.let { q.options.getOrNull(it) }
            OdsCard(gap = 8.dp) {
                val cat = b.categoryTitle(q.category)
                if (cat.isNotEmpty()) T(cat, size = 14.sp, color = c.muted)
                T(q.question, weight = FontWeight.SemiBold)
                AnswerLine("Ваш ответ: ${yours ?: "нет ответа"}", c.bad)
                AnswerLine("Правильно: ${q.options[q.correct]}", c.ok)
                if (q.explanation.isNotEmpty()) T(q.explanation)
                if (q.source.isNotEmpty()) T("Источник: ${q.source}", size = 14.sp, color = c.muted)
            }
        }
        item {
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OdsButton("Новый экзамен", { vm.newExam() }, kind = BtnKind.Primary, block = true, delayed = true)
                OdsButton("На главную", { vm.navigate(Route.Home) }, block = true, delayed = true)
            }
        }
    }
}

@Composable
private fun AnswerLine(text: String, color: androidx.compose.ui.graphics.Color) {
    T(text, Modifier.leftStripe(color, 3.dp).padding(start = 10.dp), size = 15.sp)
}

/** Заголовок раздела без верхнего отступа (в списках). */
@Composable
fun SectionTitleTight(text: String) {
    T(
        text.uppercase(),
        size = 15.sp,
        weight = FontWeight.SemiBold,
        color = LocalOdsColors.current.muted,
        style = BaseText.copy(letterSpacing = 0.04.em),
    )
}
