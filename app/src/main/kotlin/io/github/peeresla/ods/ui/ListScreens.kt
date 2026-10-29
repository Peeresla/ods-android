package io.github.peeresla.ods.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.peeresla.ods.AppViewModel
import io.github.peeresla.ods.Route
import io.github.peeresla.ods.core.GRADES
import io.github.peeresla.ods.core.LEARNED_STREAK
import io.github.peeresla.ods.core.formatDateTime
import io.github.peeresla.ods.core.mistakeIds
import io.github.peeresla.ods.core.percentText
import io.github.peeresla.ods.core.statsFor
import kotlinx.coroutines.delay

@Composable
fun MistakesScreen(vm: AppViewModel) {
    val c = LocalOdsColors.current
    val state by vm.state.collectAsState()
    val bank by vm.bank.collectAsState()
    val b = bank ?: return
    val ids = mistakeIds(state, b)
    if (ids.isEmpty()) {
        ScreenScroll {
            EmptyState("Ошибок нет. Вопрос попадает сюда после неверного ответа и уходит после $LEARNED_STREAK правильных ответов подряд.")
        }
        return
    }
    ScreenList {
        item(key = "go") {
            Column(Modifier.padding(bottom = 4.dp)) {
                OdsButton("Повторить ошибки (${ids.size})", { vm.goMistakesTraining() }, kind = BtnKind.Primary, block = true, delayed = true)
            }
        }
        items(ids.size, key = { ids[it] }) { i ->
            val q = b.questionsById.getValue(ids[i])
            val p = state.progressOf(q.id)
            OdsCard(gap = 8.dp) {
                val cat = b.categoryTitle(q.category)
                if (cat.isNotEmpty()) T(cat, size = 14.sp, color = c.muted)
                T(q.question, weight = FontWeight.SemiBold)
                T("Верно подряд: ${p.streak} из $LEARNED_STREAK", size = 14.sp, color = c.muted)
            }
        }
    }
}

@Composable
fun DocsScreen(vm: AppViewModel) {
    val c = LocalOdsColors.current
    val state by vm.state.collectAsState()
    val bank by vm.bank.collectAsState()
    val b = bank ?: return
    if (b.categories.isEmpty()) {
        ScreenScroll { EmptyState("Разделы не заданы в банке вопросов.") }
        return
    }
    ScreenList {
        items(b.categories.size, key = { b.categories[it].id }) { i ->
            val cat = b.categories[i]
            val ids = b.questionsInCategory(cat.id)
            val s = statsFor(state, ids)
            PressCard({ vm.navigate(Route.of("train", cat.id)) }) {
                T(cat.title, weight = FontWeight.SemiBold)
                ProgressBar(s.learned, Math.max(s.total, 1))
                T(
                    if (ids.isNotEmpty()) "Выучено ${s.learned} из ${s.total} · правильных: ${percentText(s.percent)}" else "Вопросов пока нет",
                    size = 14.sp,
                    color = c.muted,
                )
            }
        }
    }
}

