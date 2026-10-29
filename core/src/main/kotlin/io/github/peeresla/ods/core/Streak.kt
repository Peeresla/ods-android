package io.github.peeresla.ods.core

data class StreakStatus(
    val never: Boolean,
    val doneToday: Boolean,
    val atRisk: Boolean,
    val streak: Int,
    val best: Int,
    /** Полных дней без нормы. */
    val missed: Int,
    val todayCount: Int,
    val left: Int,
)

// Серия по правилам Duolingo: день засчитывается, когда выполнена дневная норма;
// пропущенный день без нормы обнуляет серию. Норма сегодня не выполнена — серия ещё жива до полуночи.
fun streakStatus(a: Activity, today: String): StreakStatus {
    val gap = a.lastDone?.let { Math.max(0L, daysBetween(it, today)) }
    val todayCount = if (a.today == today) a.todayCount else 0
    return StreakStatus(
        never = gap == null,
        doneToday = gap == 0L,
        atRisk = gap == 1L,
        streak = if (gap == 0L || gap == 1L) a.streak else 0,
        best = a.best,
        missed = if (gap == null || gap <= 1) 0 else (gap - 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        todayCount = Math.min(todayCount, DAILY_GOAL),
        left = Math.max(0, DAILY_GOAL - todayCount),
    )
}

/** Засчитывает ответы в дневную норму. second = true, если норма выполнена только что. */
fun countPractice(activity: Activity, amount: Int, today: String): Pair<Activity, Boolean> {
    var a = activity
    if (a.today != today) a = a.copy(today = today, todayCount = 0)
    a = a.copy(todayCount = a.todayCount + amount)
    if (a.todayCount < DAILY_GOAL || a.lastDone == today) return a to false
    val gap = a.lastDone?.let { daysBetween(it, today) }
    val streak = if (gap == 1L) a.streak + 1 else 1
    return a.copy(streak = streak, best = Math.max(a.best, streak), lastDone = today) to true
}

fun goalToast(streak: Int): String = "Норма выполнена: $streak ${plural(streak, DAYS)} подряд"

/** Слова начальника ЦУКС: по серии, по невыполненной норме и по числу пропущенных дней. */
fun bossPhrase(st: StreakStatus): String {
    fun days(n: Int) = "$n ${plural(n, DAYS)}"
    val left = "Осталось ${st.left} ${plural(st.left, listOf("вопрос", "вопроса", "вопросов"))}."
    if (st.never) {
        if (st.todayCount > 0) return "Первое дежурство идёт: ${st.todayCount} из $DAILY_GOAL. Добей норму — и серия пошла. $left"
        return "Заступаешь на первое дежурство. Норма — $DAILY_GOAL вопросов в день. Приступай."
    }
    if (st.doneToday) {
        val tomorrow = "Завтра будет ${days(st.streak + 1)} подряд — не сорви."
        if (st.streak >= 30) return "Месяц без пропусков. Такого дежурного хоть сейчас в старшие. $tomorrow"
        if (st.streak >= 14) return "Две недели без пропусков. На разборе поставлю тебя в пример. $tomorrow"
        if (st.streak >= 7) return "Неделя без пропусков. Вот это оперативная готовность. $tomorrow"
        if (st.streak >= 4) return "Заступаешь стабильно. Так и докладывай — без замечаний. $tomorrow"
        return "Норма выполнена. Но один день — ещё не система. $tomorrow"
    }
    if (st.atRisk) {
        if (st.streak >= 7) return "Сегодня ты ещё не заступал. ${days(st.streak)} подряд сгорят в полночь — не подводи смену. $left"
        return "Норма на сегодня не выполнена. Серия ${days(st.streak)} сгорит в полночь. $left"
    }
    if (st.missed == 1) return "Вчера норма не выполнена — серия сгорела. Начинай заново, пока не вошло в привычку."
    if (st.missed <= 3) return "${days(st.missed)} тишины. В ЦУКС так не работают — догоняй."
    if (st.missed <= 6) return "Тебя не было ${days(st.missed)}. Обстановка меняется каждые сутки, а ты — нет."
    if (st.missed < MAX_MISSED_SHADE) return "${days(st.missed)} без тренировки. Готовь объяснительную или садись за вопросы."
    return "${days(st.missed)} без доклада. Я уже решил, что ты сменил место службы. Срочно за тренажёр."
}
