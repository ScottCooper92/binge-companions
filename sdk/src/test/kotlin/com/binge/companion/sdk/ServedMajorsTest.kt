package com.binge.companion.sdk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The host reading a companion's declared majors (#167): scoped key first, the bare key as fallback. */
class ServedMajorsTest {
    private fun majors(action: String, vararg meta: Pair<String, String>): Set<Int> = servedMajors(action, meta.toMap()::get)

    @Test
    fun `a single-action Service's bare key is read`() {
        assertEquals(setOf(1), majors(CompanionManifest.ACTION_REQUEST, CompanionManifest.META_MAJORS to "1"))
        assertEquals(setOf(1, 2), majors(CompanionManifest.ACTION_REQUEST, CompanionManifest.META_MAJORS to "1, 2"))
    }

    @Test
    fun `a multi-action Service's scoped key wins over the bare one`() {
        val meta =
            arrayOf(
                CompanionManifest.META_MAJORS_REQUEST to "1",
                CompanionManifest.META_MAJORS_LIBRARY to "2",
                CompanionManifest.META_MAJORS to "9",
            )
        assertEquals(setOf(1), majors(CompanionManifest.ACTION_REQUEST, *meta))
        assertEquals(setOf(2), majors(CompanionManifest.ACTION_LIBRARY, *meta))
        assertEquals(setOf(9), majors(CompanionManifest.ACTION_STREAM, *meta))
    }

    @Test
    fun `nothing declared, or nothing readable, is no majors`() {
        assertEquals(emptySet<Int>(), majors(CompanionManifest.ACTION_REQUEST))
        assertEquals(emptySet<Int>(), majors(CompanionManifest.ACTION_REQUEST, CompanionManifest.META_MAJORS to "one,0,-2"))
        assertEquals(setOf(3), majors(CompanionManifest.ACTION_REQUEST, CompanionManifest.META_MAJORS to "x,3,"))
    }

    @Test
    fun `an action that is not a contract reads only the bare key`() {
        assertEquals(setOf(1), majors("com.example.OTHER", CompanionManifest.META_MAJORS to "1"))
    }
}
