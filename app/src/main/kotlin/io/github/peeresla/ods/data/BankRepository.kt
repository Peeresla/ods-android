package io.github.peeresla.ods.data

import android.content.Context
import android.util.AtomicFile
import io.github.peeresla.ods.core.Envelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Загрузка зашифрованного банка. Тот же файл, что у веб-версии. */
class BankRepository(context: Context) {
    private val cache = AtomicFile(File(context.filesDir, "bank.enc.json"))

    /** Свежий файл с сайта. Таймаут около 5 с, затем — null (будет использован кэш). */
    suspend fun fetch(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(urlOverride ?: BANK_URL).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.useCaches = false
                conn.setRequestProperty("Cache-Control", "no-cache")
                if (conn.responseCode != 200) return@runCatching null
                val text = conn.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
                Envelope.parse(text) // проверка формата
                text
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    suspend fun cached(): String? = withContext(Dispatchers.IO) {
        runCatching { String(cache.readFully(), Charsets.UTF_8) }.getOrNull()
    }

    /** Кэшируется только банк, который удалось расшифровать. На устройстве он хранится зашифрованным. */
    suspend fun saveCache(text: String) = withContext(Dispatchers.IO) {
        val out = runCatching { cache.startWrite() }.getOrNull() ?: return@withContext
        try {
            out.write(text.toByteArray(Charsets.UTF_8))
            cache.finishWrite(out)
        } catch (e: Exception) {
            cache.failWrite(out)
        }
    }

    fun hasCache(): Boolean = cache.baseFile.exists()

    companion object {
        const val BANK_URL = "https://peeresla.github.io/ods/questions.enc.json"

        /** Только для сквозных тестов: другой адрес банка (например, недоступный — проверка работы офлайн). */
        @Volatile
        var urlOverride: String? = null
    }
}
