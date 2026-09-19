package com.get.detail.rentdesk.lock

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object CredentialHasher {
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private const val ITERATIONS = 210_000

    data class Result(val salt: ByteArray, val hash: ByteArray)

    fun create(credential: CharArray): Result {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        return Result(salt, derive(credential, salt))
    }

    fun verify(credential: CharArray, salt: ByteArray, expectedHash: ByteArray): Boolean {
        return MessageDigest.isEqual(derive(credential, salt), expectedHash)
    }

    private fun derive(credential: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(credential, salt, ITERATIONS, KEY_BITS)
        return try {
            // HMAC-SHA1 PBKDF2 is available across the app's full API 24+ range.
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            credential.fill('\u0000')
        }
    }
}
