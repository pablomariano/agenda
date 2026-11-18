package com.pablomariano.agenda

import android.app.Application
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AccessCodeStore(application: Application) {
    private val prefs = application.getSharedPreferences("agenda_access", Context.MODE_PRIVATE)
    private val alias = "agenda_personal_access"
    private val origin = BuildConfig.AGENDA_API_URL.toByteArray(Charsets.UTF_8)

    suspend fun read(): String? = withContext(Dispatchers.IO) {
        val stored = prefs.getString("ciphertext", null) ?: return@withContext null
        runCatching {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            require(bytes.size == 92)
            val key = keyStore().getKey(alias, null) as SecretKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            cipher.updateAAD(origin)
            cipher.doFinal(bytes, 12, bytes.size - 12).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    suspend fun write(code: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val key = keyStore().getKey(alias, null) as? SecretKey
                ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                    init(
                        KeyGenParameterSpec.Builder(
                            alias,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                            .setKeySize(256)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .build(),
                    )
                    generateKey()
                }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            cipher.updateAAD(origin)
            val bytes = cipher.iv + cipher.doFinal(code.toByteArray(Charsets.UTF_8))
            prefs.edit().putString("ciphertext", Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()
        }.getOrDefault(false)
    }

    suspend fun clear(): Boolean = withContext(Dispatchers.IO) {
        val removed = prefs.edit().remove("ciphertext").commit()
        val deleted = runCatching { keyStore().deleteEntry(alias) }.isSuccess
        removed || deleted
    }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}
