package io.github.peeresla.ods.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Сохранённый ключ банка (не пароль) вместе с идентификатором параметров KDF.
 * Сам ключ зашифрован ключом из Android Keystore, который не покидает устройство.
 * Файл unlock.xml исключён из резервного копирования Android.
 */
class KeyStorage(context: Context) {
    private val prefs = context.getSharedPreferences("unlock", Context.MODE_PRIVATE)

    /** Ключ, если он сохранён для этих же параметров KDF. */
    fun load(kdfId: String): ByteArray? {
        if (prefs.getString(KDF, null) != kdfId) return null
        val wrapped = prefs.getString(KEY, null) ?: return null
        val iv = prefs.getString(IV, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, masterKey(create = false) ?: return null, GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            cipher.doFinal(Base64.decode(wrapped, Base64.NO_WRAP))
        }.getOrNull()
    }

    fun save(kdfId: String, key: ByteArray) {
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, masterKey(create = true))
            val wrapped = cipher.doFinal(key)
            prefs.edit()
                .putString(KDF, kdfId)
                .putString(KEY, Base64.encodeToString(wrapped, Base64.NO_WRAP))
                .putString(IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .commit()
        }
    }

    fun forget() {
        prefs.edit().clear().commit()
    }

    private fun masterKey(create: Boolean): SecretKey? {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        if (!create) return null
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private companion object {
        const val ALIAS = "ods-bank-key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KDF = "kdf"
        const val KEY = "key"
        const val IV = "iv"
    }
}
