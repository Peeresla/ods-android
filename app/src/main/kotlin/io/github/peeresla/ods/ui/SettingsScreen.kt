package io.github.peeresla.ods.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import io.github.peeresla.ods.AppViewModel
import io.github.peeresla.ods.BuildConfig
import io.github.peeresla.ods.core.EXAM_SIZE
import io.github.peeresla.ods.core.formatBankDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val c = LocalOdsColors.current
    val state by vm.state.collectAsState()
    val bank by vm.bank.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val threshold = state.settings.passThreshold

    // «Сохранить как»: системный выбор места (Storage Access Framework)
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = vm.backupText()
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) } }.isSuccess
            }
            vm.showToast(if (ok) "Резервная копия сохранена" else "Не удалось сохранить файл")
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)!!.use { String(it.readBytes(), Charsets.UTF_8) } }.getOrNull()
            }
            vm.importBackup(text)
        }
    }

    ScreenScroll {
        SectionTitleTight("Экзамен")
        Column(Modifier.padding(top = 10.dp)) {
            OdsCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    T("Порог «сдано»", Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OdsButton("−", { vm.thresholdChange(-1) }, Modifier.width(48.dp), enabled = threshold > 1, fontSize = 22.sp, contentPadding = PaddingValues(0.dp))
                        T("$threshold из $EXAM_SIZE", Modifier.widthIn(min = 64.dp), weight = FontWeight.SemiBold, align = TextAlign.Center)
                        OdsButton("+", { vm.thresholdChange(1) }, Modifier.width(48.dp), enabled = threshold < EXAM_SIZE, fontSize = 22.sp, contentPadding = PaddingValues(0.dp))
                    }
                }
            }
        }

        ReminderSection(vm)

        SectionTitle("Резервная копия")
        OdsCard(gap = 12.dp) {
            Muted("Сохраняйте копию прогресса в файл и при необходимости восстанавливайте из неё. Копия совместима с веб-версией: через неё можно перенести прогресс в обе стороны.")
            OdsButton("Сохранить резервную копию", { saveLauncher.launch(vm.backupFileName()) }, kind = BtnKind.Primary, block = true)
            OdsButton("Поделиться копией", { shareBackup(context, vm.backupFileName(), vm.backupText()) }, block = true)
            OdsButton("Восстановить", { openLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }, block = true)
        }

        SectionTitle("Прогресс")
        OdsCard { OdsButton("Сбросить прогресс", { vm.resetProgressAsk() }, kind = BtnKind.Danger, block = true) }

        SectionTitle("Доступ")
        OdsCard(gap = 12.dp) {
            Muted("Пароль к вопросам запомнен на этом устройстве. Если его забыть, при следующем запуске пароль спросят снова. Прогресс не пострадает.")
            OdsButton("Забыть пароль на этом устройстве", { vm.forgetPasswordAsk() }, block = true)
        }

        SectionTitle("О приложении")
        OdsCard {
            val b = bank
            if (b == null) {
                Muted("Банк вопросов не загружен.")
            } else {
                if (b.note.isNotEmpty()) T(b.note) else Muted("Примечание к банку вопросов не задано.")
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoRow("Версия банка вопросов", b.version.ifEmpty { "—" })
                    InfoRow("Обновлён", formatBankDate(b.updated).ifEmpty { "—" })
                    InfoRow("Вопросов", "${b.questions.size}")
                    InfoRow("Ситуаций", "${b.scenarios.size}")
                    if (b.skipped > 0) InfoRow("Пропущено (ошибки в файле)", "${b.skipped}")
                    InfoRow("Версия приложения", BuildConfig.VERSION_NAME)
                    InfoRow("Работа офлайн", if (vm.offlineReady()) "готов" else "после первой загрузки")
                }
            }
        }
        Muted("Приложение работает без интернета и никуда не отправляет данные: всё хранится только на этом устройстве.", Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        T(label, Modifier.weight(1f), size = 15.sp, color = LocalOdsColors.current.muted)
        T(value, size = 15.sp, align = TextAlign.End)
    }
}

