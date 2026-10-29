package com.binge.companion.sdk

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val COMPANION = "com.example.companion"
private const val OTHER = "com.example.other"

class HandOffTargetPolicyTest {
    private fun permits(
        companionPackage: String = COMPANION,
        intentPackage: String? = COMPANION,
        intentHasComponent: Boolean = false,
        resolvedPackage: String? = COMPANION,
        resolvedExported: Boolean = true,
    ) = HandOffTargetPolicy.permits(companionPackage, intentPackage, intentHasComponent, resolvedPackage, resolvedExported)

    @Test
    fun `an exported Activity inside the companion's package is startable`() {
        assertTrue(permits())
    }

    @Test
    fun `an Activity that does not resolve is refused`() {
        assertFalse(permits(resolvedPackage = null, resolvedExported = false))
    }

    @Test
    fun `an Activity in another package is refused`() {
        assertFalse(permits(resolvedPackage = OTHER))
    }

    @Test
    fun `an Activity that is not exported is refused`() {
        assertFalse(permits(resolvedExported = false))
    }

    @Test
    fun `an Intent not addressed to the companion's package is refused`() {
        assertFalse(permits(intentPackage = null))
        assertFalse(permits(intentPackage = OTHER))
    }

    @Test
    fun `an Intent that names a component is refused`() {
        assertFalse(permits(intentHasComponent = true))
    }

    @Test
    fun `an empty companion package is refused`() {
        assertFalse(permits(companionPackage = "", intentPackage = "", resolvedPackage = ""))
    }
}
