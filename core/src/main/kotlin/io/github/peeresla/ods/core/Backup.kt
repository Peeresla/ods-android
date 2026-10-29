package io.github.peeresla.ods.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.ZoneId

/** Резервная копия, совместимая с веб-версией в обе стороны. */
object Backup {
    fun fileName(now: Long, zone: ZoneId): String = "ods-trainer-backup-${dayKey(now, zone)}.json"

    /** При экспорте начатый экзамен не сохраняется. Ключ и пароль в копию не попадают. */
    fun export(state: AppState, bankVersion: String?, now: Long): String {
        val payload = buildJsonObject {
            put("app", "ods-trainer")
            put("schema", STATE_SCHEMA)
            put("exportedAt", isoString(now))
            put("bankVersion", bankVersion?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: JsonNull)
            put("state", state.copy(exam = null).toJson())
        }
        return prettyJson.encodeToString(JsonElement.serializer(), payload)
    }

    sealed interface Parsed {
        data class Ok(val state: AppState, val exportedAt: String?) : Parsed
        data class Error(val message: String) : Parsed
    }

    /** Читает копию. Восстановленный прогресс ещё нужно перенести на банк (migrateToBank). */
    fun parse(text: String): Parsed {
        val payload = runCatching { Json.parseToJsonElement(text.removePrefix("﻿")) }.getOrNull()
            ?: return Parsed.Error("Не удалось прочитать файл")
        val obj = payload.obj()
        if (obj == null || obj["app"].str() != "ods-trainer" || obj["state"].obj() == null) {
            return Parsed.Error("Это не резервная копия тренажёра")
        }
        val state = AppState.normalize(obj["state"]).copy(exam = null)
        val exportedAt = obj["exportedAt"]?.takeIf { it.truthy() }?.jsString()
        return Parsed.Ok(state, exportedAt)
    }

    fun confirmText(exportedAt: String?, zone: ZoneId = ZoneId.systemDefault()): String {
        val exported = exportedAt?.let { " от ${formatDateTime(it, zone)}" } ?: ""
        return "Восстановить прогресс из копии$exported? Текущий прогресс будет заменён."
    }
}
