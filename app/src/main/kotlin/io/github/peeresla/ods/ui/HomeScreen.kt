package io.github.peeresla.ods.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.peeresla.ods.AppViewModel
import io.github.peeresla.ods.Route
import io.github.peeresla.ods.core.DAILY_GOAL
import io.github.peeresla.ods.core.DAYS
import io.github.peeresla.ods.core.EXAM_DURATION_MS
import io.github.peeresla.ods.core.KEY_ALL
import io.github.peeresla.ods.core.LEARNED_STREAK
import io.github.peeresla.ods.core.MAX_MISSED_SHADE
import io.github.peeresla.ods.core.bossPhrase
import io.github.peeresla.ods.core.dayKey
import io.github.peeresla.ods.core.examRemaining
import io.github.peeresla.ods.core.examSize
import io.github.peeresla.ods.core.formatDuration
import io.github.peeresla.ods.core.mistakeIds
import io.github.peeresla.ods.core.plural
import io.github.peeresla.ods.core.statsFor
import io.github.peeresla.ods.core.streakStatus
import java.time.ZoneId
import androidx.compose.material3.Text as M3Text

@Composable
fun HomeScreen(vm: AppViewModel) {
    val c = LocalOdsColors.current
    val bank by vm.bank.collectAsState()
    val state by vm.state.collectAsState()
    val now by vm.now.collectAsState()
    val b = bank

    ScreenScroll {
        if (b == null) {
            Notice("Не удалось загрузить вопросы. Откройте приложение при подключении к интернету.")
            Column(Modifier.padding(top = 16.dp)) {
                MenuTile(Icons.Settings, "Настройки", "Резервная копия и восстановление", null) { vm.navigate(Route.of("settings")) }
            }
            return@ScreenScroll
        }

        val s = statsFor(state, b.questions.map { it.id })
        val today = dayKey(now, ZoneId.systemDefault())
        val st = streakStatus(state.activity, today)
        val mistakes = mistakeIds(state, b).size
        val missed = Math.min(st.missed, MAX_MISSED_SHADE)
        val pending = !st.doneToday
        val status = c.status(missed)
        val statusBg = c.statusBg(missed)

        OdsCard(topAccent = status, gap = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(if (pending) c.surface2 else lerpOver(c.surface, statusBg)),
                    contentAlignment = Alignment.Center,
                ) {
                    IconView(Icons.Streak, 30.dp, if (pending) c.muted else status)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val bump = rememberBumpOnChange(st.streak)
                        T(
                            "${st.streak}",
                            modifier = Modifier.graphicsLayer { scaleX = bump.value; scaleY = bump.value },
                            size = 44.sp,
                            weight = FontWeight.ExtraBold,
                            color = if (pending) c.muted else status,
                            lineHeight = 1.em,
                        )
                        T("${plural(st.streak, DAYS)} подряд", Modifier.padding(bottom = 3.dp), size = 18.sp, weight = FontWeight.SemiBold, lineHeight = 1.2.em)
                    }
                    T(
                        if (st.doneToday) "Норма выполнена" else "Норма на сегодня: ${st.todayCount} / $DAILY_GOAL",
                        size = 14.sp,
                        weight = FontWeight.SemiBold,
                        color = if (pending) c.text else status,
                    )
                }
                M3Text(
                    buildAnnotatedString {
                        append("Рекорд\n")
                        withStyle(SpanStyle(fontSize = 20.sp, color = c.text, fontWeight = FontWeight.Bold)) { append("${st.best}") }
                    },
                    style = BaseText.copy(fontSize = 13.sp, lineHeight = 1.3.em, color = c.muted, textAlign = TextAlign.End),
                )
            }
            val quoteShape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(quoteShape)
                    .background(lerpOver(c.surface, statusBg))
                    .leftStripe(status, 4.dp)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                T(bossPhrase(st), size = 16.sp, weight = FontWeight.Medium, lineHeight = 1.4.em)
                T("— Начальник ЦУКС", Modifier.padding(top = 6.dp), size = 13.sp, weight = FontWeight.SemiBold, color = status)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    T("${s.percent ?: 0}%", size = 30.sp, weight = FontWeight.ExtraBold, lineHeight = 1.1.em)
                    T("правильных ответов", size = 15.sp, weight = FontWeight.Medium)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    M3Text(
                        buildAnnotatedString {
                            append("${s.learned}")
                            withStyle(SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.muted)) { append(" / ${s.total}") }
                        },
                        style = BaseText.copy(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 1.1.em, color = c.text),
                    )
                    T("выучено вопросов", size = 15.sp, weight = FontWeight.Medium)
                }
            }
            ProgressBar(s.learned, Math.max(s.total, 1))
            T("Ответов дано: ${s.shown}", size = 14.sp, color = c.muted)
        }

        val exam = state.exam
        if (exam != null) {
            Spacer(Modifier.size(16.dp))
            OdsCard(borderColor = c.accent, gap = 10.dp) {
                M3Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Идёт экзамен.") }
                        append(" Осталось ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(formatDuration(examRemaining(exam, now))) }
                    },
                    style = BaseText.copy(color = c.text),
                )
                OdsButton("Продолжить экзамен", { vm.navigate(Route.of("exam")) }, kind = BtnKind.Primary, block = true, delayed = true)
            }
        }

        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MenuTile(Icons.Train, "Тренировка", "Все вопросы: сначала ошибки и давно не повторённые", null) {
                vm.navigate(Route.of("train", KEY_ALL))
            }
            MenuTile(Icons.Exam, "Экзамен", "${examSize(b)} вопросов · ${EXAM_DURATION_MS / 60000} минут", null) {
                vm.navigate(Route.of("exam"))
            }
            MenuTile(Icons.Mistakes, "Ошибки", "Уходят после $LEARNED_STREAK правильных ответов подряд", mistakes to (mistakes > 0)) {
                vm.navigate(Route.of("mistakes"))
            }
            MenuTile(Icons.Scenarios, "Ситуации", "Свой ответ и сверка с эталоном", b.scenarios.size to false) {
                vm.navigate(Route.of("scenarios"))
            }
            MenuTile(Icons.Docs, "По документам", "Прогресс по каждому разделу", b.categories.size to false) {
                vm.navigate(Route.of("docs"))
            }
        }
    }
}

/** Плитка главного экрана: контурная иконка, заголовок, подпись и счётчик. badge = (число, акцентный ли). */
@Composable
fun MenuTile(icon: OutlineIcon, title: String, sub: String, badge: Pair<Int, Boolean>?, onClick: () -> Unit) {
    val c = LocalOdsColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .cardShadow(c)
            .pressFeedback(ripple = false, delayed = true, shape = CardShape, base = c.surface, held = c.surface2, rippleColor = c.text, onClick = onClick)
            .border(1.dp, c.border, CardShape)
            .clip(CardShape)
            .leftStripe(c.navy, 4.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)).background(c.iconBg), contentAlignment = Alignment.Center) {
            IconView(icon, 28.dp, c.icon, accent = c.accent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T(title, size = 18.sp, weight = FontWeight.SemiBold, lineHeight = 1.3.em)
            T(sub, size = 14.sp, color = c.muted, lineHeight = 1.35.em)
        }
        if (badge != null) Badge("${badge.first}", if (badge.second) BadgeKind.Accent else BadgeKind.Muted)
    }
}

/** Число «подпрыгивает», когда меняется (но не при первом показе). */
@Composable
fun rememberBumpOnChange(value: Any?): androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val seen = remember { arrayOf(value) }
    val changed = seen[0] != value
    LaunchedEffect(value) { seen[0] = value }
    return rememberBump(value, enabled = changed)
}
