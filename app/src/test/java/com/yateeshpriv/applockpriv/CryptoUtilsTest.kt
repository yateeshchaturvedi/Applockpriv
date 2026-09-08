package com.yateeshpriv.applockpriv

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [CryptoUtils] — verifiable with no Android framework dependencies.
 * Run with: ./gradlew test
 */
class CryptoUtilsTest {

    @Test
    fun `hashSecret produces a 64-char hex string`() {
        val hash = CryptoUtils.hashSecret("password", "salt")
        assertEquals("Hash must be 64 hex chars (SHA-256)", 64, hash.length)
        assertTrue("Hash must be lowercase hex", hash.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun `hashSecret is deterministic for the same inputs`() {
        val salt  = "fixed_salt_1234"
        val hash1 = CryptoUtils.hashSecret("my_secret", salt)
        val hash2 = CryptoUtils.hashSecret("my_secret", salt)
        assertEquals("Same inputs must always produce the same hash", hash1, hash2)
    }

    @Test
    fun `hashSecret produces different outputs for different salts`() {
        val hash1 = CryptoUtils.hashSecret("secret", "salt_a")
        val hash2 = CryptoUtils.hashSecret("secret", "salt_b")
        assertNotEquals("Different salts must produce different hashes", hash1, hash2)
    }

    @Test
    fun `hashSecret produces different outputs for different secrets`() {
        val salt  = CryptoUtils.generateSalt()
        val hash1 = CryptoUtils.hashSecret("password1", salt)
        val hash2 = CryptoUtils.hashSecret("password2", salt)
        assertNotEquals("Different secrets must produce different hashes", hash1, hash2)
    }

    @Test
    fun `verify returns true for the correct input`() {
        val salt  = CryptoUtils.generateSalt()
        val hash  = CryptoUtils.hashSecret("correct_password", salt)
        assertTrue("Correct input must verify", CryptoUtils.verify("correct_password", hash, salt))
    }

    @Test
    fun `verify returns false for a wrong input`() {
        val salt = CryptoUtils.generateSalt()
        val hash = CryptoUtils.hashSecret("correct_password", salt)
        assertFalse("Wrong input must not verify", CryptoUtils.verify("wrong_password", hash, salt))
    }

    @Test
    fun `verify returns false for empty input`() {
        val salt = CryptoUtils.generateSalt()
        val hash = CryptoUtils.hashSecret("password", salt)
        assertFalse("Empty string must not verify against a real hash", CryptoUtils.verify("", hash, salt))
    }

    @Test
    fun `generateSalt produces a 32-char hex string`() {
        val salt = CryptoUtils.generateSalt()
        assertEquals("Salt must be 32 hex chars (16 random bytes)", 32, salt.length)
        assertTrue("Salt must be lowercase hex", salt.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun `generateSalt produces unique values each call`() {
        val salts = (1..20).map { CryptoUtils.generateSalt() }.toSet()
        assertEquals("All 20 generated salts should be unique", 20, salts.size)
    }

    @Test
    fun `isLegacyPlaintext returns true for a short PIN`() {
        assertTrue(CryptoUtils.isLegacyPlaintext("123456"))
    }

    @Test
    fun `isLegacyPlaintext returns true for a text password`() {
        assertTrue(CryptoUtils.isLegacyPlaintext("myPassword1!"))
    }

    @Test
    fun `isLegacyPlaintext returns false for a valid SHA-256 hex hash`() {
        val hash = CryptoUtils.hashSecret("test", "salt")
        assertFalse("A 64-char hex hash must NOT be flagged as plaintext", CryptoUtils.isLegacyPlaintext(hash))
    }

    @Test
    fun `isLegacyPlaintext returns true for a 64-char non-hex string`() {
        // 64 chars but with uppercase (not valid hash output) — should be treated as legacy
        val nonHex = "A".repeat(64)
        assertTrue("Uppercase 64-char string is not a valid hash", CryptoUtils.isLegacyPlaintext(nonHex))
    }
}
