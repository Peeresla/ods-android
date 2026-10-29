package io.github.peeresla.ods.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

// Терпимое чтение JSON — так же, как это делает app.js: лишние и неверные поля не ломают разбор.

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
internal val prettyJson = Json { prettyPrint = true; prettyPrintIndent = "  " }

internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject
internal fun JsonElement?.arr(): JsonArray? = this as? JsonArray

/** Строка, только если в JSON это именно строка (typeof v === 'string'). */
internal fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/** Число, только если в JSON это число. */
internal fun JsonElement?.num(): Double? =
    (this as? JsonPrimitive)?.takeIf { !it.isString && it !is JsonNull }?.let { it.longOrNull?.toDouble() ?: it.doubleOrNull }

/** Целое (Number.isInteger). */
internal fun JsonElement?.int(): Long? {
    val p = this as? JsonPrimitive ?: return null
    if (p.isString || p is JsonNull) return null
    p.longOrNull?.let { return it }
    val d = p.doubleOrNull ?: return null
    return if (d.isFinite() && d == Math.floor(d)) d.toLong() else null
}

internal fun JsonElement?.isTrue(): Boolean =
    (this as? JsonPrimitive)?.let { !it.isString && it.booleanOrNull == true } == true

/** Boolean(v) из JS. */
internal fun JsonElement?.truthy(): Boolean {
    val p = this ?: return false
    if (p is JsonNull) return false
    if (p !is JsonPrimitive) return true
    if (p.isString) return p.content.isNotEmpty()
    p.booleanOrNull?.let { return it }
    val d = p.doubleOrNull ?: return false
    return d != 0.0 && !d.isNaN()
}

/** null или undefined в JS (`v != null`). */
internal fun JsonElement?.isNullish(): Boolean = this == null || this is JsonNull

/** String(v) из JS для простых значений. */
internal fun JsonElement.jsString(): String = when (this) {
    is JsonNull -> "null"
    is JsonPrimitive -> if (isString) content else {
        val d = doubleOrNull
        when {
            longOrNull != null -> longOrNull.toString()
            d != null && d.isFinite() && d == Math.floor(d) && Math.abs(d) < 1e21 -> d.toLong().toString()
            else -> contentOrNull ?: ""
        }
    }
    is JsonArray -> joinToString(",") { if (it.isNullish()) "" else it.jsString() }
    is JsonObject -> "[object Object]"
}

/** toCount из app.js: неотрицательное целое, иначе 0. */
internal fun toCount(v: JsonElement?): Long {
    val p = v as? JsonPrimitive ?: return 0
    if (p is JsonNull) return 0
    val d = if (p.isString) p.content.trim().let { if (it.isEmpty()) 0.0 else it.toDoubleOrNull() } else (p.longOrNull?.toDouble() ?: p.doubleOrNull ?: p.booleanOrNull?.let { if (it) 1.0 else 0.0 })
    if (d == null || !d.isFinite() || d < 0) return 0
    return Math.floor(d).toLong()
}

/**
 * Порядок ключей объекта как в JS: сначала ключи-индексы массива по возрастанию, затем остальные в порядке записи.
 * От него зависит, какой прогресс побеждает при переносе по отпечаткам.
 */
internal fun <V> jsKeyOrder(entries: Map<String, V>): List<Pair<String, V>> {
    val (index, other) = entries.entries.partition { isArrayIndex(it.key) }
    return index.sortedBy { it.key.toLong() }.map { it.key to it.value } + other.map { it.key to it.value }
}

private fun isArrayIndex(key: String): Boolean {
    if (key.isEmpty() || key.length > 10) return false
    if (key == "0") return true
    if (key[0] == '0' || !key.all { it in '0'..'9' }) return false
    return key.toLong() < 4294967295L
}
