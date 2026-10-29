package io.github.peeresla.ods.core

import kotlinx.serialization.json.Json
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Зашифрованный банк (questions.enc.json), формат tools/build.mjs. */
class Envelope(
    val kdfName: String,
    val kdfHash: String,
    val iterations: Int,
    val salt: String,
    val iv: String,
    val data: String,
) {
    /** Идентификатор параметров KDF: name:hash:iterations:salt (kdfId в app.js). */
    val kdfId: String get() = "$kdfName:$kdfHash:$iterations:$salt"

    companion object {
        fun parse(text: String): Envelope {
            val env = runCatching { Json.parseToJsonElement(text) }.getOrNull().obj()
            val kdf = env?.get("kdf").obj()
            val cipher = env?.get("cipher").obj()
            if (env == null || env["format"].str() != "ods-trainer-encrypted" || kdf == null || cipher == null) {
                throw IllegalArgumentException("Неверный формат файла вопросов")
            }
            return Envelope(
                kdfName = kdf["name"]?.jsString() ?: "",
                kdfHash = kdf["hash"]?.jsString() ?: "",
                iterations = kdf["iterations"].int()?.toInt() ?: 0,
                salt = kdf["salt"].str() ?: "",
                iv = cipher["iv"].str() ?: "",
                data = env["data"].str() ?: "",
            )
        }
    }
}

object BankCrypto {
    /**
     * PBKDF2-HMAC-SHA256 (пароль в UTF-8) → 256-битный ключ AES. Своя реализация, а не SecretKeyFactory:
     * так кодировка пароля гарантированно UTF-8 на любом Android, как в WebCrypto.
     * 600 000 итераций — вызывать только в фоне.
     */
    fun deriveKey(password: String, env: Envelope): ByteArray {
        require(env.kdfName == "PBKDF2" && env.kdfHash == "SHA-256" && env.iterations > 0) { "Неизвестные параметры шифрования" }
        return pbkdf2Sha256(password.toByteArray(Charsets.UTF_8), Base64.getDecoder().decode(env.salt), env.iterations, 32)
    }

    fun pbkdf2Sha256(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        val hLen = mac.macLength
        val blocks = (length + hLen - 1) / hLen
        val out = ByteArray(length)
        val u = ByteArray(hLen)
        val t = ByteArray(hLen)
        for (block in 1..blocks) {
            mac.update(salt)
            mac.update(byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
            mac.doFinal(u, 0)
            u.copyInto(t)
            for (i in 1 until iterations) {
                mac.update(u)
                mac.doFinal(u, 0)
                for (j in 0 until hLen) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            val offset = (block - 1) * hLen
            t.copyInto(out, offset, 0, Math.min(hLen, length - offset))
        }
        return out
    }

    /** Расшифровывает текст банка. null — ключ не подходит или файл повреждён. */
    fun decryptText(env: Envelope, key: ByteArray): String? = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, Base64.getDecoder().decode(env.iv)))
        String(cipher.doFinal(Base64.getDecoder().decode(env.data)), Charsets.UTF_8)
    }.getOrNull()

    /** Расшифровывает и нормализует банк. null — ключ не подходит. */
    fun decrypt(env: Envelope, key: ByteArray): Bank? =
        decryptText(env, key)?.let { runCatching { Bank.parse(it) }.getOrNull() }
}
