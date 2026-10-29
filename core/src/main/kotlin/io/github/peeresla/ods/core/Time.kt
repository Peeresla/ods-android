package io.github.peeresla.ods.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Источник времени. В тестах подменяется. */
interface Clock {
    fun nowMs(): Long
    fun zone(): ZoneId
}

object SystemClock : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/** ГГГГ-ММ-ДД по местной дате (dayKey в app.js). */
fun Clock.today(): String = dayKey(nowMs(), zone())

fun dayKey(ms: Long, zone: ZoneId): String = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString()

fun daysBetween(fromKey: String, toKey: String): Long =
    ChronoUnit.DAYS.between(LocalDate.parse(fromKey), LocalDate.parse(toKey))

private val ISO_MS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

/** new Date().toISOString() */
fun isoString(ms: Long): String = ISO_MS.format(Instant.ofEpochMilli(ms))

fun parseIso(value: String): Instant? =
    runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant() }.getOrNull()

private val RU_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")

/** formatDateTime: «29.10.2026, 14:05», как toLocaleString('ru-RU'). */
fun formatDateTime(value: String, zone: ZoneId = ZoneId.systemDefault()): String =
    parseIso(value)?.atZone(zone)?.format(RU_DATE_TIME) ?: ""

private val BANK_DATE = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")

fun formatBankDate(value: String): String =
    BANK_DATE.matchEntire(value)?.groupValues?.let { "${it[3]}.${it[2]}.${it[1]}" } ?: value

/** mm:ss, секунды округляются вверх. */
fun formatDuration(ms: Long): String {
    val total = Math.max(0L, Math.ceil(ms / 1000.0).toLong())
    return (total / 60).toString().padStart(2, '0') + ":" + (total % 60).toString().padStart(2, '0')
}

fun percentText(p: Int?): String = if (p == null) "—" else "$p%"

fun plural(n: Long, forms: List<String>): String {
    val n10 = n % 10
    val n100 = n % 100
    if (n10 == 1L && n100 != 11L) return forms[0]
    if (n10 in 2..4 && (n100 < 10 || n100 >= 20)) return forms[1]
    return forms[2]
}

fun plural(n: Int, forms: List<String>): String = plural(n.toLong(), forms)

val DAYS = listOf("день", "дня", "дней")
