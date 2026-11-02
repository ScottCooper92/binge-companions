package com.binge.companion.sdk

import android.app.Application
import io.grpc.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

private const val HOST = "com.example.host"
private const val HOST_UID = 10_042
private const val STRANGER = "com.example.stranger"
private const val STRANGER_UID = 10_099
private val OLD_KEY = byteArrayOf(1, 2, 3)
private val NEW_KEY = byteArrayOf(4, 5, 6)

/**
 * The Android lookups behind [HostPolicy] (#282): a uid resolved to its packages through the package manager, and a package's
 * signing lineage read from it. The decisions are [HostSecurityPolicy] and [HostPackagePolicy], tested apart on the JVM.
 */
@RunWith(RobolectricTestRunner::class)
class HostPolicyTest {
    private val context: Application = RuntimeEnvironment.getApplication()

    private fun pinnedTo(vararg keys: ByteArray) = HostPolicy.pinned(context, listOf(KnownHost(HOST, keys.map(::sha256Hex).toSet())))

    @Test
    fun `pinned admits a host signed by a pinned certificate`() {
        installPackage(context, HOST, HOST_UID, listOf(OLD_KEY))

        assertEquals(Status.Code.OK, pinnedTo(OLD_KEY).checkAuthorization(HOST_UID).code)
    }

    @Test
    fun `pinned admits a host whose rotated lineage still includes the pinned certificate`() {
        installPackage(context, HOST, HOST_UID, listOf(OLD_KEY, NEW_KEY))

        assertEquals(Status.Code.OK, pinnedTo(OLD_KEY).checkAuthorization(HOST_UID).code)
    }

    @Test
    fun `pinned refuses a known host signed by another certificate`() {
        installPackage(context, HOST, HOST_UID, listOf(NEW_KEY))

        assertEquals(Status.Code.PERMISSION_DENIED, pinnedTo(OLD_KEY).checkAuthorization(HOST_UID).code)
    }

    @Test
    fun `pinned refuses a uid that is not a known host`() {
        installPackage(context, STRANGER, STRANGER_UID, listOf(OLD_KEY))

        assertEquals(Status.Code.PERMISSION_DENIED, pinnedTo(OLD_KEY).checkAuthorization(STRANGER_UID).code)
    }

    @Test
    fun `pinned refuses a uid with no packages`() {
        assertEquals(Status.Code.PERMISSION_DENIED, pinnedTo(OLD_KEY).checkAuthorization(HOST_UID).code)
    }

    @Test
    fun `anyCertificateOf admits a listed package under any certificate`() {
        installPackage(context, HOST, HOST_UID, listOf(NEW_KEY))
        val policy = HostPolicy.anyCertificateOf(context, packageNames = listOf(HOST))

        assertEquals(Status.Code.OK, policy.checkAuthorization(HOST_UID).code)
    }

    @Test
    fun `anyCertificateOf refuses a package that is not listed`() {
        installPackage(context, STRANGER, STRANGER_UID, listOf(OLD_KEY))
        val policy = HostPolicy.anyCertificateOf(context, packageNames = listOf(HOST))

        assertEquals(Status.Code.PERMISSION_DENIED, policy.checkAuthorization(STRANGER_UID).code)
    }

    @Test
    fun `anyCaller admits every uid`() {
        assertEquals(Status.Code.OK, HostPolicy.anyCaller().checkAuthorization(STRANGER_UID).code)
    }

    @Test
    fun `signerSha256s is the whole lineage of a package, as lowercase hex`() {
        installPackage(context, HOST, HOST_UID, listOf(OLD_KEY, NEW_KEY))

        assertEquals(setOf(sha256Hex(OLD_KEY), sha256Hex(NEW_KEY)), context.packageManager.signerSha256s(HOST))
    }

    @Test
    fun `signerSha256s of a package that is not installed is nothing`() {
        assertTrue(context.packageManager.signerSha256s("com.example.missing").isEmpty())
    }

    @Test
    fun `signerSha256s of a package with no signing info is nothing`() {
        installPackage(context, HOST, HOST_UID)

        assertTrue(context.packageManager.signerSha256s(HOST).isEmpty())
    }
}
