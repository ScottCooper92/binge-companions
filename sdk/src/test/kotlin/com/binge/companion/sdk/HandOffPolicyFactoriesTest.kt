package com.binge.companion.sdk

import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

private const val HOST = "com.example.host"
private val KEY = byteArrayOf(7, 8, 9)

/** The three companion-side factories of [HandOffPolicy] (#282), wired to the real package manager. The decision is [HandOffCallerPolicy]. */
@RunWith(RobolectricTestRunner::class)
class HandOffPolicyFactoriesTest {
    private val context: Application = RuntimeEnvironment.getApplication()

    @Test
    fun `pinned admits the calling package when its certificate is pinned`() {
        installPackage(context, HOST, uid = 10_042, lineage = listOf(KEY))
        val policy = HandOffPolicy.pinned(context, listOf(KnownHost(HOST, setOf(sha256Hex(KEY)))))

        assertTrue(policy.permits(HOST))
    }

    @Test
    fun `pinned refuses a package signed by another certificate, and no caller at all`() {
        installPackage(context, HOST, uid = 10_042, lineage = listOf(byteArrayOf(1)))
        val policy = HandOffPolicy.pinned(context, listOf(KnownHost(HOST, setOf(sha256Hex(KEY)))))

        assertFalse(policy.permits(HOST))
        assertFalse(policy.permits(null))
    }

    @Test
    fun `anyCertificateOf admits a listed caller only`() {
        val policy = HandOffPolicy.anyCertificateOf(packageNames = listOf(HOST))

        assertTrue(policy.permits(HOST))
        assertFalse(policy.permits("com.example.stranger"))
    }

    @Test
    fun `anyCaller admits a caller that started the Activity for a result, and refuses one that did not`() {
        val policy = HandOffPolicy.anyCaller()

        assertTrue(policy.permits("com.example.anything"))
        assertFalse(policy.permits(null))
    }
}
