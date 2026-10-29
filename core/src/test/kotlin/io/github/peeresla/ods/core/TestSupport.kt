package io.github.peeresla.ods.core

import java.time.LocalDateTime
import java.time.ZoneId

const val TEST_PASSWORD = "test-pass-123"

fun fixture(name: String): String =
    requireNotNull(object {}.javaClass.getResource("/fixtures/$name")) { "нет файла $name" }.readText()

fun testBank(): Bank = Bank.parse(fixture("test-bank.json"))

/** Часы, которые можно переводить. */
class FakeClock(var ms: Long, private val zone: ZoneId = ZoneId.of("Europe/Moscow")) : Clock {
    override fun nowMs(): Long = ms
    override fun zone(): ZoneId = zone

    fun set(dateTime: String) {
        ms = LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli()
    }

    fun advance(millis: Long) {
        ms += millis
    }
}
