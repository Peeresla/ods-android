package io.github.peeresla.ods.extras

import android.content.Context
import android.util.AtomicFile
import io.github.peeresla.ods.core.AppState
import java.io.File

/** Прогресс для виджета и напоминания, когда приложение закрыто. Тот же файл, что пишет StateStore. */
object StateFile {
    fun read(context: Context): AppState {
        val text = runCatching { String(AtomicFile(File(context.filesDir, "state.json")).readFully(), Charsets.UTF_8) }.getOrNull()
        return AppState.parse(text)
    }
}