@Composable
fun ScenariosScreen(vm: AppViewModel) {
    val c = LocalOdsColors.current
    val state by vm.state.collectAsState()
    val bank by vm.bank.collectAsState()
    val b = bank ?: return
    if (b.scenarios.isEmpty()) {
        ScreenScroll { EmptyState("Ситуаций пока нет.") }
        return
    }
    ScreenList {
        items(b.scenarios.size, key = { b.scenarios[it].id }) { i ->
            val s = b.scenarios[i]
            val last = state.scenarioOf(s.id).attempts.lastOrNull()
            PressCard({ vm.navigate(Route.of("scenarios", s.id)) }) {
                T(s.title.ifEmpty { "Ситуация" }, weight = FontWeight.SemiBold)
                if (s.internal) InternalBadge()
                T(s.situation, size = 15.sp, color = c.muted, maxLines = 2)
                if (last != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Badge(GRADES[last.grade] ?: "", badgeKindForGrade(last.grade))
                        T(formatDateTime(last.date), size = 14.sp, color = c.muted)
                    }
                } else {
                    T("Ещё не решалась", size = 14.sp, color = c.muted)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScenarioScreen(vm: AppViewModel, id: String) {
    val c = LocalOdsColors.current
    val state by vm.state.collectAsState()
    val bank by vm.bank.collectAsState()
    val revealed by vm.revealed.collectAsState()
    val b = bank ?: return
    val s = b.scenariosById[id]
    if (s == null) {
        ScreenScroll { EmptyState("Такой ситуации нет в банке вопросов.") }
        return
    }
    val data = state.scenarioOf(id)
    val isRevealed = id in revealed
    // Поле ответа: черновик сохраняется автоматически; после оценки поле очищается
    var text by rememberSaveable(id, data.attempts.size) { mutableStateOf(data.draft) }
    val refBring = remember { BringIntoViewRequester() }

    ScreenScroll {
        if (s.internal) FlowRow(Modifier.padding(bottom = 12.dp)) { InternalBadge() }
        OdsCard { T(s.situation, size = 18.sp, lineHeight = 1.45.em) }
        FieldLabel("Ваш ответ")
        TextArea(
            value = text,
            onChange = {
                text = it
                vm.scenarioDraft(id, it)
            },
            placeholder = "Действия, сроки, документы",
        )
        T("Черновик сохраняется автоматически", Modifier.padding(top = 6.dp, bottom = 16.dp), size = 13.sp, color = c.muted)

        if (isRevealed) {
            Column(Modifier.riseIn(id).bringIntoViewRequester(refBring)) {
                OdsCard {
                    T("Эталон", size = 19.sp, weight = FontWeight.Bold)
                    if (s.answer.isNotEmpty()) {
                        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            s.answer.forEachIndexed { i, a ->
                                Row {
                                    T("${i + 1}.", Modifier.padding(end = 8.dp), color = c.text)
                                    T(a, Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        Muted("Эталонный ответ не задан.", Modifier.padding(top = 10.dp))
                    }
                }
                FieldLabel("Как вы справились?")
                ButtonRow {
                    val kinds = mapOf("ok" to BtnKind.GradeOk, "partial" to BtnKind.GradePartial, "fail" to BtnKind.GradeFail)
                    for ((k, label) in GRADES) {
                        OdsButton(
                            label,
                            { vm.scenarioGrade(id, k, text) },
                            Modifier.weight(1f),
                            kind = kinds.getValue(k),
                            fontSize = 15.sp,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            LaunchedEffect(Unit) {
                delay(60)
                refBring.bringIntoView()
            }
        } else {
            OdsButton("Показать эталон", { vm.scenarioReveal(id) }, kind = BtnKind.Primary, block = true, delayed = true)
        }

        if (data.attempts.isNotEmpty()) {
            SectionTitle("История")
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((i, a) in data.attempts.withIndex().reversed()) {
                    AttemptCard(a.grade, a.date, a.text, key = "$id:$i")
                }
            }
        }
    }
}

@Composable
private fun AttemptCard(grade: String, date: String, text: String, key: String) {
    val c = LocalOdsColors.current
    var open by rememberSaveable(key) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .cardShadow(c)
            .clip(CardShape)
            .background(c.surface)
            .border(1.dp, c.border, CardShape)
            .animateContentSize(tween(if (LocalReduceMotion.current) 0 else 250, easing = Easings.Out))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .pressFeedback(ripple = true, delayed = false, shape = RoundedCornerShape(8.dp), base = c.surface, held = c.surface2, rippleColor = c.text, onClick = { open = !open }),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            T(if (open) "▾" else "▸", size = 14.sp, color = c.muted)
            Badge(GRADES[grade] ?: "", badgeKindForGrade(grade))
            T(formatDateTime(date), size = 14.sp, color = c.muted)
        }
        if (open) {
            if (text.isNotEmpty()) T(text, Modifier.padding(top = 10.dp), size = 15.sp)
            else Muted("Ответ не записан", Modifier.padding(top = 10.dp))
        }
    }
}

/** Многострочное поле в стиле приложения. */
@Composable
fun TextArea(value: String, onChange: (String) -> Unit, placeholder: String) {
    val c = LocalOdsColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp)
            .onFocusChanged { focused = it.isFocused },
        textStyle = BaseText.copy(fontSize = 16.sp, color = c.text),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp)
                    .clip(shape)
                    .background(c.surface)
                    .border(1.5.dp, if (focused) c.accent else c.border, shape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (value.isEmpty()) T(placeholder, size = 16.sp, color = c.muted)
                inner()
            }
        },
    )
}
