package com.yateeshpriv.applockpriv

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Cryptographic utilities for securely hashing and verifying user credentials.
 *
 * Strategy: SHA-256(salt + secret) → 64-char hex string.
 * The salt is generated once per credential change and stored separately.
 * This is a one-way hash — the original secret cannot be recovered.
 */
object CryptoUtils {

    /**
     * Generates a cryptographically random 16-byte salt, returned as a 32-char hex string.
     */
    fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHexString()
    }

    /**
     * Hashes [raw] with [salt] using SHA-256.
     * Returns a 64-character lowercase hex string.
     */
    fun hashSecret(raw: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = (salt + raw).toByteArray(Charsets.UTF_8)
        return digest.digest(input).toHexString()
    }

    /**
     * Returns true if [input] (plaintext from user) matches the [storedHash] when hashed with [salt].
     */
    fun verify(input: String, storedHash: String, salt: String): Boolean {
        return hashSecret(input, salt) == storedHash
    }

    /**
     * Returns true if [stored] appears to be a legacy plaintext secret rather than
     * a SHA-256 hex hash (which is always exactly 64 lowercase hex characters).
     */
    fun isLegacyPlaintext(stored: String): Boolean {
        if (stored.length != 64) return true
        return !stored.all { it.isDigit() || it in 'a'..'f' }
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}