/** «Поделиться»: файл копии через системное меню (Telegram, почта, «Файлы»…). */
private fun shareBackup(context: Context, name: String, text: String) {
    runCatching {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, name)
        file.writeText(text, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, "Резервная копия ОДС")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Резервная копия ОДС"))
    }
}

@Composable
fun UnlockScreen(vm: AppViewModel) {
    val c = LocalOdsColors.current
    val unlocking by vm.unlocking.collectAsState()
    val error by vm.unlockError.collectAsState()
    var password by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    ScreenScroll {
        OdsCard {
            T("Вход", size = 22.sp, weight = FontWeight.Bold, lineHeight = 1.3.em)
            Muted("Вопросы зашифрованы. Введите пароль — на этом устройстве он понадобится один раз.", Modifier.padding(top = 16.dp))
            FieldLabel("Пароль")
            BasicTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                singleLine = true,
                enabled = !unlocking,
                textStyle = BaseText.copy(fontSize = 16.sp, color = c.text),
                cursorBrush = SolidColor(c.accent),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { vm.unlock(password) }),
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(shape)
                            .background(c.surface)
                            .border(1.5.dp, if (focused) c.accent else c.border, shape)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) { inner() }
                },
            )
            if (error != null) T(error!!, Modifier.padding(top = 8.dp), size = 15.sp, weight = FontWeight.SemiBold, color = c.bad)
            OdsButton(
                if (unlocking) "Проверяю…" else "Открыть",
                { vm.unlock(password) },
                Modifier.padding(top = 16.dp),
                kind = BtnKind.Primary,
                block = true,
                enabled = !unlocking,
            )
        }
    }
}

/** Этап 2: вечернее напоминание и виброотклик. Настройки — только этого телефона, в резервную копию не попадают. */
@Composable
private fun ReminderSection(vm: AppViewModel) {
    val context = LocalContext.current
    val extras by vm.extras.collectAsState()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setReminder(true) else vm.showToast("Без разрешения на уведомления напоминание не придёт")
    }
    val time = "%02d:%02d".format(java.util.Locale.ROOT, extras.hour, extras.minute)

    SectionTitle("Напоминание")
    OdsCard(gap = 12.dp) {
        SwitchRow("Напомнить вечером, если норма на сегодня не выполнена", extras.reminderOn) { on ->
            if (on && android.os.Build.VERSION.SDK_INT >= 33 &&
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                vm.setReminder(on)
            }
        }
        if (extras.reminderOn) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                T("Время", Modifier.weight(1f))
                OdsButton(time, {
                    android.app.TimePickerDialog(context, { _, h, m -> vm.setReminderTime(h, m) }, extras.hour, extras.minute, true).show()
                }, Modifier.widthIn(min = 96.dp))
            }
            Muted("Слова — от начальника ЦУКС, как на главной. Если норма уже выполнена, напоминания не будет.")
            OdsButton("Проверить уведомление", { vm.testReminder() }, block = true)
            Muted("На телефонах Tecno (HiOS) система может закрывать приложения в фоне, и напоминание не придёт. Обычно помогает: «Настройки приложения» → «Батарея» → «Без ограничений», а в приложении «Phone Master» → «Автозапуск» — включить ОДС.")
            OdsButton("Настройки приложения", { openAppSettings(context) }, block = true)
        }
    }

    SectionTitle("Отклик")
    OdsCard {
        SwitchRow("Виброотклик при ответе", extras.haptics) { vm.setHaptics(it) }
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalOdsColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        T(text, Modifier.weight(1f))
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = null,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                checkedTrackColor = c.accent,
                checkedBorderColor = c.accent,
                uncheckedThumbColor = c.muted,
                uncheckedTrackColor = c.surface2,
                uncheckedBorderColor = c.border,
            ),
        )
    }
}

private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
