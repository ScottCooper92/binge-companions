package com.binge.companion.sdk.paging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ListRefreshesTest {
    private val refreshes = ListRefreshes<String>()

    @Test
    fun `a list has no refresh until one finishes`() {
        assertTrue(refreshes.latest.value.isEmpty())
    }

    @Test
    fun `a finished refresh reports the rows it wrote`() {
        refreshes.record("a", 3)

        assertEquals(
            3,
            refreshes.latest.value
                .getValue("a")
                .rowsWritten,
        )
    }

    @Test
    fun `a refresh that starts clears the last answer for its list only`() {
        refreshes.record("a", 3)
        refreshes.record("b", 1)

        refreshes.record("a", null)

        assertEquals(setOf("b"), refreshes.latest.value.keys)
    }

    @Test
    fun `two refreshes that wrote the same number are told apart`() {
        refreshes.record("a", 2)
        val first = refreshes.latest.value.getValue("a")

        refreshes.record("a", null)
        refreshes.record("a", 2)

        assertEquals(
            first.rowsWritten,
            refreshes.latest.value
                .getValue("a")
                .rowsWritten,
        )
        assertNotEquals(
            first.sequence,
            refreshes.latest.value
                .getValue("a")
                .sequence,
        )
    }
}
