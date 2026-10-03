package com.binge.companion.sdk

import android.os.Build
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private val CERT_A = byteArrayOf(1, 2, 3)
private val CERT_B = byteArrayOf(4, 5, 6)

class SignerDigestsTest {
    private val modern = Build.VERSION_CODES.P

    private fun single(cert: ByteArray = CERT_A) = SignerSnapshot(hasMultipleSigners = false, certificates = listOf(cert))

    @Test
    fun `a single current signer yields its SHA-256`() {
        val digests = signerDigests(modern) { single() }

        assertEquals(setOf(sha256Hex(CERT_A)), digests)
    }

    @Test
    fun `the digest is lowercase hex of the SHA-256`() {
        assertEquals("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81", sha256Hex(CERT_A))
    }

    @Test
    fun `below API 28 nothing is trusted, even with a valid signer`() {
        assertEquals(emptySet<String>(), signerDigests(Build.VERSION_CODES.O_MR1) { single() })
    }

    @Test
    fun `a package with several current signers has no identity`() {
        val multi = SignerSnapshot(hasMultipleSigners = true, certificates = listOf(CERT_A, CERT_B))

        assertEquals(emptySet<String>(), signerDigests(modern) { multi })
    }

    @Test
    fun `a failed lookup has no signer`() {
        assertEquals(emptySet<String>(), signerDigests(modern) { error("NameNotFoundException stand-in") })
    }

    @Test
    fun `a package with no signing info has no signer`() {
        assertEquals(emptySet<String>(), signerDigests(modern) { null })
    }

    @Test
    fun `an API 28 floor is checked before the lookup runs`() {
        var looked = false

        signerDigests(Build.VERSION_CODES.O_MR1) {
            looked = true
            single()
        }

        assertEquals(false, looked)
    }

    /** A host that rotated its key keeps the old certificate in its lineage, so a digest pinned before still matches (#115). */
    @Test
    fun `a rotated signer is admitted through its history`() {
        val rotated = SignerSnapshot(hasMultipleSigners = false, certificates = listOf(CERT_A, CERT_B))

        assertEquals(setOf(sha256Hex(CERT_A), sha256Hex(CERT_B)), signerDigests(modern) { rotated })
    }
}
