package com.binge.companion.sdk

import io.grpc.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private const val HOST = "com.example.host"
private const val OTHER = "com.example.other"
private const val UID = 10_042
private const val CERT = "0011aabb"

class HostSecurityPolicyTest {
    private val uidOwners = mutableMapOf<Int, List<String>>()
    private val signers = mutableMapOf<String, Set<String>>()

    private fun policy(vararg hosts: KnownHost) =
        HostSecurityPolicy(
            hosts = hosts.toList(),
            packagesForUid = { uidOwners[it].orEmpty() },
            signerSha256s = { signers[it].orEmpty() },
        )

    private fun code(policy: HostSecurityPolicy) = policy.checkAuthorization(UID).code

    @Test
    fun `a listed host under a listed certificate is admitted`() {
        uidOwners[UID] = listOf(HOST)
        signers[HOST] = setOf(CERT)

        assertEquals(Status.Code.OK, code(policy(KnownHost(HOST, setOf(CERT)))))
    }

    @Test
    fun `an unlisted package is refused`() {
        uidOwners[UID] = listOf(OTHER)
        signers[OTHER] = setOf(CERT)

        assertEquals(Status.Code.PERMISSION_DENIED, code(policy(KnownHost(HOST, setOf(CERT)))))
    }

    @Test
    fun `a listed package under an unlisted certificate is refused`() {
        uidOwners[UID] = listOf(HOST)
        signers[HOST] = setOf("ffff")

        assertEquals(Status.Code.PERMISSION_DENIED, code(policy(KnownHost(HOST, setOf(CERT)))))
    }

    @Test
    fun `a host with no readable signer is refused`() {
        uidOwners[UID] = listOf(HOST)

        assertEquals(Status.Code.PERMISSION_DENIED, code(policy(KnownHost(HOST, setOf(CERT)))))
    }

    @Test
    fun `a host listed with no certificates matches nothing`() {
        uidOwners[UID] = listOf(HOST)
        signers[HOST] = setOf(CERT)

        assertEquals(Status.Code.PERMISSION_DENIED, code(policy(BingeHosts.release.copy(packageName = HOST))))
    }

    @Test
    fun `digests match regardless of case and separators`() {
        uidOwners[UID] = listOf(HOST)
        signers[HOST] = setOf(CERT)

        assertEquals(Status.Code.OK, code(policy(KnownHost(HOST, setOf("00:11:AA:BB")))))
    }

    @Test
    fun `a shared uid is admitted through its listed owner`() {
        uidOwners[UID] = listOf(OTHER, HOST)
        signers[HOST] = setOf(CERT)

        assertEquals(Status.Code.OK, code(policy(KnownHost(HOST, setOf(CERT)))))
    }
}
