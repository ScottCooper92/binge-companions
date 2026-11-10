package com.binge.companion.kit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** The cipher against a software key: Robolectric has no AndroidKeyStore, and the cipher is the same either way. */
class SecretCipherTest {
    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val key = newKey()
    private val cipher = AesGcmSecretCipher { key }

    @Test
    fun `a secret round-trips`() {
        assertEquals("s3cret é", cipher.decrypt(cipher.encrypt("s3cret é")))
        assertEquals("", cipher.decrypt(cipher.encrypt("")))
    }

    @Test
    fun `each encryption gets a fresh IV`() {
        assertNotEquals(cipher.encrypt("same"), cipher.encrypt("same"))
    }

    @Test
    fun `the blob is a 12-byte IV, the ciphertext and a 16-byte tag`() {
        val blob = Base64.getDecoder().decode(cipher.encrypt("abc"))
        assertEquals(12 + 3 + 16, blob.size)
    }

    @Test
    fun `a second cipher with the same key reads the first's blob`() {
        assertEquals("hello", AesGcmSecretCipher { key }.decrypt(cipher.encrypt("hello")))
    }

    @Test
    fun `a lost key reads as no secret`() {
        assertNull(AesGcmSecretCipher { newKey() }.decrypt(cipher.encrypt("hello")))
    }

    @Test
    fun `a tampered blob reads as no secret`() {
        val blob = Base64.getDecoder().decode(cipher.encrypt("hello"))
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 1).toByte()
        assertNull(cipher.decrypt(Base64.getEncoder().encodeToString(blob)))
    }

    @Test
    fun `a blob shorter than an IV reads as no secret`() {
        assertNull(cipher.decrypt(Base64.getEncoder().encodeToString(ByteArray(5))))
    }

    @Test
    fun `text that is not Base64 reads as no secret`() {
        assertNull(cipher.decrypt("not base64 !!"))
    }

    @Test
    fun `the key is asked for once, on first use`() {
        var calls = 0
        val counting =
            AesGcmSecretCipher {
                calls++
                key
            }
        assertEquals(0, calls)
        counting.decrypt(counting.encrypt("a"))
        counting.encrypt("b")
        assertEquals(1, calls)
    }
}
