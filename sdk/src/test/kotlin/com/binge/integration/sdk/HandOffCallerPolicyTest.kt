package com.binge.integration.sdk

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val HOST = "com.example.host"
private const val OTHER = "com.example.other"
private const val CERT = "0011aabb"

class HandOffCallerPolicyTest {
    private val signers = mutableMapOf<String, Set<String>>()

    private fun pinned(vararg hosts: KnownHost) = HandOffCallerPolicy(PinnedHosts(hosts.toList()) { signers[it].orEmpty() })

    @Test
    fun `a listed host under a listed certificate is admitted`() {
        signers[HOST] = setOf(CERT)

        assertTrue(pinned(KnownHost(HOST, setOf(CERT))).permits(HOST))
    }

    @Test
    fun `an unlisted package, or a listed one under another certificate, is refused`() {
        signers[OTHER] = setOf(CERT)
        signers[HOST] = setOf("ffff")

        assertFalse(pinned(KnownHost(HOST, setOf(CERT))).permits(OTHER))
        assertFalse(pinned(KnownHost(HOST, setOf(CERT))).permits(HOST))
    }

    @Test
    fun `a caller that did not start the Activity for a result is refused under every policy`() {
        signers[HOST] = setOf(CERT)

        assertFalse(pinned(KnownHost(HOST, setOf(CERT))).permits(null))
        assertFalse(HandOffCallerPolicy(pinned = null).permits(null))
    }

    @Test
    fun `the debug policy admits any caller that asked for a result`() {
        assertTrue(HandOffCallerPolicy(pinned = null).permits(OTHER))
    }
}
