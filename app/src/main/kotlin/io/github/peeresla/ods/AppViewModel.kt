package io.github.peeresla.ods

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.peeresla.ods.core.AppState
import io.github.peeresla.ods.core.Backup
import io.github.peeresla.ods.core.Bank
import io.github.peeresla.ods.core.BankCrypto
import io.github.peeresla.ods.core.DAILY_GOAL
import io.github.peeresla.ods.core.Envelope
import io.github.peeresla.ods.core.KEY_ALL
import io.github.peeresla.ods.core.KEY_MISTAKES
import io.github.peeresla.ods.core.Session
import io.github.peeresla.ods.core.SystemClock
import io.github.peeresla.ods.core.TrainView
import io.github.peeresla.ods.core.answerTraining
import io.github.peeresla.ods.core.answeredCount
import io.github.peeresla.ods.core.chooseExamAnswer
import io.github.peeresla.ods.core.clampThreshold
import io.github.peeresla.ods.core.createSession
import io.github.peeresla.ods.core.examFinishConfirmText
import io.github.peeresla.ods.core.examRemaining
import io.github.peeresla.ods.core.finishExam
import io.github.peeresla.ods.core.goalToast
import io.github.peeresla.ods.core.gradeScenario
import io.github.peeresla.ods.core.leaveTrainingText
import io.github.peeresla.ods.core.migrateToBank
import io.github.peeresla.ods.core.moveExam
import io.github.peeresla.ods.core.nextQuestion
import io.github.peeresla.ods.core.prepareTrain
import io.github.peeresla.ods.core.resetProgress
import io.github.peeresla.ods.core.sanitizeExam
import io.github.peeresla.ods.core.startExam
import io.github.peeresla.ods.core.today
import io.github.peeresla.ods.core.withScenario
import io.github.peeresla.ods.data.BankRepository
import io.github.peeresla.ods.data.KeyStorage
import io.github.peeresla.ods.data.StateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Экран — как адрес в веб-версии: «», «train/all», «scenarios/s1»… Глубина = число частей. */
data class Route(val parts: List<String>) {
    val screen: String get() = parts.getOrElse(0) { "" }
    val arg: String? get() = parts.getOrNull(1)
    val depth: Int get() = parts.size

    override fun toString(): String = parts.joinToString("/")

    companion object {
        val Home = Route(emptyList())
        fun of(vararg parts: String) = Route(parts.toList())
        fun parse(s: String?) = Route(s.orEmpty().split('/').filter { it.isNotEmpty() })
    }
}

/** Смена экрана: вперёд, назад, растворение. */
enum class Nav { Forward, Back, Fade }

sealed interface Phase {
    data object Loading : Phase
    data class Locked(val env: Envelope, val text: String) : Phase
    data object Ready : Phase
    /** Банк не загрузился и в кэше его нет. */
    data object NoBank : Phase
}

data class TrainUi(
    val key: String,
    val view: TrainView,
    /** Только что выбран ответ — анимировать варианты и счётчики. */
    val animate: Boolean,
    val recordBumped: Boolean,
)

data class ModalRequest(
    val title: String,
    val text: String,
    val ok: String,
    val cancel: String,
    val onOk: () -> Unit,
)

data class ToastMsg(val text: String, val id: Long)

class AppViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private val store = StateStore(app)
    private val keys = KeyStorage(app)
    private val repo = BankRepository(app)
    private val clock = SystemClock
    private val random = Random.Default

    private val _phase = MutableStateFlow<Phase>(Phase.Loading)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _bank = MutableStateFlow<Bank?>(null)
    val bank: StateFlow<Bank?> = _bank.asStateFlow()

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    /** Текущее время, обновляется раз в секунду — для таймера экзамена и смены дня. */
    private val _now = MutableStateFlow(clock.nowMs())
    val now: StateFlow<Long> = _now.asStateFlow()

    private val _route = MutableStateFlow(Route.parse(saved.get<String>(ROUTE_KEY)))
    val route: StateFlow<Route> = _route.asStateFlow()

    private val _nav = MutableStateFlow(Nav.Fade)
    val nav: StateFlow<Nav> = _nav.asStateFlow()

    private val _train = MutableStateFlow<TrainUi?>(null)
    val train: StateFlow<TrainUi?> = _train.asStateFlow()
    private var session: Session? = null

    private val _revealed = MutableStateFlow<Set<String>>(emptySet())
    val revealed: StateFlow<Set<String>> = _revealed.asStateFlow()

    private val _modal = MutableStateFlow<ModalRequest?>(null)
    val modal: StateFlow<ModalRequest?> = _modal.asStateFlow()

    private val _toast = MutableStateFlow<ToastMsg?>(null)
    val toast: StateFlow<ToastMsg?> = _toast.asStateFlow()

    private val _unlocking = MutableStateFlow(false)
    val unlocking: StateFlow<Boolean> = _unlocking.asStateFlow()

    private val _unlockError = MutableStateFlow<String?>(null)
    val unlockError: StateFlow<String?> = _unlockError.asStateFlow()

    private val _ready = MutableStateFlow(false)
    /** Запуск закончен (банк загружен или ясно, что его нет) — можно убирать заставку. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var saveJob: Job? = null
    private var tickJob: Job? = null
    private var saveWarned = false
    private var toastId = 0L

    init {
        viewModelScope.launch { start() }
    }

    private suspend fun start() {
        _state.value = store.load()
        val fresh = repo.fetch()
        val text = fresh ?: repo.cached()
        val env = text?.let { runCatching { Envelope.parse(it) }.getOrNull() }
        if (env == null) {
            _phase.value = Phase.NoBank
        } else {
            val bank = withContext(Dispatchers.Default) { keys.load(env.kdfId)?.let { BankCrypto.decrypt(env, it) } }
            if (bank != null) {
                if (fresh != null) repo.saveCache(fresh)
                setBank(bank)
                _phase.value = Phase.Ready
            } else {
                _phase.value = Phase.Locked(env, text)
            }
        }
        if (_phase.value == Phase.Ready) refreshTrain()
        _ready.value = true
    }

    /** Ввод пароля. PBKDF2 на 600 000 итераций идёт в фоне. */
    fun unlock(password: String) {
        val locked = _phase.value as? Phase.Locked ?: return
        if (password.isEmpty() || _unlocking.value) return
        _unlocking.value = true
        _unlockError.value = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val key = BankCrypto.deriveKey(password, locked.env)
                    BankCrypto.decrypt(locked.env, key)?.let { key to it }
                }.getOrNull()
            }
            if (result == null) {
                _unlocking.value = false
                _unlockError.value = "Неверный пароль"
                return@launch
            }
            withContext(Dispatchers.IO) { keys.save(locked.env.kdfId, result.first) }
            repo.saveCache(locked.text)
            setBank(result.second)
            _nav.value = Nav.Fade
            _phase.value = Phase.Ready
            _unlocking.value = false
            refreshTrain()
        }
    }

    private fun setBank(bank: Bank) {
        _bank.value = bank
        var st = migrateToBank(_state.value, bank)
        st = sanitizeExam(st, bank, random)
        _state.value = st
        if (st.exam != null && examRemaining(st.exam, clock.nowMs()) <= 0) {
            finishExamNow(timeout = true)
            showToast("Время экзамена истекло — результат сохранён")
        }
        save()
    }

    /* ---------------- Сохранение ---------------- */

    private fun save() {
        saveJob?.cancel()
        saveJob = null
        val snapshot = _state.value
        viewModelScope.launch {
            if (!store.save(snapshot) && !saveWarned) {
                saveWarned = true
                showToast("Не удалось сохранить прогресс на устройстве")
            }
        }
    }

    private fun saveSoon() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            saveJob = null
            save()
        }
    }

    /** Приложение ушло в фон — дописываем отложенное сохранение. */
    fun onBackground() {
        tickJob?.cancel()
        tickJob = null
        if (saveJob != null) save()
    }

    fun onForeground() {
        tick()
        if (tickJob == null) {
            tickJob = viewModelScope.launch {
                while (true) {
                    delay(1000 - clock.nowMs() % 1000)
                    tick()
                }
            }
        }
    }

    private fun tick() {
        _now.value = clock.nowMs()
        val ex = _state.value.exam ?: return
        if (_bank.value == null) return
        if (examRemaining(ex, _now.value) <= 0) {
            val onExam = _route.value.screen == "exam"
            finishExamNow(timeout = true)
            showToast("Время экзамена вышло")
            if (onExam) navigate(Route.of("exam", "result"))
        }
    }

    /* ---------------- Переходы ---------------- */

    fun navigate(to: Route, nav: Nav? = null) {
        val from = _route.value
        _revealed.value = emptySet()
        _nav.value = nav ?: when {
            to.depth > from.depth -> Nav.Forward
            to.depth < from.depth -> Nav.Back
            else -> Nav.Fade
        }
        _route.value = to
        saved[ROUTE_KEY] = to.toString()
        if (to.screen == "train") refreshTrain()
    }

    /** Куда ведёт «Назад» (как ссылки в шапке веб-версии). null — главная, выхода нет. */
    fun backTarget(r: Route = _route.value): Route? = when (r.screen) {
        "" -> null
        "train" -> when (r.arg) {
            KEY_ALL, null -> Route.Home
            KEY_MISTAKES -> Route.of("mistakes")
            else -> Route.of("docs")
        }
        "scenarios" -> if (r.arg != null) Route.of("scenarios") else Route.Home
        else -> Route.Home
    }

    /** «Назад» в шапке и системный жест. false — уже на главной. */
    fun back(): Boolean {
        val target = backTarget() ?: return false
        val s = session
        if (_route.value.screen == "train" && s != null && s.answered > 0) {
            confirmLeaveTraining(s.answered, target)
        } else {
            navigate(target)
        }
        return true
    }

    private fun confirmLeaveTraining(n: Int, target: Route) {
        _modal.value = ModalRequest(
            title = "Выйти из тренировки?",
            text = leaveTrainingText(n),
            ok = "Выйти",
            cancel = "Остаться",
            onOk = {
                session = null
                _train.value = null
                navigate(target)
            },
        )
    }

    fun closeModal(ok: Boolean) {
        val m = _modal.value ?: return
        _modal.value = null
        if (ok) m.onOk()
    }

    private fun confirm(text: String, ok: String, onOk: () -> Unit) {
        _modal.value = ModalRequest(title = "", text = text, ok = ok, cancel = "Отмена", onOk = onOk)
    }

    fun showToast(text: String) {
        _toast.value = ToastMsg(text, ++toastId)
    }

    /* ---------------- Тренировка ---------------- */

    private fun refreshTrain(animate: Boolean = false, recordBumped: Boolean = false) {
        val r = _route.value
        val bank = _bank.value ?: return
        if (r.screen != "train") return
        val key = r.arg ?: KEY_ALL
        var s = session
        if (s == null || s.key != key) s = createSession(key, _state.value, bank, random)
        val (prepared, view) = prepareTrain(s, _state.value, bank, random)
        session = prepared
        _train.value = TrainUi(key, view, animate, recordBumped)
    }

    fun trainAnswer(orig: Int) {
        val bank = _bank.value ?: return
        val s = session ?: return
        val r = answerTraining(_state.value, s, bank, orig, clock.nowMs(), clock.today()) ?: return
        session = r.session
        _state.value = r.state
        save()
        r.toast?.let { showToast(it) }
        refreshTrain(animate = true, recordBumped = r.recordBumped)
    }

    fun trainNext() {
        session = session?.let { nextQuestion(it) }
        refreshTrain()
    }

    fun goMistakesTraining() {
        session = null
        navigate(Route.of("train", KEY_MISTAKES))
    }

    /* ---------------- Экзамен ---------------- */

    fun examStart() {
        val bank = _bank.value ?: return
        _state.value = startExam(_state.value, bank, clock.nowMs(), random)
        _now.value = clock.nowMs()
        save()
    }

    fun examChoose(orig: Int) {
        val ex = _state.value.exam ?: return
        if (examRemaining(ex, clock.nowMs()) <= 0) {
            tick()
            return
        }
        _state.value = chooseExamAnswer(_state.value, orig)
        save()
    }

    fun examMove(delta: Int) {
        if (_state.value.exam == null) return
        _state.value = moveExam(_state.value, delta)
        save()
    }

    fun examFinish() {
        val ex = _state.value.exam ?: return
        val left = ex.ids.size - answeredCount(ex)
        val finish = {
            finishExamNow(timeout = false)
            navigate(Route.of("exam", "result"))
        }
        if (left > 0) confirm(examFinishConfirmText(left), "Завершить", finish) else finish()
    }

    private fun finishExamNow(timeout: Boolean) {
        val bank = _bank.value ?: return
        val (st, goal) = finishExam(_state.value, bank, timeout, clock.nowMs(), clock.today())
        _state.value = st
        save()
        if (goal) showToast(goalToast(st.activity.streak))
    }

    fun newExam() {
        navigate(Route.of("exam"))
    }

    /* ---------------- Ситуации ---------------- */

    fun scenarioReveal(id: String) {
        _revealed.value = _revealed.value + id
    }

    fun scenarioDraft(id: String, text: String) {
        _state.value = withScenario(_state.value, _bank.value, id) { it.copy(draft = text) }
        saveSoon()
    }

    fun scenarioGrade(id: String, grade: String, text: String) {
        val (st, goal) = gradeScenario(_state.value, _bank.value, id, grade, text, clock.nowMs(), clock.today())
        _state.value = st
        _revealed.value = _revealed.value - id
        save()
        showToast(if (goal) goalToast(st.activity.streak) else "Ответ и оценка сохранены")
    }

    /* ---------------- Настройки ---------------- */

    fun thresholdChange(delta: Int) {
        val st = _state.value
        _state.value = st.copy(settings = st.settings.copy(passThreshold = clampThreshold(st.settings.passThreshold + delta)))
        save()
    }

    fun backupText(): String = Backup.export(_state.value, _bank.value?.version, clock.nowMs())

    fun backupFileName(): String = Backup.fileName(clock.nowMs(), clock.zone())

    /** Восстановление из файла: проверка, подтверждение, перенос по отпечаткам. */
    fun importBackup(text: String?) {
        if (text == null) {
            showToast("Не удалось прочитать файл")
            return
        }
        when (val parsed = Backup.parse(text)) {
            is Backup.Parsed.Error -> showToast(parsed.message)
            is Backup.Parsed.Ok -> confirm(Backup.confirmText(parsed.exportedAt), "Восстановить") {
                var st = parsed.state.copy(exam = null)
                session = null
                _train.value = null
                _bank.value?.let { st = migrateToBank(st, it) }
                _state.value = st
                save()
                showToast("Прогресс восстановлен")
            }
        }
    }

    fun resetProgressAsk() {
        confirm(
            "Сбросить весь прогресс: статистику, ошибки, ответы на ситуации и результаты экзамена? Отменить будет нельзя.",
            "Сбросить",
        ) {
            _state.value = resetProgress(_state.value)
            session = null
            _train.value = null
            save()
            showToast("Прогресс сброшен")
        }
    }

    fun forgetPasswordAsk() {
        confirm("Забыть пароль на этом устройстве? Для входа нужно будет ввести его снова.", "Забыть") {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { keys.forget() }
                session = null
                _train.value = null
                _bank.value = null
                _unlockError.value = null
                navigate(Route.Home, Nav.Fade)
                _phase.value = Phase.Loading
                start()
            }
        }
    }

    fun offlineReady(): Boolean = repo.hasCache()

    /** Дневная норма (для текста на главной). */
    val dailyGoal: Int get() = DAILY_GOAL

    private companion object {
        const val ROUTE_KEY = "route"
    }
}
