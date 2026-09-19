package com.get.detail.rentdesk.lock

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AppLockManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    val isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false) && lockType != null && prefs.contains(KEY_CREDENTIAL)

    val lockType: LockType?
        get() = prefs.getString(KEY_LOCK_TYPE, null)?.let {
            runCatching { LockType.valueOf(it) }.getOrNull()
        }

    var biometricsEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRICS, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRICS, value).apply()

    var timeoutMillis: Long
        get() = prefs.getLong(KEY_TIMEOUT, 0L)
        set(value) = prefs.edit().putLong(KEY_TIMEOUT, value.coerceAtLeast(0L)).apply()

    fun saveCredential(type: LockType, credential: String) {
        val result = CredentialHasher.create(credential.toCharArray())
        val payload = ByteBuffer.allocate(8 + result.salt.size + result.hash.size)
            .putInt(result.salt.size)
            .put(result.salt)
            .putInt(result.hash.size)
            .put(result.hash)
            .array()
        val encrypted = encrypt(payload)
        prefs.edit()
            .putString(KEY_LOCK_TYPE, type.name)
            .putString(KEY_CREDENTIAL, Base64.encodeToString(encrypted.data, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(encrypted.iv, Base64.NO_WRAP))
            .putBoolean(KEY_ENABLED, true)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .remove(KEY_LOCKED_UNTIL)
            .apply()
    }

    fun verifyCredential(credential: String): Boolean {
        if (remainingLockoutSeconds() > 0) return false
        val encodedData = prefs.getString(KEY_CREDENTIAL, null) ?: return false
        val encodedIv = prefs.getString(KEY_IV, null) ?: return false
        return runCatching {
            val payload = decrypt(
                Base64.decode(encodedData, Base64.NO_WRAP),
                Base64.decode(encodedIv, Base64.NO_WRAP)
            )
            val buffer = ByteBuffer.wrap(payload)
            val saltSize = buffer.int
            require(saltSize in 8..64 && saltSize <= buffer.remaining())
            val salt = ByteArray(saltSize).also(buffer::get)
            val hashSize = buffer.int
            require(hashSize in 16..64 && hashSize <= buffer.remaining())
            val expectedHash = ByteArray(hashSize).also(buffer::get)
            CredentialHasher.verify(credential.toCharArray(), salt, expectedHash)
        }.getOrDefault(false)
    }

    fun recordFailedAttempt(): Long {
        val attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        if (attempts >= MAX_ATTEMPTS) {
            val lockedUntil = System.currentTimeMillis() + LOCKOUT_MILLIS
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKED_UNTIL, lockedUntil)
                .apply()
            return LOCKOUT_MILLIS / 1000
        }
        prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts).apply()
        return 0
    }

    fun clearFailedAttempts() {
        prefs.edit().putInt(KEY_FAILED_ATTEMPTS, 0).remove(KEY_LOCKED_UNTIL).apply()
    }

    fun remainingLockoutSeconds(): Long {
        val remaining = prefs.getLong(KEY_LOCKED_UNTIL, 0L) - System.currentTimeMillis()
        if (remaining <= 0) {
            prefs.edit().remove(KEY_LOCKED_UNTIL).apply()
            return 0
        }
        return (remaining + 999) / 1000
    }

    fun disable() {
        prefs.edit().clear().apply()
        AppLockSession.markUnlocked()
    }

    private fun encrypt(plainText: ByteArray): EncryptedValue {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return EncryptedValue(cipher.iv, cipher.doFinal(plainText))
    }

    private fun decrypt(cipherText: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(cipherText)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private data class EncryptedValue(val iv: ByteArray, val data: ByteArray)

    companion object {
        private const val PREF_NAME = "RentDeskAppLock"
        private const val KEY_ALIAS = "rent_desk_app_lock_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LOCK_TYPE = "lockType"
        private const val KEY_CREDENTIAL = "credential"
        private const val KEY_IV = "credentialIv"
        private const val KEY_BIOMETRICS = "biometrics"
        private const val KEY_TIMEOUT = "timeout"
        private const val KEY_FAILED_ATTEMPTS = "failedAttempts"
        private const val KEY_LOCKED_UNTIL = "lockedUntil"
        private const val MAX_ATTEMPTS = 5
        private const val LOCKOUT_MILLIS = 30_000L
    }
}
