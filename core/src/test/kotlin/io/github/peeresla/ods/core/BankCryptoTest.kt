package io.github.peeresla.ods.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BankCryptoTest {
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    @Test
    fun pbkdf2Vectors() {
        assertEquals(
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            hex(BankCrypto.pbkdf2Sha256("password".toByteArray(), "salt".toByteArray(), 1, 32)),
        )
        assertEquals(
            "c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a",
            hex(BankCrypto.pbkdf2Sha256("password".toByteArray(), "salt".toByteArray(), 4096, 32)),
        )
    }

    @Test
    fun decryptsFileMadeByBuildMjs() {
        val env = Envelope.parse(fixture("test-bank.enc.json"))
        assertEquals("PBKDF2:SHA-256:600000:opBwVqLNSPlhYQuQMyRx/A==", env.kdfId)
        val key = BankCrypto.deriveKey(TEST_PASSWORD, env)
        val bank = assertNotNull(BankCrypto.decrypt(env, key))
        assertEquals("test-1", bank.version)
        assertEquals(listOf("1", "2", "3"), bank.questions.map { it.id })
        assertEquals(3, bank.skipped)
        assertEquals(2, bank.scenarios.size)
        assertTrue(bank.questionsById.getValue("2").internal)
    }

    @Test
    fun wrongPassword() {
        val env = Envelope.parse(fixture("test-bank.enc.json"))
        assertNull(BankCrypto.decrypt(env, BankCrypto.deriveKey("wrong-password", env)))
    }

    @Test
    fun badEnvelope() {
        assertFailsWith<IllegalArgumentException> { Envelope.parse("{\"format\":\"other\"}") }
        assertFailsWith<IllegalArgumentException> { Envelope.parse("not json") }
    }
}
