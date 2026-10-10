package com.binge.companion.sdk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The host reading a companion's declared majors (#167): scoped keys for a multi-action Service, the bare key for one action. */
class ServedMajorsTest {
    private val single = setOf(CompanionManifest.ACTION_REQUEST)
    private val multi = setOf(CompanionManifest.ACTION_REQUEST, CompanionManifest.ACTION_LIBRARY)

    private fun majors(
        action: String,
        actions: Set<String>,
        vararg meta: Pair<String, String>,
    ): Set<Int> = servedMajors(action, actions, meta.toMap()::get)

    @Test
    fun `a single-action Service's bare key is read`() {
        assertEquals(setOf(1), majors(CompanionManifest.ACTION_REQUEST, single, CompanionManifest.META_MAJORS to "1"))
        assertEquals(setOf(1, 2), majors(CompanionManifest.ACTION_REQUEST, single, CompanionManifest.META_MAJORS to "1, 2"))
    }

    @Test
    fun `a single-action Service ignores a scoped key`() {
        assertEquals(emptySet<Int>(), majors(CompanionManifest.ACTION_REQUEST, single, CompanionManifest.META_MAJORS_REQUEST to "1"))
    }

    @Test
    fun `a multi-action Service reads each contract's scoped key`() {
        val meta =
            arrayOf(
                CompanionManifest.META_MAJORS_REQUEST to "1",
                CompanionManifest.META_MAJORS_LIBRARY to "2",
                CompanionManifest.META_MAJORS to "9",
            )
        assertEquals(setOf(1), majors(CompanionManifest.ACTION_REQUEST, multi, *meta))
        assertEquals(setOf(2), majors(CompanionManifest.ACTION_LIBRARY, multi, *meta))
    }

    @Test
    fun `a multi-action Service with only a bare key declares nothing`() {
        assertEquals(emptySet<Int>(), majors(CompanionManifest.ACTION_REQUEST, multi, CompanionManifest.META_MAJORS to "1"))
        assertEquals(emptySet<Int>(), majors(CompanionManifest.ACTION_LIBRARY, multi, CompanionManifest.META_MAJORS to "1"))
    }

    @Test
    fun `a multi-action Service missing one scoped key declares nothing for that contract`() {
        val meta = arrayOf(CompanionManifest.META_MAJORS_REQUEST to "1")
        assertEquals(setOf(1), majors(CompanionManifest.ACTION_REQUEST, multi, *meta))
        assertEquals(emptySet<Int>(), majors(CompanionManifest.ACTION_LIBRARY, multi, *meta))
    }

    @Test
    fun `nothing declared, or nothing readable, is no majors`() {
        assertEquals(emptySet<Int>(), majors(CompanionManifest.ACTION_REQUEST, single))
        assertEquals(emptySet<Int>(), majors(CompanionManifest.ACTION_REQUEST, single, CompanionManifest.META_MAJORS to "one,0,-2"))
        assertEquals(setOf(3), majors(CompanionManifest.ACTION_REQUEST, single, CompanionManifest.META_MAJORS to "x,3,"))
    }

    @Test
    fun `an action that is not a contract has no scoped key on a multi-action Service`() {
        assertEquals(emptySet<Int>(), majors("com.example.OTHER", multi + "com.example.OTHER", CompanionManifest.META_MAJORS to "1"))
    }
}
