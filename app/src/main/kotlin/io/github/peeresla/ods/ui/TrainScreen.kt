package io.github.peeresla.ods.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.peeresla.ods.AppViewModel
import io.github.peeresla.ods.core.Bank
import io.github.peeresla.ods.core.Current
import io.github.peeresla.ods.core.Question
import io.github.peeresla.ods.core.TrainView
import kotlinx.coroutines.delay

@Composable
fun TrainScreen(vm: AppViewModel) {
    val ui by vm.train.collectAsState()
    val state by vm.state.collectAsState()
    val bank by vm.bank.collectAsState()
    val t = ui ?: return
    val b = bank ?: return
    when (val view = t.view) {
        TrainView.Unknown -> ScreenScroll { EmptyState("Такого раздела нет в банке вопросов.") }
        is TrainView.Empty -> ScreenScroll { EmptyState(view.text) }
        is TrainView.Ask -> {
            val s = view.session
            val cur = s.current ?: return
            val answered = cur.chosen != null
            val bring = remember { BringIntoViewRequester() }
            ScreenScroll(
                bottomBar = if (answered) {
                    { OdsButton("Дальше", { vm.trainNext() }, kind = BtnKind.Primary, block = true, delayed = true) }
                } else {
                    null
                },
            ) {
                RunStats(s.answered, state.correctRunRecord, bumpAnswered = t.animate, bumpRecord = t.animate && t.recordBumped)
                val density = androidx.compose.ui.platform.LocalDensity.current
                val reduce = LocalReduceMotion.current
                AnimatedContent(
                    targetState = QuestionKey(s.answered - (if (answered) 1 else 0), cur.id, view.question, cur),
                    transitionSpec = { nudge(forward = true, density, reduce) },
                    contentKey = { "${it.seq}|${it.id}" },
                    label = "question",
                ) { key ->
                    // Уходящий вопрос показываем таким, каким он был
                    val shownCur = key.cur
                    Column {
                        QuestionTags(b, key.question)
                        T(key.question.question, Modifier.padding(top = 6.dp, bottom = 16.dp), size = 20.sp, weight = FontWeight.SemiBold, lineHeight = 1.35.em)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val order = shownCur.order
                            val chosen = shownCur.chosen
                            for (orig in order) {
                                val look = when {
                                    chosen == null -> OptionLook.Normal
                                    orig == key.question.correct -> OptionLook.Correct
                                    orig == chosen -> OptionLook.Wrong
                                    else -> OptionLook.Normal
                                }
                                OptionButton(
                                    text = key.question.options[orig],
                                    look = look,
                                    enabled = chosen == null,
                                    animateKey = if (t.animate && chosen != null && look != OptionLook.Normal) "${key.seq}:${key.id}:$chosen" else null,
                                    onClick = { vm.trainAnswer(orig) },
                                )
                            }
                        }
                        if (chosen != null) {
                            Feedback(key.question, chosen == key.question.correct, Modifier.bringIntoViewRequester(bring))
                            LaunchedEffect(key.id, chosen) {
                                delay(60)
                                bring.bringIntoView()
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class QuestionKey(val seq: Int, val id: String, val question: Question, val cur: Current)

/** Смена вопроса — короткий сдвиг с растворением, без наложения текстов. */
fun nudge(forward: Boolean, density: Density, reduce: Boolean): ContentTransform {
    if (reduce) return (fadeIn(tween(0)) togetherWith fadeOut(tween(0))).using(SizeTransform(clip = false))
    val dx = with(density) { 40.dp.roundToPx() }
    val sign = if (forward) 1 else -1
    val inSpec = tween<IntOffset>(280, delayMillis = 100, easing = Easings.Out)
    val outSpec = tween<IntOffset>(130, easing = Easings.EaseIn)
    return ((slideInHorizontally(inSpec) { sign * dx } + fadeIn(tween(280, delayMillis = 100, easing = Easings.Out))) togetherWith
        (slideOutHorizontally(outSpec) { -sign * dx } + fadeOut(tween(130, easing = Easings.EaseIn))))
        .using(SizeTransform(clip = false))
}

@Composable
private fun RunStats(answered: Int, record: Long, bumpAnswered: Boolean, bumpRecord: Boolean) {
    val c = LocalOdsColors.current
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RunStat(Modifier.weight(1f), "$answered", "отвечено подряд", c.text, rememberBump(answered, bumpAnswered).value)
        RunStat(Modifier.weight(1f), "$record", "рекорд верных подряд", c.accent, rememberBump(record, bumpRecord).value)
    }
}

@Composable
private fun RunStat(modifier: Modifier, value: String, label: String, color: androidx.compose.ui.graphics.Color, scale: Float) {
    val c = LocalOdsColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.border, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        T(value, Modifier.graphicsLayer { scaleX = scale; scaleY = scale }, size = 26.sp, weight = FontWeight.ExtraBold, color = color, lineHeight = 1.em)
        T(label, size = 13.sp, color = c.muted, lineHeight = 1.2.em)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestionTags(bank: Bank, q: Question) {
    val cat = bank.categoryTitle(q.category)
    if (cat.isEmpty() && !q.internal) return
    FlowRow(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (cat.isNotEmpty()) Tag(cat)
        if (q.internal) InternalBadge()
    }
}

@Composable
private fun Feedback(q: Question, ok: Boolean, modifier: Modifier) {
    val c = LocalOdsColors.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .padding(top = 16.dp)
            .riseIn(q.id)
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.border, shape)
            .leftStripe(if (ok) c.ok else c.bad, 4.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        T(if (ok) "Верно" else "Неверно", weight = FontWeight.Bold, color = if (ok) c.ok else c.bad)
        if (q.explanation.isNotEmpty()) T(q.explanation)
        if (q.source.isNotEmpty()) T("Источник: ${q.source}", size = 14.sp, color = c.muted)
    }
}
