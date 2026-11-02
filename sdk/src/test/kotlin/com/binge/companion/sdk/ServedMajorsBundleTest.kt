package com.binge.companion.sdk

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Bundle half of [readServedMajors], which [ServedMajorsTest] cannot reach: aapt types a bare number as an Int, so a
 * host that reads only the String form reports every single-major companion as declaring nothing (`docs/Architecture.md`
 * > Transport). The parsing itself is [servedMajors], tested apart.
 */
@RunWith(RobolectricTestRunner::class)
class ServedMajorsBundleTest {
    private val single = setOf(CompanionManifest.ACTION_REQUEST)

    private fun majors(configure: Bundle.() -> Unit): Set<Int> =
        Bundle().apply(configure).readServedMajors(CompanionManifest.ACTION_REQUEST, single)

    @Test
    fun `a bare number aapt typed as an Int is read`() {
        assertEquals(setOf(1), majors { putInt(CompanionManifest.META_MAJORS, 1) })
    }

    @Test
    fun `a String list is read`() {
        assertEquals(setOf(1, 2), majors { putString(CompanionManifest.META_MAJORS, "1,2") })
    }

    @Test
    fun `a value of another type declares nothing`() {
        assertEquals(emptySet<Int>(), majors { putBoolean(CompanionManifest.META_MAJORS, true) })
        assertEquals(emptySet<Int>(), majors { putFloat(CompanionManifest.META_MAJORS, 1.5f) })
    }

    @Test
    fun `an absent key declares nothing`() {
        assertEquals(emptySet<Int>(), majors { })
    }

    @Test
    fun `an action the Service does not filter on declares nothing, though the bare key is an Int`() {
        val bundle = Bundle().apply { putInt(CompanionManifest.META_MAJORS, 1) }

        assertEquals(emptySet<Int>(), bundle.readServedMajors(CompanionManifest.ACTION_LIBRARY, single))
    }
}
