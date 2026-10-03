package com.binge.companion.sdk

import io.grpc.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val UID = 10_042

/** The debug policy: Binge's package names under any certificate, never every app on the device (#122). */
class HostPackagePolicyTest {
    private val uidOwners = mutableMapOf<Int, List<String>>()
    private val warnings = mutableListOf<String>()

    private fun policy(vararg names: String = BingeHosts.PACKAGE_NAMES.toTypedArray()) =
        HostPackagePolicy(names.toList(), packagesForUid = { uidOwners[it].orEmpty() }, warn = { warnings += it })

    @Test
    fun `debug Binge is admitted under any certificate, and the admission is logged`() {
        uidOwners[UID] = listOf(BingeHosts.DEBUG_PACKAGE_NAME)

        assertEquals(Status.Code.OK, policy().checkAuthorization(UID).code)
        assertTrue(warnings.single().contains(BingeHosts.DEBUG_PACKAGE_NAME))
    }

    @Test
    fun `release Binge is admitted too, by default`() {
        uidOwners[UID] = listOf(BingeHosts.RELEASE_PACKAGE_NAME)

        assertEquals(Status.Code.OK, policy().checkAuthorization(UID).code)
    }

    @Test
    fun `any other app is refused`() {
        uidOwners[UID] = listOf("com.example.other")

        assertEquals(Status.Code.PERMISSION_DENIED, policy().checkAuthorization(UID).code)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `a uid with no packages is refused`() {
        assertEquals(Status.Code.PERMISSION_DENIED, policy().checkAuthorization(UID).code)
    }

    @Test
    fun `a caller may name its own test host`() {
        uidOwners[UID] = listOf("com.example.testhost")

        assertEquals(Status.Code.OK, policy("com.example.testhost").checkAuthorization(UID).code)
    }
}
