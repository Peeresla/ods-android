package io.github.peeresla.ods.data

import android.content.Context
import android.util.AtomicFile
import io.github.peeresla.ods.core.AppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Прогресс в одном JSON-файле той же структуры, что и в веб-версии.
 * AtomicFile: запись целиком или никак — переживает убийство процесса посреди записи.
 */
class StateStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "state.json"))
    private val mutex = Mutex()

    suspend fun load(): AppState = withContext(Dispatchers.IO) {
        mutex.withLock {
            val text = runCatching { String(file.readFully(), Charsets.UTF_8) }.getOrNull()
            AppState.parse(text)
        }
    }

    /** false — записать не удалось. */
    suspend fun save(state: AppState): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val bytes = state.serialize().toByteArray(Charsets.UTF_8)
            val out = runCatching { file.startWrite() }.getOrNull() ?: return@withLock false
            try {
                out.write(bytes)
                file.finishWrite(out)
                true
            } catch (e: Exception) {
                file.failWrite(out)
                false
            }
        }
    }
}
